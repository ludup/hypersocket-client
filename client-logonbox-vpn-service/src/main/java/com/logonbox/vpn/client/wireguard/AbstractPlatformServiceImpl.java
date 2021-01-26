package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.Ipv4;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.LogonBoxVPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.OSCommand;

public abstract class AbstractPlatformServiceImpl implements PlatformService {

	final static Logger LOG = LoggerFactory.getLogger(AbstractPlatformServiceImpl.class);
	private String interfacePrefix;
	
	protected AbstractPlatformServiceImpl(String interfacePrefix) {
		this.interfacePrefix = interfacePrefix;
	}

	@Override
	public final boolean exists(String name) {
		return exists(name, ips());
	}

	@Override
	public final String pubkey(String privateKey) {
		return Keys.pubkey(privateKey).getBase64PublicKey();
	}

	protected String getWGCommand() {
		return "wg";
	}

	@Override
	public final VirtualInetAddress get(String name) {
		return find(name, ips());
	}

	@Override
	public final InetAddress getBestAddress(NetworkInterface nif) {
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			InetAddress ipAddr = addr.getAddress();
			if (!ipAddr.isAnyLocalAddress() && !ipAddr.isLinkLocalAddress() && !ipAddr.isLoopbackAddress()) {
				return ipAddr;
			}
		}
		return null;
	}

	@Override
	public final List<String> getBestLocalAddresses(boolean network, String... exclude) {
		List<String> excludeList = Arrays.asList(exclude);
		List<String> addrList = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if (!excludeList.contains(nif.getName()) && !nif.isLoopback() && nif.isUp()) {
					for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
						InetAddress ipAddr = addr.getAddress();
						if (!ipAddr.isAnyLocalAddress() && !ipAddr.isLinkLocalAddress()
								&& !ipAddr.isLoopbackAddress()) {
							int pref = addr.getNetworkPrefixLength();
							if (network)
								addrList.add(Ipv4.of(ipAddr.getHostAddress()).lowerBoundForPrefix(pref).toString() + "/"
										+ pref);
							else
								addrList.add(ipAddr.getHostAddress());
						}
					}
				}
			}
		} catch (Exception e) {

		}
		return addrList;
	}

	@Override
	public List<VirtualInetAddress> ips() {
		List<VirtualInetAddress> ips = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if (isWireGuardInterface(nif)) {
					VirtualInetAddress vaddr = createVirtualInetAddress(nif);
					if (vaddr != null)
						ips.add(vaddr);
				}
			}
		} catch (Exception e) {
			// TODO throw
			e.printStackTrace();
		}
		return ips;
	}

	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}

	protected abstract VirtualInetAddress createVirtualInetAddress(NetworkInterface nif) throws IOException;

	@Override
	public final List<NetworkInterface> getBestLocalNic() {
		List<NetworkInterface> addrList = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if (!isWireGuardInterface(nif) && !nif.isLoopback() && nif.isUp()) {
					for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
						InetAddress ipAddr = addr.getAddress();
						if (!ipAddr.isAnyLocalAddress() && !ipAddr.isLinkLocalAddress()
								&& !ipAddr.isLoopbackAddress()) {
							addrList.add(nif);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
		}
		return addrList;
	}

	protected String getInterfacePrefix() {
		return System.getProperty("logonbox.vpn.interfacePrefix", interfacePrefix);
	}

	@Override
	public final String getPublicKey(String interfaceName) throws IOException {
		String pk = OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", interfaceName, "public-key")
				.iterator().next().trim();
		if (pk.equals("(none)") || pk.equals(""))
			return null;
		else
			return pk;
	}

	@Override
	public final Collection<LogonBoxVPNSession> start(LocalContext ctx) {

		/*
		 * Look for wireguard already existing interfaces, checking if they are
		 * connected. When we find some, find the associated Peer Configuration /
		 * Connection objects so we can populate the in-memory map of active sessions.
		 */
		LOG.info("Looking for existing wireguard interfaces.");
		List<LogonBoxVPNSession> sessions = new ArrayList<>();
		for (int i = 0; i < LogonBoxVPNSession.MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			LOG.info(String.format("Checking %s.", name));
			if (exists(name)) {
				/*
				 * Interface exists, Find it's public key so we can match a peer configuration
				 */
				// wg show wg0 public-key
				try {
					String publicKey = getPublicKey(name);
					if (publicKey != null) {
						LOG.info(String.format("%s has public key of %s.", name, publicKey));
						Connection peerConfig = ctx.getConnectionService().getConfigurationForPublicKey(publicKey);
						if (peerConfig != null) {
							LOG.info(String.format(
									"Existing wireguard session on %s for %s, adding back to internal map for %s:%s",
									name, publicKey, peerConfig.getEndpointAddress(), peerConfig.getEndpointPort()));
							sessions.add(new LogonBoxVPNSession(peerConfig, ctx, get(name)));
						} else
							LOG.info(String.format(
									"No known public key of %s on %s, so likely managed outside of LogonBox VPN.",
									publicKey, name));
					} else {
						LOG.info(
								String.format("%s has no public key, so it is a free wireguard interface.", publicKey));
					}
				} catch (Exception e) {
					LOG.error("Failed to get peer configuration for existing wireguard interface.", e);
				}
			}
		}
		return sessions;
	}

	protected boolean exists(String name, Iterable<VirtualInetAddress> links) {
		try {
			find(name, links);
			return true;
		} catch (IllegalArgumentException iae) {
			return false;
		}
	}

	protected VirtualInetAddress find(String name, Iterable<VirtualInetAddress> links) {
		for (VirtualInetAddress link : links)
			if (Objects.equals(name, link.getName()))
				return link;
		throw new IllegalArgumentException(String.format("No IP item %s", name));
	}

}
