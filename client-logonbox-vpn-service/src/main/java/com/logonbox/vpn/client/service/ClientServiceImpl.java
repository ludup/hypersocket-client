package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.UserCancelledException;
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
import com.logonbox.vpn.common.client.Branding;
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

	static final int POLL_RATE = 30;

	private static final int PHASES_TIMEOUT = 3600 * 24;

	protected Map<Connection, VPNSession> activeSessions = new HashMap<>();
	protected Set<Connection> authorizingClients = new HashSet<>();
	protected ConfigurationService configurationService;
	protected Map<Connection, VPNSession> connectingSessions = new HashMap<>();

	private int appsToUpdate;

	private LocalContext context;
	private UUID deviceUUID;
	private boolean guiNeedsSeparateUpdate;
	private boolean needsUpdate;
	private ClientUpdater serviceUpdateJob;
	private Semaphore startupLock = new Semaphore(1);
	private ScheduledExecutorService timer;
	private boolean updating;
	private long phasesLastRetrieved = 0;

	private JsonExtensionPhaseList phaseList;

	public ClientServiceImpl(LocalContext context) {
		this.context = context;

		try {
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		((ConnectionServiceImpl) context.getConnectionService()).addListener(this);

		timer = Executors.newScheduledThreadPool(1);
	}

	@Override
	public void authorized(Connection connection) throws RemoteException {
		synchronized (activeSessions) {
			if (!authorizingClients.contains(connection)) {
				throw new RemoteException("No authorization request.");
			}
			authorizingClients.remove(connection);
		}
		getConnectionService().save(connection);
		connect(connection);
	}

	@Override
	public void connect(Connection c) throws RemoteException {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			VPNSession task = createJob(c);
			task.setTask(timer.schedule(() -> doConnect(task), 500, TimeUnit.MILLISECONDS));
		}
	}

	@Override
	public void connectionAdded(Connection connection) {
		try {
			context.getGuiRegistry().connectionAdded(connection);
		} catch (RemoteException e) {
			log.error("Failed to signal connection added.", e);
		}
	}

	@Override
	public void connectionAdding(Connection connection) {
	}

	@Override
	public void connectionRemoved(Connection connection) {
		try {
			context.getGuiRegistry().connectionRemoved(connection);
		} catch (RemoteException e) {
			log.error("Failed to signal connection removed.", e);
		}
	}

	@Override
	public void connectionRemoving(Connection connection) {
		try {
			synchronized (activeSessions) {
				if (getStatus(connection) == Type.CONNECTED) {
					if (StringUtils.isNotBlank(connection.getUserPublicKey())) {
						VirtualInetAddress addr = getContext().getPlatformService()
								.getByPublicKey(connection.getPublicKey());
						if (addr != null) {
							addr.delete();
						}
					}
					disconnect(connection, null);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to disconnect.", e);
		}
	}

	@Override
	public void connectionUpdated(Connection connection) {
		try {
			context.getGuiRegistry().connectionUpdated(connection);
		} catch (RemoteException e) {
			log.error("Failed to signal connection updated.", e);
		}
	}

	@Override
	public void connectionUpdating(Connection connection) {
	}

	public ClientContext createClientContent(HypersocketClient<Connection> client) {
		return new ClientContext() {

			@Override
			public HypersocketClient<Connection> getClient() {
				return client;
			}

			@Override
			public LocalContext getLocalContext() {
				return getContext();
			}
		};
	}

	@Override
	public void deauthorize(Connection connection) throws RemoteException {
		synchronized (activeSessions) {
			connection.deauthorize();
			authorizingClients.remove(connection);
		}
		getConnectionService().save(connection);

	}

	@Override
	public void disconnect(Connection c, String reason) throws RemoteException {

		if (log.isInfoEnabled()) {
			log.info("Disconnecting connection with id " + c.getId() + "/" + c.getHostname());
		}
		boolean disconnect = false;
		try {
			beforeDisconnectClient(c);
		} catch (IOException e) {
			throw new RemoteException("Failed to disconnect.", e);
		}
		synchronized (activeSessions) {
			if (authorizingClients.contains(c)) {
				if (log.isInfoEnabled()) {
					log.info("Was authorizing, cancelling");
				}
				authorizingClients.remove(c);
				disconnect = true;
			}
			if (activeSessions.containsKey(c)) {
				if (log.isInfoEnabled()) {
					log.info("Was connected, disconnecting");
				}
				disconnect = true;
				activeSessions.remove(c);
			}
			if (connectingSessions.containsKey(c)) {
				if (log.isInfoEnabled()) {
					log.info("Was connecting, cancelling");
				}
				try {
					connectingSessions.get(c).close();
				} catch (IOException e) {
				}
				connectingSessions.remove(c);
				disconnect = true;
			}

			if (!disconnect) {
				throw new RemoteException("Not connected.");
			}
		}
		try {
			disconnectClient(c);
		} catch (IOException e) {
			throw new RemoteException("Failed to disconnect.");
		}
		/**
		 * Force removal here for final chance clean up
		 */
		context.getGuiRegistry().disconnected(c, reason);
	}

	public void disconnected(Connection connection, HypersocketClient<Connection> client) {
		synchronized (activeSessions) {
			authorizingClients.remove(client.getAttachment());
			activeSessions.remove(client.getAttachment());
			connectingSessions.remove(client.getAttachment());
		}
		context.getGuiRegistry().notify(client.getHost() + " disconnected", GUICallback.NOTIFY_DISCONNECT);
	}

	public void failedToConnect(Connection connection, Throwable jpe) {
		synchronized (activeSessions) {
			authorizingClients.remove(connection);
			connectingSessions.remove(connection);
		}
	}

	public void finishedConnecting(Connection connection, VPNSession job) {
		synchronized (activeSessions) {
			connectingSessions.remove(connection);
			activeSessions.put(connection, job);
			context.getGuiRegistry().started(connection);
		}
	}

	@Override
	public ConfigurationService getConfigurationService() throws RemoteException {
		return configurationService;
	}

	@Override
	public ConnectionService getConnectionService() throws RemoteException {
		return context.getConnectionService();
	}

	public LocalContext getContext() {
		return context;
	}

	public GUIRegistry getGuiRegistry() {
		return context.getGuiRegistry();
	}

	@Override
	public String[] getMissingPackages() throws RemoteException {
		return getContext().getPlatformService().getMissingPackages();
	}

	public JsonExtensionPhaseList getPhases() throws RemoteException {
		JsonExtensionPhaseList l = new JsonExtensionPhaseList();
		if(isTrackServerVersion()) {
			/* Return an empty phase list, the client should not be 
			 * showing a phase list if tracking server version
			 */
			return l;
		}
		else { 
			if (this.phaseList == null || phasesLastRetrieved < System.currentTimeMillis() - (PHASES_TIMEOUT * 1000)) {
				ObjectMapper mapper = new ObjectMapper();
				String extensionStoreRoot = AbstractExtensionUpdater.getExtensionStoreRoot();
				phasesLastRetrieved = System.currentTimeMillis();
				try {
					NettyClientTransport transport = new NettyClientTransport(context.getBoss(), context.getWorker());
					transport.connect(extensionStoreRoot);
					String update = transport.get("store/phases");
					JsonExtensionPhaseList phaseList = mapper.readValue(update, JsonExtensionPhaseList.class);
					this.phaseList = phaseList;
				} catch (IOException ioe) {
					this.phaseList = l;
					throw new RemoteException(String.format("Failed to get extension phases from %s.", extensionStoreRoot),
							ioe);
				}
			}
			return this.phaseList;
		}
	}

	@Override
	public List<ConnectionStatus> getStatus() throws RemoteException {

		List<ConnectionStatus> ret = new ArrayList<ConnectionStatus>();
		Collection<Connection> connections = context.getConnectionService().getConnections();
		List<Connection> added = new ArrayList<Connection>();
		synchronized (activeSessions) {
			addConnections(ret, connections, added);
			addConnections(ret, activeSessions.keySet(), added);
			addConnections(ret, connectingSessions.keySet(), added);
		}
		return ret;

	}

	@Override
	public Type getStatus(Connection c) {
		if (authorizingClients.contains(c))
			return Type.AUTHORIZING;
		if (activeSessions.containsKey(c))
			return Type.CONNECTED;
		if (connectingSessions.containsKey(c))
			return Type.CONNECTING;
		return Type.DISCONNECTED;
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
		if (highestVersionUpdate == null) {
			throw new RemoteException("Failed to get most recent version from any servers.");
		}
		return highestVersionUpdate;
	}

	@Override
	public Branding getBranding(Connection connection) throws RemoteException {
		ObjectMapper mapper = new ObjectMapper();
		Branding branding = null;
		if (connection != null) {
			try {
				branding = getBrandingForConnection(mapper, connection);
			} catch (IOException ioe) {
				log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (branding == null) {
			for (Connection conx : context.getConnectionService().getConnections()) {
				try {
					branding = getBrandingForConnection(mapper, conx);
					break;
				} catch (IOException ioe) {
					log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
							connection.getPort()));
				}
			}
		}
		return branding;
	}

	protected Branding getBrandingForConnection(ObjectMapper mapper, Connection connection)
			throws UnknownHostException, IOException, JsonProcessingException, JsonMappingException {
		NettyClientTransport transport = new NettyClientTransport(context.getBoss(), context.getWorker());
		transport.connect(connection.getHostname(), connection.getPort(), "/");
		String update = transport.get("brand/info");
		Branding brandingObj = mapper.readValue(update, Branding.class);
		brandingObj.setLogo("https://" + connection.getHostname() + ":" + connection.getPort() + connection.getPath()
				+ "/api/brand/logo");
		return brandingObj;
	}

	@Override
	public UUID getUUID() {
		return deviceUUID;
	}

	@Override
	public boolean isGUINeedsUpdating() throws RemoteException {
		return guiNeedsSeparateUpdate;
	}

	@Override
	public boolean isNeedsUpdating() throws RemoteException {
		return needsUpdate;
	}

	@Override
	public boolean isTrackServerVersion() throws RemoteException {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.trackServerVersion", "true"));
	}

	public boolean isUpdating() {
		return updating;
	}

	@Override
	public void ping() {
		// Noop
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

					try {
						guiRegistry.onUpdateInit(appsToUpdate);
						try {
							boolean atLeastOneUpdate = guiJob.update();

							log.info("Update complete, restarting.");
							guiRegistry.onUpdateDone(atLeastOneUpdate, null);

						} catch (IOException e) {
							log.error("Failed to update GUI.", e);
							guiRegistry.onUpdateDone(false, e.getMessage());
						}
					} catch (Exception re) {
						log.error("GUI refused to update, ignoring.", re);
						guiRegistry.onUpdateDone(false, null);
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
	public void requestAuthorize(Connection connection) throws RemoteException {
		synchronized (activeSessions) {
			/* Can request multiple times */
			if (connectingSessions.containsKey(connection)) {
				throw new RemoteException("Already connecting.");
			}
			if (activeSessions.containsKey(connection)) {
				throw new RemoteException("Already connected.");
			}
			if (connection.isAuthorized())
				throw new RemoteException("Already authorized.");

			context.getGuiRegistry().showBrowser(connection, "/logonbox-vpn-client/");
			authorizingClients.add(connection);
		}
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

		synchronized (activeSessions) {
			if (activeSessions.containsKey(c)) {
				activeSessions.put(newConnection, activeSessions.remove(c));
			}
			if (authorizingClients.contains(c)) {
				authorizingClients.remove(c);
				authorizingClients.add(newConnection);
			}
			if (connectingSessions.containsKey(c)) {
				connectingSessions.put(newConnection, connectingSessions.remove(c));
			}
		}
		onSave(c, newConnection);
		return newConnection;

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
			VPNSession job = createJob(c);
			job.setTask(timer.schedule(() -> doConnect(job), reconnectSeconds, TimeUnit.SECONDS));
		}

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
			activeSessions.put(getContext().getConnectionService().getConnection(session.getConnection()), session);
		}

	}

	public boolean startSavedConnections() {

		try {

			String deviceUUIDString = context.getConfigurationService().getValue("deviceUUID", "");
			if (deviceUUIDString.equals("")) {
				deviceUUID = UUID.randomUUID();
				context.getConfigurationService().setValue("deviceUUID", deviceUUID.toString());
			} else
				deviceUUID = UUID.fromString(deviceUUIDString);

			int connected = 0;
			for (Connection c : context.getConnectionService().getConnections()) {
				if (c.isConnectAtStartup() && getStatus(c) == Type.DISCONNECTED) {
					try {
						connect(c);
						connected++;
					} catch (Exception e) {
						log.error(String.format("Failed to start on-startup connection %s", c.getName()), e);
					}
				}
			}

			timer.scheduleAtFixedRate(() -> checkConnectionsAlive(), POLL_RATE, POLL_RATE, TimeUnit.SECONDS);

			return connected > 0;
		} catch (RemoteException e) {
			log.error("Failed to start service", e);
			return false;
		} finally {
			startupLock.release();
		}
	}

	public void stopService() throws RemoteException {

		synchronized (activeSessions) {
			activeSessions.clear();
			connectingSessions.clear();
			authorizingClients.clear();
		}
		timer.shutdown();

	}

	@Override
	public void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException {
		if (gui.isInteractive()) {
			context.getGuiRegistry().unregisterGUI(gui, callback);
		}
	}

	public void update() throws RemoteException {
		if (!isNeedsUpdating()) {
			throw new RemoteException("An update is not required.");
		}
		update(false);
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
								boolean atLeastOneUpdate = guiJob.update();
								guiRegistry.onUpdateDone(atLeastOneUpdate, null);
								if(atLeastOneUpdate) {
									log.info("Update complete, restarting.");
									context.getRestartCallback().run();
								}
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

	protected void beforeDisconnectClient(Connection c) throws IOException {
		synchronized (activeSessions) {
			VPNSession wireguardSession = activeSessions.get(c);
			if (wireguardSession != null)
				wireguardSession.close();
		}

	}

	protected VPNSession createJob(Connection c) throws RemoteException {
		return new VPNSession(c.getId(), getContext());
	}

	protected void disconnectClient(Connection c) throws IOException {
	}

	protected void onSave(Connection oldConnection, Connection newConnection) {
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

	private void checkConnectionsAlive() {
		synchronized (activeSessions) {
			for (Map.Entry<Connection, VPNSession> sessionEn : new HashMap<>(activeSessions).entrySet()) {
				try {
					if (connectingSessions.containsKey(sessionEn.getKey())
							|| getContext().getPlatformService().isAlive(sessionEn.getValue(), sessionEn.getKey())) {
						/* If still 'connecting' or completely alive, skip to next session */
						continue;
					}
				} catch (IOException ioe) {
					log.warn("Failed to test if session was alive. Assuming it isn't", ioe);
				}

				/* Kill the dead session */
				log.info(String.format(
						"Session with public key %s hasn't had a valid handshake for %d seconds, disconnecting.",
						sessionEn.getKey().getUserPublicKey(), ClientService.HANDSHAKE_TIMEOUT));
				try {
					disconnect(sessionEn.getKey(), null);
				} catch (Exception e) {
					log.warn("Failed to disconnect dead session. State may be incorrect.", e);
				}
			}
		}
	}

	private void checkValidConnect(Connection c) throws RemoteException {
		synchronized (activeSessions) {
			if (connectingSessions.containsKey(c)) {
				throw new RemoteException("Already connecting.");
			}
			if (activeSessions.containsKey(c)) {
				throw new RemoteException("Already connected.");
			}
			if (authorizingClients.contains(c)) {
				throw new RemoteException("Currently authorizing.");
			}
			if (!c.isAuthorized())
				throw new RemoteException("Not authorized.");
		}
	}

	private void doConnect(VPNSession job) {
		try {

			Connection connection = getConnectionService().getConnection(job.getConnection());
			connectingSessions.put(connection, job);

			if (log.isInfoEnabled()) {
				log.info("Connecting to " + connection);
			}
			try {
				job.open();
				if (log.isInfoEnabled()) {
					log.info("Connected to " + connection);
				}
				getGuiRegistry().transportConnected(connection);
				getGuiRegistry().ready(connection);
				finishedConnecting(connection, job);
			} catch (Exception e) {
				if (log.isErrorEnabled()) {
					log.error("Failed to connect " + connection, e);
				}

				failedToConnect(connection, e);
				if (e instanceof ReauthorizeException) {
					try {
						/*
						 * The connection did not get it's first handshake in a timely manner. We don't
						 * know why it failed, it could be an invalidated session, or some other
						 * transient problem with the path to the server, or perhaps the server itself
						 * is have problems.
						 * 
						 * All we can do is request authorization again IF the GUI is open. If not, just
						 * keep trying.
						 */
						deauthorize(connection);
						requestAuthorize(connection);
						return;
					} catch (Exception e1) {
						if (log.isErrorEnabled()) {
							log.error("Failed to request authorization.", e1);
						}
					}
				}
				getGuiRegistry().failedToConnect(connection, e.getMessage());
				if (!(e instanceof UserCancelledException)) {
					if (connection.isStayConnected()) {
						try {
							scheduleConnect(connection);
							return;
						} catch (RemoteException e1) {
						}
					}
				}
			}

		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to get connection.", e);
			}
		}
	}
}
