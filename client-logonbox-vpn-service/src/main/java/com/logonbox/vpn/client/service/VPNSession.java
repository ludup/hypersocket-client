package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;

public class VPNSession implements Closeable {

	public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

	static Logger log = LoggerFactory.getLogger(VPNSession.class);

	private List<String> allows = new ArrayList<>();
	private VirtualInetAddress<?> ip;
	private LocalContext localContext;
	private Connection connection;
	private ScheduledFuture<?> task;
	private boolean reconnect;

	public boolean isReconnect() {
		return reconnect;
	}

	public void setReconnect(boolean reconnect) {
		this.reconnect = reconnect;
	}

	public LocalContext getLocalContext() {
		return localContext;
	}
	
	public Connection getConnection() {
		return connection;
	}

	public VPNSession(Connection connection, LocalContext localContext) {
		this(connection, localContext, null);
	}

	public VPNSession(Connection connection, LocalContext localContext, VirtualInetAddress<?> ip) {
		this.localContext = localContext;
		this.connection = connection;
		this.ip = ip;
	}

	@Override
	public void close() throws IOException {
		if (task != null) {
			task.cancel(false);
		}
		if (ip != null) {
			log.info(String.format("Closing VPN session for %s", ip.getName()));

			if(StringUtils.isNotBlank(connection.getPreDown())) {
				log.info("Running pre-down commands.", connection.getPreDown());
				runHook(connection.getPreDown());  
			}
			
			LocalContext cctx = getLocalContext();
			cctx.getPlatformService().disconnect(this);

			if(StringUtils.isNotBlank(connection.getPostDown())) {
				log.info("Running post-down commands.", connection.getPostDown());
				runHook(connection.getPostDown());  
			}
		}
	}

	private void runHook(String hookScript) throws IOException {
		getLocalContext().getPlatformService().runHook(this, hookScript);
	}

	public void open() throws IOException {
		LocalContext cctx = getLocalContext();
		ConnectionStatus connection = cctx.getClientService().getStatus(this.connection.getId());
		Connection vpnConnection = connection.getConnection();
		if (log.isInfoEnabled()) {
			log.info(String.format("Connecting to %s", vpnConnection.getUri(true)));
		}
		if(!vpnConnection.isAuthorized()) {
			throw new ReauthorizeException("Requires authorization.");
		}
		
		String preUp = vpnConnection.getPreUp();
		if(StringUtils.isNotBlank(preUp)) {
			log.info("Running pre-up commands.", preUp);
			runHook(preUp);  
		}
		
		try {
			ip = getLocalContext().getPlatformService().connect(this, vpnConnection);
		}
		catch(ReauthorizeException re) {
			/* Probe for the reason we did not get a handshake by testing
			 * the HTTP service.
			 */
			IOException ioe = cctx.getClientService().getConnectionError(vpnConnection);
			if(ioe instanceof ReauthorizeException)
				throw re;
			else
				throw ioe;
		}
		
		String postUp = vpnConnection.getPostUp();
		if(StringUtils.isNotBlank(postUp)) {
			log.info("Running post-up commands.", postUp);
			runHook(postUp);  
		}
		
		if (log.isInfoEnabled()) {
			log.info("Ready to " + connection);
		}

	}

	public VirtualInetAddress<?> getIp() {
		return ip;
	}

	public List<String> getAllows() {
		return allows;
	}

	public void setTask(ScheduledFuture<?> task) {
		this.task = task;
	}
}
