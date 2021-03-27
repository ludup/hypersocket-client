package com.logonbox.vpn.client.service.vpn;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.db.HibernateSessionFactory;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionImpl;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.Util;

public class ConnectionRepositoryImpl implements ConnectionRepository {

	static Logger log = LoggerFactory.getLogger(ConnectionRepositoryImpl.class);

	private Session session;

	public ConnectionRepositoryImpl() {
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
	public Connection update(URI uriObj, Connection connection) {
		Util.prepareConnectionWithURI(uriObj, connection);
		return connection;
	}

	@Override
	public Connection save(Connection connection) {
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
		return connectionSaved;
	}

	@Override
	public List<Connection> getConnections(String owner) {
		synchronized (session) {
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
			Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
			if (owner == null)
				query = query.select(root);
			else
				query = query.select(root)
						.where(builder.or(
								builder.equal(root.get("owner"), owner),
								builder.isTrue(root.get("shared")) 
										));
			
			TypedQuery<Connection> typeQuery = session.createQuery(query);
			return typeQuery.getResultList();
		}
	}

	@Override
	public void delete(Connection con) {
		synchronized (session) {
			Transaction trans = session.beginTransaction();
			try {
				CriteriaBuilder builder = session.getCriteriaBuilder();
				CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
				Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
				query = query.select(root).where(builder.equal(root.get("id"), con.getId()));
				TypedQuery<Connection> typeQuery = session.createQuery(query);
				List<Connection> results = typeQuery.getResultList();
				if(!results.isEmpty()) {
					session.delete(results.get(0));
				}
				session.flush();
				trans.commit();
			} catch (Throwable e) {
				log.error("Failed to save connection.", e);
				trans.rollback();
				throw new IllegalStateException(e.getMessage(), e);
			}
		}
	}

	@Override
	public Connection getConnectionByName(String owner, final String name) {

		synchronized (session) {
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
			Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
			if (owner == null)
				query = query.select(root)
						.where(builder.and(
								builder.isTrue(root.get("shared")), 
								builder.equal(root.get("name"), name)));
			else
				query = query.select(root)
						.where(builder.or(
								builder.and(
										builder.isFalse(root.get("shared")), 
										builder.equal(root.get("owner"), owner)),
										builder.equal(root.get("name"), name)),
								builder.and(
									builder.isTrue(root.get("shared")), 
									builder.equal(root.get("name"), name)));
			
			TypedQuery<Connection> typeQuery = session.createQuery(query);
			List<Connection> results = typeQuery.getResultList();
			return results.isEmpty() ? null : results.get(0);
		}
	}

	@Override
	public Connection getConnectionByURI(String owner, final String uri) {
		
		synchronized (session) {
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
			Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
			URI uriObj;
			try {
				uriObj = Util.getUri(uri);
			} catch (URISyntaxException e) {
				throw new IllegalArgumentException("Invalid URI.", e);
			}
			if (owner == null)
				query = query.select(root)
						.where(builder.and(
								builder.isTrue(root.get("shared")),
								builder.equal(root.get("hostname"), uriObj.getHost()),
								builder.equal(root.get("port"), uriObj.getPort() <= 0 ? 443 : uriObj.getPort()),
								builder.equal(root.get("path"), uriObj.getPath())
							));
			else
				query = query.select(root)
						.where(builder.or(
								builder.and(
										builder.isFalse(root.get("shared")), 
										builder.equal(root.get("owner"), owner)),
										builder.equal(root.get("hostname"), uriObj.getHost()),
										builder.equal(root.get("port"), uriObj.getPort() <= 0 ? 443 : uriObj.getPort()),
										builder.equal(root.get("path"), uriObj.getPath())
										),
								builder.and(
									builder.isTrue(root.get("shared")), 
									builder.equal(root.get("hostname"), uriObj.getHost()),
									builder.equal(root.get("port"), uriObj.getPort() <= 0 ? 443 : uriObj.getPort()),
									builder.equal(root.get("path"), uriObj.getPath())
									));
			
			TypedQuery<Connection> typeQuery = session.createQuery(query);
			List<Connection> results = typeQuery.getResultList();
			return results.isEmpty() ? null : results.get(0);
		}
	}

	@Override
	public Connection getConnection(Long id) {
		synchronized (session) {
			CriteriaBuilder builder = session.getCriteriaBuilder();
			CriteriaQuery<Connection> query = builder.createQuery(Connection.class);
			Root<ConnectionImpl> root = query.from(ConnectionImpl.class);
			query = query.select(root).where(builder.equal(root.get("id"), id));
			TypedQuery<Connection> typeQuery = session.createQuery(query);
			List<Connection> results = typeQuery.getResultList();
			return results.isEmpty() ? null : results.get(0);
		}
	}

}
