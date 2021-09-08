package com.logonbox.vpn.common.client.dbus;

import java.util.Map;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("com.logonbox.vpn.VPN")
public interface VPN extends DBusInterface {

	void register(String username, boolean interactive, String app, String dirPath, String[] urls, boolean supportsAuthorization, Map<String, String> archives, String target);

	String[] getMissingPackages();

	String[] getConnections();
	
	long getMaxMemory();
	
	long getFreeMemory();

	boolean isUpdatesEnabled();

	boolean isNeedsUpdating();

	boolean isGUINeedsUpdating();

	boolean isUpdating();

	String getAvailableVersion();

	String getUUID();

	String getVersion();

	String getDeviceName();

	void ping();

	Map<String, String> getPhases();

	boolean isTrackServerVersion();

//	JsonExtensionUpdate getUpdates();

	void update();

	void checkForUpdate();

	void cancelUpdate();

	long getConnectionIdForURI(String uri);

	long createConnection(String uri, boolean connectAtStartup, boolean stayConnected, String mode);

	int getNumberOfConnections();

	long connect(String uri);

	String getValue(String name, String defaultValue);
	
	void setValue(String name, String value); 
	
	void disconnectAll();

	int getActiveButNonPersistentConnections();

	void deregister();
	
	void deferUpdate();

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

	public class ExtensionUpdated extends DBusSignal {

		private final String app;
		private final String extensionId;

		public ExtensionUpdated(String path, String app, String extensionId) throws DBusException {
			super(path, app, extensionId);
			this.app = app;
			this.extensionId = extensionId;
		}

		public String getApp() {
			return app;
		}

		public String getExtensionId() {
			return extensionId;
		}
	}

	public class UpdateProgress extends DBusSignal {

		private final String app;
		private final long sinceLastProgress;
		private final long totalSoFar;
		private final long totalBytesExpected;

		public UpdateProgress(String path, String app, long sinceLastProgress, long totalSoFar, long totalBytesExpected)
				throws DBusException {
			super(path, app, sinceLastProgress, totalSoFar, totalBytesExpected);
			this.app = app;
			this.sinceLastProgress = sinceLastProgress;
			this.totalSoFar = totalSoFar;
			this.totalBytesExpected = totalBytesExpected;
		}

		public String getApp() {
			return app;
		}

		public long getSinceLastProgress() {
			return sinceLastProgress;
		}

		public long getTotalSoFar() {
			return totalSoFar;
		}

		public long getTotalBytesExpected() {
			return totalBytesExpected;
		}

	}

	public class UpdateStart extends DBusSignal {

		private final String app;
		private final long totalBytesExpected;

		public UpdateStart(String path, String app, long totalBytesExpected) throws DBusException {
			super(path, app, totalBytesExpected);
			this.app = app;
			this.totalBytesExpected = totalBytesExpected;
		}

		public String getApp() {
			return app;
		}

		public long getTotalBytesExpected() {
			return totalBytesExpected;
		}

	}

	public class UpdateInit extends DBusSignal {

		private final int apps;

		public UpdateInit(String path, int apps) throws DBusException {
			super(path, apps);
			this.apps = apps;
		}

		public int getApps() {
			return apps;
		}

	}

	public class UpdateComplete extends DBusSignal {

		private final String app;
		private final long totalBytesTransfered;

		public UpdateComplete(String path, String app, long totalBytesTransfered) throws DBusException {
			super(path, app, totalBytesTransfered);
			this.app = app;
			this.totalBytesTransfered = totalBytesTransfered;
		}

		public String getApp() {
			return app;
		}

		public long getTotalBytessTransfered() {
			return totalBytesTransfered;
		}

	}

	public class UpdateFailure extends DBusSignal {

		private final String app;
		private final String message;
		private final String trace;

		public UpdateFailure(String path, String app, String message, String trace) throws DBusException {
			super(path, app, message, trace);
			this.app = app;
			this.message = message;
			this.trace = trace;
		}

		public String getTrace() {
			return trace;
		}

		public String getApp() {
			return app;
		}

		public String getMessage() {
			return message;
		}

	}

	public class UpdateDone extends DBusSignal {

		private final boolean restart;
		private final String failureMessage;

		public UpdateDone(String path, boolean restart, String failureMessage) throws DBusException {
			super(path, restart, failureMessage);
			this.restart = restart;
			this.failureMessage = failureMessage;
		}

		public boolean isRestart() {
			return restart;
		}

		public String getFailureMessage() {
			return failureMessage;
		}

	}




}
