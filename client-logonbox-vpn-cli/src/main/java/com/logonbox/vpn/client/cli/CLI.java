package com.logonbox.vpn.client.cli;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.PropertyConfigurator;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.cli.commands.About;
import com.logonbox.vpn.client.cli.commands.Config;
import com.logonbox.vpn.client.cli.commands.Connect;
import com.logonbox.vpn.client.cli.commands.Connections;
import com.logonbox.vpn.client.cli.commands.Create;
import com.logonbox.vpn.client.cli.commands.Debug;
import com.logonbox.vpn.client.cli.commands.Delete;
import com.logonbox.vpn.client.cli.commands.Disconnect;
import com.logonbox.vpn.client.cli.commands.Edit;
import com.logonbox.vpn.client.cli.commands.Exit;
import com.logonbox.vpn.client.cli.commands.Help;
import com.logonbox.vpn.client.cli.commands.Show;
import com.logonbox.vpn.client.cli.commands.Update;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.PromptingCertManager;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.dbus.DBusClient;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Spec;

@Command(name = "logonbox-vpn-cli", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.", subcommands = {
		Connections.class, Connect.class, Create.class, Delete.class, Disconnect.class, Exit.class, Show.class,
		About.class, Edit.class, Update.class, Debug.class, Config.class })
public class CLI extends AbstractDBusClient implements Runnable, CLIContext, DBusClient {

	static Logger log;

	public final static ResourceBundle BUNDLE = ResourceBundle.getBundle(CLI.class.getName());

