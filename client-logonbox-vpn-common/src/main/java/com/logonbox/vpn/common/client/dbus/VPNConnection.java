package com.logonbox.vpn.common.client.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("com.logonbox.vpn.Connection")
public interface VPNConnection extends DBusInterface {

	void update(String name, String uri, boolean connectAtStartup);

	long save();

	String getHostname();

	int getPort();

	String getUri(boolean withUsername);

	boolean isTransient();

	long getId();

	String getName();

	String getDisplayName();

	String getDefaultDisplayName();

	boolean isConnectAtStartup();

	void delete();

	void disconnect(String reason);

	void connect();

	String getStatus();

	void authorize();

	void authorized();

	void deauthorize();

	String getUsernameHint();

	String getUserPublicKey();

	boolean hasPrivateKey();

	String getPublicKey();

	String getEndpointAddress();

	int getEndpointPort();

	int getMtu();

	String getAddress();

	String[] getDns();

	int getPersistentKeepalive();

	String[] getAllowedIps();

	boolean isAuthorized();

	boolean isShared();
	
	String getOwner();

	void setConnectAtStartup(boolean connectAtStartup);

	void setName(String name);

	void setHostname(String host);

	void setPort(int port);
	
	void setUsernameHint(String usernameHint);

	void setUserPrivateKey(String base64PrivateKey);

	void setUserPublicKey(String base64PublicKey);

	void setEndpointAddress(String endpointAddress);

	void setEndpointPort(int endpointPort);
	
	void setPeristentKeepalive(int peristentKeepalive);

	void setPublicKey(String publicKey);

	void setAllowedIps(String[] allowedIps);
	
	void setAddress(String address);

	void setDns(String[] dns);
	
	void setPath(String path);
	
	void setOwner(String owner);
	
	void setShared(boolean shared);
	
	void setPreUp(String preUp);
	
	void setPostUp(String preUp);
	
	void setPreDown(String preDown);
	
	void setPostDown(String preDown);
	
	void setRouteAll(boolean routeAll);
	
	boolean isRouteAll();
	
	String getPreUp();
	
	String getPostUp();
	
	String getPreDown();
	
	String getPostDown();

	String getPath();

	public class Connected extends DBusSignal {
		public Connected(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Connecting extends DBusSignal {
		public Connecting(String path) throws DBusException {
			super(path);
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Failed extends DBusSignal {

		private final String reason;

		public Failed(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Disconnected extends DBusSignal {

		private final String reason;

		public Disconnected(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Disconnecting extends DBusSignal {

		private final String reason;

		public Disconnecting(String path, String reason) throws DBusException {
			super(path, reason);
			this.reason = reason;
		}

		public String getReason() {
			return reason;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}

	public class Authorize extends DBusSignal {

		private final String uri;

		public Authorize(String path, String uri) throws DBusException {
			super(path, uri);
			this.uri = uri;
		}

		public String getUri() {
			return uri;
		}

		public long getId() {
			return Long.parseLong(getPath().substring(getPath().lastIndexOf('/') + 1));
		}
	}


}
