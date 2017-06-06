package com.hypersocket.client.rmi;

import java.net.URI;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import com.hypersocket.client.CredentialCache.Credential;

public interface ConnectionService extends Remote {

	public Connection createNew() throws RemoteException;
	
	public Connection createNew(URI uri) throws RemoteException;
	
	public Connection update(URI uri, Connection connection) throws RemoteException;
	
	public Connection save(Connection connection) throws RemoteException;
	
	public List<Connection> getConnections() throws RemoteException;

	public void delete(Connection con) throws RemoteException;

	public Connection getConnection(Long id) throws RemoteException;

	Connection getConnection(String server) throws RemoteException;
	
	Connection getConnectionByName(String name) throws RemoteException;
	
	Connection getConnectionByNameWhereIdIsNot(String name, Long conId) throws RemoteException;

	Boolean hasEncryptedPassword(Connection connection) throws RemoteException;

	char[] getDecryptedPassword(Connection connection) throws RemoteException;
	
	public Connection getConnectionByHostPortAndPath(String host, int port, String path) throws RemoteException;
	
	public Connection getConnectionByHostPortAndPathWhereIdIsNot(String host, int port, String path, Long conId) throws RemoteException;
	
	public Credential getCredentials(String host) throws RemoteException;
	
	public void removeCredentials(String host) throws RemoteException;
	
	public void saveCredentials(String host, String username, String password) throws RemoteException;
	
}
