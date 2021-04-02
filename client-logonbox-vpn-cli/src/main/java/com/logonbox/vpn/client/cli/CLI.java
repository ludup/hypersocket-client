package com.logonbox.vpn.client.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.cli.commands.About;
import com.logonbox.vpn.client.cli.commands.Connect;
import com.logonbox.vpn.client.cli.commands.Connections;
import com.logonbox.vpn.client.cli.commands.Create;
import com.logonbox.vpn.client.cli.commands.Delete;
import com.logonbox.vpn.client.cli.commands.Disconnect;
import com.logonbox.vpn.client.cli.commands.Edit;
import com.logonbox.vpn.client.cli.commands.Exit;
import com.logonbox.vpn.client.cli.commands.Show;
import com.logonbox.vpn.common.client.AbstractDBusClient;
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

@Command(name = "logonbox-vpn-cli", mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.", subcommands = {
		Connections.class, Connect.class, Create.class, Delete.class, Disconnect.class, Exit.class, Show.class,
		About.class, Edit.class })
public class CLI extends AbstractDBusClient implements Runnable, CLIContext, DBusClient {

	static Logger log = LoggerFactory.getLogger(CLI.class);

	final static ResourceBundle bundle = ResourceBundle.getBundle(CLI.class.getName());

	public static void main(String[] args) throws Exception {
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
	private boolean awaitingServiceStop = false;
	private ConsoleProvider console;
	private boolean interactive = false;
	private Updater updater;
	private boolean exitWhenDone;

	public CLI() {
		super();
		try {
			console = new NativeConsoleDevice();
		} catch (IllegalArgumentException iae) {
			console = new BufferedDevice();
		}
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
		PrintWriter writer = getConsole().out();
		writer.println(String.format("CLI Version: %s", HypersocketVersion.getVersion("client-logonbox-vpn-ccli")));
		writer.println(String.format("Service Version: %s", getVPN().getVersion()));
		writer.println(String.format("Device Name: %s", getVPN().getDeviceName()));
		writer.println(String.format("Device UUID: %s", getVPN().getUUID()));
	}

	@Override
	public void run() {
		try {
			PrintWriter err = console.err();
			about();
			do {
				try {
					String cmd = console.readLine("LogonBox VPN> ");
					if (StringUtils.isNotBlank(cmd)) {
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
		} catch (Exception e1) {
			e1.printStackTrace();
			throw new IllegalStateException("Failed to open console.", e1);
		} finally {
			exitCLI();
		}
	}

	@Override
	protected boolean isInteractive() {
		return interactive;
	}

	protected void init() throws Exception {
		super.init();

		if (monitor) {
			getBus().addGenericSigHandler(new DBusMatchRule((String) null, "com.logonbox.vpn.VPN", (String) null),
					(sig) -> {
						try {
							getConsole().err().println(sig);
						} catch (IOException e) {
							throw new IllegalStateException("Cannot write to console.");
						}
					});
			getBus().addGenericSigHandler(
					new DBusMatchRule((String) null, "com.logonbox.vpn.Connection", (String) null), (sig) -> {
						try {
							getConsole().err().println(sig);
						} catch (IOException e) {
							throw new IllegalStateException("Cannot write to console.");
						}
					});
		}

		getBus().addSigHandler(VPN.UpdateStart.class, new DBusSigHandler<VPN.UpdateStart>() {
			@Override
			public void handle(VPN.UpdateStart sig) {
				if (isUpdateCancelled()) {
					getVPN().cancelUpdate();
				} else
					updater.start(sig.getApp(), sig.getTotalBytesExpected());
			}
		});

		getBus().addSigHandler(VPN.UpdateComplete.class, new DBusSigHandler<VPN.UpdateComplete>() {
			@Override
			public void handle(VPN.UpdateComplete sig) {
				if (isUpdateCancelled()) {
					getVPN().cancelUpdate();
				} else {
					updater.complete(sig.getApp());
					appsUpdated++;
					log.info(String.format("Update of %s complete, have now updated %d of %d apps", sig.getApp(),
							appsUpdated, appsToUpdate));
				}
			}
		});
		getBus().addSigHandler(VPN.UpdateProgress.class, new DBusSigHandler<VPN.UpdateProgress>() {
			@Override
			public void handle(VPN.UpdateProgress sig) {
				if (isUpdateCancelled()) {
					getVPN().cancelUpdate();
				} else
					updater.progress(sig.getApp(), sig.getSinceLastProgress(), sig.getTotalSoFar());
			}
		});
		getBus().addSigHandler(VPN.UpdateFailure.class, new DBusSigHandler<VPN.UpdateFailure>() {
			@Override
			public void handle(VPN.UpdateFailure sig) {
				if (updater != null) {
					updater.failure(sig.getApp(), sig.getMessage());
					appsUpdated++;
				}
			}
		});
		getBus().addSigHandler(VPN.UpdateInit.class, new DBusSigHandler<VPN.UpdateInit>() {
			@Override
			public void handle(VPN.UpdateInit sig) {
				appsToUpdate = sig.getApps();
				appsUpdated = 0;
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
		});

		getBus().addSigHandler(VPN.UpdateDone.class, new DBusSigHandler<VPN.UpdateDone>() {
			@Override
			public void handle(VPN.UpdateDone sig) {
				if (isUpdateCancelled()) {
					getVPN().cancelUpdate();
				} else {
					if (sig.getFailureMessage() == null || sig.getFailureMessage().equals("")) {
						if (sig.isRestart()) {
							log.info(String.format("Connections apps updated, starting restart process"));
							updater.done();
							awaitingServiceStop = true;
							new Thread() {
								@Override
								public void run() {
									try {
										Thread.sleep(30000);
									} catch (InterruptedException e) {
									}
									if (awaitingServiceStop)
										updater.failure(null,
												bundle.getString("client.update.serviceDidNotStopInTime"));
								}
							}.start();
						}
					} else {
						updater.failure(null, sig.getFailureMessage());
					}
				}
			}
		});
	}

	private boolean isUpdateCancelled() {
		return updater == null || updater.isCancelled();
	}

	@Command(name = "logonbox-vpn-cli-interactive", mixinStandardHelpOptions = true, description = "Interactive shell.", subcommands = {
			Connections.class, Connect.class, Create.class, Delete.class, Disconnect.class, Exit.class, Show.class,
			About.class, Edit.class })

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
	}
}
