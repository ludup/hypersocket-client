package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.hypersocket.client.rmi.Connection;

public interface PeerConfigurationService extends Remote {
	PeerConfiguration getConfiguration(Connection connection) throws RemoteException;

	void start() throws Exception;

	void add(PeerConfiguration config) throws RemoteException;

	PeerConfiguration getConfigurationForPublicKey(String publicKey) throws RemoteException;
}
