package com.hypersocket.client;

import java.util.concurrent.ExecutorService;

import com.hypersocket.client.rmi.ClientService;
import com.hypersocket.client.rmi.ConnectionService;
import com.hypersocket.client.rmi.GUIRegistry;
import com.hypersocket.client.rmi.ResourceService;

public interface LocalContext<S extends ClientService> {

	S getClientService();

	ResourceService getResourceService();

	ExecutorService getWorker();

	ExecutorService getBoss();

	ConnectionService getConnectionService();

	GUIRegistry getGuiRegistry();

	Runnable getRestartCallback();
}