	public static void main(String[] args) throws Exception {

		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if (logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(CLI.class.getResource("/default-log4j-cli.properties"));
		} else {
			File logConfigFile = new File(logConfigPath);
			if (logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(CLI.class.getResource("/default-log4j-cli.properties"));
		}

		log = LoggerFactory.getLogger(CLI.class);
		;

		CLI cli = new CLI();
		try {
			System.exit(new CommandLine(cli).execute(args));
		} finally {
			cli.console.out().flush();
		}
	}

	@Option(names = { "-M", "--monitor" }, description = "Monitor events.")
	private boolean monitor;
	@Option(names = { "-q", "--quiet" }, description = "Don't output any messages about current state.")
	private boolean quiet;
	@Spec
	private CommandSpec spec;

	private int appsToUpdate = -1;
	private int appsUpdated = 0;
	private Thread awaitingServiceStop;
	private ConsoleProvider console;
	private boolean interactive = false;
	private Updater updater;
	private boolean exitWhenDone;
	private Thread awaitingServiceStart;

	public CLI() throws DBusException {
		/* TODO: Note this is a temporary hack due to issues with update server */
		super(ExtensionTarget.CLIENT_GUI);
		setSupportsAuthorization(true);
		try {
			console = new NativeConsoleDevice();
		} catch (IllegalArgumentException iae) {
			console = new BufferedDevice();
		}

		addBusLifecycleListener(new BusLifecycleListener() {

			@Override
			public void busInitializer(DBusConnection connection) throws DBusException {
				log.info("Configuring Bus");

				boolean finishUpdate = awaitingServiceStart != null;
				giveUpWaitingForServiceStart();

				DBusConnection bus = getBus();
				if (monitor) {
					bus.addGenericSigHandler(new DBusMatchRule((String) null, "com.logonbox.vpn.VPN", (String) null),
							(sig) -> {
								try {
									getConsole().err().println(sig);
								} catch (IOException e) {
									throw new IllegalStateException("Cannot write to console.");
								}
							});
					bus.addGenericSigHandler(
							new DBusMatchRule((String) null, "com.logonbox.vpn.Connection", (String) null), (sig) -> {
								try {
									getConsole().err().println(sig);
								} catch (IOException e) {
									throw new IllegalStateException("Cannot write to console.");
								}
							});
				}
				bus.addSigHandler(VPN.Exit.class, new DBusSigHandler<VPN.Exit>() {
					@Override
					public void handle(VPN.Exit sig) {
						exitCLI();
					}
				});

				bus.addSigHandler(VPN.UpdateStart.class, new DBusSigHandler<VPN.UpdateStart>() {
					@Override
					public void handle(VPN.UpdateStart sig) {
						log.debug("Update Start");
						createUpdater();
						if (isUpdateCancelled()) {
							getVPN().cancelUpdate();
						} else {
							updater.start(sig.getApp(), sig.getTotalBytesExpected());
						}
					}
				});

				bus.addSigHandler(VPN.UpdateComplete.class, new DBusSigHandler<VPN.UpdateComplete>() {
					@Override
					public void handle(VPN.UpdateComplete sig) {
						log.debug("Update Complete");
						createUpdater();
						if (isUpdateCancelled()) {
							getVPN().cancelUpdate();
						} else {
							updater.complete(sig.getApp());
							appsUpdated++;
							log.info(String.format("Update of %s complete, have now updated %d of %d apps",
									sig.getApp(), appsUpdated, appsToUpdate));
						}
					}
				});
				bus.addSigHandler(VPN.UpdateProgress.class, new DBusSigHandler<VPN.UpdateProgress>() {
					@Override
					public void handle(VPN.UpdateProgress sig) {
						createUpdater();
						if (isUpdateCancelled()) {
							getVPN().cancelUpdate();
						} else
							updater.progress(sig.getApp(), sig.getSinceLastProgress(), sig.getTotalSoFar());
					}
				});
				bus.addSigHandler(VPN.UpdateFailure.class, new DBusSigHandler<VPN.UpdateFailure>() {
					@Override
					public void handle(VPN.UpdateFailure sig) {
						log.debug("Update Failure");
						createUpdater();
						updater.failure(sig.getApp(), sig.getMessage());
						appsUpdated++;
					}
				});
				bus.addSigHandler(VPN.UpdateInit.class, new DBusSigHandler<VPN.UpdateInit>() {
					@Override
					public void handle(VPN.UpdateInit sig) {
						log.debug("Update Init");
						createUpdater();
						appsToUpdate = sig.getApps();
						appsUpdated = 0;
					}
				});

				bus.addSigHandler(VPN.UpdateDone.class, new DBusSigHandler<VPN.UpdateDone>() {
					@Override
					public void handle(VPN.UpdateDone sig) {
						log.debug("Update Done");
						createUpdater();
						if (isUpdateCancelled()) {
							getVPN().cancelUpdate();
						} else {
							if (sig.getFailureMessage() == null || sig.getFailureMessage().equals("")) {
								updater.done();
								if (sig.isRestart()) {
									/* Can stop local bus now to avoid errors if server stops 
									 * unexpectedly
									 */
									
									
									log.debug(String.format("Apps updated, starting restart process"));
									awaitingServiceStop = new Thread() {
										@Override
										public void run() {
											try {
												Thread.sleep(30000);
											} catch (InterruptedException e) {
											}
											if (awaitingServiceStop != null)
												updater.failure(null,
														BUNDLE.getString("client.update.serviceDidNotStopInTime"));
										}
									};
									awaitingServiceStop.start();
								}
							} else {
								updater.failure(null, sig.getFailureMessage());
							}
						}
					}
				});

				if (finishUpdate) {
					log.debug("Finishing Update");
					createUpdater();
					updater.awaitingRestart();
					getScheduler().schedule(() -> {
						exit();
						System.exit(99);
					}, 5, TimeUnit.SECONDS);
				}
			}

			@Override
			public void busGone() {
				if (awaitingServiceStop != null) {
					// Bridge lost as result of update, wait for it to come back
					giveUpWaitingForServiceStop();
					log.debug(String.format("Service stopped, awaiting restart"));
					awaitingServiceStart = new Thread() {
						@Override
						public void run() {
							try {
								Thread.sleep(30000);
							} catch (InterruptedException e) {
							}
							if (awaitingServiceStop != null)
								giveUpWaitingForServiceStart();
						}
					};
					awaitingServiceStart.start();
				}
			}

		});
	}

	@Override
	public boolean isQuiet() {
		return quiet;
	}

	@Override
	public ConsoleProvider getConsole() {
		lazyInit();
		return console;
	}

	@Override
	public void exitWhenDone() {
		exitWhenDone = true;
	}

	public void exitCLI() {
		exit();
		System.exit(0);
	}

	@Override
	public void about() throws IOException {
		ConsoleProvider console = getConsole();
		PrintWriter writer = console.out();
		writer.println(String.format("CLI Version: %s",
				HypersocketVersion.getVersion("com.logonbox/client-logonbox-vpn-cli")));
		try {
			VPN vpn = getVPN();
			writer.println(String.format("Service Version: %s", vpn.getVersion()));
			writer.println(String.format("Device Name: %s", vpn.getDeviceName()));
			writer.println(String.format("Device UUID: %s", vpn.getUUID()));
		} catch (IllegalStateException ise) {
			writer.println("Service not available.");
		}
		console.flush();
	}

	@Override
	public void run() {
		try {
			about();
			runPrompt();
			console.out().println();
			console.flush();
		} catch (Exception e1) {
			throw new IllegalStateException("Failed to open console.", e1);
		} finally {
			exitCLI();
		}
	}

	private void runPrompt() throws IOException {
		PrintWriter err = console.err();
		do {
			try {
				String cmd = console.readLine("LogonBox VPN> ");
				if (cmd == null) {
					exitWhenDone = true;
				} else if (StringUtils.isNotBlank(cmd)) {
					List<String> newargs = Util.parseQuotedString(cmd);
					newargs.removeIf(item -> item == null || "".equals(item));
					String[] args = newargs.toArray(new String[0]);
					if (args.length > 0) {
						CommandLine cl = new CommandLine(new InteractiveConsole());
						cl.setTrimQuotes(true);
						cl.setUnmatchedArgumentsAllowed(true);
						cl.setUnmatchedOptionsAllowedAsOptionParameters(true);
						cl.setUnmatchedOptionsArePositionalParams(true);
						cl.execute(args);
					}
				}

			} catch (Exception e) {
				err.println(String.format("%s", e.getMessage()));
			}
		} while (!exitWhenDone);
	}
	

	protected void createUpdater() {
		if (updater == null) {
			log.debug("Creating updater");
			updater = new Updater(CLI.this) {
				@Override
				public void close() {
					super.close();
					updater = null;
					appsToUpdate = 0;
					appsUpdated = 0;
				}
			};
			updater.show();
		}
	}

	@Override
	protected PromptingCertManager createCertManager() {
		return new PromptingCertManager(BUNDLE) {

			@Override
			protected boolean isToolkitThread() {
				return true;
			}

			@Override
			protected void runOnToolkitThread(Runnable r) {
				r.run();
			}

			@Override
			protected boolean promptForCertificate(PromptType alertType, String title, String content, String key,
					String hostname, String message, Preferences preference) {

				try {

					PrintWriter ou = console.out();
					ou.println(alertType.name());
					ou.println(Util.repeat(alertType.name().length(), '-'));
					ou.println();

					/* Title */
					if (StringUtils.isNotBlank(title)) {
						ou.println(title);
						ou.println(Util.titleUnderline(title.length()));
						ou.println();
					}

					/* Content */
					ou.println(MessageFormat.format(content, hostname, message));
					ou.println();

					console.flush();

					String yesAbbrev = BUNDLE.getString("certificate.yes");
					String noAbbrev = BUNDLE.getString("certificate.no");
					String saveAbbrev = BUNDLE.getString("certificate.save");

					String reply = console.readLine(BUNDLE.getString("certificate.prompt"), yesAbbrev,
							"(" + noAbbrev + ")", saveAbbrev);
					if (reply == null)
						throw new IllegalStateException("Aborted.");

					if (saveAbbrev.equalsIgnoreCase(reply)) {
						preference.putBoolean(key, true);
						return true;
					} else if (yesAbbrev.equalsIgnoreCase(reply)) {
						return true;
					} else
						return false;
				} catch (IOException ioe) {
					throw new IllegalStateException("Failed to prompt for certificate.", ioe);
				}
			}

		};
	}

	@Override
	protected boolean isInteractive() {
		return interactive;
	}

	private void giveUpWaitingForServiceStart() {
		if (awaitingServiceStart != null) {
			Thread t = awaitingServiceStart;
			awaitingServiceStart = null;
			t.interrupt();
		}
	}

	private void giveUpWaitingForServiceStop() {
		if (awaitingServiceStop != null) {
			Thread t = awaitingServiceStop;
			awaitingServiceStop = null;
			t.interrupt();
		}
	}

	private boolean isUpdateCancelled() {
		return updater != null && updater.isCancelled();
	}

	@Command(name = "logonbox-vpn-cli-interactive", mixinStandardHelpOptions = true, description = "Interactive shell.", subcommands = {
			Connections.class, Connect.class, Create.class, Delete.class, Disconnect.class, Exit.class, Show.class,
			About.class, Edit.class, Update.class, Debug.class, Help.class, Config.class })

	class InteractiveConsole implements Runnable, CLIContext {
		@Override
		public void run() {
			throw new ParameterException(spec.commandLine(), "Missing required subcommand");
		}

		@Override
		public VPN getVPN() {
			return CLI.this.getVPN();
		}

		@Override
		public VPNConnection getVPNConnection(long connectionId) {
			return CLI.this.getVPNConnection(connectionId);
		}

		@Override
		public ConsoleProvider getConsole() {
			return CLI.this.getConsole();
		}

		@Override
		public List<VPNConnection> getVPNConnections() {
			return CLI.this.getVPNConnections();
		}

		@Override
		public void about() throws IOException {
			CLI.this.about();
		}

		@Override
		public void exitWhenDone() {
			CLI.this.exitWhenDone();
		}

		@Override
		public boolean isQuiet() {
			return CLI.this.isQuiet();
		}

		@Override
		public DBusConnection getBus() {
			return CLI.this.getBus();
		}

		@Override
		public PromptingCertManager getCertManager() {
			return CLI.this.getCertManager();
		}
	}
}
