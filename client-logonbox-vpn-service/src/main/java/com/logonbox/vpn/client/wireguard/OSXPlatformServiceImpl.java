package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OSXPlatformServiceImpl extends AbstractPlatformServiceImpl {

	final static Logger LOG = LoggerFactory.getLogger(OSXPlatformServiceImpl.class);

	@Override
	public VirtualInetAddress add(String name, String type) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public List<VirtualInetAddress> ips() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

}
