package com.logonbox.vpn.client.service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.utils.HttpUtilsHolder;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionImpl;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.Keys.KeyPair;
import com.logonbox.vpn.common.client.StatusDetail;
import com.logonbox.vpn.common.client.UserCancelledException;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public class ClientServiceImpl implements ClientService {

	static Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);

	static final int POLL_RATE = 30;

	private static final int AUTHORIZE_TIMEOUT = Integer
			.parseInt(System.getProperty("logonbox.vpn.authorizeTimeout", "300"));

	private static final String AUTHORIZE_URI = "/logonBoxVPNClient/";
	private static final long PING_TIMEOUT = TimeUnit.SECONDS.toMillis(30);
	private static final long UPDATE_SERVER_POLL_INTERVAL = TimeUnit.MINUTES.toMillis(10);

	protected Map<Connection, VPNSession> activeSessions = new HashMap<>();
	protected Map<Connection, ScheduledFuture<?>> authorizingClients = new HashMap<>();
	protected Map<Connection, VPNSession> connectingSessions = new HashMap<>();
	protected Set<Connection> disconnectingClients = new HashSet<>();
	protected Set<Connection> temporarilyOffline = new HashSet<>();

	private ConfigurationRepository configurationRepository;
	private ConnectionRepository connectionRepository;
	private LocalContext context;
	private long deferUpdatesUntil;
	private Semaphore startupLock = new Semaphore(1);
	private ScheduledExecutorService timer;
	private AtomicLong transientConnectionId = new AtomicLong();
	private ScheduledFuture<?> updateCheckTask;

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
	public void authorized(Connection connection) {
		synchronized (activeSessions) {
			if (!authorizingClients.containsKey(connection)) {
				throw new IllegalStateException("No authorization request.");
			}
			log.info(String.format("Authorized %s", connection.getDisplayName()));
			authorizingClients.remove(connection).cancel(false);
			temporarilyOffline.remove(connection);
		}
		save(connection);
		connect(connection);
	}

	@Override
	public void cancelUpdate() {
			context.getUpdateService().cancelUpdate();
			rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);
	}

	@Override
	public void checkForUpdate() {
		setDeferUpdatesUntil(0);
		update(true);
		rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);
	}

	@Override
	public void connect(Connection c) {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			c.setError(null);
			save(c);
			
			VPNSession task = createJob(c);
			temporarilyOffline.remove(c);
			task.setTask(timer.schedule(() -> doConnect(task), 1, TimeUnit.MILLISECONDS));
		}
	}

	@Override
	public Connection connect(String owner, String uri) {
		synchronized (activeSessions) {
			if (hasStatus(owner, uri)) {
				Connection connection = getStatus(owner, uri).getConnection();
				connect(connection);
				return connection;
			}

			/* New temporary connection */
			ConnectionImpl connection = new ConnectionImpl();
			connection.setId(transientConnectionId.decrementAndGet());
			connection.updateFromUri(uri);
			connection.setConnectAtStartup(false);
			connect(connection);
			return connection;
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

	@Override
	public void deauthorize(Connection connection) {
		synchronized (activeSessions) {
			log.info(String.format("De-authorizing connection %s", connection.getDisplayName()));
			connection.deauthorize();
			temporarilyOffline.remove(connection);
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
		}
		save(connection);

	}

	@Override
	public void deferUpdate() {
		long dayMs = TimeUnit.DAYS.toMillis(1);
		resetUpdateState();
		setDeferUpdatesUntil(((System.currentTimeMillis() / dayMs) * dayMs) + dayMs);
		setValue(ConfigurationRepository.DEFER_UPDATES_UNTIL, String.valueOf(deferUpdatesUntil));
		rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);
		update(true);
	}

	@Override
	public void delete(Connection connection) {
		try {
			try {
				context.sendMessage(new VPN.ConnectionRemoving("/com/logonbox/vpn", connection.getId()));
				synchronized (activeSessions) {
					if (getStatusType(connection) == Type.CONNECTED) {
						if (StringUtils.isNotBlank(connection.getUserPublicKey())) {
							VirtualInetAddress<?> addr = getContext().getPlatformService()
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
	public void disconnect(Connection c, String reason) {

		if (log.isInfoEnabled()) {
			log.info(String.format("Disconnecting connection with id %d, %s because '%s'", c.getId(), c.getHostname(),
					reason == null ? "" : reason));
		}
		boolean disconnect = false;
		VPNSession wireguardSession = null;
		synchronized (activeSessions) {
			c.setError(StringUtils.isBlank(reason) ? null : reason);
			save(c);
			temporarilyOffline.remove(c);
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

				try {
					if (log.isInfoEnabled()) {
						log.info("Sending disconnecting event");
					}
					context.sendMessage(new VPNConnection.Disconnecting(
							String.format("/com/logonbox/vpn/%d", c.getId()), reason == null ? "" : reason));
				} catch (DBusException e) {
					log.error("Failed to send disconnected event.", e);
				}

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
			try {
				if (log.isInfoEnabled()) {
					log.info("Sending disconnected event on bus");
				}

				VPNConnection.Disconnected message = new VPNConnection.Disconnected(
						String.format("/com/logonbox/vpn/%d", c.getId()), reason == null ? "" : reason);
				context.sendMessage(message);

				if (log.isInfoEnabled()) {
					log.info("Sent disconnected event on bus");
				}
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send disconnected message.", e);
			}
		}
	}

	public void failedToConnect(Connection connection, Throwable jpe) {
		synchronized (activeSessions) {
			log.info(String.format("Failed to connect %s, removing state.", connection.getDisplayName()));
			ScheduledFuture<?> f = authorizingClients.remove(connection);
			if (f != null)
				f.cancel(false);
			connection.setError(jpe.getMessage());
			save(connection);
			connectingSessions.remove(connection);
		}
	}

	@Override
	public String getActiveInterface(Connection c) {
		synchronized (activeSessions) {
			if (activeSessions.containsKey(c))
				return activeSessions.get(c).getIp().getName();
			return null;
		}
	}

	@Override
	public IOException getConnectionError(Connection connection) {

		try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
			String uri = connection.getUri(false) + "/api/server/ping";
			log.info(String.format("Testing if a connection to %s should be retried using %s.",
					connection.getDisplayName(), uri));
			String content = HttpUtilsHolder.getInstance().doHttpGetContent(uri, true, new HashMap<>());

			/*
			 * The Http service appears to be there, so this is likely an invalidated
			 * session.
			 */
			log.info("Error is not retryable, invalidate configuration. " + content);
			return new ReauthorizeException(
					"Your configuration has been invalidated, and you will need to sign-on again.");

		} catch (IOException ex) {
			/* Decide what's happening based on the error. */
			log.info("Error is retryable.", ex);
			return ex;
		}
	}

	@Override
	public List<Connection> getConnections(String owner) {
		return connectionRepository.getConnections(owner);
	}

	public LocalContext getContext() {
		return context;
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
	public String[] getKeys() {
		return configurationRepository.getKeys();
	}

	@Override
	public String[] getMissingPackages() {
		return getContext().getPlatformService().getMissingPackages();
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
	public List<ConnectionStatus> getStatus(String owner) {

		List<ConnectionStatus> ret = new ArrayList<>();
		Collection<Connection> connections = connectionRepository.getConnections(owner);
		List<Connection> added = new ArrayList<>();
		synchronized (activeSessions) {
			addConnections(ret, connections, added);
			addConnections(ret, activeSessions.keySet(), added);
			addConnections(ret, connectingSessions.keySet(), added);
		}
		return ret;

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
	public Type getStatusType(Connection c) {
		synchronized (activeSessions) {
			if (temporarilyOffline.contains(c))
				return Type.TEMPORARILY_OFFLINE;
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
	public ScheduledExecutorService getTimer() {
		return timer;
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
	public String getValue(String key, String defaultValue) {
		return configurationRepository.getValue(key, defaultValue);
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
	public boolean isUpdateChecksEnabled() {
		return "false".equals(System.getProperty("hypersocket.development.noUpdateChecks", "false"));
	}

	@Override
	public boolean isUpdatesEnabled() {
		return "false".equals(System.getProperty("hypersocket.development.noUpdates", "false"));
	}

	@Override
	public void ping() {
		// Noop
	}

	@Override
	public void registered(VPNFrontEnd frontEnd) {

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

		log.info(String.format("Registered front-end %s as %s, %s, %s", frontEnd.getSource(), frontEnd.getUsername(),
				frontEnd.isSupportsAuthorization() ? "supports auth" : "doesnt support auth",
				frontEnd.isInteractive() ? "interactive" : "not interactive"));
		for (Map.Entry<String, File> en : frontEnd.getPlace().getBootstrapArchives().entrySet()) {
			log.info(String.format("    Has extension: %s (%s)", en.getKey(), en.getValue()));
		}

//		/*
//		 * If this front end supports authorization, send it the signal to start
//		 * authorizing
//		 */
//		if (frontEnd.isSupportsAuthorization()) {
//			getTimer().schedule(() -> {
//				try {
//					for (ConnectionStatus conx : getStatus(frontEnd.getUsername())) {
//						if (conx.getStatus() == Type.AUTHORIZING) {
//
//							context.sendMessage(new VPNConnection.Authorize(
//									String.format("/com/logonbox/vpn/%d", conx.getConnection().getId()),
//									AUTHORIZE_URI, conx.getConnection().getMode().name()));
//
//							/* Done */
//							return;
//						}
//					}
//				} catch (Exception e) {
//					throw new IllegalStateException("Failed to get connections.", e);
//				}
//			}, 6, TimeUnit.SECONDS);
//
//		}

		try {
			context.getUpdateService().updateFrontEnd(frontEnd);
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

			/*
			 * Setup a timeout so that clients don't get stuck authorizing if nobody is
			 * there to authorize.
			 *
			 * Put into the map before the event so the status would be correct if some
			 * client queried it.
			 */
			cancelAuthorize(connection);
			log.info(String.format("Setting up authorize timeout for %s", connection.getDisplayName()));
			authorizingClients.put(connection, timer.schedule(() -> {
				disconnect(connection, "Authorization timeout.");
			}, AUTHORIZE_TIMEOUT, TimeUnit.SECONDS));

			try {
				log.info(String.format("Asking client to authorize %s", connection.getDisplayName()));
				context.sendMessage(new VPNConnection.Authorize(
						String.format("/com/logonbox/vpn/%d", connection.getId()), AUTHORIZE_URI, connection.getMode().name()));
			} catch (DBusException e) {
				throw new IllegalStateException("Failed to send message.", e);
			}
		}
	}

	@Override
	public void restart() {
		log.info("Restarting with exit 99.");
		stopService();
		System.exit(99);
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

	public void scheduleConnect(Connection c, boolean reconnect) {
		synchronized (activeSessions) {
			checkValidConnect(c);
			if (log.isInfoEnabled()) {
				log.info("Scheduling connect for connection id " + c.getId() + "/" + c.getHostname());
			}

			Integer reconnectSeconds = Integer
					.valueOf(configurationRepository.getValue("client.reconnectInSeconds", "5"));

			Connection connection = connectionRepository.getConnection(c.getId());
			if (connection == null) {
				log.warn("Ignoring a scheduled connection that no longer exists, probably deleted.");
			} else {
				VPNSession job = createJob(c);
				job.setReconnect(reconnect);
				job.setTask(timer.schedule(() -> doConnect(job), reconnectSeconds, TimeUnit.SECONDS));
			}
		}

	}

	@Override
	public void setValue(String key, String value) {
		String was = configurationRepository.getValue(key, null);
		if(!Objects.equals(was, value)) {
			configurationRepository.setValue(key, value);
			
			/* TODO bit of a crap way to do this. have some generic change event? */
			
			if(key.equals(ConfigurationRepository.PHASE)) {
				resetUpdateState();
				context.getUpdateService().getAvailableVersion();
				rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);
			}
			
			if (key.equals(ConfigurationRepository.LOG_LEVEL)) {
				if (StringUtils.isBlank(value))
					org.apache.log4j.Logger.getRootLogger().setLevel(getContext().getDefaultLogLevel());
				else
					org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(value));
			}
		}
	}

	public void start() throws Exception {

		deferUpdatesUntil = Long.parseLong(getValue(ConfigurationRepository.DEFER_UPDATES_UNTIL, "0"));
		rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);

		Collection<VPNSession> toStart = getContext().getPlatformService().start(getContext());
		if (!toStart.isEmpty()) {
			log.warn(String.format("%d connections already active.", toStart.size()));
		}
		for (VPNSession session : toStart) {
			activeSessions.put(session.getConnection(), session);
		}

		if ((!context.getUpdateService().isTrackServerVersion() || connectionRepository.getConnections(null).size() > 0)) {
			if(getAttempts() > 1) {
				/* Don't do update check, we are likely restarting as the result
				 * of an upgrade. The 
				 */
				log.info("Skipping checking updates for now, we have just restarted probably as result of an upate, lets not slow the client start up down.");
				return;
			}
			
			/*
			 * Only check for updates for now, waiting for the first auto
			 * update check, or the first GUI to connect at which point we 
			 * might auto update
			 */
			try {
				update(true);
				if (context.getUpdateService().isNeedsUpdating()) {
					/*
					 * If updates are manual, don't try to connect until the GUI connects and does
					 * it's update
					 */
					log.info("Needs update, awaiting GUI to connect.");
				}
			} catch (Exception e) {
				log.info(String.format("Extension versions not checked."), e);
			}
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
	public void stopService() {
		if(!timer.isShutdown())
			timer.shutdown();
		synchronized (activeSessions) {
			activeSessions.clear();
			connectingSessions.clear();
			authorizingClients.clear();
			temporarilyOffline.clear();
		}
	}

	@Override
	public void update() {
		if (!context.getUpdateService().isNeedsUpdating()) {
			throw new IllegalStateException("An update is not required.");
		}
		update(false);
		rescheduleBackgroundUpdateCheck(UPDATE_SERVER_POLL_INTERVAL);
	}

	protected VPNSession createJob(Connection c) {
		return new VPNSession(c, getContext());
	}

	protected boolean isUseAllCloudPhases() {
		return "true".equalsIgnoreCase(System.getProperty("logonbox.vpn.updates.useAllCloudPhases", "false"));
	}

	protected void rescheduleBackgroundUpdateCheck(long initialInterval) {
		/*
		 * Regardless of any other configuration or state, always check for updates
		 * periodically so the update server gets pinged and we can track basic usages
		 * of the client
		 */
		if(updateCheckTask != null)
			updateCheckTask.cancel(false);
		updateCheckTask = timer.scheduleAtFixedRate(() -> {
			/* Force phases to reload */
			context.getUpdateService().clearCaches();
			update(true);
			if(deferUpdatesUntil == 0 && isAutomaticUpdates() && context.getUpdateService().isNeedsUpdating())
				update();
		}, initialInterval, UPDATE_SERVER_POLL_INTERVAL, TimeUnit.MILLISECONDS);		
	}

	protected void resetUpdateState() {
		context.getUpdateService().resetUpdateState();
		setDeferUpdatesUntil(0);
	}

	protected void setDeferUpdatesUntil(long d) {
		deferUpdatesUntil = d;
		setValue(ConfigurationRepository.DEFER_UPDATES_UNTIL, String.valueOf(d));
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

	private void addConnections(List<ConnectionStatus> ret, Collection<Connection> connections,
			List<Connection> added) {
		for (Connection c : connections) {
			if (!added.contains(c)) {
				StatusDetail status = StatusDetail.EMPTY;
				VPNSession session = activeSessions.get(c);
				if (session != null) {
					try {
						status = getContext().getPlatformService().status(session.getIp().getName());
					} catch (IOException e) {
						throw new IllegalStateException(
								String.format("Failed to get status for an active connection %s on interface %s.",
										c.getDisplayName(), session.getIp().getName()),
								e);
					}
				}
				ret.add(new ConnectionStatusImpl(c, status, getStatusType(c), AUTHORIZE_URI));
				added.add(c);
			}
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

	private void checkConnectionsAlive() {
		synchronized (activeSessions) {
			for (Map.Entry<Connection, VPNSession> sessionEn : new HashMap<>(activeSessions).entrySet()) {
				Connection connection = sessionEn.getKey();

				if (temporarilyOffline.contains(connection)) {
					/* Temporarily offline, are we still offline? */
					try {
						if (getContext().getPlatformService().isAlive(sessionEn.getValue(), connection)) {
							/*
							 * If now completely alive again, remove from the list of temporarily offline
							 * connections and fire a connected event
							 */
							temporarilyOffline.remove(connection);
							log.info(String.format("%s back online.", connection.getDisplayName()));
							try {
								context.sendMessage(new VPNConnection.Connected(
										String.format("/com/logonbox/vpn/%d", connection.getId())));
							} catch (DBusException e) {
								throw new IllegalStateException("Failed to send event.", e);
							}

							/* Next session */
							continue;
						}
					} catch (IOException ioe) {
						if (log.isDebugEnabled())
							log.debug("Failed to test if session was alive. Assuming it isn't", ioe);
					}

					if (log.isDebugEnabled())
						log.debug(String.format("%s still temporarily offline.", connection.getDisplayName()));
				} else {

					try {
						if (!connection.isAuthorized() || authorizingClients.containsKey(connection)
								|| connectingSessions.containsKey(connection)
								|| getContext().getPlatformService().isAlive(sessionEn.getValue(), connection)) {
							/*
							 * If not authorized, still 'connecting', 'authorizing', or completely alive,
							 * skip to next session
							 */
							continue;
						}
					} catch (IOException ioe) {
						log.warn("Failed to test if session was alive. Assuming it isn't", ioe);
					}

					/* Kill the dead session */
					log.info(String.format(
							"Session with public key %s hasn't had a valid handshake for %d seconds, disconnecting.",
							connection.getUserPublicKey(), ClientService.HANDSHAKE_TIMEOUT));

					/*
					 * Try to work out why .... we can only do this with LogonBox VPN because the
					 * HTTP service should be there as well. If there is an internet outage, or a
					 * problem on the server, then we can use this HTTP channel to try and get a
					 * little more info as to why we have no received a handshake for a while.
					 */
					IOException reason = getConnectionError(connection);

					if (reason instanceof ReauthorizeException) {
						if (connection.isAuthorized())
							deauthorize(connection);
						disconnect(connection, "Re-authorize required");
					} else {
						if (connection.isStayConnected()) {
							log.info("Signalling temporarily offline.");
							temporarilyOffline.add(connection);
							try {
								context.sendMessage(new VPNConnection.TemporarilyOffline(
										String.format("/com/logonbox/vpn/%d", connection.getId()),
										reason.getMessage() == null ? "" : reason.getMessage()));
							} catch (DBusException e) {
								log.error("Failed to send message.", e);
							}
						} else {
							disconnect(connection, reason.getMessage());
						}
					}
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
					log.info(String.format("Requested  reauthorization for %s", connection.getUri(true)));
					try {
						/*
						 * The connection did not get it's first handshake in a timely manner. We don't
						 * have determined that it is very likely it needs re-authorizing
						 */
						if (connection.isAuthorized())
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

				StringBuilder errorCauseText = new StringBuilder();
				Throwable ex = e.getCause();
				while (ex != null) {
					if (!errorCauseText.toString().trim().equals("")
							&& !errorCauseText.toString().trim().endsWith(".")) {
						errorCauseText.append(". ");
					}
					if (ex.getMessage() != null)
						errorCauseText.append(ex.getMessage());
					ex = ex.getCause();
				}
				StringWriter trace = new StringWriter();
				e.printStackTrace(new PrintWriter(trace));
				context.sendMessage(new VPNConnection.Failed(String.format("/com/logonbox/vpn/%d", connection.getId()),
						e.getMessage(), errorCauseText.toString(), trace.toString()));

				if (!(e instanceof UserCancelledException)) {
					log.info(String.format("Connnection not cancelled by user. Reconnect is %s, stay connected is %s",
							job.isReconnect(), connection.isStayConnected()));
					if (connection.isStayConnected() && job.isReconnect()) {
						if (log.isInfoEnabled()) {
							log.info("Stay connected is set, so scheduling new connection to " + connection);
						}
						scheduleConnect(connection, true);
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

	private void generateKeys(Connection connection) {
		log.info("Generating private key");
		KeyPair key = Keys.genkey();
		connection.setUserPrivateKey(key.getBase64PrivateKey());
		connection.setUserPublicKey(key.getBase64PublicKey());
		log.info(String.format("Public key is %s", connection.getUserPublicKey()));
	}

	private int getAttempts() {
		try {
			return Integer.parseInt(System.getProperty("forker.info.attempts"));
		}
		catch(Exception e) {
			// First start, ignore
			return 0;
		}
	}

	private boolean isAutomaticUpdates() {
		boolean automaticUpdates = Boolean
				.valueOf(configurationRepository.getValue(ConfigurationRepository.AUTOMATIC_UPDATES, String.valueOf(ConfigurationRepository.AUTOMATIC_UPDATES_DEFAULT)));
		return automaticUpdates;
	}

	private void update(boolean checkOnly) {

		try {
			if (isUpdatesEnabled() || isUpdateChecksEnabled()) {
				if (deferUpdatesUntil == 0 || System.currentTimeMillis() >= deferUpdatesUntil) {
					setDeferUpdatesUntil(0);
					context.getUpdateService().update(checkOnly);
				} else {
					if(log.isDebugEnabled()) {
						log.debug(String.format("Updates deferred until %s",
								DateFormat.getDateTimeInstance().format(new Date(deferUpdatesUntil))));
					}
				}
			}
		} catch (Exception re) {
			if (log.isDebugEnabled()) {
				log.error("Failed to get GUI extension information. Update aborted.", re);
			} else {
				log.error(
						String.format("Failed to get GUI extension information. Update aborted. %s", re.getMessage()));
			}
		} 
	}
}
