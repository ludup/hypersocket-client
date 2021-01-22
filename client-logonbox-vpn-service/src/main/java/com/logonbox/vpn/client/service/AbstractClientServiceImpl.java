package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.updates.ClientUpdater;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatusImpl;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.GUIRegistry;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;

public abstract class AbstractClientServiceImpl
		implements ClientService {

	static Logger log = LoggerFactory.getLogger(AbstractClientServiceImpl.class);

	protected ConfigurationService configurationService;
	protected Map<Connection, LogonBoxVPNSession> activeClients = new HashMap<>();
	protected Map<Connection, LogonBoxVPNSession> connectingClients = new HashMap<>();

	private Timer timer;
	protected Map<Connection, Set<ServicePlugin>> connectionPlugins = new HashMap<Connection, Set<ServicePlugin>>();

	private Semaphore startupLock = new Semaphore(1);
	private boolean updating;
	private boolean guiNeedsSeparateUpdate;
	private int appsToUpdate;
	private ClientUpdater serviceUpdateJob;
	private LocalContext context;

	public AbstractClientServiceImpl(LocalContext context) {
		this.context = context;

		try {
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		timer = new Timer(true);
	}

	@Override
	public ConnectionService getConnectionService() throws RemoteException {
		return context.getConnectionService();
	}

	public LocalContext getContext() {
		return context;
	}

	@Override
	public void registerGUI(GUICallback gui) throws RemoteException {
		try {
			/*
			 * BPS - We need registration to wait until the client services are started up
			 * or there will be weird hibernate transaction errors if the GUI connects while
			 * the client is trying to connect
			 */
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		try {

			if (gui.isInteractive()) {
				GUIRegistry guiRegistry = context.getGuiRegistry();
				guiRegistry.registerGUI(gui);
				if (updating && guiNeedsSeparateUpdate) {
					/*
					 * If we register while an update is taking place, try to make the client catch
					 * up and show the update progress window
					 */
					guiRegistry.onUpdateInit(appsToUpdate);
					guiRegistry.onUpdateStart(ExtensionPlace.getDefault().getApp(), serviceUpdateJob.getTotalSize(),
							null);
					guiRegistry.onUpdateProgress(ExtensionPlace.getDefault().getApp(), 0,
							serviceUpdateJob.getTransfered(), serviceUpdateJob.getTotalSize());
					if (serviceUpdateJob.getTransfered() >= serviceUpdateJob.getTotalSize()) {
						guiRegistry.onUpdateComplete(ExtensionPlace.getDefault().getApp(),
								serviceUpdateJob.getTransfered());
					}
				}
			}
		} finally {
			startupLock.release();
		}
	}

	@Override
	public void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException {
		if (gui.isInteractive()) {
			context.getGuiRegistry().unregisterGUI(gui, callback);
		}
	}

	@Override
	public void ping() {
		// Noop
	}

	public boolean startService() {

		try {
			for (Connection c : context.getConnectionService().getConnections()) {
				if (c.isConnectAtStartup()) {
					connect(c);
				}
			}

			return true;
		} catch (RemoteException e) {
			log.error("Failed to start service", e);
			return false;
		} finally {
			startupLock.release();
		}
	}

	@Override
	public void connect(Connection c) throws RemoteException {
		synchronized (activeClients) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			LogonBoxVPNSession task = createJob(c);
			connectingClients.put(c, task);
			schedule(task, 500);
		}
	}

	private void schedule(TimerTask task, int delay) {
		try {
			timer.schedule(task, delay);
		} catch (Throwable e) {
			timer = new Timer(true);
			timer.schedule(task, delay);
		}
	}

	private void checkValidConnect(Connection c) throws RemoteException {
		synchronized (activeClients) {
			if (connectingClients.containsKey(c)) {
				throw new RemoteException("Already connecting.");
			}
			if (activeClients.containsKey(c)) {
				throw new RemoteException("Already connected.");
			}
		}
	}

	@Override
	public void scheduleConnect(Connection c) throws RemoteException {
		checkValidConnect(c);
		if (log.isInfoEnabled()) {
			log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
		}

		Integer reconnectSeconds = Integer.valueOf(configurationService.getValue("client.reconnectInSeconds", "5"));

		Connection connection = context.getConnectionService().getConnection(c.getId());
		if (connection == null) {
			log.warn("Ignoring a scheduled connection that no longer exists, probably deleted.");
		} else {
			schedule(createJob(c), reconnectSeconds * 1000);
		}

	}

	protected abstract LogonBoxVPNSession createJob(Connection c) throws RemoteException;

	public void stopService() throws RemoteException {

		synchronized (activeClients) {
			activeClients.clear();
			connectingClients.clear();
		}
		timer.cancel();

	}

	@Override
	public boolean isConnected(Connection c) throws RemoteException {
		return activeClients.containsKey(c);
	}

	@Override
	public void disconnect(Connection c) throws RemoteException {

		if (log.isInfoEnabled()) {
			log.info("Disconnecting connection with id " + c.getId() + "/" + c.getHostname());
		}
		boolean disconnect = false;
		try {
			beforeDisconnectClient(c);
		} catch (IOException e) {
			throw new RemoteException("Failed to disconnect.");
		}
		synchronized (activeClients) {
			if (activeClients.containsKey(c)) {
				if (log.isInfoEnabled()) {
					log.info("Was connected, disconnecting");
				}
				disconnect = true;
				activeClients.remove(c);
			} else if (connectingClients.containsKey(c)) {
				if (log.isInfoEnabled()) {
					log.info("Was connecting, cancelling");
				}
				connectingClients.get(c).cancel();
				connectingClients.remove(c);
			} else {
				throw new RemoteException("Not connected.");
			}
		}
		if (disconnect) {
			try {
				disconnectClient(c);
			} catch (IOException e) {
				throw new RemoteException("Failed to disconnect.");
			}
		}
		/**
		 * Force removal here for final chance clean up
		 */
		context.getGuiRegistry().disconnected(c, null);
	}

	protected void beforeDisconnectClient(Connection c) throws IOException {
	}

	protected void disconnectClient(Connection c) throws IOException {
	}

	@Override
	public int getStatus(Connection c) {
		return activeClients.containsKey(c) ? ConnectionStatus.CONNECTED
				: connectingClients.containsKey(c) ? ConnectionStatus.CONNECTING : ConnectionStatus.DISCONNECTED;
	}

	@Override
	public List<ConnectionStatus> getStatus() throws RemoteException {

		List<ConnectionStatus> ret = new ArrayList<ConnectionStatus>();
		Collection<Connection> connections = context.getConnectionService().getConnections();
		List<Connection> added = new ArrayList<Connection>();
		synchronized (activeClients) {
			addConnections(ret, connections, added);
			addConnections(ret, activeClients.keySet(), added);
			addConnections(ret, connectingClients.keySet(), added);
		}
		return ret;

	}

	@Override
	public boolean isGUINeedsUpdating() throws RemoteException {
		return guiNeedsSeparateUpdate;
	}

	public boolean isUpdating() {
		return updating;
	}

	public boolean update(final Connection c, ServiceClient client) throws RemoteException {

		if ("true".equals(System.getProperty("hypersocket.development.noUpdates"))) {
			log.info("No updates to do.");
			guiNeedsSeparateUpdate = false;
		} else {
			log.info("Updating via " + c.getUri(true));

			try {
				updating = true;
				guiNeedsSeparateUpdate = true;
				GUIRegistry guiRegistry = context.getGuiRegistry();

				/*
				 * For the client service, we use the local 'extension place'
				 */
				appsToUpdate = 1;
				ExtensionPlace defaultExt = ExtensionPlace.getDefault();
				defaultExt.setDownloadAllExtensions(true);
				serviceUpdateJob = new ClientUpdater(guiRegistry, c, client, defaultExt,
						ExtensionTarget.CLIENT_SERVICE);

				/*
				 * For the GUI, we get the extension place remotely, as the GUI itself is best
				 * placed to know what extensions it has and where they stored.
				 * 
				 * However, it's possible the GUI is not yet running, so we only do this if it
				 * is available. If this happens we may need to update the GUI as well when it
				 * eventually
				 */
				ClientUpdater guiJob = null;
				if (guiRegistry.hasGUI()) {
					appsToUpdate++;
					guiNeedsSeparateUpdate = false;
					guiJob = new ClientUpdater(guiRegistry, c, client, guiRegistry.getGUI().getExtensionPlace(),
							ExtensionTarget.CLIENT_GUI);
				}

				try {
					guiRegistry.onUpdateInit(appsToUpdate);

					int updates = 0;

					if (serviceUpdateJob.update()) {
						updates++;
					}

					if (guiJob != null && guiJob.update()) {
						updates++;
					}

					if (updates > 0) {

						/*
						 * If when we started the update, the GUI wasn't attached, but it is now, then
						 * instead of restarting immediately, try to update any client extensions too
						 */
						if (guiNeedsSeparateUpdate && guiRegistry.hasGUI()) {
							guiNeedsSeparateUpdate = false;
							appsToUpdate = 1;
							guiJob = new ClientUpdater(guiRegistry, c, client, guiRegistry.getGUI().getExtensionPlace(),
									ExtensionTarget.CLIENT_GUI);
							guiRegistry.onUpdateInit(appsToUpdate);
							guiJob.update();

							guiRegistry.onUpdateDone(true, null);
							log.info("Update complete, restarting.");
							context.getRestartCallback().run();
						} else {
							guiRegistry.onUpdateDone(true, null);
							log.info("Update complete, restarting.");
							context.getRestartCallback().run();
						}
					} else {
						guiRegistry.onUpdateDone(false, "Nothing to update.");
					}

				} catch (IOException e) {
					log.error("Failed to execute update job.", e);
					return false;
				}
			} catch (RemoteException re) {
				log.error("Failed to get GUI extension information. Update aborted.", re);
			} finally {
				updating = false;
			}
		}
		return false;
	}

	private void addConnections(List<ConnectionStatus> ret, Collection<Connection> connections,
			List<Connection> added) {
		for (Connection c : connections) {
			if (!added.contains(c)) {
				ret.add(new ConnectionStatusImpl(c, getStatus(c)));
				added.add(c);
			}
		}
	}

	protected void stopPlugins(HypersocketClient<Connection> client) {

		Set<ServicePlugin> plugins = connectionPlugins.get(client.getAttachment());
		for (ServicePlugin plugin : plugins) {
			try {
				plugin.stop();
			} catch (Throwable e) {
				log.error("Failed to stop plugin " + plugin.getName(), e);
			}
		}
	}

	protected void startPlugins(final HypersocketClient<Connection> client) {
		Enumeration<URL> urls;

		if (log.isInfoEnabled()) {
			log.info("Starting plugins");
		}
		if (!connectionPlugins.containsKey(client.getAttachment())) {
			connectionPlugins.put(client.getAttachment(), new HashSet<ServicePlugin>());
		}
		try {
			urls = getClass().getClassLoader().getResources("service-plugin.properties");

			if (log.isInfoEnabled() && !urls.hasMoreElements()) {
				log.info("There are no plugins in classpath");

				urls = getClass().getClassLoader().getResources("/service-plugin.properties");
			}

			while (urls.hasMoreElements()) {

				URL url = urls.nextElement();

				if (log.isInfoEnabled()) {
					log.info("Found plugin at " + url.toExternalForm());
				}
				try {

					Properties p = new Properties();
					p.load(url.openStream());

					String name = p.getProperty("plugin.name");
					String clz = p.getProperty("plugin.class");

					if (log.isInfoEnabled()) {
						log.info("Starting plugin " + name + "[" + clz + "]");
					}

					@SuppressWarnings({ "unchecked" })
					Class<ServicePlugin> pluginClz = (Class<ServicePlugin>) Class.forName(clz);

					ServicePlugin plugin = pluginClz.getConstructor().newInstance();
					plugin.start(createClientContent(client));

					connectionPlugins.get(client.getAttachment()).add(plugin);
				} catch (Throwable e) {
					log.error("Failed to start plugin", e);
				}
			}

		} catch (Throwable e) {
			log.error("Failed to start plugins", e);
		}
	}

	protected abstract ClientContext createClientContent(final HypersocketClient<Connection> client);

	@Override
	public ConfigurationService getConfigurationService() throws RemoteException {
		return configurationService;
	}

	public GUIRegistry getGuiRegistry() {
		return context.getGuiRegistry();
	}

	@Override
	public Connection save(Connection c) throws RemoteException {
		// If a non-persistent connection is now being saved as a persistent
		// one, then update our maps
		Long oldId = c.getId();
		Connection newConnection = context.getConnectionService().save(c);

		if (oldId == null && newConnection.getId() != null) {
			log.info(String.format("Saving non-persistent connection, now has ID %d", newConnection.getId()));
		}

		synchronized (activeClients) {
			if (activeClients.containsKey(c)) {
				activeClients.put(newConnection, activeClients.remove(c));
			}
			if (connectingClients.containsKey(c)) {
				connectingClients.put(newConnection, connectingClients.remove(c));
			}
			if (connectionPlugins.containsKey(c)) {
				connectionPlugins.put(newConnection, connectionPlugins.remove(c));
			}
		}
		onSave(c, newConnection);
		return newConnection;

	}

	protected void onSave(Connection oldConnection, Connection newConnection) {
	}

	public void finishedConnecting(Connection connection, LogonBoxVPNSession job) {
		synchronized (activeClients) {
			connectingClients.remove(connection);
			activeClients.put(connection, job);
			context.getGuiRegistry().started(connection);
		}
	}

	public void failedToConnect(Connection connection, Throwable jpe) {
		connectingClients.remove(connection);
	}

	public void disconnected(Connection connection, HypersocketClient<Connection> client) {
		synchronized (activeClients) {
			activeClients.remove(client.getAttachment());
			connectingClients.remove(client.getAttachment());
		}
		context.getGuiRegistry().notify(client.getHost() + " disconnected", GUICallback.NOTIFY_DISCONNECT);
		stopPlugins(client);
	}
}
