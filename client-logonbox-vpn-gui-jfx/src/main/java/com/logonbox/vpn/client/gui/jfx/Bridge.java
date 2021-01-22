package com.logonbox.vpn.client.gui.jfx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.Prompt;
import com.hypersocket.extensions.ExtensionDefinition;
import com.hypersocket.extensions.ExtensionPlace;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;

import javafx.application.Platform;

@SuppressWarnings({ "serial" })
public class Bridge extends UnicastRemoteObject implements GUICallback {

	static Logger log = LoggerFactory.getLogger(Bridge.class);

	private ClientService clientService;
	private ConfigurationService configurationService;
	private boolean connected;
	private List<Listener> listeners = new ArrayList<>();
	private ConnectionService connectionService;

	static int failedConnectionAttempts = 0;
	
	public interface Listener {

		void connecting(Connection connection);

		void finishedConnecting(Connection connection, Exception e);

		void started(Connection connection);

		void disconnecting(Connection connection);

		void disconnected(Connection connection, Exception e);

		void bridgeEstablished();

		void bridgeLost();

		void ping();

		Map<String, String> showPrompts(Connection connection, ResourceBundle resources, List<Prompt> prompts, int attempts, boolean success);

		void initUpdate(int apps, UIState mode);

		void initDone(boolean restart, String errorMessage);

		void startingUpdate(String app, long totalBytesExpected, Connection connection);

		void updateProgressed(String app, long sincelastProgress,
				long totalSoFar, long totalBytesExpected);

		void updateComplete(String app, long totalBytesTransfered);

		void updateFailure(String app, String message);

		void extensionUpdateComplete(String app, ExtensionDefinition def);
	}
	
	@Override
	public boolean isInteractive() throws RemoteException {
		return true;
	}

