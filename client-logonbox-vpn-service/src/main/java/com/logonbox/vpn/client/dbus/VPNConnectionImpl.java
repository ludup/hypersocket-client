package com.logonbox.vpn.client.dbus;

import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.annotations.DBusInterfaceName;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

@DBusInterfaceName("com.logonbox.vpn.Connection")
public class VPNConnectionImpl extends AbstractVPNComponent implements VPNConnection {

	private Connection connection;
	private LocalContext ctx;

	public VPNConnectionImpl(LocalContext ctx, Connection connection) {
		super(ctx);
		this.connection = connection;
		this.ctx = ctx;
	}

	@Override
	public void authorize() {
		assertRegistered();
		ctx.getClientService().requestAuthorize(connection);
	}

	@Override
	public void authorized() {
		assertRegistered();
		ctx.getClientService().authorized(connection);
	}

	@Override
	public void connect() {
		assertRegistered();
		ctx.getClientService().connect(connection);
	}

	@Override
	public void deauthorize() {
		assertRegistered();
		ctx.getClientService().deauthorize(connection);
	}

	@Override
	public void delete() {
		assertRegistered();
		ctx.getClientService().delete(connection);
	}

	@Override
	public void disconnect(String reason) {
		assertRegistered();
		ctx.getClientService().disconnect(connection, reason);
	}

