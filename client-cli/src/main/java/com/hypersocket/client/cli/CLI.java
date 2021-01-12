package com.hypersocket.client.cli;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.Prompt;
import com.hypersocket.client.i18n.I18N;
import com.hypersocket.client.rmi.ApplicationLauncherTemplate;
import com.hypersocket.client.rmi.CancelledException;
import com.hypersocket.client.rmi.ClientService;
import com.hypersocket.client.rmi.ConfigurationService;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionService;
import com.hypersocket.client.rmi.GUICallback;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.ResourceService;
import com.hypersocket.extensions.ExtensionDefinition;
import com.hypersocket.extensions.ExtensionPlace;

public class CLI extends UnicastRemoteObject implements GUICallback {

	static Logger log = LoggerFactory.getLogger(CLI.class);

	public static final String APPLICATION_ICON = "/app-icon.png";

	final static int DEFAULT_TIMEOUT = 10000;

	private int registrations = 0;

	private ConnectionService connectionService;
	private ConfigurationService configurationService;
	private ResourceService resourceService;
	private ClientService clientService;
	private boolean awaitingServiceStop = false;
	private int appsToUpdate = -1;
	private int appsUpdated = 0;
	private Updater updater;
	private CommandLine cli;
	private ConsoleProvider console;
	private boolean exitWhenDone;
	private Registry registry;
	private boolean interactive = false;
	
	protected CLI(String[] args) throws Exception {
		super();

		try {
			console = new NativeConsoleDevice();
		} catch (IllegalArgumentException iae) {
			console = new BufferedDevice();
		}
		
		try {
			/**
			 * Don't act as a CLI if we pass a command through in arguments
			 */
			if(args.length > 0) {
				runCommand(args);
			} else {
				interactive = true;
				runInteractive();
			}
		} catch (Exception e) {
			if(interactive)
				System.out.println(e.getMessage());
			else
				System.err.println(e.getMessage());
			System.exit(1);
		} finally {
			exitCli();
		}

	}

	@Override
	public boolean isInteractive() throws RemoteException {
		return interactive;
	}
	
	private void runCommand(String[] args) throws Exception {
		
		connectToService();
		CommandLineParser pp = new DefaultParser();
		Options opts = new Options();
		
		Command command = createCommand(args[0]);
		command.buildOptions(opts);
		
		cli = pp.parse(opts, args);
		command.run(this);
		
	}

	private void runInteractive() throws Exception {
		
		connectToService();
		
		CommandLineParser pp = new DefaultParser();

		try {
			
			do {
				
				try {
					
					String cmd = console.readLine("LogonBox> ");
					if(StringUtils.isNotBlank(cmd)) {
						List<String> newargs = parseQuotedString(cmd);
						newargs.removeIf(item -> item == null || "".equals(item));
						
						String [] args = newargs.toArray(new String[0]);
						
						if(args.length > 0) {
							
							Options opts = new Options();
							
							Command command = createCommand(args[0]);
							if(command == null)
								throw new NoSuchElementException(String.format("No command named %s", args[0]));
							command.buildOptions(opts);
							
							cli = pp.parse(opts, args);
							command.run(this);
						} 
					}
				
				}
				catch(Exception e) {
					if(interactive)
						System.out.println(String.format("Error: %s", e.getMessage()));
					else
						System.err.println(String.format("Error: %s", e.getMessage()));
				}
			} while(!exitWhenDone);
		} finally {
			exitCli();
		}
	}

	private static final long serialVersionUID = 4078585204004591626L;

	public ConsoleProvider getConsole() {
		return console;
	}
	
	public void exitWhenDone() {
		exitWhenDone = true;
	}

	public boolean isFirstRegistration() {
		return registrations == 1;
	}

	public void registered() {
		registrations++;
		log.info("Connected to local service");
	}

	@Override
	public void unregistered() {
		registrations--;
		log.info("Disconnected from local service");
		System.exit(0);
	}

	public void notify(String msg, int type) {
		if (log.isInfoEnabled()) {
			log.info(msg);
		}
	}

