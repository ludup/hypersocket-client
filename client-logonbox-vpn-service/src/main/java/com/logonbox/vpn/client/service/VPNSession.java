package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;

public class VPNSession implements Closeable {

	public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

	static Logger log = LoggerFactory.getLogger(VPNSession.class);

	private List<String> allows = new ArrayList<>();
	private VirtualInetAddress ip;
	private LocalContext localContext;
	private Long connectionId;
	private ScheduledFuture<?> task;

	public LocalContext getLocalContext() {
		return localContext;
	}

	public Long getConnection() {
		return connectionId;
	}

	public VPNSession(Long connectionId, LocalContext localContext) {
		this(connectionId, localContext, null);
	}

	public VPNSession(Long connectionId, LocalContext localContext, VirtualInetAddress ip) {
		this.localContext = localContext;
		this.connectionId = connectionId;
		this.ip = ip;
	}

	@Override
	public void close() throws IOException {
		if (task != null) {
			task.cancel(false);
		}
		if (ip != null) {
			log.info(String.format("Closing VPN session for %s", ip.getName()));
			ip.down();
			ip.delete();
		}
	}

	public void open() throws IOException {
		LocalContext cctx = getLocalContext();
		Connection connection = cctx.getConnectionService().getConnection(connectionId);
		if (log.isInfoEnabled()) {
			log.info("Connecting to " + connection);
		}
		ip = getLocalContext().getPlatformService().connect(this, connection);
		if (log.isInfoEnabled()) {
			log.info("Connected to " + connection);
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
