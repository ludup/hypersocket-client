package com.logonbox.vpn.common.client;

import java.io.Serializable;
import java.rmi.Remote;
import java.rmi.RemoteException;

import com.hypersocket.extensions.ExtensionDefinition;
import com.hypersocket.extensions.ExtensionPlace;


public interface GUICallback extends Serializable, Remote {
	
	public static final int NOTIFY_ERROR = 0;
	public static final int NOTIFY_WARNING = 1;
	public static final int NOTIFY_INFO = 2;
	public static final int NOTIFY_CONNECT = 3;
	public static final int NOTIFY_DISCONNECT = 4;

	void registered() throws RemoteException;

	void unregistered() throws RemoteException;

	void notify(String msg, int type) throws RemoteException;
	
	void showBrowser(Connection connection, String uri) throws RemoteException;

	void disconnected(Connection connection, String errorMessage)
			throws RemoteException;

	void failedToConnect(Connection connection, String errorMessage)
			throws RemoteException;

	void transportConnected(Connection connection) throws RemoteException;

	void ready(Connection connection) throws RemoteException;

	void started(Connection connection) throws RemoteException;

	void onUpdateInit(int expectedApps) throws RemoteException;
	
	void onUpdateStart(String app, long totalBytesExpected) throws RemoteException;
	
	void onUpdateProgress(String app, long sincelastProgress, long totalSoFar, long totalBytesExpected) throws RemoteException;
	
	void onUpdateComplete(long totalBytesTransfered, String app) throws RemoteException;
	
	void onUpdateFailure(String app, String message) throws RemoteException;
	
	void onExtensionUpdateComplete(String app, ExtensionDefinition def) throws RemoteException;
	
	void onUpdateDone(boolean restart, String failureMessage)  throws RemoteException;
	
	ExtensionPlace getExtensionPlace() throws RemoteException;

	boolean isInteractive() throws RemoteException;
	
	void ping() throws RemoteException;

	void onConnectionAdded(Connection connection)  throws RemoteException;

	void onConnectionRemoved(Connection connection)  throws RemoteException;

	void onConnectionUpdated(Connection connection)  throws RemoteException;

	void onConfigurationUpdated(String name, String value)  throws RemoteException;
}
