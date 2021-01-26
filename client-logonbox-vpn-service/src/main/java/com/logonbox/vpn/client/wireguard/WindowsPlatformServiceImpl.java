package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.io.Writer;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.sun.jna.Library;
import com.sun.jna.Native;

public class WindowsPlatformServiceImpl extends AbstractPlatformServiceImpl {

	public static TunnelInterface INSTANCE;
	private static final String INTERFACE_PREFIX = "net";

	static {
		try {
			Native.extractFromResourcePath("tunnel.dll");
			INSTANCE = Native.load("tunnel", TunnelInterface.class);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load support library.", e);
		}
	}

	public static interface TunnelInterface extends Library {
		boolean WireGuardTunnelService(String confFile);

		/** Unused, keys are generated using Java */
		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

	public WindowsPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	@Override
	protected VirtualInetAddress createVirtualInetAddress(NetworkInterface nif) throws IOException {
		WindowsIP ip = new WindowsIP(nif.getName(), nif.getIndex());
		if (nif.getHardwareAddress() != null)
			ip.setMac(IpUtil.toIEEE802(nif.getHardwareAddress()));
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			ip.addresses.add(addr.getAddress().toString());
		}
		return ip;
	}

	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return super.isWireGuardInterface(nif) && nif.getDisplayName().equals("Wintun Userspace Tunnel");
	}

	@Override
	public VirtualInetAddress connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		VirtualInetAddress ip = null;

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		int maxIface = -1;
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			LOG.info(String.format("Looking for %s.", name));

			/*
			 * Get ALL the interfacecs because on Windows the interface name is netXXX, and
			 * 'net' isn't specific to wireguard, nor even to WinTun.
			 */
			if (exists(name, false)) {
				/* Get if this is actually a Wireguard interface. */
				if (isWireGuardInterface(NetworkInterface.getByName(name))) {
					/* Interface exists and is wireguard, is it connected? */

					// TODO check service state, we can't rely on the public key
					// as we manage storage of it ourselves (no wg show command)
					String publicKey = getPublicKey(name);
					if (publicKey == null) {
						/* No addresses, wireguard not using it */
						LOG.info(String.format("%s is free.", name));
						ip = get(name);
						maxIface = i;
						break;
					} else if (publicKey.equals(configuration.getUserPublicKey())) {
						throw new IllegalStateException(
								String.format("Peer with public key %s on %s is already active.", publicKey, name));
					} else {
						LOG.info(String.format("%s is already in use.", name));
					}
				} else
					LOG.info(String.format("%s is already in use by something other than WinTun.", name));
			} else if (maxIface == -1) {
				/* This one is the next free number */
				maxIface = i;
				LOG.info(String.format("%s is next free interface.", name));
			}
		}
		if (maxIface == -1)
			throw new IOException(String.format("Exceeds maximum of %d interfaces.", MAX_INTERFACES));

		if (ip == null) {
			String name = getInterfacePrefix() + maxIface;
			LOG.info(String.format("No existing unused interfaces, creating new one (%s) for public key .", name,
					configuration.getUserPublicKey()));
			ip = new WindowsIP(name, maxIface);
			LOG.info(String.format("Created %s", name));
		} else
			LOG.info(String.format("Using %s", ip.getName()));

		/* Set the address reserved */
		ip.setAddresses(configuration.getAddress());

		Path tempDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve(System.getProperty("user.name"))
				.resolve("logonbox-wireguard");
		if(!Files.exists(tempDir))
			Files.createDirectories(tempDir);
		Path tempFile = tempDir.resolve(ip.getName() + ".conf");
		try {
			try (Writer writer = Files.newBufferedWriter(tempFile)) {
				write(configuration, writer);
			}
			LOG.info(String.format("Activating Wireguard configuration for %s (in %s)", ip.getName(), tempFile));
			if (INSTANCE.WireGuardTunnelService(tempFile.toString())) {
				LOG.info(String.format("Activated Wireguard configuration for %s", ip.getName()));
			} else
				throw new IOException(String.format("Failed to activate %s.", ip.getName()));
		} finally {
			Files.delete(tempFile);
		}

//		/* Bring up the interface (will set the given MTU) */
//		ip.setMtu(configuration.getMtu());
//		LOG.info(String.format("Bringing up %s", ip.getName()));
//		ip.up();

		/* Set the routes */
//		LOG.info(String.format("Setting routes for %s", ip.getName()));
//		setRoutes(session, ip);

		return ip;
	}
}
