package com.logonbox.vpn.common.client;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.DBusMatchRule;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusSigHandler;
import org.freedesktop.dbus.interfaces.Local;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionPlace;
import com.logonbox.vpn.common.client.dbus.DBusClient;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

public abstract class AbstractDBusClient implements DBusClient {

	public interface BusLifecycleListener {
		void busInitializer(DBusConnection connection);

		default void busGone() {
		}
	}

	final static int DEFAULT_TIMEOUT = 10000;
	static Logger log = LoggerFactory.getLogger(AbstractDBusClient.class);

	private static final String BUS_NAME = "com.logonbox.vpn";

	private static final String ROOT_OBJECT_PATH = "/com/logonbox/vpn";

	private DBusConnection conn;
	private Object initLock = new Object();

	private ScheduledExecutorService scheduler;
	private VPN vpn;
	private boolean busAvailable;
	private List<BusLifecycleListener> busLifecycleListeners = new ArrayList<>();

	@Option(names = { "-ba", "--bus-address" }, description = "Use an alternative bus address.")
	private String busAddress;
	@Option(names = { "-sb", "--session-bus" }, description = "Use session bus.")
	private boolean sessionBus;
	@Spec
	private CommandSpec spec;
	private ScheduledFuture<?> pingTask;
	private boolean supportsAuthorization;

	protected AbstractDBusClient() {
		scheduler = Executors.newScheduledThreadPool(1);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				if(vpn != null) {
					try {
						vpn.deregister();
					}
					catch(Exception e) {
						log.warn("De-registrating failed. Maybe service is already gone.");
					}
				}
			}
		});
	}

	public boolean isSupportsAuthorization() {
		return supportsAuthorization;
	}

	public void setSupportsAuthorization(boolean supportsAuthorization) {
		this.supportsAuthorization = supportsAuthorization;
	}

	public void addBusLifecycleListener(BusLifecycleListener busInitializer) {
		this.busLifecycleListeners.add(busInitializer);
		if (busAvailable) {
			busInitializer.busInitializer(conn);
		}
	}

	public void removeBusLifecycleListener(BusLifecycleListener busInitializer) {
		this.busLifecycleListeners.remove(busInitializer);
	}

	public DBusConnection getBus() {
		return conn;
	}

	public boolean isBusAvailable() {
		return busAvailable;
	}

	public VPN getVPN() {
		lazyInit();
		return vpn;
	}

	public VPNConnection getVPNConnection(long id) {
		lazyInit();
		try {
			return conn.getRemoteObject(BUS_NAME, String.format("%s/%d", ROOT_OBJECT_PATH, id), VPNConnection.class);
		} catch (DBusException e) {
			throw new IllegalStateException("Failed to get connection.");
		}
	}

	public List<VPNConnection> getVPNConnections() {
		lazyInit();
		List<VPNConnection> l = new ArrayList<>();
		for (String id : vpn.getConnections()) {
			l.add(getVPNConnection(Long.parseLong(id)));
		}
		return l;
	}

	protected void exit() {
		scheduler.shutdown();
	}

	protected void init() throws Exception {

		log.debug("Getting bus.");
		String busAddress = this.busAddress;
		if (StringUtils.isNotBlank(busAddress)) {
			conn = DBusConnection.getConnection(busAddress);
		} else {
			if (sessionBus) {
				conn = DBusConnection.getConnection(DBusBusType.SESSION);
			} else {
				String fixedAddress = getServerDBusAddress();
				if (fixedAddress == null) {
					conn = DBusConnection.getConnection(DBusBusType.SYSTEM);
				} else {
					conn = DBusConnection.getConnection(fixedAddress);
				}
			}
		}
		log.debug("Got bus connection.");

		conn.addSigHandler(new DBusMatchRule((String) null, "org.freedesktop.DBus.Local", "Disconnected"),
				new DBusSigHandler<Local.Disconnected>() {
					@Override
					public void handle(Local.Disconnected sig) {
						busGone();
					}
				});

		/* Load the VPN object */
		loadRemote();

		for (BusLifecycleListener i : busLifecycleListeners)
			i.busInitializer(conn);
	}

	protected abstract boolean isInteractive();

	protected void lazyInit() {
		if (vpn == null) {
			synchronized (initLock) {
				try {
					init();
				} catch (DBusException | ServiceUnknown dbe) {
					busGone();
				} catch (RuntimeException re) {
					throw re;
				} catch (Exception e) {
					throw new IllegalStateException("Failed to initialize.", e);
				}
			}
		}
	}

	private void loadRemote() throws DBusException { 
		vpn = conn.getRemoteObject(BUS_NAME, ROOT_OBJECT_PATH, VPN.class);
		ExtensionPlace place = ExtensionPlace.getDefault();
		vpn.register(System.getProperty("user.name"), isInteractive(), place.getApp(), place.getDir().getAbsolutePath(),
				place.getUrls().stream().map(placeUrl -> placeUrl.toExternalForm()).collect(Collectors.toList())
						.toArray(new String[0]), supportsAuthorization, toStringMap(ExtensionPlace.getDefault().getBootstrapArchives()));
		busAvailable = true;
		log.info("Registered with DBus.");
		pingTask = scheduler.scheduleAtFixedRate(() -> {
			if (vpn != null) {
				try {
					vpn.ping();
				} catch (Exception e) {
					busGone();
				}
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	private Map<String, String> toStringMap(Map<String, File> bootstrapArchives) {
		Map<String, String> map = new HashMap<String, String>();
		for(Map.Entry<String, File> en : bootstrapArchives.entrySet()) { 
			map.put(en.getKey(), en.getValue().getAbsolutePath());
		}
		return map;
	}

	private void cancelPingTask() {
		if (pingTask != null) {
			log.info("Stopping pinging.");
			pingTask.cancel(false);
			pingTask = null;
		}
	}

	private void busGone() {
		cancelPingTask();

		if (busAvailable) {
			busAvailable = false;
			vpn = null;
			for (BusLifecycleListener b : busLifecycleListeners) {
				b.busGone();
			}
		}

		/*
		 * Only really likely to happen with the embedded bus. As the service itself
		 * hosts it.
		 */
		scheduler.schedule(() -> {
			try {
				init();
			} catch (DBusException | ServiceUnknown dbe) {
				busGone();
			} catch (RuntimeException re) {
				throw re;
			} catch (Exception e) {
				throw new IllegalStateException("Failed to schedule new connection.", e);
			}
		}, 5, TimeUnit.SECONDS);
	}
}
