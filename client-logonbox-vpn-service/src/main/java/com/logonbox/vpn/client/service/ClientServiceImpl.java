package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.extensions.AbstractExtensionUpdater;
import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.hypersocket.json.version.Version;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.service.updates.ClientUpdater;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionImpl;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.Keys.KeyPair;
import com.logonbox.vpn.common.client.ConnectionStatusImpl;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.UserCancelledException;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public class ClientServiceImpl implements ClientService {

	static Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

	static final int POLL_RATE = 30;

	private static final int AUTHORIZE_TIMEOUT = Integer
			.parseInt(System.getProperty("logonbox.vpn.authorizeTimeout", "180"));
	private static final int PHASES_TIMEOUT = 3600 * 24;
	private static final long PING_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

	protected Map<Connection, VPNSession> activeSessions = new HashMap<>();
	protected Map<Connection, ScheduledFuture<?>> authorizingClients = new HashMap<>();
	protected Set<Connection> disconnectingClients = new HashSet<>();
	protected Map<Connection, VPNSession> connectingSessions = new HashMap<>();
	private AtomicLong transientConnectionId = new AtomicLong();
	private int appsToUpdate;

	private LocalContext context;
	private boolean guiNeedsSeparateUpdate;
	private boolean needsUpdate;
	private Semaphore startupLock = new Semaphore(1);
	private ScheduledExecutorService timer;
	private boolean updating;
	private long phasesLastRetrieved = 0;
	private JsonExtensionPhaseList phaseList;
	private ConfigurationRepository configurationRepository;
	private ConnectionRepository connectionRepository;

	public ClientServiceImpl(LocalContext context, ConnectionRepository connectionRepository,
			ConfigurationRepository configurationRepository) {
		this.context = context;
		this.configurationRepository = configurationRepository;
		this.connectionRepository = connectionRepository;

		try {
			startupLock.acquire();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		timer = Executors.newScheduledThreadPool(1);
		timer.scheduleAtFixedRate(() -> {
			long now = System.currentTimeMillis();
			Set<VPNFrontEnd> toRemove = new LinkedHashSet<>();
			synchronized (context.getFrontEnds()) {
				for (VPNFrontEnd fe : context.getFrontEnds()) {
					if (fe.getLastPing() < now - PING_TIMEOUT) {
						toRemove.add(fe);
					}
				}
			}
			for (VPNFrontEnd fe : toRemove) {
				log.warn(String.format("Front-end with source %s hasn't pinged for at least %dms", fe.getSource(),
						PING_TIMEOUT));
				context.deregisterFrontEnd(fe.getSource());
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	@Override
	public String getDeviceName() {
		String hostname = SystemUtils.getHostName();
		if (StringUtils.isBlank(hostname)) {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				hostname = "Unknown Host";
			}
		}
		String os = System.getProperty("os.name");
		if (SystemUtils.IS_OS_WINDOWS) {
			os = "Windows";
		} else if (SystemUtils.IS_OS_LINUX) {
			os = "Linux";
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			os = "Mac OSX";
		}
		return os + " " + hostname;
	}

	@Override
	public void authorized(Connection connection) {
		synchronized (activeSessions) {
			if (!authorizingClients.containsKey(connection)) {
				throw new IllegalStateException("No authorization request.");
			}
			authorizingClients.remove(connection).cancel(false);
		}
		save(connection);
		connect(connection);
	}

	@Override
	public Connection connect(String owner, String uri) {
		synchronized (activeSessions) {
			if (hasStatus(owner, uri)) {
				Connection connection = getStatus(owner, uri).getConnection();
				connect(connection);
				return connection;
			}
		}

		/* New temporary connection */
		ConnectionImpl connection = new ConnectionImpl();
		connection.setId(transientConnectionId.decrementAndGet());
		connection.updateFromUri(uri);
		connection.setConnectAtStartup(false);
		connect(connection);
		return connection;
	}

	@Override
	public void connect(Connection c) {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			VPNSession task = createJob(c);
			task.setTask(timer.schedule(() -> doConnect(task), 1, TimeUnit.MILLISECONDS));
		}
	}

	@Override
	public void deauthorize(Connection connection) {
		synchronized (activeSessions) {
			connection.deauthorize();
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
		}
		save(connection);

	}

	@Override
	public void disconnect(Connection c, String reason) {

		if (log.isInfoEnabled()) {
			log.info(String.format("Disconnecting connection with id %d, %s because '%s'", c.getId(), c.getHostname(),
					reason == null ? "" : reason));
		}
		boolean disconnect = false;
		VPNSession wireguardSession = null;
		synchronized (activeSessions) {
			if (!disconnectingClients.contains(c)) {
				if (authorizingClients.containsKey(c)) {
					if (log.isInfoEnabled()) {
						log.info("Was authorizing, cancelling");
					}
					cancelAuthorize(c);
					disconnect = true;
				}
				if (activeSessions.containsKey(c)) {
					if (log.isInfoEnabled()) {
						log.info("Was connected, disconnecting");
					}
					disconnect = true;
					wireguardSession = activeSessions.remove(c);
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
			}

			if (!disconnect) {
				throw new IllegalStateException("Not connected.");
			}
			disconnectingClients.add(c);
		}

		try {
			try {

				timer.execute(() -> {
					try {
						if (log.isInfoEnabled()) {
							log.info("Sending disconnecting event");
						}
						context.sendMessage(new VPNConnection.Disconnecting(
								String.format("/com/logonbox/vpn/%d", c.getId()), reason));
					} catch (DBusException e) {
						log.error("Failed to send disconnected event.", e);
					}
				});

				try {
					if (wireguardSession != null) {
						if (log.isInfoEnabled()) {
							log.info("Closing wireguard session");
						}
						wireguardSession.close();
						if (log.isInfoEnabled()) {
							log.info("WireGuard session closed");
						}
					}
				} catch (Exception e) {
					log.error("Failed to disconnect cleanly.", e);
				}
			} finally {
				disconnectingClients.remove(c);
			}
		} finally {
			timer.execute(() -> {
				try {
					if (log.isInfoEnabled()) {
						log.info("Sending disconnected event on bus");
					}
					context.sendMessage(
							new VPNConnection.Disconnected(String.format("/com/logonbox/vpn/%d", c.getId()), reason));

					if (log.isInfoEnabled()) {
						log.info("Sent disconnected event on bus");
					}
				} catch (DBusException e) {
					throw new IllegalStateException("Failed to send disconnected message.", e);
				}
			});
		}
	}

	private ScheduledFuture<?> cancelAuthorize(Connection c) {
		ScheduledFuture<?> s = authorizingClients.remove(c);
		if (s != null) {
			log.info(String.format("Removed authorization timeout for %s", c.getUri(true)));
			s.cancel(false);
		}
		return s;
	}

	public void failedToConnect(Connection connection, Throwable jpe) {
		synchronized (activeSessions) {
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
			connectingSessions.remove(connection);
		}
	}

	public LocalContext getContext() {
		return context;
	}

	@Override
	public String[] getMissingPackages() {
		return getContext().getPlatformService().getMissingPackages();
	}

	@Override
	public JsonExtensionPhaseList getPhases() {
		JsonExtensionPhaseList l = new JsonExtensionPhaseList();
		if (isTrackServerVersion()) {
			/*
			 * Return an empty phase list, the client should not be showing a phase list if
			 * tracking server version
			 */
			return l;
		} else {
			if (this.phaseList == null || phasesLastRetrieved < System.currentTimeMillis() - (PHASES_TIMEOUT * 1000)) {
				ObjectMapper mapper = new ObjectMapper();
				String extensionStoreRoot = AbstractExtensionUpdater.getExtensionStoreRoot();
				phasesLastRetrieved = System.currentTimeMillis();
				try {
					URL url = new URL(extensionStoreRoot + "/api/store/phases");
					URLConnection urlConnection = url.openConnection();
					this.phaseList = l;
					try (InputStream in = urlConnection.getInputStream()) {
						this.phaseList = mapper.readValue(in, JsonExtensionPhaseList.class);
					}
				} catch (IOException ioe) {
					this.phaseList = l;
					throw new IllegalStateException(
							String.format("Failed to get extension phases from %s.", extensionStoreRoot), ioe);
				}
			}
			return this.phaseList;
		}
	}

	@Override
	public boolean hasStatus(String owner, String uri) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(owner);
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(true).equals(uri)) {
					return true;
				}
			}
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(false).equals(uri)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public ConnectionStatus getStatus(String owner, String uri) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(owner);
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(true).equals(uri)) {
					return s;
				}
			}
			for (ConnectionStatus s : status) {
				if (s.getConnection().getUri(false).equals(uri)) {
					return s;
				}
			}
		}
		throw new IllegalArgumentException(String.format("No connection with URI %s.", uri));
	}

	@Override
	public ConnectionStatus getStatus(long id) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(null);
			for (ConnectionStatus s : status) {
				if (s.getConnection().getId() == id) {
					return s;
				}
			}
		}
		throw new IllegalStateException(String.format("Failed to get status for %d.", id));
	}

	@Override
	public ConnectionStatus getStatusForPublicKey(String publicKey) {
		synchronized (activeSessions) {
			List<ConnectionStatus> status = getStatus(null);
			for (ConnectionStatus s : status) {
				if (publicKey.equals(s.getConnection().getUserPublicKey())) {
					return s;
				}
			}
		}
		throw new IllegalStateException(String.format("Failed to get status for %s.", publicKey));
	}

	@Override
	public void delete(Connection connection) {
		try {
			try {
				context.sendMessage(new VPN.ConnectionRemoving("/com/logonbox/vpn", connection.getId()));
				synchronized (activeSessions) {
					if (getStatusType(connection) == Type.CONNECTED) {
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
			connectionRepository.delete(connection);
			context.sendMessage(new VPN.ConnectionRemoved("/com/logonbox/vpn", connection.getId()));
			context.getConnection().unExportObject(String.format("/com/logonbox/vpn/%d", connection.getId()));

		} catch (DBusException e) {
			throw new IllegalStateException("Failed to delete.", e);
		}
	}

	@Override
	public List<ConnectionStatus> getStatus(String owner) {

		List<ConnectionStatus> ret = new ArrayList<ConnectionStatus>();
		Collection<Connection> connections = connectionRepository.getConnections(owner);
		List<Connection> added = new ArrayList<Connection>();
		synchronized (activeSessions) {
			addConnections(ret, connections, added);
			addConnections(ret, activeSessions.keySet(), added);
			addConnections(ret, connectingSessions.keySet(), added);
		}
		return ret;

	}

	@Override
	public Type getStatusType(Connection c) {
		synchronized (activeSessions) {
			if (authorizingClients.containsKey(c))
				return Type.AUTHORIZING;
			if (activeSessions.containsKey(c))
				return Type.CONNECTED;
			if (connectingSessions.containsKey(c))
				return Type.CONNECTING;
			if (disconnectingClients.contains(c))
				return Type.DISCONNECTING;
			return Type.DISCONNECTED;
		}
	}

	@Override
	public JsonExtensionUpdate getUpdates() {
		ObjectMapper mapper = new ObjectMapper();
		/* Find the server with the highest version */
		Version highestVersion = null;
		JsonExtensionUpdate highestVersionUpdate = null;
		for (Connection connection : connectionRepository.getConnections(null)) {
			try {
				URL url = new URL(connection.getUri(false) + "/api/extensions/checkVersion");
				URLConnection urlConnection = url.openConnection();
				try (InputStream in = urlConnection.getInputStream()) {
					JsonExtensionUpdate extensionUpdate = mapper.readValue(in, JsonExtensionUpdate.class);
					Version version = new Version(extensionUpdate.getResource().getLatestVersion());
					if (highestVersion == null || version.compareTo(highestVersion) > 0) {
						highestVersion = version;
						highestVersionUpdate = extensionUpdate;
					}
				}
			} catch (IOException ioe) {
				log.info(String.format("Skipping %s:%d because it appears offline.", connection.getHostname(),
						connection.getPort()));
			}
		}
		if (highestVersionUpdate == null) {
			throw new IllegalStateException("Failed to get most recent version from any servers.");
		}
		return highestVersionUpdate;
	}

	@Override
	public UUID getUUID(String owner) {
		UUID deviceUUID;
		String key = String.format("deviceUUID.%s", owner);
		String deviceUUIDString = configurationRepository.getValue(key, "");
		if (deviceUUIDString.equals("")) {
			deviceUUID = UUID.randomUUID();
			configurationRepository.setValue(key, deviceUUID.toString());
		} else
			deviceUUID = UUID.fromString(deviceUUIDString);
		return deviceUUID;
	}

	@Override
	public boolean isGUINeedsUpdating() {
		return guiNeedsSeparateUpdate;
	}

	@Override
	public boolean isNeedsUpdating() {
		return needsUpdate;
	}

	@Override
	public boolean isTrackServerVersion() {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.trackServerVersion", "true"));
	}

	@Override
	public boolean isUpdating() {
		return updating;
	}

	@Override
	public void ping() {
		// Noop
	}

	@Override
	public String getValue(String name, String defaultValue) {
		return configurationRepository.getValue(name, defaultValue);
	}

	@Override
	public void setValue(String name, String value) {
		configurationRepository.setValue(name, value);
	}

	@Override
	public void registered(VPNFrontEnd frontEnd) {
		log.info(String.format("Registered front-end %s as %s, %s, %s", frontEnd.getSource(), frontEnd.getUsername(),
				frontEnd.isSupportsAuthorization() ? "supports auth" : "doesnt support auth",
				frontEnd.isInteractive() ? "interactive" : "not interactive"));

		/*
		 * If this front end supports authorization, send it the signal to start
		 * authorizing
		 */
		if (frontEnd.isSupportsAuthorization()) {
			getTimer().schedule(() -> {
				try {
					for (ConnectionStatus conx : getStatus(frontEnd.getUsername())) {
						if (conx.getStatus() == Type.AUTHORIZING) {

							context.sendMessage(new VPNConnection.Authorize(
									String.format("/com/logonbox/vpn/%d", conx.getConnection().getId()),
									"/logonbox-vpn-client/"));

							/* Done */
							return;
						}
					}
				} catch (Exception e) {
					throw new IllegalStateException("Failed to get connections.", e);
				}
			}, 6, TimeUnit.SECONDS);

		}

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
			if (frontEnd.isInteractive()) {
				if (guiNeedsSeparateUpdate) {
					/* Do the separate GUI update */
					appsToUpdate = 1;
					ClientUpdater guiJob = new ClientUpdater(frontEnd.getPlace(), ExtensionTarget.CLIENT_GUI, context);

					try {
						context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate));
						try {
							boolean atLeastOneUpdate = guiJob.update();
							if (atLeastOneUpdate)
								log.info("Update complete, at least one found so restarting.");
							else
								log.info("No updates available.");
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", atLeastOneUpdate, null));
						} catch (IOException e) {
							if(log.isDebugEnabled())
								log.error("Failed to update GUI.", e);
							else
								log.error(String.format("Failed to update GUI. %s", e.getMessage()));
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, e.getMessage()));
						}
					} catch (Exception re) {
						log.error("GUI refused to update, ignoring.", re);
						try {
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, null));
						} catch (DBusException e) {
							throw new IllegalStateException("Failed to send message.", e);
						}
					}
				}
