package com.logonbox.vpn.client.dbus;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionPlace;
import com.hypersocket.extensions.JsonExtensionPhase;
import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.updates.ClientUpdater;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionImpl;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public class VPNImpl extends AbstractVPNComponent implements VPN {
	static Logger log = LoggerFactory.getLogger(VPNImpl.class);

	private LocalContext ctx;

	public VPNImpl(LocalContext ctx) {
		super(ctx);
		this.ctx = ctx;
	}

	@Override
	public String getObjectPath() {
		return "/com/logonbox/vpn";
	}

	public boolean isRemote() {
		return true;
	}

	@Override
	public String[] getMissingPackages() {
		assertRegistered();
		return ctx.getClientService().getMissingPackages();
	}

	@Override
	public boolean isNeedsUpdating() {
		assertRegistered();
		return ctx.getClientService().isNeedsUpdating();
	}
	
	@Override
	public boolean isUpdatesEnabled() {
		assertRegistered();
		return ctx.getClientService().isUpdatesEnabled();
	}

	@Override
	public String[] getKeys() {
		assertRegistered();
		return ctx.getClientService().getKeys();
	}

	@Override
	public String getAvailableVersion() {
		assertRegistered();
		return ctx.getClientService().getAvailableVersion();
	}

	@Override
	public boolean isGUINeedsUpdating() {
		assertRegistered();
		return ctx.getClientService().isGUINeedsUpdating();
	}

	@Override
	public boolean isUpdating() {
		assertRegistered();
		return ctx.getClientService().isUpdating();
	}

	@Override
	public String getUUID() {
		assertRegistered();
		UUID uuid = ctx.getClientService().getUUID(getOwner());
		return uuid == null ? null : uuid.toString();
	}

	@Override
	public String getVersion() {
		assertRegistered();
		return HypersocketVersion.getVersion(ClientUpdater.ARTIFACT_COORDS);
	}

	@Override
	public void ping() {
		assertRegistered();
		ctx.getFrontEnd(DBusConnection.getCallInfo().getSource()).ping();
		ctx.getClientService().ping();
	}

	@Override
	public boolean isTrackServerVersion() {
		assertRegistered();
		return ctx.getClientService().isTrackServerVersion();
	}

	@Override
	public void update() {
		assertRegistered();
		ctx.getClientService().update();
	}

	@Override
	public void checkForUpdate() {
		assertRegistered();
		ctx.getClientService().checkForUpdate();
	}

	@Override
	public void deferUpdate() {
		assertRegistered();
		ctx.getClientService().deferUpdate();
	}

	@Override
	public void deregister() {
		String src = DBusConnection.getCallInfo().getSource();
		log.info(String.format("De-register front-end %s", src));
		ctx.deregisterFrontEnd(src);
	}

	@Override
	public void register(String username, boolean interactive, String id, String dirPath, String[] urls,
			boolean supportsAuthorization, Map<String, String> archives, String target) {
		log.info(String.format("Register client %s", id));
		VPNFrontEnd frontEnd = null;
		String source = DBusConnection.getCallInfo().getSource();
		if(ctx.hasFrontEnd(source)) {
			ctx.deregisterFrontEnd(source);
		}
		frontEnd = ctx.registerFrontEnd(source, target);
		frontEnd.setUsername(username);
		frontEnd.setInteractive(interactive);
		frontEnd.setSupportsAuthorization(supportsAuthorization);

		// TODO deregister when DBus goes

		Map<String, File> bootstrapArchives = new HashMap<>();
		for(Map.Entry<String, String> en : archives.entrySet()) {
			bootstrapArchives.put(en.getKey(), new File(en.getValue()));
		}

		// TODO authorize the user somehow. We need to prove the username being
		// requested is actually
		// the one that will be using the bus connection
		//
		// An idea
		// 1. The service creates a file ONLY readable by the requesting user (it should
		// be allowed to do this, its running as root)
		// 2. The service places a random cookie in this file (in 'dirPath')
		// 3. The server responds with just the filename (no path)
		// 4. The client reads this file from dirPath
		// 5. The client calls a new DBUS method 'authenticate' with the cookie value
		// 6. The server marks the sources as authenticated and will allow other methods

		frontEnd.setPlace(
				new ExtensionPlace(id, new File(dirPath), bootstrapArchives, Arrays.asList(urls).stream().map(url -> {
					try {
						return new URL(url);
					} catch (MalformedURLException murle) {
						throw new IllegalArgumentException(murle);
					}
				}).collect(Collectors.toList())));
		ctx.getClientService().registered(frontEnd);
	}

	@Override
	public void cancelUpdate() {
		assertRegistered();
		ctx.getClientService().cancelUpdate();
	}

	@Override
	public String[] getConnections() {
		assertRegistered();
		List<String> connections = new ArrayList<>();
		try {
			for (ConnectionStatus conx : ctx.getClientService().getStatus(getOwner())) {
				connections.add(String.valueOf(conx.getConnection().getId()));
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to get connections.", e);
		}
		return connections.toArray(new String[0]);
	}

	@Override
	public String getDeviceName() {
		assertRegistered();
		return ctx.getClientService().getDeviceName();
	}

	@Override
	public long getConnectionIdForURI(String uri) {
		assertRegistered();
		try {
			return ctx.getClientService().getStatus(getOwner(), uri).getConnection().getId();
		} catch (IllegalArgumentException iae) {
			return -1;
		}
	}

	@Override
	public long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode) {
		assertRegistered();
		ConnectionImpl connection = new ConnectionImpl();
		try {
			ctx.getClientService().getStatus(getOwner(), uri);
			throw new IllegalArgumentException(String.format("Connection with URI %s already exists.", uri));
		} catch (Exception e) {
			/* Doesn't exist */
			connection.updateFromUri(uri);
			VPNFrontEnd fe = ctx.getFrontEnd(DBusConnection.getCallInfo().getSource());
			connection.setOwner(fe == null ? System.getProperty("user.name") : fe.getUsername());
			connection.setConnectAtStartup(connectAtStartup);
			connection.setMode(Mode.valueOf(mode));
			connection.setStayConnected(stayConnected);
			return ctx.getClientService().create(connection).getId();
		}
	}

	@Override
	public long connect(String uri) {
		assertRegistered();
		return ctx.getClientService().connect(getOwner(), uri).getId();
	}

	@Override
	public int getNumberOfConnections() {
		assertRegistered();
		return ctx.getClientService().getStatus(getOwner()).size();
	}

	@Override
	public String getValue(String name, String defaultValue) {
		assertRegistered();
		return ctx.getClientService().getValue(name, defaultValue);
	}

	@Override
	public void setValue(String name, String value) {
		assertRegistered();
		ctx.getClientService().setValue(name, value);

	}

	@Override
	public String[] getPhases() {
		assertRegistered();
		Map<String, String> phases = new LinkedHashMap<>();
		JsonExtensionPhaseList phaseList = ctx.getClientService().getPhases();
		if (phaseList.getResult() != null) {
			for (JsonExtensionPhase phase : phaseList.getResult()) {
				if(phase.isPublicPhase() && (!isNightly(phase) || (Boolean.getBoolean("logonbox.vpn.updates.nightly") || Boolean.getBoolean("hypersocket.development"))))
					phases.put(phase.getName(), phase.getVersion());
			}
		}
		return phases.keySet().toArray(new String[0]);
	}

	@Override
	public void disconnectAll() {
		assertRegistered();
		for (ConnectionStatus s : ctx.getClientService().getStatus(getOwner())) {
			if (s.getStatus() != Type.DISCONNECTED && s.getStatus() != Type.DISCONNECTING) {
				try {
					ctx.getClientService().disconnect(s.getConnection(), null);
				} catch (Exception re) {
					log.error("Failed to disconnect " + s.getConnection().getId(), re);
				}
			}
		}
	}

	@Override
	public int getActiveButNonPersistentConnections() {
		assertRegistered();
		int active = 0;
		for (ConnectionStatus s : ctx.getClientService().getStatus(getOwner())) {
			if (s.getStatus() == Type.CONNECTED && !s.getConnection().isStayConnected()) {
				active++;
			}
		}
		return active;
	}

	@Override
	public long getMaxMemory() {
		return Runtime.getRuntime().maxMemory();
	}

	@Override
	public long getFreeMemory() {
		return Runtime.getRuntime().freeMemory();
	}

	private boolean isNightly(JsonExtensionPhase phase) {
		return phase.getName().startsWith("nightly");
	}

	@Override
	public void shutdown(boolean restart) {
		ctx.shutdown(restart);
		
	}
}
