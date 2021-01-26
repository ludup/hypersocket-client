package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.ptr.ShortByReference;

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
		boolean WireGuardTunnelService(ShortByReference confFile);

		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

	public WindowsPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}
	
	@Override
	public VirtualInetAddress add(String name, String type) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	@Override
	protected VirtualInetAddress createVirtualInetAddress(NetworkInterface nif) throws IOException {
		WindowsIP ip = new WindowsIP(nif.getName(), nif.getIndex());
		if(nif.getHardwareAddress() != null)
			ip.setMac(IpUtil.toIEEE802(nif.getHardwareAddress()));
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			ip.addresses.add(addr.getAddress().toString());
		}
		return ip;
	}

	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return super.isWireGuardInterface(nif) && nif.getDisplayName().equals("Wintun Userspace Tunnel");
	}

	public static void main(String[] args) throws Exception {
		PlatformService link = new WindowsPlatformServiceImpl();
		
		System.out.println("VINS");
		for(VirtualInetAddress vin : link.ips()) {
			System.out.println(vin.getName() + " : " + vin.getMac() + " " + vin.isUp());
		}
		VirtualInetAddress ip = link.add("wg0", "wireguard");
		System.out.println("Added:" + link);
		try {
			ip.addAddress("192.168.92.1/24");
			System.out.println("    " + link);
			try {
				ip.addAddress("192.168.92.2/24");
				System.out.println("    " + link);
				ip.removeAddress("192.168.92.2/24");
			} finally {
				ip.removeAddress("192.168.92.1/24");
			}
		} finally {
			ip.delete();
		}

		System.out.println("Ips: " + IpUtil.optimizeIps("10.0.0.0/16", "10.0.0.2/32", "192.168.10.0/24",
				"192.168.2.0/24", "192.168.91.0/24"));
		System.out.println("Ips: " + IpUtil.optimizeIps("10.0.1.6", "192.168.2.1", "10.0.0.0/16"));
		System.out.println("Ips: " + IpUtil.optimizeIps("192.168.2.1", "10.0.0.0/16", "10.0.1.6"));
	}
}
