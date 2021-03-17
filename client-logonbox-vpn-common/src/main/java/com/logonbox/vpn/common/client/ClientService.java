package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

import com.hypersocket.extensions.JsonExtensionPhaseList;
import com.hypersocket.extensions.JsonExtensionUpdate;

public interface ClientService extends Remote {
	
	int CONNECT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.connectTimeout", "20"));
	int HANDSHAKE_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.handshakeTimeout", "180"));
	int SERVICE_WAIT_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.serviceWaitTimeout", "2"));

	default String[] getMissingPackages() throws RemoteException {
		return new String[0];
	}
	
	boolean isNeedsUpdating() throws RemoteException;
	
	boolean isGUINeedsUpdating() throws RemoteException;
	
	boolean isUpdating() throws RemoteException;

	void registerGUI(GUICallback gui) throws RemoteException;
	
	UUID getUUID() throws RemoteException;

	void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException;
	
	Connection save(Connection c) throws RemoteException;
	
	void connect(Connection c) throws RemoteException;
	
	void disconnect(Connection c, String reason) throws RemoteException;

	List<ConnectionStatus> getStatus() throws RemoteException;

	void ping() throws RemoteException;
	
	ConfigurationService getConfigurationService() throws RemoteException;

	ConnectionStatus.Type getStatus(Connection con) throws RemoteException;

	void scheduleConnect(Connection c) throws RemoteException;

	ConnectionService getConnectionService() throws RemoteException;

	JsonExtensionPhaseList getPhases() throws RemoteException;

	void requestAuthorize(Connection connection) throws RemoteException;

	void authorized(Connection connection) throws RemoteException;
	
	boolean isTrackServerVersion() throws RemoteException;

	JsonExtensionUpdate getUpdates() throws RemoteException;

	Branding getBranding(Connection connection) throws RemoteException;

	void update() throws RemoteException;

	void deauthorize(Connection connection) throws RemoteException;
}
