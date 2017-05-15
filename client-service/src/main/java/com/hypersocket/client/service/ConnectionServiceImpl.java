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

import com.hypersocket.client.db.HibernateSessionFactory;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionImpl;
import com.hypersocket.client.rmi.ConnectionService;
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
		prepareConnectionWithURI(uriObj, newConnection);
		return newConnection;
	}
	
	@Override
	public void update(URI uriObj, Connection connection) throws RemoteException {
		prepareConnectionWithURI(uriObj, connection);
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
	public Connection getConnection(String hostname) throws RemoteException {
		
		Criteria crit = session.createCriteria(ConnectionImpl.class);
		crit.add(Restrictions.eq("hostname", hostname));
		return (Connection) crit.uniqueResult();
		
	}
	
	
	@Override
	public Connection getConnectionByName(String name) throws RemoteException {
		
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
	
	private void prepareConnectionWithURI(URI uriObj, Connection connection) {
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException(
					"Only HTTPS is supported.");
		}

		log.info(String.format("Created new connection for %s",
				uriObj.toString()));

		connection.setHostname(uriObj.getHost());
		connection.setPort(uriObj.getPort() <= 0 ? 443 : uriObj.getPort());
		connection.setConnectAtStartup(false);
		String path = uriObj.getPath();
		if (path.equals("") || path.equals("/")) {
			path = "/hypersocket";
		} else if (path.indexOf('/', 1) > -1) {
			path = path.substring(0, path.indexOf('/', 1));
		}
		connection.setPath(path);

		// Prompt for authentication
		connection.setUsername(connection.getUsername());
		connection.setPassword(connection.getEncryptedPassword());
		connection.setRealm(connection.getRealm());
		
	}

}
