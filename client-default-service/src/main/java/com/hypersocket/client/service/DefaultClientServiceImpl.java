package com.hypersocket.client.service;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.DefaultContext;
import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.DefaultClientService;

public class DefaultClientServiceImpl
		extends AbstractClientServiceImpl<DefaultClientContext, DefaultConnectionJob, DefaultContext>
		implements DefaultClientService {

	static Logger log = LoggerFactory.getLogger(DefaultClientServiceImpl.class);

	private Map<Connection, HypersocketClient<Connection>> activeClients = new HashMap<Connection, HypersocketClient<Connection>>();
	private Map<Connection, ResourceBundle> resources = new HashMap<>();

	public DefaultClientServiceImpl(DefaultContext ctx) {
		super(ctx);
	}

	@Override
	public ResourceBundle getResources(Connection c) throws RemoteException {
		if (!resources.containsKey(c)) {
			HypersocketClient<Connection> conx = activeClients.get(c);
			if (conx != null) {
				try {
					resources.put(c, conx.getResources());
				} catch (IOException e) {
					log.error(String.format("Failed to get resources for connection %d", c.getId()));
					resources.put(c, null);
				}
			}
		}
		return resources.get(c);
	}

	@Override
	public byte[] getBlob(Connection connection, String path, long timeout) throws IOException {

		HypersocketClient<Connection> s = null;
		for (HypersocketClient<Connection> a : activeClients.values()) {
			if (a.getAttachment() == connection) {
				s = a;
				break;
			}
		}
		if (s == null) {
			throw new RemoteException("No connection for " + connection);
		}
		try {
			return s.getTransport().getBlob(path, timeout);
		} catch (IOException e) {
			throw new RemoteException(e.getMessage());
		}

	}

	@Override
	public byte[] getBlob(String host, String path, long timeout) throws RemoteException {
		HypersocketClient<Connection> s = null;
		for (HypersocketClient<Connection> a : activeClients.values()) {
			if (a.getHost().equals(host)) {
				s = a;
				break;
			}
		}
		if (s == null) {
			throw new RemoteException("No connection for " + host);
		}
		try {
			return s.getTransport().getBlob(path, timeout);
		} catch (IOException e) {
			throw new RemoteException(String.format("Failed to get %s from %s. %s", path, host, e.getMessage()));
		}
	}

	@Override
	public DefaultClientContext createClientContent(HypersocketClient<Connection> client) {
		return new DefaultClientContext() {
			@Override
			public DefaultContext getLocalContext() {
				return getContext();
			}

			@Override
			public HypersocketClient<Connection> getClient() {
				return client;
			}
		};
	}

	@Override
	public void stopService() throws RemoteException {

		for (HypersocketClient<?> client : activeClients.values()) {
			if (log.isInfoEnabled()) {
				log.info(String.format("%s service is stopping", client.getHost()));
			}
			client.disconnect(false);
		}

		resources.clear();
		super.stopService();
	}

	@Override
	public void disconnect(Connection c) throws RemoteException {
		resources.remove(c);
		super.disconnect(c);
	}

	@Override
	protected void disconnectClient(Connection c) {
		activeClients.remove(c).disconnect(false);
	}

	@Override
	protected void onSave(Connection oldConnection, Connection newConnection) {
		activeClients.put(newConnection, activeClients.remove(oldConnection));
	}

	@Override
	public void finishedConnecting(Connection connection, HypersocketClient<Connection> client) {
		activeClients.put(connection, client);
	}

	@Override
	protected DefaultConnectionJob createJob(Connection c) throws RemoteException {
		return new DefaultConnectionJob(c.getUri(true), new Locale(configurationService.getValue("ui.locale", "en")),
				getContext(), c);
	}

}
