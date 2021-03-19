package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;
import java.rmi.server.RMIClientSocketFactory;

@SuppressWarnings("serial")
public final class LocalRMIClientSocketFactory implements RMIClientSocketFactory, Serializable {

	@Override
	public Socket createSocket(String host, int port) throws IOException {
		return new Socket("127.0.0.1", port);
	}
}