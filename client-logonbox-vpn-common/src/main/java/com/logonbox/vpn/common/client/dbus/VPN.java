package com.logonbox.vpn.common.client.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public interface VPN extends DBusInterface {

	void register(String username, boolean interactive, boolean supportsAuthorization);

	String[] getMissingPackages();

	String[] getConnections();
	
	long getMaxMemory();
	
	long getFreeMemory();

	String getUUID();

	String getVersion();

	String getDeviceName();

	void ping();

	void shutdown(boolean restart);

	long getConnectionIdForURI(String uri);

	long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode);

	int getNumberOfConnections();

	long connect(String uri);

	String getValue(String key, String defaultValue);
	
	void setValue(String key, String value); 
	
	void disconnectAll();

	int getActiveButNonPersistentConnections();

	void deregister();

	String[] getKeys();

//

	public class ConnectionAdded extends DBusSignal {

		private final Long id;

		public ConnectionAdded(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionAdding extends DBusSignal {

		public ConnectionAdding(String path) throws DBusException {
			super(path);
		}
	}

	public class ConnectionRemoved extends DBusSignal {

		private final Long id;

		public ConnectionRemoved(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionRemoving extends DBusSignal {

		private final Long id;

		public ConnectionRemoving(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionUpdated extends DBusSignal {

		private final Long id;

		public ConnectionUpdated(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class ConnectionUpdating extends DBusSignal {

		private final Long id;

		public ConnectionUpdating(String path, Long id) throws DBusException {
			super(path, id);
			this.id = id;
		}

		public Long getId() {
			return id;
		}
	}

	public class GlobalConfigChange extends DBusSignal {

		private final String name;
		private final String value;

		public GlobalConfigChange(String path, String name, String value) throws DBusException {
			super(path, name, value);
			this.name = name;
			this.value = value;
		}

		public String getName() {
			return name;
		}

		public String getValue() {
			return value;
		}

	}

	public class Alert extends DBusSignal {

		private final String message;
		private final int alertType;

		public Alert(String path, String message, int alertType) throws DBusException {
			super(path, message, alertType);
			this.message = message;
			this.alertType = alertType;
		}

		public String getMessage() {
			return message;
		}

		public int getAlertType() {
			return alertType;
		}

	}

	public static  class Exit extends DBusSignal {
		public Exit(String path) throws DBusException {
			super(path);
		}
	}

}
