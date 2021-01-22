package com.logonbox.vpn.common.client;

import java.net.URI;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ConnectionService extends Remote {

	void start() throws Exception;

	void add(Connection config) throws RemoteException;

	Connection getConfigurationForPublicKey(String publicKey) throws RemoteException;

	Connection getConnectionByHostPortAndPathWhereIdIsNot(String host, int port, String path, Long conId)
			throws RemoteException;

	Connection getConnectionByHostPortAndPath(String host, int port, String path) throws RemoteException;

	Connection getConnectionByNameWhereIdIsNot(String name, Long conId) throws RemoteException;

	Connection getConnection(Long id) throws RemoteException;

	Connection getConnectionByName(String name) throws RemoteException;

	Connection getConnection(String hostname) throws RemoteException;

	Connection save(Connection connection) throws RemoteException;

	Connection createNew() throws RemoteException;

	Connection createNew(URI uriObj) throws RemoteException;

	Connection update(URI uriObj, Connection connection) throws RemoteException;

	List<Connection> getConnections() throws RemoteException;

	void delete(Connection con) throws RemoteException;
}
