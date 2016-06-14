package com.hypersocket.client.gui.cli;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.jgntp.GntpClient;
import com.google.code.jgntp.GntpNotificationInfo;
import com.google.common.io.Closeables;
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

	int timeout = DEFAULT_TIMEOUT;
	GntpClient client;
	GntpNotificationInfo notif1;
	int registrations = 0;

	ConnectionService connectionService;
	ConfigurationService configurationService;
	ResourceService resourceService;

	ClientService clientService;

	boolean suspendShellClose = false;
	boolean awaitingServiceStop = false;
	boolean awaitingServiceStart = false;

	private int appsToUpdate = -1;
	private int appsUpdated = 0;
	private Updater updater;
	private CommandLine cli;
	private ConsoleProvider console;
	private boolean exitWhenDone;

	private Registry registry;

	protected CLI(String[] args) throws Exception {
		super();

		try {
			console = new NativeConsoleDevice();
		} catch (IllegalArgumentException iae) {
			console = new BufferedDevice();
		}
		
		Options opts = new Options();

		if(args.length > 0) {
			try {
				Command command = createCommand(args[0]);
				command.buildOptions(opts);
			}
			catch(Exception e) {
				// Ignore, will get caught later
			}
		}
		

		CommandLineParser pp = new DefaultParser();
		cli = pp.parse(opts, args);

		connectToService();

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
		setOnlineState(true);
		showPopupMessage("Connected to local Hypersocket service", "Hypersocket Client");
	}

	@Override
	public void unregistered() {
		registrations--;
		setOnlineState(false);
		showPopupMessage("Disconnected from local Hypersocket service", "Hypersocket Client");
		System.exit(0);
	}

	private void setOnlineState(final boolean online) {
		if (log.isInfoEnabled()) {
			log.info("Setting online state to " + online);
		}
	}

	public void notify(String msg, int type) {

		switch (type) {
		case NOTIFY_CONNECT:
		case NOTIFY_DISCONNECT:

			// rebuildLaunchMenu();

			break;
		default:
			break;
		}
		showPopupMessage(msg, "Hypersocket Client");
	}

	private void showPopupMessage(final String message, final String title) {
		System.out.println("** " + title + "** " + message);
	}

	private void connectToService() throws Exception {

		Properties properties = new Properties();
		FileInputStream in;
		try {
			if (Boolean.getBoolean("hypersocket.development")) {
				in = new FileInputStream(System.getProperty("user.home") + File.separator + ".hypersocket"
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
			e2.printStackTrace();
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

		// Runtime.getRuntime().addShutdownHook(new Thread() {
		// public void run() {
		// System.out.println("Unregistering");
		// }
		// });

		//
		String cmd = cli.getArgs()[0];
		Command command = createCommand(cmd);
		try {
			command.run(this);
		} finally {
			if (!exitWhenDone)
				exitCli();
		}
	}

	private Command createCommand(String cmd)
			throws ClassNotFoundException, InstantiationException, IllegalAccessException {
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		if (cl == null) {
			cl = getClass().getClassLoader();
		}
		@SuppressWarnings("unchecked")
		Class<Command> cmdClass = (Class<Command>) Class.forName(CLI.class.getPackage().getName() + ".commands."
				+ (cmd.substring(0, 1).toUpperCase() + cmd.substring(1)), true, cl);
		Command command = cmdClass.newInstance();
		return command;
	}

	protected RenderedImage getImage(String name) throws IOException {
		InputStream is = getClass().getResourceAsStream(name);
		try {
			return ImageIO.read(is);
		} finally {
			Closeables.closeQuietly(is);
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
	}

	@Override
	public void failedToConnect(Connection connection, String errorMessage) throws RemoteException {
	}

	@Override
	public void transportConnected(Connection connection) throws RemoteException {
	}

	@Override
	public void loadResources(Connection connection) throws RemoteException {
		System.out.println(String.format("Loading resources for %s", getUri(connection)));
	}

	@Override
	public void ready(Connection connection) throws RemoteException {
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
		System.out.println(String.format("%s is ready", getUri(connection)));
		if (exitWhenDone) {
			exitCli();
		}

	}

	@Override
	public void updateResource(ResourceUpdateType type, Resource resource) throws RemoteException {
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
		String uri = "https://" + connection.getHostname();
		if (connection.getPort() != 443) {
			uri += ":" + connection.getPort();
		}
		uri += connection.getPath();
		return uri;
	}

	private void exitCli() {
		System.out.println("Exiting");
		new Thread() {
			public void run() {

				try {
					clientService.unregisterGUI(CLI.this);
				} catch (RemoteException e) {
				}
			}
		}.start();
	}
}
