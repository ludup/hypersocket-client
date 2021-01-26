package com.logonbox.vpn.client.wireguard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.OSCommand;

public class LinuxPlatformServiceImpl extends AbstractPlatformServiceImpl {

	static Logger log = LoggerFactory.getLogger(LinuxPlatformServiceImpl.class);

	private static final String INTERFACE_PREFIX = "wg";

	final static Logger LOG = LoggerFactory.getLogger(LinuxPlatformServiceImpl.class);

	enum IpAddressState {
		HEADER, IP, MAC
	}

	static Object lock = new Object();
	
	public LinuxPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	protected VirtualInetAddress add(String name, String type) throws IOException {
		OSCommand.adminCommand("ip", "link", "add", "dev", name, "type", type);
		return find(name, ips(false));
	}

	@Override
	protected List<VirtualInetAddress> ips(boolean wireguardOnly) {
		/* TODO: Check if this is still needed, the pure Java version looks like it might be OK */
		List<VirtualInetAddress> l = new ArrayList<>();
		LinuxIP lastLink = null;
		try {
			IpAddressState state = IpAddressState.HEADER;
			for (String r : OSCommand.runCommandAndCaptureOutput("ip", "address")) {
				if (!r.startsWith(" ")) {
					String[] a = r.split(":");
					String name = a[1].trim();
					if(!wireguardOnly || (wireguardOnly && name.startsWith(getInterfacePrefix()))) {
						l.add(lastLink = new LinuxIP(name, Integer.parseInt(a[0].trim())));
						state = IpAddressState.MAC;
					}
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
		LinuxPlatformServiceImpl link = new LinuxPlatformServiceImpl();
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
	public String[] getMissingPackages() {
		if (new File("/etc/debian_version").exists()) {
			Set<String> missing = new LinkedHashSet<>(Arrays.asList("wireguard-tools"));
			if (doesCommandExist(getWGCommand()))
				missing.remove("wireguard-tools");
			return missing.toArray(new String[0]);
		} else {
			return new String[0];
		}
	}

	boolean doesCommandExist(String command) {
		for (String dir : System.getenv("PATH").split(File.pathSeparator)) {
			File wg = new File(dir, command);
			if (wg.exists())
				return true;
		}
		return false;
	}

	@Override
	protected VirtualInetAddress createVirtualInetAddress(NetworkInterface nif) throws IOException {
		LinuxIP ip = new LinuxIP(nif.getName(), nif.getIndex());
		ip.setMac(IpUtil.toIEEE802(nif.getHardwareAddress()));
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			ip.addresses.add(addr.getAddress().toString());
		}
		return ip;
	}

	@Override
	public VirtualInetAddress connect(VPNSession session, Connection configuration) throws IOException {
		VirtualInetAddress ip = null;

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		int maxIface = -1;
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			log.info(String.format("Looking for %s.", name));
			if (exists(name, true)) {
				/* Interface exists, is it connected? */
				String publicKey = getPublicKey(name);
				if (publicKey == null) {
					/* No addresses, wireguard not using it */
					log.info(String.format("%s is free.", name));
					ip = get(name);
					maxIface = i;
					break;
				} else if (publicKey.equals(configuration.getUserPublicKey())) {
					throw new IllegalStateException(
							String.format("Peer with public key %s on %s is already active.", publicKey, name));
				} else {
					log.info(String.format("%s is already in use.", name));
				}
			} else if (maxIface == -1) {
				/* This one is the next free number */
				maxIface = i;
				log.info(String.format("%s is next free interface.", name));
			}
		}
		if (maxIface == -1)
			throw new IOException(String.format("Exceeds maximum of %d interfaces.", MAX_INTERFACES));
		if (ip == null) {
			String name = getInterfacePrefix() + maxIface;
			log.info(String.format("No existing unused interfaces, creating new one (%s) for public key .", name,
					configuration.getUserPublicKey()));
			ip = add(name, "wireguard");
			if (ip == null)
				throw new IOException("Failed to create virtual IP address.");
			log.info(String.format("Created %s", name));
		} else
			log.info(String.format("Using %s", ip.getName()));

		/* Set the address reserved */
		ip.setAddresses(configuration.getAddress());

		Path tempFile = Files.createTempFile(getWGCommand(), "cfg");
		try {
			try (Writer writer = Files.newBufferedWriter(tempFile)) {
				write(configuration, writer);
			}
			log.info(String.format("Activating Wireguard configuration for %s (in %s)", ip.getName(), tempFile));
			OSCommand.runCommand("cat", tempFile.toString());
			OSCommand.runCommand(getWGCommand(), "setconf", ip.getName(), tempFile.toString());
			log.info(String.format("Activated Wireguard configuration for %s", ip.getName()));
		} finally {
			Files.delete(tempFile);
		}

		/* Bring up the interface (will set the given MTU) */
		ip.setMtu(configuration.getMtu());
		log.info(String.format("Bringing up %s", ip.getName()));
		ip.up();

		/* Set the routes */
		log.info(String.format("Setting routes for %s", ip.getName()));
		setRoutes(session, ip);
		
		return ip;
	}

	void setRoutes(VPNSession session, VirtualInetAddress ip) throws IOException {

		/* Set routes from the known allowed-ips supplies by Wireguard. */
		session.getAllows().clear();

		for (String s : OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", ip.getName(), "allowed-ips")) {
			StringTokenizer t = new StringTokenizer(s);
			if (t.hasMoreTokens()) {
				t.nextToken();
				while (t.hasMoreTokens())
					session.getAllows().add(t.nextToken());
			}
		}

		/*
		 * Sort by network subnet size (biggest first)
		 */
		Collections.sort(session.getAllows(), (a, b) -> {
			String[] sa = a.split("/");
			String[] sb = b.split("/");
			Integer ia = Integer.parseInt(sa[1]);
			Integer ib = Integer.parseInt(sb[1]);
			int r = ia.compareTo(ib);
			if (r == 0) {
				return a.compareTo(b);
			} else
				return r * -1;
		});
		/* Actually add routes */
		ip.setRoutes(session.getAllows());
	}
}
