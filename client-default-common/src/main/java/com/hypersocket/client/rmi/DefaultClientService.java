package com.hypersocket.client.rmi;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ResourceBundle;

import com.hypersocket.client.HypersocketClient;

public interface DefaultClientService extends ClientService {

	byte[] getBlob(String host, String path, long timeout) throws RemoteException;
	
	ResourceBundle getResources(Connection c) throws RemoteException;

	byte[] getBlob(Connection connection, String path, long timeout)
			throws IOException, RemoteException;

	void finishedConnecting(Connection connection, HypersocketClient<Connection> client);
}
