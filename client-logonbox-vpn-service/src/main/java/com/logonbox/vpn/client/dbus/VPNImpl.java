package com.logonbox.vpn.client.dbus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.Main;
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
	public String[] getKeys() {
		assertRegistered();
		return ctx.getClientService().getKeys();
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
		return HypersocketVersion.getVersion(Main.ARTIFACT_COORDS);
	}

	@Override
	public void ping() {
		assertRegistered();
		ctx.getFrontEnd(DBusConnection.getCallInfo().getSource()).ping();
		ctx.getClientService().ping();
	}

	@Override
	public void deregister() {
		String src = DBusConnection.getCallInfo().getSource();
		log.info(String.format("De-register front-end %s", src));
		ctx.deregisterFrontEnd(src);
	}

	@Override
	public void register(String username, boolean interactive, boolean supportsAuthorization) {
		VPNFrontEnd frontEnd = null;
		String source = DBusConnection.getCallInfo().getSource();
		log.info(String.format("Register client %s", source));
		if(ctx.hasFrontEnd(source)) {
			ctx.deregisterFrontEnd(source);
		}
		frontEnd = ctx.registerFrontEnd(source);
		frontEnd.setUsername(username);
		frontEnd.setInteractive(interactive);
		frontEnd.setSupportsAuthorization(supportsAuthorization);

		ctx.getClientService().registered(frontEnd);
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

	@Override
	public void shutdown(boolean restart) {
		ctx.shutdown(restart);
		
	}
}
