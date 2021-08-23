package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.AbstractIp;
import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;

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
			log.info(String.format("Connected to %s", vpnConnection.getUri(true)));
		}
		if(!vpnConnection.isAuthorized())
			throw new ReauthorizeException("New connection.");
		
		String preUp = vpnConnection.getPreUp();
		if(StringUtils.isNotBlank(preUp)) {
			log.info("Running pre-up commands.", preUp);
			runHook(preUp);  
		}
		
		ip = getLocalContext().getPlatformService().connect(this, vpnConnection);
		String postUp = vpnConnection.getPostUp();
		if(StringUtils.isNotBlank(postUp)) {
			log.info("Running post-up commands.", postUp);
			runHook(postUp);  
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
}
