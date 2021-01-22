package com.logonbox.vpn.client.service;

import java.io.IOException;
import java.rmi.RemoteException;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.vpn.ConnectionServiceImpl;
import com.logonbox.vpn.client.service.vpn.ConnectionServiceImpl.Listener;
import com.logonbox.vpn.common.client.Connection;

public class LogonBoxVPNClientServiceImpl
		extends AbstractClientServiceImpl
		implements Listener {

	static Logger log = LoggerFactory.getLogger(LogonBoxVPNClientServiceImpl.class);

	public LogonBoxVPNClientServiceImpl(LocalContext ctx) {
		super(ctx);
		((ConnectionServiceImpl) ctx.getConnectionService()).addListener(this);

	}

	@Override
	public String[] getMissingPackages() throws RemoteException {
		return getContext().getPlatformService().getMissingPackages();
	}

	public void start() {
		for (LogonBoxVPNSession session : getContext().getPlatformService().start(getContext())) {
			activeClients.put(session.getConnection(), session);
		}
	}

	@Override
	protected void beforeDisconnectClient(Connection c) throws IOException {
		synchronized (activeClients) {
			LogonBoxVPNSession wireguardSession = activeClients.get(c);
			if (wireguardSession != null)
				wireguardSession.close();
		}

	}

	@Override
	public ClientContext createClientContent(HypersocketClient<Connection> client) {
		return new ClientContext() {

			@Override
			public LocalContext getLocalContext() {
				return getContext();
			}

			@Override
			public HypersocketClient<Connection> getClient() {
				return client;
			}
		};
	}

	@Override
	protected LogonBoxVPNSession createJob(Connection c) throws RemoteException {
		return new LogonBoxVPNSession(c, getContext());
	}

	@Override
	public void connectionRemoving(Connection connection, Session session) {
		try {
			if (isConnected(connection))
				disconnect(connection);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to disconnect.", e);
		}
	}

	@Override
	public void connectionRemoved(Connection connection, Session session) {
	}

}
