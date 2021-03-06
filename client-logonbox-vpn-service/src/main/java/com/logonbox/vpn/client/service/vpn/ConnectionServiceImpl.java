package com.logonbox.vpn.client.service.vpn;

import java.net.URI;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.criterion.Restrictions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.db.HibernateSessionFactory;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionImpl;
import com.logonbox.vpn.common.client.ConnectionService;
import com.logonbox.vpn.common.client.Util;

public class ConnectionServiceImpl implements ConnectionService {

	public interface Listener {
		void connectionAdding(Connection connection);

		void connectionAdded(Connection connection);

		void connectionRemoving(Connection connection);

		void connectionRemoved(Connection connection);

		void connectionUpdating(Connection connection);

		void connectionUpdated(Connection connection);
	}

	static Logger log = LoggerFactory.getLogger(ConnectionServiceImpl.class);

	private Session session;
	private LocalContext context;

	public ConnectionServiceImpl(LocalContext context) throws RemoteException {
		this.context = context;
	}

//	@Override
//	public Connection getConfiguration(Connection connection) throws RemoteException {
//		CriteriaBuilder builder = session.getCriteriaBuilder();
//		CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
//		Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
//		query = query.select(root).where(builder.equal(root.get("connection"), connection));
//		TypedQuery<Connection> typeQuery = session.createQuery(query);
//		List<Connection> results = typeQuery.getResultList();
//		return results.isEmpty() ? null : results.get(0);
//	}

	@Override
	public Connection add(Connection config) throws RemoteException {
		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionAdding(config);
		synchronized (session) {
			Transaction trans = session.beginTransaction();
			try {
				session.save(config);
				session.flush();
				trans.commit();
			} catch (Exception e) {
				trans.rollback();
				log.error("Failed to save.", e);
				throw new RemoteException("Failed to save.", e);
			}
		}
		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionAdded(config);
		return config;
	}

	@Override
	public void start() throws Exception {
		session = HibernateSessionFactory.getFactory().openSession();

	}

	public Connection getConfigurationForPublicKey(String publicKey) {
		synchronized (session) {
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
			Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
			query = query.select(root).where(builder.equal(root.get("userPublicKey"), publicKey));
			TypedQuery<Connection> typeQuery = session.createQuery(query);
			List<Connection> results = typeQuery.getResultList();
			return results.isEmpty() ? null : results.get(0);
		}
	}

	private List<Listener> listeners = new ArrayList<>();

	public void addListener(Listener listener) {
		this.listeners.add(listener);
	}

	public void removeListener(Listener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public Connection createNew() {
		return new ConnectionImpl();
	}

	@Override
	public Connection createNew(URI uriObj) {
		Connection newConnection = new ConnectionImpl();
		Util.prepareConnectionWithURI(uriObj, newConnection);
		return newConnection;
	}

	@Override
	public Connection update(URI uriObj, Connection connection) throws RemoteException {
		Util.prepareConnectionWithURI(uriObj, connection);
		return connection;
	}

	@Override
	public Connection save(Connection connection) {
		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionUpdating(connection);

		Connection connectionSaved = null;
		synchronized (session) {
			Transaction trans = session.beginTransaction();

			try {

				if (connection.getId() != null) {
					log.info("Updating existing connection " + connection);
					connectionSaved = (Connection) session.merge(connection);
				} else {
					log.info("Saving new connection " + connection);
					session.save(connection);
					connectionSaved = connection;
					log.info("Saved new connection " + connection);
				}
				session.flush();
				trans.commit();
			} catch (Throwable e) {
				log.error("Failed to save connection.", e);
				trans.rollback();
				throw new IllegalStateException(e.getMessage(), e);
			}
		}

		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionUpdated(connection);

		return connectionSaved;
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Connection> getConnections() throws RemoteException {

		Criteria crit = session.createCriteria(ConnectionImpl.class);
		return crit.list();
	}

	@Override
	public void delete(Connection con) {

		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionRemoving(con);

		synchronized (session) {
			Transaction trans = session.beginTransaction();

			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("id", con.getId()));

			Connection toDelete = (Connection) crit.uniqueResult();
			if (toDelete != null) {
				session.delete(toDelete);
				session.flush();
			}
			trans.commit();
		}

		for (int i = listeners.size() - 1; i >= 0; i--)
			listeners.get(i).connectionRemoved(con);

	}

	@Override
	public Connection getConnection(final String hostname) throws RemoteException {
		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("hostname", hostname));
			return (Connection) crit.uniqueResult();
		}
	}

	@Override
	public Connection getConnectionByName(final String name) throws RemoteException {

		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("name", name));
			return (Connection) crit.uniqueResult();
		}
	}

	@Override
	public Connection getConnection(Long id) throws RemoteException {

		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("id", id));
			return (Connection) crit.uniqueResult();
		}
	}

	@Override
	public Connection getConnectionByNameWhereIdIsNot(String name, Long conId) throws RemoteException {
		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("name", name));
			crit.add(Restrictions.not(Restrictions.idEq(conId)));
			return (Connection) crit.uniqueResult();
		}
	}

	@Override
	public Connection getConnectionByHostPortAndPath(String host, int port, String path) throws RemoteException {
		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("hostname", host));
			crit.add(Restrictions.eq("port", port));
			crit.add(Restrictions.eq("path", path));
			return (Connection) crit.uniqueResult();
		}
	}

	@Override
	public Connection getConnectionByHostPortAndPathWhereIdIsNot(String host, int port, String path, Long conId)
			throws RemoteException {
		synchronized (session) {
			Criteria crit = session.createCriteria(ConnectionImpl.class);
			crit.add(Restrictions.eq("hostname", host));
			crit.add(Restrictions.eq("port", port));
			crit.add(Restrictions.eq("path", path));
			crit.add(Restrictions.not(Restrictions.idEq(conId)));
			return (Connection) crit.uniqueResult();
		}
	}

}
