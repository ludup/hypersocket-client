package com.logonbox.vpn.client.wireguard.linux;

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
import com.logonbox.vpn.client.wireguard.AbstractPlatformServiceImpl;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.OSCommand;

public class LinuxPlatformServiceImpl extends AbstractPlatformServiceImpl<LinuxIP> {

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

	protected LinuxIP add(String name, String type) throws IOException {
		OSCommand.adminCommand("ip", "link", "add", "dev", name, "type", type);
		return find(name, ips(false));
	}

	@Override
	protected String getDefaultGateway() throws IOException {
		String gw = null;
		for(String line : OSCommand.adminCommandAndIterateOutput("ip", "route")) {
			if(gw == null && line.startsWith("default via")) {
				String[] args = line.split("\\s+");
				if(args.length > 2)
					gw = args[2];
			}
		}
		if(gw == null)
			throw new IOException("Could not get default gateway.");
		else
			return gw;
	}


	@Override
	protected long getLatestHandshake(String iface, String publicKey) throws IOException {
		for(String line : OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", iface, "latest-handshakes")) {
			String[] args = line.trim().split("\\s+");
			if(args.length == 2) {
				if(args[0].equals(publicKey)) {
					return Long.parseLong(args[1]) * 1000;
				}
			}
		}
		return 0;
	}

	@Override
	protected String getPublicKey(String interfaceName) throws IOException {
		try {
			String pk = OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", interfaceName, "public-key")
					.iterator().next().trim();
			if (pk.equals("(none)") || pk.equals(""))
				return null;
			else
				return pk;
			
		}
		catch(IOException ioe) {
			if(ioe.getMessage() != null && ioe.getMessage().indexOf("The system cannot find the file specified") != -1)
				return null;
			else
				throw ioe;
		}
	}
	
	@Override
	public List<LinuxIP> ips(boolean wireguardOnly) {
		/* TODO: Check if this is still needed, the pure Java version looks like it might be OK */
		List<LinuxIP> l = new ArrayList<>();
		LinuxIP lastLink = null;
		try {
			IpAddressState state = IpAddressState.HEADER;
			for (String r : OSCommand.runCommandAndCaptureOutput("ip", "address")) {
				if (!r.startsWith(" ")) {
					String[] a = r.split(":");
					String name = a[1].trim();
					if(!wireguardOnly || (wireguardOnly && name.startsWith(getInterfacePrefix()))) {
						l.add(lastLink = new LinuxIP(name, this));
						state = IpAddressState.MAC;
					}
				} else if(lastLink != null) {
					r = r.trim();
					if (state == IpAddressState.MAC) {
						String[] a = r.split("\\s+");
						if (a.length > 1) {
							String mac = lastLink.getMac();
							if(mac != null && !mac.equals(a[1]))
								throw new IllegalStateException("Unexpected MAC.");
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

	protected boolean exists(String name, Iterable<LinuxIP> links) {
		try {
			find(name, links);
			return true;
		} catch (IllegalArgumentException iae) {
			return false;
		}
	}

	protected LinuxIP find(String name, Iterable<LinuxIP> links) {
		for (LinuxIP link : links)
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
	protected LinuxIP createVirtualInetAddress(NetworkInterface nif) throws IOException {
		LinuxIP ip = new LinuxIP(nif.getName(), this);
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			ip.addresses.add(addr.getAddress().toString());
		}
		return ip;
	}

	@Override
	protected LinuxIP onConnect(VPNSession session, Connection configuration) throws IOException {
		LinuxIP ip = null;

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
				break;
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
			OSCommand.adminCommand(getWGCommand(), "setconf", ip.getName(), tempFile.toString());
			log.info(String.format("Activated Wireguard configuration for %s", ip.getName()));
		} finally {
			Files.delete(tempFile);
		}

		
		/* About to start connection. The "last handshake" should be this value
		 * or later if we get a valid connection 
		 */
		long connectionStarted = ( (System.currentTimeMillis() / 1000l ) -1 ) * 1000l;

		/* Bring up the interface (will set the given MTU) */
		ip.setMtu(configuration.getMtu());
		log.info(String.format("Bringing up %s", ip.getName()));		
		ip.up();
		
		/* Wait for the first handshake. As soon as we have it, we are 'connected'.
		 * If we don't get a handshake in that time, then consider this a failed connection.
		 * We don't know WHY, just it has failed  */
		log.info(String.format("Waiting for first handshake on %s", ip.getName()));
		LinuxIP ok = waitForFirstHandshake(configuration, ip, connectionStarted);
		
		/* DNS */
		dns(configuration, ip);

		/* Set the routes */
		log.info(String.format("Setting routes for %s", ip.getName()));
		setRoutes(session, ip);
		
		return ok;
	}

	void setRoutes(VPNSession session, LinuxIP ip) throws IOException {

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

	@Override
	public LinuxIP getByPublicKey(String publicKey) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}
}
