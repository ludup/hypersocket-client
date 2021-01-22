package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ClientService extends Remote {
	
	default String[] getMissingPackages() throws RemoteException {
		return new String[0];
	}
	
	boolean isGUINeedsUpdating() throws RemoteException;
	
	boolean isUpdating() throws RemoteException;

	void registerGUI(GUICallback gui) throws RemoteException;

	void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException;
	
	boolean isConnected(Connection c) throws RemoteException;
	
	Connection save(Connection c) throws RemoteException;
	
	void connect(Connection c) throws RemoteException;
	
	void disconnect(Connection c) throws RemoteException;

	List<ConnectionStatus> getStatus() throws RemoteException;

	void ping() throws RemoteException;
	
	ConfigurationService getConfigurationService() throws RemoteException;

	int getStatus(Connection con) throws RemoteException;

	void scheduleConnect(Connection c) throws RemoteException;

	ConnectionService getConnectionService() throws RemoteException;
}
