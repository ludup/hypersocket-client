package com.logonbox.vpn.client.wireguard.osx;

import java.io.IOException;
import java.net.NetworkInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.AbstractPlatformServiceImpl;
import com.logonbox.vpn.common.client.Connection;

public class OSXPlatformServiceImpl extends AbstractPlatformServiceImpl<OSXIP> {

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
	protected OSXIP createVirtualInetAddress(NetworkInterface nif) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public OSXIP connect(VPNSession logonBoxVPNSession, Connection configuration)
			throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	protected String getPublicKey(String interfaceName) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public OSXIP getByPublicKey(String publicKey) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

}
