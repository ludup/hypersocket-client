package com.hypersocket.client.rmi;

import java.rmi.RemoteException;

import com.hypersocket.client.rmi.GUICallback.ResourceUpdateType;
import com.hypersocket.extensions.ExtensionDefinition;

public interface GUIRegistry {

	public abstract boolean hasGUI();

	public abstract GUICallback getGUI();

	public abstract void registerGUI(GUICallback gui) throws RemoteException;

	public abstract void unregisterGUI(GUICallback gui) throws RemoteException;

	public abstract void started(Connection connection);

	public abstract void ready(Connection connection);

	public abstract void loadResources(Connection connection);

	public abstract void failedToConnect(Connection connection, String reply);

	public abstract void disconnected(Connection connection, String message);

	public abstract void transportConnected(Connection connection);

	public abstract void notify(String msg, int type);

	public abstract void onExtensionUpdateComplete(String app,
			ExtensionDefinition def);

	public abstract void onUpdateProgress(String app, long sincelastProgress,
			long totalSoFar, long totalBytesExpected);

	public abstract void onUpdateStart(String app, long totalBytesExpected, Connection connection);

	public abstract void onUpdateInit(int apps) throws RemoteException;

	public abstract void onUpdateComplete(String app, long totalBytesTransfered);

	public abstract void onUpdateFailure(String app, Throwable e);

	public abstract void updateResource(ResourceUpdateType type,
			Resource resource) throws RemoteException;

	public abstract void onUpdateDone(String failureMessage)
			throws RemoteException;

}