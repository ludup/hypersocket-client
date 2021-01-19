package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.ShortByReference;

public class WindowsPlatformServiceImpl extends AbstractPlatformServiceImpl {

	public static TunnelInterface INSTANCE;

	static {
		try {
			Native.extractFromResourcePath("tunnel.dll");
			INSTANCE = Native.load("tunnel", TunnelInterface.class);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load support library.", e);
		}
	}

	public static interface TunnelInterface extends Library {
		boolean WireGuardTunnelService(ShortByReference confFile);

		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

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
