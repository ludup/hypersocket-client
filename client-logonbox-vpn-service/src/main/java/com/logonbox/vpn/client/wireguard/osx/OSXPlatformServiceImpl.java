package com.logonbox.vpn.client.wireguard.osx;

import java.io.IOException;
import java.net.NetworkInterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.AbstractPlatformServiceImpl;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.OSCommand;

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
	protected String getDefaultGateway() throws IOException {
		for(String line : OSCommand.adminCommandAndIterateOutput("route", "-n", "get", "default")) {
			line = line.trim();
			if(line.startsWith("gateway:")) {
				String[] args = line.split(":");
				if(args.length > 1)
					return args[1].trim();
			}
		}
		throw new IOException("Could not get default gateway.");
	}

	@Override
	protected OSXIP createVirtualInetAddress(NetworkInterface nif) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	protected OSXIP onConnect(VPNSession logonBoxVPNSession, Connection configuration)
			throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

}
