package com.logonbox.vpn.common.client;

import java.rmi.RemoteException;

import com.hypersocket.extensions.ExtensionDefinition;

public interface GUIRegistry {

	boolean hasGUI();

	GUICallback getGUI();

	void registerGUI(GUICallback gui) throws RemoteException;

	void unregisterGUI(GUICallback gui, boolean callback) throws RemoteException;

	void started(Connection connection);

	void ready(Connection connection);

	void failedToConnect(Connection connection, String reply);

	void disconnected(Connection connection, String message);

	void transportConnected(Connection connection);

	void notify(String msg, int type);

	void onExtensionUpdateComplete(String app,
			ExtensionDefinition def);

	void onUpdateProgress(String app, long sincelastProgress,
			long totalSoFar, long totalBytesExpected);

	void onUpdateStart(String app, long totalBytesExpected, Connection connection);

	void onUpdateInit(int apps) throws RemoteException;

	void onUpdateComplete(String app, long totalBytesTransfered);

	void onUpdateFailure(String app, Throwable e);

	void onUpdateDone(boolean restart, String failureMessage)
			throws RemoteException;

}