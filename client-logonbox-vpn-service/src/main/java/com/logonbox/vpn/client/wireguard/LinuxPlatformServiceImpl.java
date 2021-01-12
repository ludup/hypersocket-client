package com.logonbox.vpn.client.wireguard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.Ipv4;
import com.logonbox.vpn.client.LogonBoxVPNContext;
import com.logonbox.vpn.client.service.LogonBoxVPNSession;
import com.logonbox.vpn.common.client.PeerConfiguration;
import com.sshtools.forker.client.EffectiveUserFactory;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;
import com.sshtools.forker.client.OSCommand;

public class LinuxPlatformServiceImpl implements PlatformService {

	final static Logger LOG = LoggerFactory.getLogger(LinuxPlatformServiceImpl.class);

	enum IpAddressState {
		HEADER, IP, MAC
	}

	static Object lock = new Object();

	@Override
	public VirtualInetAddress add(String name, String type) throws IOException {
		OSCommand.adminCommand("ip", "link", "add", "dev", name, "type", type);
		return find(name, ips());
	}

	@Override
	public boolean exists(String name) {
		return exists(name, ips());
	}

	@Override
	public VirtualInetAddress get(String name) {
		return find(name, ips());
	}

	@Override
	public InetAddress getBestAddress(NetworkInterface nif) {
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			InetAddress ipAddr = addr.getAddress();
			if (!ipAddr.isAnyLocalAddress() && !ipAddr.isLinkLocalAddress() && !ipAddr.isLoopbackAddress()) {
				return ipAddr;
			}
		}
		return null;
	}

	@Override
	public List<NetworkInterface> getBestLocalNic() {
		List<NetworkInterface> addrList = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if (!nif.getName().startsWith("wg") && !nif.isLoopback() && nif.isUp()) {
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

	@Override
	public List<String> getBestLocalAddresses(boolean network, String... exclude) {
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
		List<VirtualInetAddress> l = new ArrayList<>();
		LinuxIP lastLink = null;
		try {
			IpAddressState state = IpAddressState.HEADER;
			for (String r : OSCommand.runCommandAndCaptureOutput("ip", "address")) {
				if (!r.startsWith(" ")) {
					String[] a = r.split(":");
					l.add(lastLink = new LinuxIP(a[1].trim(), Integer.parseInt(a[0].trim())));
					state = IpAddressState.MAC;
				} else {
					r = r.trim();
					if (state == IpAddressState.MAC) {
						String[] a = r.split("\\s+");
						if (a.length > 1) {
							lastLink.setMac(a[1]);
						}
						state = IpAddressState.IP;
					} else if (state == IpAddressState.IP) {
						if (r.startsWith("inet ")) {
							String[] a = r.split("\\s+");
							if (a.length > 1) {
								lastLink.addresses.add(a[1]);
							}
							state = IpAddressState.HEADER;
						}
					}
				}
			}
		} catch (IOException ioe) {
			if (!Boolean.getBoolean("hypersocket.development")) {
				throw new IllegalStateException("Failed to get network devices.", ioe);
			}
		}
		return l;
	}

	public static void main(String[] args) throws Exception {
		PlatformService link = new LinuxPlatformServiceImpl();
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

	String resolvconfIfacePrefix() throws IOException {
		File f = new File("/etc/resolvconf/interface-order");
		if (f.exists()) {
			try (BufferedReader br = new BufferedReader(new FileReader(f))) {
				String l;
				Pattern p = Pattern.compile("^([A-Za-z0-9-]+)\\*$");
				while ((l = br.readLine()) != null) {
					Matcher m = p.matcher(l);
					if (m.matches()) {
						return m.group(1);
					}
				}
			}
		}
		return "";
	}

	@Override
	public String genkey(String privateKey) {
		ForkerBuilder b = new ForkerBuilder("wg", "pubkey");
		b.effectiveUser(EffectiveUserFactory.getDefault().administrator());
		b.redirectErrorStream(true);
		try {
			ForkerProcess p = b.start();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				try (PrintWriter pw = new PrintWriter(p.getOutputStream(), true)) {
					pw.println(privateKey);
				}
				return r.readLine();
			} finally {
				if (p.waitFor() != 0)
					throw new IllegalStateException("Failed to convert key. Exit code " + p.exitValue());
			}
		} catch (InterruptedException e) {
			throw new IllegalStateException("Failed to convert key.", e);
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to convert key.");
		}
	}

	@Override
	public Collection<LogonBoxVPNSession> start(LogonBoxVPNContext ctx) {

		/*
		 * Look for wireguard already existing interfaces, checking if they are
		 * connected. When we find some, find the associated Peer Configuration /
		 * Connection objects so we can populate the in-memory map of active sessions.
		 */
		LOG.info("Looking for existing wireguard interaces.");
		List<LogonBoxVPNSession> sessions = new ArrayList<>();
		for (int i = 0; i < LogonBoxVPNSession.MAX_INTERFACES; i++) {
			String name = "wg" + i;
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
						PeerConfiguration peerConfig = ctx.getPeerConfigurationService()
								.getConfigurationForPublicKey(publicKey);
						if (peerConfig != null) {
							LOG.info(String.format(
									"Existing wireguard session on %s for %s, adding back to internal map for %s:%s",
									name, publicKey, peerConfig.getEndpointAddress(), peerConfig.getEndpointPort()));
							sessions.add(new LogonBoxVPNSession(peerConfig.getConnection(), ctx, get(name)));
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

	@Override
	public String getPublicKey(String interfaceName) throws IOException {
		String pk = OSCommand.runCommandAndCaptureOutput("wg", "show", interfaceName, "public-key").iterator().next()
				.trim();
		if (pk.equals("(none)") || pk.equals(""))
			return null;
		else
			return pk;
	}

}