	/**
	 * Parse a space separated string into a list, treating portions quotes with
	 * single quotes as a single element. Single quotes themselves and spaces
	 * can be escaped with a backslash.
	 * 
	 * @param command
	 *            command to parse
	 * @return parsed command
	 */
	private static List<String> parseQuotedString(String command) {
		List<String> args = new ArrayList<String>();
		boolean escaped = false;
		boolean quoted = false;
		StringBuilder word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '"' && !escaped) {
				if (quoted) {
					quoted = false;
				} else {
					quoted = true;
				}
			} else if (c == '\\' && !escaped) {
				escaped = true;
			} else if (c == ' ' && !escaped && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString());
					word.setLength(0);
					;
				}
			} else {
				word.append(c);
			}
		}
		if (word.length() > 0)
			args.add(word.toString());
		return args;
	}

	private void connectToService() throws Exception {

		Properties properties = new Properties();
		FileInputStream in;
		try {
			if (Boolean.getBoolean("hypersocket.development")) {
				in = new FileInputStream(System.getProperty("user.home") + File.separator + ".logonbox"
						+ File.separator + "conf" + File.separator + "rmi.properties");
			} else {
				in = new FileInputStream("conf" + File.separator + "rmi.properties");
			}

			try {
				properties.load(in);
			} finally {
				in.close();
			}
		} catch (IOException e2) {
			log.warn("Could not load conf/rmi.properties file. Is the service running?");
		}
		int port = Integer.parseInt(properties.getProperty("port", "50000"));

		if (log.isDebugEnabled()) {
			log.debug("Connecting to local service on port " + port);
		}
		registry = LocateRegistry.getRegistry(port);

		connectionService = (ConnectionService) registry.lookup("connectionService");
		configurationService = (ConfigurationService) registry.lookup("configurationService");
		resourceService = (ResourceService) registry.lookup("resourceService");
		clientService = (ClientService) registry.lookup("clientService");
		
		clientService.registerGUI(this);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					clientService.unregisterGUI(CLI.this, false);
				} catch (RemoteException e) {
				}
			}
		});
	}

	private Command createCommand(String cmd) {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = getClass().getClassLoader();
		}
		try {
			@SuppressWarnings("unchecked")
			Class<Command> cmdClass = (Class<Command>) Class.forName(CLI.class.getPackage().getName() + ".commands."
					+ (cmd.substring(0, 1).toUpperCase() + cmd.substring(1)), true, cl);
			Command command = cmdClass.newInstance();
			return command;
		} catch (Exception e) {
			throw new IllegalStateException(String.format("%s not found", cmd));
		}
	}

	protected RenderedImage getImage(String name) throws IOException {
		InputStream is = getClass().getResourceAsStream(name);
		try {
			return ImageIO.read(is);
		} finally {
			IOUtils.closeQuietly(is);
		}
	}

	protected void changeLocale(String locale) {
	}

	public ConfigurationService getConfigurationService() {
		return configurationService;
	}

	@Override
	public Map<String, String> showPrompts(Connection connection, ResourceBundle resources, final List<Prompt> prompts,
			int attempts, boolean success) {
		final Map<String, String> results = new HashMap<String, String>();
		Logon logonDialog = new Logon(prompts, this);
		try {
			logonDialog.show();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
			return null;
		}
		Map<String, String> res = logonDialog.getResults();
		if (res != null) {
			results.putAll(res);
		}
		if (results.size() > 0) {
			return results;
		} else {
			return null;
		}
	}

	@Override
	public int executeAsUser(ApplicationLauncherTemplate launcherTemplate, String clientUsername,
			String connectedHostname) throws RemoteException {
		return 0;
	}

	@Override
	public void disconnected(Connection connection, String errorMessage) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info(String.format("Disconnected: %s", getUri(connection)));
		}
	}

	@Override
	public void failedToConnect(Connection connection, String errorMessage) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info(String.format("Connection failed: %s", errorMessage));
		}
	}

	@Override
	public void transportConnected(Connection connection) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info(String.format("Connected: %s", getUri(connection)));
		}
	}

	@Override
	public void loadResources(Connection connection) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info(String.format("Loading resources for %s", getUri(connection)));
		}
	}

	@Override
	public void ready(Connection connection) throws RemoteException {
		if (log.isInfoEnabled()) {
			log.info(String.format("Ready: %s", getUri(connection)));
		}
	}

	@Override
	public void onUpdateStart(final String app, final long totalBytesExpected, Connection connection)
			throws RemoteException {
		if (isUpdateCancelled()) {
			throw new CancelledException();
		}
		updater.start(app, totalBytesExpected);
	}

	@Override
	public void onUpdateProgress(final String app, final long sincelastProgress, final long totalSoFar,
			long totalBytesExpected) throws RemoteException {
		if (isUpdateCancelled()) {
			throw new CancelledException();
		}
		updater.progress(app, sincelastProgress, totalSoFar);
	}

	@Override
	public void onUpdateComplete(final long totalBytesTransfered, final String app) throws RemoteException {
		if (isUpdateCancelled()) {
			throw new RemoteException("Cancelled by user.");
		}
		updater.complete(app);
		appsUpdated++;
		log.info(
				String.format("Update of %s complete, have now updated %d of %d apps", app, appsUpdated, appsToUpdate));

	}

	@Override
	public void onUpdateFailure(final String app, final String message) {
		if (updater != null) {
			updater.failure(app, message);
			appsUpdated++;
		}
	}

	@Override
	public void onExtensionUpdateComplete(String app, ExtensionDefinition def) {
	}

	@Override
	public void onUpdateInit(int expectedApps) throws RemoteException {
		appsToUpdate = expectedApps;
		appsUpdated = 0;
		updater = new Updater() {
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

	@Override
	public ExtensionPlace getExtensionPlace() throws RemoteException {
		return ExtensionPlace.getDefault();
	}

	@Override
	public void onUpdateDone(final boolean restart, final String errorMessage) throws RemoteException {
		if (isUpdateCancelled()) {
			throw new CancelledException();
		}
		if (errorMessage == null) {
			if (restart) {
				log.info(String.format("All apps updated, starting restart process"));
				updater.done();
				awaitingServiceStop = true;
				new Thread() {
					public void run() {
						try {
							Thread.sleep(30000);
						} catch (InterruptedException e) {
						}
						if (awaitingServiceStop)
							updater.failure(null, I18N.getResource("client.update.serviceDidNotStopInTime"));
					}
				}.start();
			}
		} else {
			updater.failure(null, errorMessage);
		}
	}

	public boolean isUpdating() {
		return appsToUpdate > 0;
	}

	private boolean isUpdateCancelled() {
		return updater == null || updater.isCancelled();
	}

	@Override
	public void started(Connection connection) throws RemoteException {

	}

	@Override
	public void updateResource(Connection connection, ResourceUpdateType type, Resource resource) throws RemoteException {
	}

	public ConnectionService getConnectionService() {
		return connectionService;
	}

	public CommandLine getCommandLine() {
		return cli;
	}

	public ClientService getClientService() {
		return clientService;
	}

	public ResourceService getResourceService() {
		return resourceService;
	}

	public static String getUri(Connection connection) {
		if (connection == null) {
			return "";
		}
		return connection.getUri(false);
	}

	@Override
	public void ping() throws RemoteException {
		// Noop
	}

	private void exitCli() {
		System.exit(0);
	}
}
