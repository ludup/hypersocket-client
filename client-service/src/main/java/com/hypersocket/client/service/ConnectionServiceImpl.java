package com.hypersocket.client.service;

import java.net.URI;
import java.rmi.RemoteException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.CredentialCache;
import com.hypersocket.client.CredentialCache.Credential;
import com.hypersocket.client.db.HibernateSessionFactory;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionImpl;
import com.hypersocket.client.rmi.ConnectionService;
import com.hypersocket.client.rmi.Util;
import com.hypersocket.encrypt.RsaEncryptionProvider;

public class ConnectionServiceImpl implements ConnectionService {

	static Logger log = LoggerFactory.getLogger(ClientServiceImpl.class);
	
	Session session;
	public ConnectionServiceImpl() {
		session = HibernateSessionFactory.getFactory().openSession();
	}

	@Override
	public Connection createNew() {
		return new ConnectionImpl();
	}
	
	@Override
	public Connection createNew(URI uriObj) {
		Connection newConnection =  new ConnectionImpl();
		Util.prepareConnectionWithURI(uriObj, newConnection);
		return newConnection;
	}
	
	@Override
	public void update(URI uriObj, Connection connection) throws RemoteException {
		Util.prepareConnectionWithURI(uriObj, connection);
		// Prompt for authentication
		connection.setUsername(connection.getUsername());
		connection.setPassword(connection.getEncryptedPassword());
		connection.setRealm(connection.getRealm());
	}
	

	@Override
	public Connection save(Connection connection) {
	
		Transaction trans = session.beginTransaction();
		
		try {
			if(StringUtils.isNotBlank(connection.getEncryptedPassword())) {
				if(!connection.getEncryptedPassword().startsWith("!ENC!")) {
					connection.setPassword("!ENC!" + RsaEncryptionProvider.getInstance().encrypt(connection.getEncryptedPassword()));
				}
			}
			if(connection.getId()!=null) {
				log.info("Updating existing connection " + connection);
				session.merge(connection);
			} else {
				log.info("Saving new connection " + connection);
				session.save(connection);
			}
			session.flush();
			trans.commit();
		} catch (Throwable e) {
			trans.rollback();
			throw new IllegalStateException(e.getMessage(), e);
		}
			
		return connection;
	}
	
	@Override
	public Boolean hasEncryptedPassword(Connection connection) throws RemoteException {
		String password = connection.getEncryptedPassword();
		if(StringUtils.isNotBlank(password)) {
			if(password.startsWith("!ENC!")) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public char[] getDecryptedPassword(Connection connection) throws RemoteException {
		if(!hasEncryptedPassword(connection)) {
			return StringUtils.isBlank(connection.getEncryptedPassword()) ? "".toCharArray() : connection.getEncryptedPassword().toCharArray();
		}
		
		try {
			return RsaEncryptionProvider.getInstance().decrypt(connection.getEncryptedPassword().substring(5)).toCharArray();
		} catch (Throwable e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Connection> getConnections() throws RemoteException {
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		return (List<Connection>) crit.list();
	}

	@Override
	public void delete(Connection con) {
		
		Transaction trans = session.beginTransaction();
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("id", con.getId()));
		
		Connection toDelete = (Connection) crit.uniqueResult();
		if(toDelete != null) {
			session.delete(toDelete);
			session.flush();
		}
		trans.commit();
		
	}

	@Override
	public Connection getConnection(final String hostname) throws RemoteException {
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("hostname", hostname));
		return (Connection) crit.uniqueResult();
	}
	
	
	@Override
	public Connection getConnectionByName(final String name) throws RemoteException {
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("name", name));
		return (Connection) crit.uniqueResult();
	}
	
	@Override
	public Connection getConnection(Long id) throws RemoteException {
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("id", id));
		return (Connection) crit.uniqueResult();
	}

	@Override
	public Connection getConnectionByNameWhereIdIsNot(String name, Long conId) throws RemoteException {
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("name", name));
		crit.add(Restrictions.not(Restrictions.idEq(conId)));
		return (Connection) crit.uniqueResult();
	}

	@Override
	public Connection getConnectionByHostPortAndPath(String host, int port, String path) throws RemoteException {
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("hostname", host));
		crit.add(Restrictions.eq("port", port));
		crit.add(Restrictions.eq("path", path));
		return (Connection) crit.uniqueResult();
	}

	@Override
	public Connection getConnectionByHostPortAndPathWhereIdIsNot(String host, int port, String path, Long conId)
			throws RemoteException {
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("hostname", host));
		crit.add(Restrictions.eq("port", port));
		crit.add(Restrictions.eq("path", path));
		crit.add(Restrictions.not(Restrictions.idEq(conId)));
		return (Connection) crit.uniqueResult();
	}

	@Override
	public Credential getCredentials(String host) {
		return CredentialCache.getInstance().getCredentials(host);
	}

	@Override
	public void removeCredentials(String host) {
		CredentialCache.getInstance().removeCredentials(host);
	}

	@Override
	public void saveCredentials(String host, String username, String password) {
		CredentialCache.getInstance().saveCredentials(host, username, password);
	}

}
