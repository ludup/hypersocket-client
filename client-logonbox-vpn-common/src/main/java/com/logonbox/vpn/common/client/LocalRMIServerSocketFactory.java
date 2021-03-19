package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.rmi.server.RMIServerSocketFactory;

@SuppressWarnings("serial")
public final class LocalRMIServerSocketFactory implements RMIServerSocketFactory, Serializable {
	@Override
	public ServerSocket createServerSocket(int port) throws IOException {
		return new ServerSocket(port, 0, InetAddress.getByName("127.0.0.1"));
	}
}