	@Override
	public String getAddress() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getAddress(), "");
	}

	@Override
	public String[] getAllowedIps() {
		assertRegistered();
		return connection.getAllowedIps().toArray(new String[0]);
	}

	@Override
	public String getDisplayName() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getDisplayName(), "");
	}

	@Override
	public String getDefaultDisplayName() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getDefaultDisplayName(), "");
	}

	@Override
	public String[] getDns() {
		assertRegistered();
		return connection.getDns().toArray(new String[0]);
	}

	@Override
	public String getEndpointAddress() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getEndpointAddress(), "");
	}

	@Override
	public int getEndpointPort() {
		assertRegistered();
		return connection.getEndpointPort();
	}

	@Override
	public String getHostname() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getHostname(), "");
	}

	@Override
	public long getId() {
		assertRegistered();
		return connection.getId();
	}

	@Override
	public int getMtu() {
		assertRegistered();
		return connection.getMtu();
	}

	@Override
	public String getName() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getName(), "");
	}

	@Override
	public String getObjectPath() {
		return "/com/logonbox/vpn/" + connection.getId();
	}

	@Override
	public String getPath() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getPath(), "");
	}

	@Override
	public int getPersistentKeepalive() {
		assertRegistered();
		return connection.getPersistentKeepalive();
	}

	@Override
	public int getPort() {
		assertRegistered();
		return connection.getPort();
	}

	@Override
	public String getPublicKey() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getPublicKey(), "");
	}

	@Override
	public String getStatus() {
		assertRegistered();
		return ctx.getClientService().getStatusType(connection).name();
	}

	@Override
	public String getUri(boolean withUsername) {
		assertRegistered();
		return connection.getUri(withUsername);
	}

	@Override
	public String getUsernameHint() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getUsernameHint(), "");
	}

	@Override
	public String getUserPublicKey() {
		assertRegistered();
		return StringUtils.defaultIfBlank(connection.getUserPublicKey(), "");
	}

	@Override
	public boolean hasPrivateKey() {
		assertRegistered();
		return connection.getUserPrivateKey() != null && connection.getUserPrivateKey().length() > 0;
	}

	@Override
	public boolean isAuthorized() {
		assertRegistered();
		return connection.isAuthorized();
	}

	@Override
	public boolean isConnectAtStartup() {
		assertRegistered();
		return connection.isConnectAtStartup();
	}

	public boolean isRemote() {
		return true;
	}

	@Override
	public boolean isTransient() {
		assertRegistered();
		return connection.isTransient();
	}

	@Override
	public long save() {
		assertRegistered();
		return ctx.getClientService().save(connection).getId();
	}

	@Override
	public void setAddress(String address) {
		assertRegistered();
		connection.setAddress(address);
	}

	@Override
	public void setAllowedIps(String[] allowedIps) {
		assertRegistered();
		connection.setAllowedIps(Arrays.asList(allowedIps));
	}

	@Override
	public void setConnectAtStartup(boolean connectAtStartup) {
		assertRegistered();
		connection.setConnectAtStartup(connectAtStartup);
	}

	@Override
	public void setDns(String[] dns) {
		assertRegistered();
		connection.setDns(Arrays.asList(dns));
	}

	@Override
	public void setEndpointAddress(String endpointAddress) {
		assertRegistered();
		connection.setEndpointAddress(endpointAddress);
	}

	@Override
	public void setEndpointPort(int endpointPort) {
		assertRegistered();
		connection.setEndpointPort(endpointPort);
	}

	@Override
	public void setHostname(String host) {
		assertRegistered();
		connection.setHostname(host);
	}

	@Override
	public void setName(String name) {
		assertRegistered();
		connection.setName(StringUtils.isBlank(name) ? null : name);
	}

	@Override
	public void setPeristentKeepalive(int peristentKeepalive) {
		assertRegistered();
		connection.setPeristentKeepalive(peristentKeepalive);
	}

	@Override
	public void setPort(int port) {
		assertRegistered();
		connection.setPort(port);
	}

	@Override
	public void setPublicKey(String publicKey) {
		assertRegistered();
		connection.setPublicKey(publicKey);
	}

	@Override
	public void setUsernameHint(String usernameHint) {
		assertRegistered();
		connection.setUsernameHint(usernameHint);
	}

	@Override
	public void setUserPrivateKey(String privateKey) {
		assertRegistered();
		connection.setUserPrivateKey(privateKey);
	}

	@Override
	public void setUserPublicKey(String publicKey) {
		assertRegistered();
		connection.setUserPublicKey(publicKey);
	}

	@Override
	public void update(String name, String uri, boolean connectAtStartup) {
		assertRegistered();
		connection.setName(name.equals("") ? null : name);
		connection.setConnectAtStartup(connectAtStartup);
		connection.updateFromUri(uri);
		if(!isTransient())
			save();
	}

	@Override
	public void setPath(String path) {
		assertRegistered();
		connection.setPath(path);		
	}

	@Override
	public boolean isShared() {
		return connection.isShared();
	}

	@Override
	public String getOwner() {
		return connection.getOwner();
	}

	@Override
	public void setOwner(String owner) {
		connection.setOwner(owner.equals("") ? null : owner);		
	}

	@Override
	public void setShared(boolean shared) {
		connection.setShared(shared);		
	}

	@Override
	public void setPreUp(String preUp) {
		connection.setPreUp(preUp);		
	}

	@Override
	public void setPostUp(String postUp) {
		connection.setPostUp(postUp);				
	}

	@Override
	public void setPreDown(String preDown) {
		connection.setPreDown(preDown);		
	}

	@Override
	public void setPostDown(String postDown) {
		connection.setPostDown(postDown);		
	}

	@Override
	public void setRouteAll(boolean routeAll) {
		connection.setRouteAll(routeAll);		
	}

	@Override
	public boolean isRouteAll() {
		return connection.isRouteAll();
	}

	@Override
	public String getPreUp() {
		return connection.getPreUp();
	}

	@Override
	public String getPostUp() {
		return connection.getPostUp();
	}

	@Override
	public String getPreDown() {
		return connection.getPreDown();
	}

	@Override
	public String getPostDown() {
		return connection.getPostDown();
	}

	@Override
	public long getLastHandshake() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().getLastHandshake();
	}

	@Override
	public long getRx() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().getRx();
	}

	@Override
	public long getTx() {
		assertRegistered();
		return ctx.getClientService().getStatus(connection.getId()).getDetail().getTx();
	}

	@Override
	public boolean isStayConnected() {
		assertRegistered();
		return connection.isStayConnected();
	}

	@Override
	public void setStayConnected(boolean stayConnected) {
		assertRegistered();
		connection.setStayConnected(stayConnected);
	}

	@Override
	public boolean isTemporarilyOffline() {
		return ctx.getClientService().getStatus(connection.getId()).getStatus() == ConnectionStatus.Type.TEMPORARILY_OFFLINE;
	}

}
