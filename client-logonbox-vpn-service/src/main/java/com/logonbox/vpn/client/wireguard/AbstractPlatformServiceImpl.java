package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.Keys;

public abstract class AbstractPlatformServiceImpl<I extends VirtualInetAddress> implements PlatformService<I> {

	protected static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("logonbox.vpn.maxInterfaces", "250"));

	final static Logger LOG = LoggerFactory.getLogger(AbstractPlatformServiceImpl.class);
	private String interfacePrefix;
	
	protected AbstractPlatformServiceImpl(String interfacePrefix) {
		this.interfacePrefix = interfacePrefix;
	}

	@Override
	public final String pubkey(String privateKey) {
		return Keys.pubkey(privateKey).getBase64PublicKey();
	}

	@Override
	public final Collection<VPNSession> start(LocalContext ctx) {

		/*
		 * Look for wireguard already existing interfaces, checking if they are
		 * connected. When we find some, find the associated Peer Configuration /
		 * Connection objects so we can populate the in-memory map of active sessions.
		 */
		LOG.info("Looking for existing wireguard interfaces.");
		List<VPNSession> sessions = new ArrayList<>();
		List<I> ips = ips(true);
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			if (exists(name, ips)) {
				LOG.info(String.format("Checking %s.", name));
				
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
							sessions.add(new VPNSession(peerConfig, ctx, get(name)));
						} else
							LOG.info(String.format(
									"No known public key of %s on %s, so likely managed outside of LogonBox VPN.",
									publicKey, name));
					} else {
						LOG.info(
								String.format("%s has no public key, so it is a free wireguard interface.", name));
					}
				} catch (Exception e) {
					LOG.error("Failed to get peer configuration for existing wireguard interface.", e);
				}
			}
		}
		return sessions;
	}

	protected abstract I createVirtualInetAddress(NetworkInterface nif) throws IOException;

	protected final boolean exists(String name, boolean wireguardOnly) {
		return exists(name, ips(wireguardOnly));
	}

	protected boolean exists(String name, Iterable<I> links) {
		try {
			find(name, links);
			return true;
		} catch (IllegalArgumentException iae) {
			return false;
		}
	}

	protected I find(String name, Iterable<I> links) {
		for (I link : links)
			if (Objects.equals(name, link.getName()))
				return link;
		throw new IllegalArgumentException(String.format("No IP item %s", name));
	}

	protected final I get(String name) {
		return find(name, ips(false));
	}

	protected String getInterfacePrefix() {
		return System.getProperty("logonbox.vpn.interfacePrefix", interfacePrefix);
	}

	protected abstract String getPublicKey(String interfaceName) throws IOException;

	protected String getWGCommand() {
		return "wg";
	}

	protected List<I> ips(boolean wireguardInterface) {
		List<I> ips = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if ((wireguardInterface && isWireGuardInterface(nif)) || (!wireguardInterface && isMatchesPrefix(nif))) {
					I vaddr = createVirtualInetAddress(nif);
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
		return isMatchesPrefix(nif);
	}

	protected boolean isMatchesPrefix(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}

	protected void write(Connection configuration, Writer writer) {
		PrintWriter pw = new PrintWriter(writer, true);
		pw.println("[Interface]");
		pw.println(String.format("PrivateKey = %s", configuration.getUserPrivateKey()));
		writeInterface(configuration, writer);
		pw.println();
		pw.println("[Peer]");
		pw.println(String.format("PublicKey = %s", configuration.getPublicKey()));
		pw.println(
				String.format("Endpoint = %s:%d", configuration.getEndpointAddress(), configuration.getEndpointPort()));
		if (configuration.getPersistentKeepalive() > 0)
			pw.println(String.format("PersistentKeepalive = %d", configuration.getPersistentKeepalive()));
		List<String> allowedIps = configuration.getAllowedIps();
		if (!allowedIps.isEmpty())
			pw.println(String.format("AllowedIPs = %s", String.join(", ", allowedIps)));
		writePeer(configuration, writer);
	}
	
	protected void writeInterface(Connection configuration, Writer writer) {
	}
	
	protected void writePeer(Connection configuration, Writer writer) {
	}
}