	public Bridge() throws RemoteException {
		super();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				try {
					if (clientService != null) {
						clientService.unregisterGUI(Bridge.this, false);
					}
				} catch (RemoteException e) {
				}
			}
		});
	}
	
	public void start() {
		new RMIConnectThread().start();
	}

	public void addListener(Listener l) {
		listeners.add(l);
	}

	public void removeListener(Listener l) {
		listeners.add(l);
	}

	public ConnectionService getPeerConfigurationService() {
		return connectionService;
	}

	public ClientService getClientService() {
		return clientService;
	}

	public ConfigurationService getConfigurationService() {
		return configurationService;
	}
	
	public boolean isConnected() {
		return connected;
	}

	private void connectToService() throws RemoteException, NotBoundException {

		Properties properties = new Properties();
		FileInputStream in;
		try {
			String path;
			if(System.getProperty("hypersocket.rmi") != null)  {
				path = System.getProperty("hypersocket.rmi");
			}
			else if (Boolean.getBoolean("hypersocket.development")) {
				path = System.getProperty("user.home")
						+ File.separator + ".logonbox" + File.separator
						+ "conf" + File.separator
						+ "rmi.properties";
			} else {
				path = "conf" + File.separator
						+ "rmi.properties";
			}
			in = new FileInputStream(path);
			log.info("Reading RMI port from " + path);

			try {
				properties.load(in);
			} finally {
				in.close();
			}
		} catch (IOException e2) {
			log.warn("Could not load conf/rmi.properties file. Is the service running?");
		}
		int port = Integer.parseInt(properties.getProperty("port", "50000"));

		try {

			if (log.isDebugEnabled()) {
				log.debug("Connecting to local service on port " + port);
			}
			
			Registry registry = LocateRegistry.getRegistry(port);

			configurationService = (ConfigurationService) registry
					.lookup("configurationService");

			connectionService = (ConnectionService) registry
					.lookup("connectionService");

			clientService = (ClientService) registry.lookup("clientService");
			
			clientService.registerGUI(this);
			failedConnectionAttempts = 0;
			connected = true;
			for (Listener l : listeners) {
				l.bridgeEstablished();
			}

			new RMIStatusThread().start();
		} catch (Throwable e) {
			int maxAttempts = Integer.parseInt(System.getProperty("hypersocket.maxAttempts", "0"));
			if(maxAttempts > 0 && failedConnectionAttempts > maxAttempts) {
				log.info("Shutting down client. Cannot connect to service");
				System.exit(0);
			}
			failedConnectionAttempts++;
			if (log.isDebugEnabled()) {
				log.debug("Failed to connect to local service on port " + port, e);
			}
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e1) {
				if(log.isInfoEnabled()) {
					log.info("Interrupted during sleep waiting for service. Exiting");
				}
				System.exit(0);
			}
			new RMIConnectThread().start();
		}

	}

	class RMIConnectThread extends Thread {
		public void run() {
			try {
				connectToService();
			} catch (Exception e) {
				if (log.isDebugEnabled()) {
					log.debug("Failed to connect to service", e);
				}
			}
		}
	}

	class RMIStatusThread extends Thread {
		public void run() {
			try {
				boolean running = true;
				while (running) {
					try {
						Thread.sleep(1000);
					} catch (Exception e) {
					}
					if (clientService != null) {
						try {
							clientService.ping();
							for (Listener l : listeners) {
								l.ping();
							}
						} catch (Exception e) {
							running = false;
							log.error("Failed to get local service status", e);
						}
					}
				}
			} finally {
				connected = false;
				for (Listener l : listeners) {
					l.bridgeLost();
				}
				new RMIConnectThread().start();
			}
		}
	}

	@Override
	public void registered() throws RemoteException {
		System.err.println("[[REGISTERED]]");
	}

	@Override
	public void unregistered() throws RemoteException {
		System.err.println("[[UNREGISTERED]]");
	}

	@Override
	public void notify(String msg, int type) throws RemoteException {
		System.err.println("[[NOTIFY]] " + msg + " (" + type + ")");
		Platform.runLater(new Runnable() {
			public void run() {
				UI.getInstance().notify(msg, type);
			}
		});
	}

	@Override
	public Map<String, String> showPrompts(Connection connection, ResourceBundle attached, List<Prompt> prompts, int attempts, boolean success)
			throws RemoteException {
		for (Listener l : new ArrayList<Listener>(listeners)) {
			Map<String, String> m = l.showPrompts(connection, attached, prompts, attempts, success);
			if (m != null) {
				return m;
			}
		}
		return null;
	}

	public void disconnect(Connection connection) throws RemoteException {
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.disconnecting(connection);
		}
		log.info(String.format("Disconnecting from %s",
				connection.getUri(false)));
		clientService.disconnect(connection);
	}

	public void connect(Connection connection) throws RemoteException {
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.connecting(connection);
		}
		log.info(String.format("Connecting to %s",
				connection.getUri(false)));
		clientService.connect(connection);
	}

	@Override
	public void disconnected(Connection connection, String errorMessage)
			throws RemoteException {
		log.info("Bridge disconnected " + connection  + " (" + errorMessage + ")");
		Exception e = errorMessage == null ? null : new Exception(errorMessage);
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.disconnected(connection, e);
		}
	}

	@Override
	public void transportConnected(Connection connection)
			throws RemoteException {
	}

	@Override
	public void started(Connection connection) throws RemoteException {
		log.info("Connection " + connection + " is now started");
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.started(connection);
		}
		notify(connection.getHostname() + " connected",
				GUICallback.NOTIFY_CONNECT);
	}

	@Override
	public void ready(Connection connection) throws RemoteException {
		log.info("Connection " + connection + " is now ready");
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.finishedConnecting(connection, null);
		}
	}

	@Override
	public void failedToConnect(Connection connection, String errorMessage)
			throws RemoteException {
		log.error(String.format("Failed to connect. %s", errorMessage));
		Exception e = errorMessage == null ? null : new Exception(errorMessage);
		for (Listener l : new ArrayList<Listener>(listeners)) {
			l.finishedConnecting(connection, e);
		}
	}

	public int getActiveConnections() {
		int active = 0;
		if (isConnected()) {
			try {
				for (ConnectionStatus s : clientService.getStatus()) {
					if (s.getStatus() == ConnectionStatus.CONNECTED) {
						active++;
					}
				}
			} catch (RemoteException e) {
				log.error("Failed to get active connections.", e);
			}
		}
		return active;
	}
	
	public int getActiveButNonPersistentConnections() {
		int active = 0;
		if (isConnected()) {
			try {
				for (ConnectionStatus s : clientService.getStatus()) {
					if (s.getStatus() == ConnectionStatus.CONNECTED && !s.getConnection().isStayConnected()) {
						active++;
					}
				}
			} catch (RemoteException e) {
				log.error("Failed to get active connections.", e);
			}
		}
		return active;
	}

	public void disconnectAll() {
		try {
			for (ConnectionStatus s : clientService.getStatus()) {
				if (s.getStatus() == ConnectionStatus.CONNECTED
						|| s.getStatus() == ConnectionStatus.CONNECTING) {
					try {
						disconnect(s.getConnection());
					} catch (RemoteException re) {
						log.error("Failed to disconnect "
								+ s.getConnection().getId(), re);
					}

				}
			}
		} catch (RemoteException e) {
			log.error("Failed to disconnect all.", e);
		}
	}

	@Override
	public void onUpdateStart(String app, long totalBytesExpected, Connection connection)
			throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.startingUpdate(app, totalBytesExpected, connection);
				}
			}
		});
	}

	@Override
	public void onUpdateProgress(String app, long sincelastProgress,
			long totalSoFar, long totalBytesExpected) throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.updateProgressed(app, sincelastProgress, totalSoFar, totalBytesExpected);
				}
			}
		});
	}

	@Override
	public void onUpdateComplete(long totalBytesTransfered, String app)
			throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.updateComplete(app, totalBytesTransfered);
				}
			}
		});
	}

	@Override
	public void onUpdateFailure(String app, String message)
			throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.updateFailure(app, message);
				}
			}
		});
	}

	@Override
	public void onExtensionUpdateComplete(String app, ExtensionDefinition def)
			throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.extensionUpdateComplete(app, def);
				}
			}
		});
	}

	@Override
	public void onUpdateInit(final int expectedApps) throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				// TODO
//				for (Listener l : new ArrayList<Listener>(listeners)) {
//					l.initUpdate(expectedApps, UI.getInstance().getMode());
//				}
			}
		});
	}

	@Override
	public ExtensionPlace getExtensionPlace() {
		ExtensionPlace defaultExt = ExtensionPlace.getDefault();
		defaultExt.setDownloadAllExtensions(true);
		return defaultExt;
	}

	@Override
	public void onUpdateDone(final boolean restart, final String failureMessage)
			throws RemoteException {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (Listener l : new ArrayList<Listener>(listeners)) {
					l.initDone(restart, failureMessage);
				}
			}
		});

	}

	public boolean isServiceUpdating() {
		try {
			return clientService != null && clientService.isUpdating();
		}	
		catch(RemoteException re) {
			return false;
		}
	}

	@Override
	public void ping() throws RemoteException {
		// Noop
	}
}
