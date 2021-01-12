package com.hypersocket.client.rmi;

import java.net.URI;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

import org.hibernate.Session;

import com.hypersocket.client.CredentialCache.Credential;

public interface ConnectionService extends Remote {

	Connection createNew() throws RemoteException;
	
	Connection createNew(URI uri) throws RemoteException;
	
	Connection update(URI uri, Connection connection) throws RemoteException;
	
	Connection save(Connection connection) throws RemoteException;
	
	List<Connection> getConnections() throws RemoteException;

	void delete(Connection con) throws RemoteException;

	Connection getConnection(Long id) throws RemoteException;

	Connection getConnection(String server) throws RemoteException;
	
	Connection getConnectionByName(String name) throws RemoteException;
	
	Connection getConnectionByNameWhereIdIsNot(String name, Long conId) throws RemoteException;

	Boolean hasEncryptedPassword(Connection connection) throws RemoteException;

	char[] getDecryptedPassword(Connection connection) throws RemoteException;
	
	Connection getConnectionByHostPortAndPath(String host, int port, String path) throws RemoteException;
	
	Connection getConnectionByHostPortAndPathWhereIdIsNot(String host, int port, String path, Long conId) throws RemoteException;
	
	Credential getCredentials(String host) throws RemoteException;
	
	void removeCredentials(String host) throws RemoteException;
	
	void saveCredentials(String host, String username, String password) throws RemoteException;
	
}