//				else if (updating) {
//					/*
//					 * If we register while an update is taking place, try to make the client catch
//					 * up and show the update progress window
//					 */
//					try {
//						context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate));
//
//						context.sendMessage(new VPN.UpdateStart("/com/logonbox/vpn",
//								ExtensionPlace.getDefault().getApp(), serviceUpdateJob.getTotalSize()));
//
//						context.sendMessage(
//								new VPN.UpdateProgress("/com/logonbox/vpn", ExtensionPlace.getDefault().getApp(), 0,
//										serviceUpdateJob.getTransfered(), serviceUpdateJob.getTotalSize()));
//
//						if (serviceUpdateJob.getTransfered() >= serviceUpdateJob.getTotalSize()) {
//							context.sendMessage(new VPN.UpdateComplete("/com/logonbox/vpn",
//									ExtensionPlace.getDefault().getApp(), serviceUpdateJob.getTransfered()));
//						}
//					} catch (DBusException e) {
//						throw new IllegalStateException("Failed to send event.", e);
//					}
//				}
			}
		} finally {
			startupLock.release();
		}
	}

	@Override
	public void requestAuthorize(Connection connection) {
		synchronized (activeSessions) {
			/* Can request multiple times */
			if (connectingSessions.containsKey(connection)) {
				throw new IllegalStateException("Already connecting.");
			}
			if (activeSessions.containsKey(connection)) {
				throw new IllegalStateException("Already connected.");
			}
			if (connection.isAuthorized())
				throw new IllegalStateException("Already authorized.");

			try {
				context.sendMessage(new VPNConnection.Authorize(
						String.format("/com/logonbox/vpn/%d", connection.getId()), "/logonbox-vpn-client/"));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send message.", e);
			}

			/*
			 * Setup a timeout so that clients don't get stuck authorizing if nobody is
			 * there to authorize
			 */
			cancelAuthorize(connection);
			authorizingClients.put(connection, timer.schedule(() -> {
				disconnect(connection, "Authorization timeout.");
			}, AUTHORIZE_TIMEOUT, TimeUnit.SECONDS));
		}
	}

	@Override
	public Connection save(Connection c) {

		try {
			context.sendMessage(new VPN.ConnectionUpdated("/com/logonbox/vpn", c.getId()));
		} catch (DBusException e) {
			log.error("Failed to signal connection updated.", e);
		}
		Connection newConnection = doSave(c);
		try {
			context.sendMessage(new VPN.ConnectionUpdating("/com/logonbox/vpn", newConnection.getId()));
		} catch (DBusException e) {
			log.error("Failed to signal connection updating.", e);
		}

		return newConnection;

	}

	Connection doSave(Connection c) {
		// If a non-persistent connection is now being saved as a persistent
		// one, then update our maps
		boolean wasTransient = c.isTransient();
		Connection newConnection = connectionRepository.save(c);

		if (wasTransient) {
			log.info(String.format("Saving non-persistent connection, now has ID %d", newConnection.getId()));
		}

		synchronized (activeSessions) {
			if (activeSessions.containsKey(c)) {
				activeSessions.put(newConnection, activeSessions.remove(c));
			}
			if (authorizingClients.containsKey(c)) {
				authorizingClients.put(newConnection, authorizingClients.remove(c));
			}
			if (connectingSessions.containsKey(c)) {
				connectingSessions.put(newConnection, connectingSessions.remove(c));
			}
		}
		return newConnection;
	}

	public void scheduleConnect(Connection c) {
		checkValidConnect(c);
		if (log.isInfoEnabled()) {
			log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
		}

		Integer reconnectSeconds = Integer.valueOf(configurationRepository.getValue("client.reconnectInSeconds", "5"));

		Connection connection = connectionRepository.getConnection(c.getId());
		if (connection == null) {
			log.warn("Ignoring a scheduled connection that no longer exists, probably deleted.");
		} else {
			VPNSession job = createJob(c);
			job.setTask(timer.schedule(() -> doConnect(job), reconnectSeconds, TimeUnit.SECONDS));
		}

	}

	public void start() throws Exception {
		boolean automaticUpdates = Boolean
				.valueOf(configurationRepository.getValue(ConfigurationRepository.AUTOMATIC_UPDATES, "true"));

		if (!isTrackServerVersion() || connectionRepository.getConnections(null).size() > 0) {
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
			activeSessions.put(session.getConnection(), session);
		}

	}

	public boolean startSavedConnections() {

		try {

			int connected = 0;
			for (Connection c : connectionRepository.getConnections(null)) {
				if (c.isConnectAtStartup() && getStatusType(c) == Type.DISCONNECTED) {
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
		} catch (Exception e) {
			log.error("Failed to start service", e);
			return false;
		} finally {
			startupLock.release();
		}
	}

	@Override
	public ScheduledExecutorService getTimer() {
		return timer;
	}

	public void stopService() {

		synchronized (activeSessions) {
			activeSessions.clear();
			connectingSessions.clear();
			authorizingClients.clear();
		}
		timer.shutdown();
	}

	@Override
	public void update() {
		if (!isNeedsUpdating()) {
			throw new IllegalStateException("An update is not required.");
		}
		update(false);
	}

	public void update(boolean checkOnly) {
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
				List<ClientUpdater> updaters = new ArrayList<>();

				/*
				 * For the client service, we use the local 'extension place'
				 */
				appsToUpdate = 1;
				ExtensionPlace defaultExt = ExtensionPlace.getDefault();
				defaultExt.setDownloadAllExtensions(true);
				updaters.add(new ClientUpdater(defaultExt, ExtensionTarget.CLIENT_SERVICE, context));

				/*
				 * For the GUI, we get the extension place remotely, as the GUI itself is best
				 * placed to know what extensions it has and where they stored.
				 * 
				 * However, it's possible the GUI is not yet running, so we only do this if it
				 * is available. If this happens we may need to update the GUI as well when it
				 * eventually
				 */
				for (VPNFrontEnd fe : context.getFrontEnds()) {
					if (!fe.isUpdated()) {
						guiNeedsSeparateUpdate = false;
						appsToUpdate++;
						updaters.add(new ClientUpdater(fe.getPlace(), ExtensionTarget.CLIENT_GUI, context));
					}
				}

				try {
					if (!checkOnly) {
						context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate));
					}

					for (ClientUpdater update : updaters) {
						if ((checkOnly && update.checkForUpdates()) || (!checkOnly && update.update())) {
							updates++;
						}
					}

					if (!checkOnly) {
						if (updates > 0) {

							/*
							 * If when we started the update, the GUI wasn't attached, but it is now, then
							 * instead of restarting immediately, try to update any client extensions too
							 */
							if (guiNeedsSeparateUpdate && !context.getFrontEnds().isEmpty()) {
								appsToUpdate = 0;
								for (VPNFrontEnd fe : context.getFrontEnds()) {
									if (!fe.isUpdated()) {
										guiNeedsSeparateUpdate = false;
										appsToUpdate++;
										updaters.add(
												new ClientUpdater(fe.getPlace(), ExtensionTarget.CLIENT_GUI, context));
									}
								}
								if (appsToUpdate == 0) {
									/* Still nothing else to update, we are done */
									context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", true, null));
									log.info("Update complete, restarting.");
									System.exit(99);
								} else {
									context.sendMessage(new VPN.UpdateInit("/com/logonbox/vpn", appsToUpdate));
									int updated = 0;
									for (ClientUpdater update : updaters) {
										if (update.update())
											updated++;
									}
									context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", updated > 0, null));
									if (updated > 0) {
										log.info("Update complete, restarting.");
										System.exit(99);
									}
								}
							} else {
								context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", true, null));
								log.info("Update complete, restarting.");
								System.exit(99);
							}
						} else {
							context.sendMessage(new VPN.UpdateDone("/com/logonbox/vpn", false, "Nothing to update."));
						}
					}

				} catch (IOException | DBusException e) {
					if (log.isDebugEnabled()) {
						log.error("Failed to execute update job.", e);
					}
					else {
						log.warn(String.format("Failed to execute update job. %s", e.getMessage()));
					}
					return;
				}
			}
		} catch (Exception re) {
			if (log.isDebugEnabled()) {
				log.error("Failed to get GUI extension information. Update aborted.", re);
			}
			else {
				log.error(String.format("Failed to get GUI extension information. Update aborted. %s", re.getMessage()));
			}
		} finally {
			updating = false;
		}

		needsUpdate = updates > 0;
	}

	protected VPNSession createJob(Connection c) {
		return new VPNSession(c, getContext());
	}

	private void addConnections(List<ConnectionStatus> ret, Collection<Connection> connections,
			List<Connection> added) {
		for (Connection c : connections) {
			if (!added.contains(c)) {
				ret.add(new ConnectionStatusImpl(c, getStatusType(c)));
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

	private void checkValidConnect(Connection c) {
		synchronized (activeSessions) {
			if (connectingSessions.containsKey(c)) {
				throw new IllegalStateException("Already connecting.");
			}
			if (activeSessions.containsKey(c)) {
				throw new IllegalStateException("Already connected.");
			}
			if (authorizingClients.containsKey(c)) {
				throw new IllegalStateException("Currently authorizing.");
			}
		}
	}

	private void doConnect(VPNSession job) {
		try {

			Connection connection = job.getConnection();
			connectingSessions.put(connection, job);

			if (log.isInfoEnabled()) {
				log.info("Connecting to " + connection);
			}
			try {

				/* Fire events */
				context.sendMessage(
						new VPNConnection.Connecting(String.format("/com/logonbox/vpn/%d", connection.getId())));

				job.open();
				if (log.isInfoEnabled()) {
					log.info("Ready to " + connection);
				}

				synchronized (activeSessions) {
					connectingSessions.remove(connection);
					activeSessions.put(connection, job);

					/* Fire Events */
					try {
						context.sendMessage(
								new VPNConnection.Connected(String.format("/com/logonbox/vpn/%d", connection.getId())));
					} catch (DBusException e) {
						throw new IllegalStateException("Failed to send event.", e);
					}
				}

			} catch (Exception e) {

				failedToConnect(connection, e);
				if (e instanceof ReauthorizeException) {
					log.info(String.format("Requesting reauthorization for %s", connection.getUri(true)));
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
				} else {
					if (log.isErrorEnabled()) {
						log.error("Failed to connect " + connection, e);
					}
				}

				context.sendMessage(new VPNConnection.Failed(String.format("/com/logonbox/vpn/%d", connection.getId()),
						e.getMessage()));

				if (!(e instanceof UserCancelledException)) {
					if (connection.isStayConnected()) {
						if (log.isInfoEnabled()) {
							log.info("Stay connected is set, so scheduling new connection to " + connection);
						}
						scheduleConnect(connection);
						return;
					}
				}
			}

		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to get connection.", e);
			}
		}
	}

	@Override
	public Connection create(Connection connection) {
		try {
			context.sendMessage(new VPN.ConnectionAdding("/com/logonbox/vpn"));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to create.", e);
		}

		generateKeys(connection);
		Connection newConnection = doSave(connection);
		try {
			context.getConnection().exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
					new VPNConnectionImpl(context, connection));
			context.sendMessage(new VPN.ConnectionAdded("/com/logonbox/vpn", connection.getId()));
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to create.", e);
		}
		return newConnection;
	}

	private void generateKeys(Connection connection) {
		log.info("Generating private key");
		KeyPair key = Keys.genkey();
		connection.setUserPrivateKey(key.getBase64PrivateKey());
		connection.setUserPublicKey(key.getBase64PublicKey());
		log.info(String.format("Public key is %s", connection.getUserPublicKey()));
	}
}
