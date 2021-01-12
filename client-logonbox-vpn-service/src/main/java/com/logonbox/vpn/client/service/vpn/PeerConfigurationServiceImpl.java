package com.logonbox.vpn.client.service.vpn;

import java.rmi.RemoteException;
import java.util.List;

import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.Session;
import org.hibernate.Transaction;

import com.hypersocket.client.db.HibernateSessionFactory;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.service.ConnectionServiceImpl;
import com.hypersocket.client.service.ConnectionServiceImpl.Listener;
import com.logonbox.vpn.client.LogonBoxVPNContext;
import com.logonbox.vpn.common.client.PeerConfiguration;
import com.logonbox.vpn.common.client.PeerConfigurationImpl;
import com.logonbox.vpn.common.client.PeerConfigurationService;

public class PeerConfigurationServiceImpl implements PeerConfigurationService, Listener {

	private Session session;
	private LogonBoxVPNContext context;

	public PeerConfigurationServiceImpl(LogonBoxVPNContext context) throws RemoteException {
		this.context = context;
	}

	@Override
	public PeerConfiguration getConfiguration(Connection connection) throws RemoteException {
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<PeerConfiguration> query = builder.createQuery(PeerConfiguration.class);
		Root<PeerConfigurationImpl> root = query.from(PeerConfigurationImpl.class);
		query = query.select(root).where(builder.equal(root.get("connection"), connection));
		TypedQuery<PeerConfiguration> typeQuery = session.createQuery(query);
		List<PeerConfiguration> results = typeQuery.getResultList();
		return results.isEmpty() ? null : results.get(0);
	}

	@Override
	public void add(PeerConfiguration config) throws RemoteException {
		if (config.getUserPublicKey() == null || config.getUserPublicKey().length() == 0) {
			config.setUserPublicKey(context.getPlatformService().genkey(config.getUserPrivateKey()));
		}

		Transaction trans = session.beginTransaction();
		try {
			session.save(config);
			session.flush();
			trans.commit();
		} catch (Exception e) {
			trans.rollback();
			throw new RemoteException("Failed to save.");
		}
	}

	@Override
	public void connectionRemoving(Connection connection, Session session) {
		try {
			PeerConfiguration cfg = getConfiguration(connection);
			if (cfg != null)
				session.delete(cfg);
		} catch (RemoteException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void connectionRemoved(Connection connection, Session session) {
	}

	@Override
	public void start() throws Exception {
		((ConnectionServiceImpl) context.getConnectionService()).addListener(this);
		session = HibernateSessionFactory.getFactory().openSession();

	}

	public PeerConfiguration getConfigurationForPublicKey(String publicKey) {
		CriteriaBuilder builder = session.getCriteriaBuilder();
		CriteriaQuery<PeerConfiguration> query = builder.createQuery(PeerConfiguration.class);
		Root<PeerConfigurationImpl> root = query.from(PeerConfigurationImpl.class);
		query = query.select(root).where(builder.equal(root.get("userPublicKey"), publicKey));
		TypedQuery<PeerConfiguration> typeQuery = session.createQuery(query);
		List<PeerConfiguration> results = typeQuery.getResultList();
		return results.isEmpty() ? null : results.get(0);
	}
}
