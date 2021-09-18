package com.logonbox.vpn.common.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public interface ClientService  {
	
	public final static File CLIENT_HOME = new File(System.getProperty("user.home") + File.separator + ".logonbox-vpn-client");	
	public final static File CLIENT_CONFIG_HOME = new File(CLIENT_HOME, "conf");
	
	int CONNECT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.connectTimeout", "12"));
	int HANDSHAKE_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.handshakeTimeout", "180"));
	int SERVICE_WAIT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.serviceWaitTimeout", "2"));

	default String[] getMissingPackages() {
		return new String[0];
	}
	
	String getDeviceName() ;
	
	boolean isNeedsUpdating() ;
	
	boolean isGUINeedsUpdating() ;
	
	boolean isUpdating() ;
	
	UUID getUUID(String owner) ;
	
	Connection save(Connection c) ;
	
	void connect(Connection c) ;
	
	Connection connect(String owner, String uri) ;
	
	void disconnect(Connection c, String reason) ;

	List<ConnectionStatus> getStatus(String owner) ;

	void ping() ;

	ConnectionStatus.Type getStatusType(Connection con) ;

	ConnectionStatus getStatus(String owner, String uri) ;

	ConnectionStatus getStatus(long id) ;

	JsonExtensionPhaseList getPhases() ;

	void requestAuthorize(Connection connection) ;

	void authorized(Connection connection) ;
	
	boolean isTrackServerVersion() ;

	JsonExtensionUpdate getUpdates() ;

	void update() ;

	void checkForUpdate() ;

	void deauthorize(Connection connection) ;

	boolean hasStatus(String owner, String uri) ;

	ConnectionStatus getStatusForPublicKey(String publicKey) ;

	void delete(Connection connection) ;

	String getValue(String name, String defaultValue) ;
	
	void setValue(String name, String value) ;

	Connection create(Connection connection);

	ScheduledExecutorService getTimer();

	void registered(VPNFrontEnd frontEnd);

	String getActiveInterface(Connection c);

	boolean isUpdatesEnabled();
	
	IOException getConnectionError(Connection connection);

	boolean isUpdateChecksEnabled();

	String getAvailableVersion();

	void deferUpdate();

	void cancelUpdate();

	void stopService();

	String[] getKeys();
}
