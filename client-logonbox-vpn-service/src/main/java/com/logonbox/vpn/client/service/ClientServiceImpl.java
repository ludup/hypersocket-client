package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.json.version.Version;
import com.hypersocket.netty.NettyClientTransport;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.updates.ClientUpdater;
import com.logonbox.vpn.client.service.vpn.ConnectionServiceImpl;
import com.logonbox.vpn.client.service.vpn.ConnectionServiceImpl.Listener;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.ConnectionStatusImpl;
import com.logonbox.vpn.common.client.GUICallback;
import com.logonbox.vpn.common.client.GUIRegistry;

public class ClientServiceImpl implements ClientService, Listener {

	static Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

	protected ConfigurationService configurationService;
	protected Set<Connection> authorizingClients = new HashSet<>();
	protected Map<Connection, VPNSession> activeClients = new HashMap<>();
	protected Map<Connection, VPNSession> connectingClients = new HashMap<>();

	private Timer timer;

	private Semaphore startupLock = new Semaphore(1);
	private boolean updating;
	private boolean guiNeedsSeparateUpdate;
	private int appsToUpdate;
	private ClientUpdater serviceUpdateJob;
	private LocalContext context;
	private UUID deviceUUID;
	private boolean needsUpdate;

	public ClientServiceImpl(LocalContext context) {
		this.context = context;

		try {
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		((ConnectionServiceImpl) context.getConnectionService()).addListener(this);

		timer = new Timer(true);
	}

	@Override
	public String[] getMissingPackages() throws RemoteException {
		return getContext().getPlatformService().getMissingPackages();
	}

	public void update() throws RemoteException {
		if (!isNeedsUpdating()) {
			throw new RemoteException("An update is not required.");
		}
		update(false);
	}

	public void start() throws Exception {
		boolean automaticUpdates = Boolean
				.valueOf(context.getConfigurationService().getValue(ConfigurationService.AUTOMATIC_UPDATES, "true"));

		if (!isTrackServerVersion() || context.getConnectionService().getConnections().size() > 0) {
			/*
			 * Do updates if we are not tracking the server version or if there are some
			 * connections we can get LogonBox VPN server version from
			 */
			try {
				if (automaticUpdates)
					update(false);
				else {
					update(true);
					if (needsUpdate) {
						/*
						 * If updates are manual, don't try to connect until the GUI connects and does
						 * it's update
						 */
						log.info("GUI Needs update, awaiting GUI to connect.");
						return;
					}
				}
			} catch (Exception e) {
				log.info(String.format("Extension versions not checked."), e);
			}
		}

		Collection<VPNSession> toStart = getContext().getPlatformService().start(getContext());
		if (!toStart.isEmpty()) {
			log.warn(String.format("Not starting %d connections until update is done.", toStart.size()));
		}
		for (VPNSession session : toStart) {
			activeClients.put(session.getConnection(), session);
		}

	}

	protected void beforeDisconnectClient(Connection c) throws IOException {
		synchronized (activeClients) {
			VPNSession wireguardSession = activeClients.get(c);
			if (wireguardSession != null)
				wireguardSession.close();
		}

	}

	public ClientContext createClientContent(HypersocketClient<Connection> client) {
		return new ClientContext() {

			@Override
			public LocalContext getLocalContext() {
				return getContext();
			}

			@Override
			public HypersocketClient<Connection> getClient() {
				return client;
			}
		};
	}

	protected VPNSession createJob(Connection c) throws RemoteException {
		return new VPNSession(c, getContext());
	}

	@Override
	public void connectionAdding(Connection connection, Session session) {
	}

	@Override
	public void connectionAdded(Connection connection, Session session) {
	}

	@Override
	public void connectionRemoving(Connection connection, Session session) {
		try {
			synchronized (activeClients) {
				if (isConnected(connection)) {
					if (StringUtils.isNotBlank(connection.getUserPublicKey())) {
						VirtualInetAddress addr = getContext().getPlatformService()
								.getByPublicKey(connection.getPublicKey());
						if (addr != null) {
							addr.delete();
						}
					}
					disconnect(connection);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to disconnect.", e);
		}
	}

	@Override
	public void connectionRemoved(Connection connection, Session session) {
	}

	@Override
	public UUID getUUID() {
		return deviceUUID;
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
				if (guiNeedsSeparateUpdate) {
					/* Do the separate GUI update */
					appsToUpdate = 1;
					ClientUpdater guiJob = new ClientUpdater(guiRegistry, guiRegistry.getGUI().getExtensionPlace(),
							ExtensionTarget.CLIENT_GUI, context);

					guiRegistry.onUpdateInit(appsToUpdate);
					try {
						guiJob.update();

						log.info("Update complete, restarting.");
						guiRegistry.onUpdateDone(true, null);

					} catch (IOException e) {
						log.error("Failed to update GUI.", e);
						guiRegistry.onUpdateDone(false, e.getMessage());
					}

				} else if (updating) {

					/*
					 * If we register while an update is taking place, try to make the client catch
					 * up and show the update progress window
					 */
					guiRegistry.onUpdateInit(appsToUpdate);
					guiRegistry.onUpdateStart(ExtensionPlace.getDefault().getApp(), serviceUpdateJob.getTotalSize());
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

			String deviceUUIDString = context.getConfigurationService().getValue("deviceUUID", "");
			if (deviceUUIDString.equals("")) {
				deviceUUID = UUID.randomUUID();
				context.getConfigurationService().setValue("deviceUUID", deviceUUID.toString());
			} else
				deviceUUID = UUID.fromString(deviceUUIDString);

			for (Connection c : context.getConnectionService().getConnections()) {
				if (c.isConnectAtStartup() && !isConnected(c)) {
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
	public void authorized(Connection connection) throws RemoteException {
		synchronized (activeClients) {
			if (!authorizingClients.contains(connection)) {
				throw new RemoteException("No authorization request.");
			}
			authorizingClients.remove(connection);
		}
		getConnectionService().save(connection);
		connect(connection);
	}

	@Override
	public void requestAuthorize(Connection connection) throws RemoteException {
		synchronized (activeClients) {
			/* Can request multiple times */
			if (connectingClients.containsKey(connection)) {
				throw new RemoteException("Already connecting.");
			}
			if (activeClients.containsKey(connection)) {
				throw new RemoteException("Already connected.");
			}
			if (connection.isAuthorized())
				throw new RemoteException("Already authorized.");

			context.getGuiRegistry().showBrowser(connection, "/logonbox-vpn-client/");
			authorizingClients.add(connection);
		}
	}

	@Override
	public void connect(Connection c) throws RemoteException {
		synchronized (activeClients) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			VPNSession task = createJob(c);
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
			if (authorizingClients.contains(c)) {
				throw new RemoteException("Currently authorizing.");
			}
			if (!c.isAuthorized())
				throw new RemoteException("Not authorized.");
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

	public void stopService() throws RemoteException {

		synchronized (activeClients) {
			activeClients.clear();
			connectingClients.clear();
			authorizingClients.clear();
		}
		timer.cancel();

	}

	@Override
	public boolean isConnected(Connection c) throws RemoteException {
		return activeClients.containsKey(c);
	}

	@Override
	public boolean isAuthorizing(Connection c) throws RemoteException {
		return authorizingClients.contains(c);
	}

	@Override
	public void disconnect(Connection c) throws RemoteException {

		
		if (log.isInfoEnabled()) {
			log.info("Disconnecting connection with id " + c.getId() + "/" + c.getHostname());
			try {
				throw new Exception();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
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

	protected void disconnectClient(Connection c) throws IOException {
	}

	@Override
	public Type getStatus(Connection c) {
		return activeClients.containsKey(c) ? Type.CONNECTED
				: connectingClients.containsKey(c) ? Type.CONNECTING : Type.DISCONNECTED;
	}

	public JsonExtensionPhaseList getPhases() throws RemoteException {
		ObjectMapper mapper = new ObjectMapper();
		String extensionStoreRoot = AbstractExtensionUpdater.getExtensionStoreRoot();
		try {
			NettyClientTransport transport = new NettyClientTransport(context.getBoss(), context.getWorker());
			transport.connect(extensionStoreRoot);
			String update = transport.get("store/phases");
			JsonExtensionPhaseList phaseList = mapper.readValue(update, JsonExtensionPhaseList.class);
			return phaseList;
		} catch (IOException ioe) {
			throw new RemoteException(String.format("Failed to get extension phases from %s.", extensionStoreRoot),
					ioe);
		}
	}

	@Override
	public JsonExtensionUpdate getUpdates() throws RemoteException {
		ObjectMapper mapper = new ObjectMapper();
		/* Find the server with the highest version */
		Version highestVersion = null;
		JsonExtensionUpdate highestVersionUpdate = null;
		for (Connection connection : context.getConnectionService().getConnections()) {
			try {
				NettyClientTransport transport = new NettyClientTransport(context.getBoss(), context.getWorker());
				transport.connect(connection.getHostname(), connection.getPort(), "/");
				String update = transport.get("extensions/checkVersion");
				JsonExtensionUpdate extensionUpdate = mapper.readValue(update, JsonExtensionUpdate.class);
				Version version = new Version(extensionUpdate.getResource().getLatestVersion());
				if (highestVersion == null || version.compareTo(highestVersion) > 0) {
					highestVersion = version;
					highestVersionUpdate = extensionUpdate;
				}
			} catch (IOException ioe) {
				log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (highestVersionUpdate == null)
			throw new RemoteException("Failed to get most recent version from any servers.");
		return highestVersionUpdate;
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

	public void update(boolean checkOnly) throws RemoteException {
		appsToUpdate = 0;
		needsUpdate = false;
		int updates = 0;

		try {
			updating = true;
			if ("true".equals(System.getProperty("hypersocket.development.noUpdates"))) {
				log.info("No updates to do.");
				guiNeedsSeparateUpdate = false;
			} else {
				log.info("Updating");
				guiNeedsSeparateUpdate = true;
				GUIRegistry guiRegistry = context.getGuiRegistry();

				/*
				 * For the client service, we use the local 'extension place'
				 */
				appsToUpdate = 1;
				ExtensionPlace defaultExt = ExtensionPlace.getDefault();
				defaultExt.setDownloadAllExtensions(true);
				serviceUpdateJob = new ClientUpdater(guiRegistry, defaultExt, ExtensionTarget.CLIENT_SERVICE, context);

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
					guiJob = new ClientUpdater(guiRegistry, guiRegistry.getGUI().getExtensionPlace(),
							ExtensionTarget.CLIENT_GUI, context);
				}

				try {
					if (!checkOnly)
						guiRegistry.onUpdateInit(appsToUpdate);

					if ((checkOnly && serviceUpdateJob.checkForUpdates())
							|| (!checkOnly && serviceUpdateJob.update())) {
						updates++;
					}

					if ((checkOnly && guiJob != null && guiJob.checkForUpdates())
							|| (!checkOnly && guiJob != null && guiJob.update())) {
						updates++;
					}

					if (!checkOnly) {
						if (updates > 0) {

							/*
							 * If when we started the update, the GUI wasn't attached, but it is now, then
							 * instead of restarting immediately, try to update any client extensions too
							 */
							if (guiNeedsSeparateUpdate && guiRegistry.hasGUI()) {
								guiNeedsSeparateUpdate = false;
								appsToUpdate = 1;
								guiJob = new ClientUpdater(guiRegistry, guiRegistry.getGUI().getExtensionPlace(),
										ExtensionTarget.CLIENT_GUI, context);
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
					}

				} catch (IOException e) {
					log.error("Failed to execute update job.", e);
					return;
				}
			}
		} catch (RemoteException re) {
			log.error("Failed to get GUI extension information. Update aborted.", re);
		} finally {
			updating = false;
		}

		needsUpdate = updates > 0;
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
			if (authorizingClients.contains(c)) {
				authorizingClients.remove(c);
				authorizingClients.add(newConnection);
			}
			if (connectingClients.containsKey(c)) {
				connectingClients.put(newConnection, connectingClients.remove(c));
			}
		}
		onSave(c, newConnection);
		return newConnection;

	}

	protected void onSave(Connection oldConnection, Connection newConnection) {
	}

	public void finishedConnecting(Connection connection, VPNSession job) {
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
			authorizingClients.remove(client.getAttachment());
			activeClients.remove(client.getAttachment());
			connectingClients.remove(client.getAttachment());
		}
		context.getGuiRegistry().notify(client.getHost() + " disconnected", GUICallback.NOTIFY_DISCONNECT);
	}

	@Override
	public boolean isTrackServerVersion() throws RemoteException {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.trackServerVersion", "true"));
	}

	@Override
	public boolean isNeedsUpdating() throws RemoteException {
		return needsUpdate;
	}
}
