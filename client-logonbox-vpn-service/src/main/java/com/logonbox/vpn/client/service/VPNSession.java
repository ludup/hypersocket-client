package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientAdapter;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.UserCancelledException;
import com.hypersocket.netty.NettyClientTransport;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;

public class VPNSession extends AbstractConnectionJob implements Closeable {

	public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

	static Logger log = LoggerFactory.getLogger(AbstractConnectionJob.class);

	private List<String> allows = new ArrayList<>();
	private VirtualInetAddress ip;

	private LocalContext localContext;
	private Connection connection;

	public LocalContext getLocalContext() {
		return localContext;
	}

	public Connection getConnection() {
		return connection;
	}

	public VPNSession(Connection connection, LocalContext localContext) {
		this(connection, localContext, null);
	}

	public VPNSession(Connection connection, LocalContext localContext, VirtualInetAddress ip) {
		this.localContext = localContext;
		this.connection = connection;
		this.ip = ip;
	}

	@Override
	public void close() throws IOException {
		log.info(String.format("Closing VPN session for %s", ip.getName()));
		try {
			ip.setRoutes(new ArrayList<>());
		} finally {
			ip.down();
		}
	}

	@Override
	public void run() {

		if (log.isInfoEnabled()) {
			log.info("Connecting to " + connection);
		}

		LocalContext cctx = getLocalContext();
		ClientServiceImpl clientServiceImpl = (ClientServiceImpl) cctx.getClientService();

		HypersocketClientListener<Connection> listener = new HypersocketClientAdapter<Connection>() {
			@Override
			public void disconnected(HypersocketClient<Connection> client, boolean onError) {
				clientServiceImpl.disconnected(connection, client);
				log.info("Client has disconnected, informing GUI");
				cctx.getGuiRegistry().disconnected(connection, onError ? "Error occured during connection." : null);
				if (client.getAttachment().isStayConnected() && onError) {
					try {
						clientServiceImpl.scheduleConnect(connection);
					} catch (RemoteException e1) {
					}
				}
			}
		};

		ServiceClient client = null;
		try {
			/*
			 * This is the HTTPs connection to the server, it is effectively now only used
			 * to get the current version for updates.
			 */
			client = new ServiceClient(new NettyClientTransport(cctx.getBoss(), cctx.getWorker()), clientServiceImpl,
					Locale.getDefault(), listener, connection, cctx.getGuiRegistry());

			client.connect(connection.getHostname(), connection.getPort(), connection.getPath(), Locale.getDefault());

			if (log.isInfoEnabled()) {
				log.info("Connected to " + connection);
			}

			start(connection);
			cctx.getGuiRegistry().transportConnected(connection);
			cctx.getGuiRegistry().ready(connection);
			clientServiceImpl.finishedConnecting(connection, this);

		} catch (Throwable e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to connect " + connection, e);
			}
			cctx.getGuiRegistry().failedToConnect(connection, e.getMessage());
			clientServiceImpl.failedToConnect(connection, e);

			if (!(e instanceof UserCancelledException)) {
				if (connection.isStayConnected()) {
					try {
						cctx.getClientService().scheduleConnect(connection);
						return;
					} catch (RemoteException e1) {
					}
				}
			}
		}
	}

	public List<String> getAllows() {
		return allows;
	}

	void start(Connection configuration) throws IOException {
		getLocalContext().getPlatformService().connect(this, configuration);
	}
}
