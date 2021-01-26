package com.logonbox.vpn.client.wireguard;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.forker.client.OSCommand;

public class LinuxPlatformServiceImpl extends AbstractPlatformServiceImpl {

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
	public String[] getMissingPackages() {
		if (new File("/etc/debian_version").exists()) {
			Set<String> missing = new LinkedHashSet<>(Arrays.asList("wireguard-tools"));
			if (doesCommandExist("wg"))
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

}
