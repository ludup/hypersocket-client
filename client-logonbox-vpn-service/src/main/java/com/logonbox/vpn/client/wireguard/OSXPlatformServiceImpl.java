package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.NetworkInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;

public class OSXPlatformServiceImpl extends AbstractPlatformServiceImpl {

	final static Logger LOG = LoggerFactory.getLogger(OSXPlatformServiceImpl.class);

	private static final String INTERFACE_PREFIX = "wg";

	public OSXPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	@Override
	protected VirtualInetAddress createVirtualInetAddress(NetworkInterface nif) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public VirtualInetAddress connect(VPNSession logonBoxVPNSession, Connection configuration)
			throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

}
