package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.Util;
import com.sshtools.forker.client.OSCommand;

public class VPNSession implements Closeable {

	public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

	static Logger log = LoggerFactory.getLogger(VPNSession.class);

	private List<String> allows = new ArrayList<>();
	private VirtualInetAddress ip;
	private LocalContext localContext;
	private Connection connection;
	private ScheduledFuture<?> task;

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
		if (task != null) {
			task.cancel(false);
		}
		if (ip != null) {
			log.info(String.format("Closing VPN session for %s", ip.getName()));

			if(StringUtils.isNotBlank(connection.getPreDown())) {
				log.info("Running pre-down commands.", connection.getPreDown());
				for(String cmd : split(connection.getPreDown())) {
					OSCommand.admin(Util.parseQuotedString(cmd));
				}
			}
			
			LocalContext cctx = getLocalContext();
			cctx.getPlatformService().disconnect(this);

			if(StringUtils.isNotBlank(connection.getPostDown())) {
				log.info("Running post-down commands.", connection.getPostDown());
				for(String cmd : split(connection.getPostDown())) {
					OSCommand.admin(Util.parseQuotedString(cmd));
				}
			}
		}
	}

	public void open() throws IOException {
		LocalContext cctx = getLocalContext();
		ConnectionStatus connection = cctx.getClientService().getStatus(this.connection.getId());
		Connection vpnConnection = connection.getConnection();
		if (log.isInfoEnabled()) {
			log.info(String.format("Connected to %s", vpnConnection.getUri(true)));
		}
		if(!vpnConnection.isAuthorized())
			throw new ReauthorizeException("New connection.");
		
		String preUp = vpnConnection.getPreUp();
		if(StringUtils.isNotBlank(preUp)) {
			log.info("Running pre-up commands.", preUp);
			for(String cmd : split(preUp)) {
				OSCommand.admin(Util.parseQuotedString(cmd));
			}
		}
		
		ip = getLocalContext().getPlatformService().connect(this, vpnConnection);
		String postUp = vpnConnection.getPostUp();
		if(StringUtils.isNotBlank(postUp)) {
			log.info("Running post-up commands.", postUp);
			for(String cmd : split(postUp)) {
				OSCommand.admin(Util.parseQuotedString(cmd));
			}
		}
		
		if (log.isInfoEnabled()) {
			log.info("Ready to " + connection);
		}

	}

	public VirtualInetAddress getIp() {
		return ip;
	}

	public List<String> getAllows() {
		return allows;
	}

	public void setTask(ScheduledFuture<?> task) {
		this.task = task;
	}
	
	private Collection<? extends String> split(String str) {
		str = str == null ? "" : str.trim();
		return str.equals("") ? Collections.emptyList() : Arrays.asList(str.split("\n"));
	}
}
