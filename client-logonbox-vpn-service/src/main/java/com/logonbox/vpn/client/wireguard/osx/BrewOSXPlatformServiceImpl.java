package com.logonbox.vpn.client.wireguard.osx;

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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.AbstractPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.OsUtil;
import com.logonbox.vpn.client.wireguard.osx.OSXDNS.InterfaceDNS;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.StatusDetail;
import com.sshtools.forker.client.OSCommand;

import jnr.posix.util.Platform;

public class BrewOSXPlatformServiceImpl extends AbstractPlatformServiceImpl<BrewOSXIP> {

	static Logger log = LoggerFactory.getLogger(BrewOSXPlatformServiceImpl.class);

	private static final String INTERFACE_PREFIX = "utun";
	final static Logger LOG = LoggerFactory.getLogger(BrewOSXPlatformServiceImpl.class);

	enum IpAddressState {
		HEADER, IP, MAC
	}

	static Object lock = new Object();

	private Path wgCommandPath;
	private Path wgGoCommandPath;

	public BrewOSXPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	@Override
	protected void beforeStart(LocalContext ctx) {
		
		/* Detect or extract the binaries for this platform */
		wgCommandPath = OsUtil.getPathOfCommandInPath("wg");
		wgGoCommandPath = OsUtil.getPathOfCommandInPath("wireguard-go");
		
		if(wgCommandPath == null) {
			try {
				wgCommandPath = extractCommand("macosx", Platform.IS_64_BIT ? "x86-64" : "x86", "wg");
			} catch (IOException e) {
				LOG.error("Failed to extract bundled wireguard components.", e);
			}
		}
		else
			LOG.info(String.format("Found 'wg' at %s", wgCommandPath));
		
		if(wgGoCommandPath == null) {
			try {
				wgGoCommandPath = extractCommand("macosx", Platform.IS_64_BIT ? "x86-64" : "x86", "wireguard-go");
			} catch (IOException e) {
				LOG.error("Failed to extract bundled wireguard components.", e);
			}
		}
		else
			LOG.info(String.format("Found 'wireguard-go' at %s", wgGoCommandPath));
	}

	protected BrewOSXIP add(String name, String type) throws IOException {
		OSCommand.adminCommand(wgGoCommandPath.toString(), name);
		return find(name, ips(false));
	}

	@Override
	protected String getDefaultGateway() throws IOException {
		String gw = null;
		for (String line : OSCommand.adminCommandAndIterateOutput("ip", "route")) {
			if (gw == null && line.startsWith("default via")) {
				String[] args = line.split("\\s+");
				if (args.length > 2)
					gw = args[2];
			}
		}
		if (gw == null)
			throw new IOException("Could not get default gateway.");
		else
			return gw;
	}

	@Override
	protected long getLatestHandshake(String iface, String publicKey) throws IOException {
		for (String line : OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", iface, "latest-handshakes")) {
			String[] args = line.trim().split("\\s+");
			if (args.length == 2) {
				if (args[0].equals(publicKey)) {
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

		} catch (IOException ioe) {
			if (ioe.getMessage() != null &&
					( ioe.getMessage().indexOf("The system cannot find the file specified") != -1 ||
					ioe.getMessage().indexOf("Unable to access interface: No such file or directory") != -1))
				return null;
			else
				throw ioe;
		}
	}
	
	protected String getWGCommand() {
		return wgCommandPath == null ? null : wgCommandPath.toString();
	}

	@Override
	public List<BrewOSXIP> ips(boolean wireguardOnly) {
		List<BrewOSXIP> l = new ArrayList<>();
		BrewOSXIP lastLink = null;
		try {
			IpAddressState state = IpAddressState.HEADER;
			for (String r : OSCommand.runCommandAndCaptureOutput("ifconfig")) {
				if (!r.startsWith(" ")) {
					String[] a = r.split(":");
					String name = a[0].trim();
					if (!wireguardOnly || (wireguardOnly && name.startsWith(getInterfacePrefix()))) {
						l.add(lastLink = new BrewOSXIP(name, this));
						state = IpAddressState.MAC;
					}
				} else if (lastLink != null) {
					r = r.trim();
					if (state == IpAddressState.MAC) {
						if(r.startsWith("ether ")) {
							String[] a = r.split("\\s+");
							if (a.length > 1) {
								String mac = lastLink.getMac();
								if (mac != null && !mac.equals(a[1]))
									throw new IllegalStateException("Unexpected MAC.");
							}
							state = IpAddressState.IP;
						}
					} else if (state == IpAddressState.IP) {
						if (r.startsWith("inet ")) {
							String[] a = r.split("\\s+");
							if (a.length > 1) {
								lastLink.getAddresses().add(a[1]);
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

	protected BrewOSXIP find(String name, Iterable<BrewOSXIP> links) {
		for (BrewOSXIP link : links)
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
		if(wgCommandPath == null && wgGoCommandPath == null)
			return new String[] {"wireguard" };
		else if(wgCommandPath == null)
			return new String[] {"wg" };
		else if(wgCommandPath == null)
			return new String[] {"wireguard-go" };
		return new String[0];
	}

	@Override
	public StatusDetail status(String iface) throws IOException {
		Collection<String> hs = OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", iface,
				"latest-handshakes");
		long lastHandshake = hs.isEmpty() ? 0 : Long.parseLong(hs.iterator().next().split("\\s+")[1]) * 1000;
		hs = OSCommand.adminCommandAndCaptureOutput(getWGCommand(), "show", iface, "transfer");
		long rx = hs.isEmpty() ? 0 : Long.parseLong(hs.iterator().next().split("\\s+")[1]);
		long tx = hs.isEmpty() ? 0 : Long.parseLong(hs.iterator().next().split("\\s+")[2]);
		return new StatusDetail() {

			@Override
			public long getTx() {
				return tx;
			}

			@Override
			public long getRx() {
				return rx;
			}

			@Override
			public long getLastHandshake() {
				return lastHandshake;
			}
		};
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
	protected BrewOSXIP createVirtualInetAddress(NetworkInterface nif) throws IOException {
		BrewOSXIP ip = new BrewOSXIP(nif.getName(), this);
		for (InterfaceAddress addr : nif.getInterfaceAddresses()) {
			ip.getAddresses().add(addr.getAddress().toString());
		}
		return ip;
	}

	@Override
	protected VPNSession configureExistingSession(LocalContext context, Connection connection, BrewOSXIP ip) {
		OSXDNS.get().configure(new InterfaceDNS(ip.getName(), connection.getDns().toArray(new String[0])));
		return super.configureExistingSession(context, connection, ip);
	}

	@Override
	protected BrewOSXIP onConnect(VPNSession session, Connection configuration) throws IOException {
		BrewOSXIP ip = null;

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		int maxIface = -1;

		List<BrewOSXIP> ips = ips(false);
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			log.info(String.format("Looking for %s.", name));
			if (exists(name, ips)) {
				/* Interface exists, is it connected? */
				String publicKey = getPublicKey(name);
				if (publicKey == null && new File("/var/run/wireguard/" + name + ".sock").exists()) {
					/* No addresses, wireguard not using it */
					log.info(String.format("%s is free.", name));
					ip = find(name, ips);
					maxIface = i;
					break;
				} else if (publicKey != null && publicKey.equals(configuration.getUserPublicKey())) {
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

		Path tempFile = Files.createTempFile("wg", "cfg");
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

		/*
		 * About to start connection. The "last handshake" should be this value or later
		 * if we get a valid connection
		 */
		long connectionStarted = ((System.currentTimeMillis() / 1000l) - 1) * 1000l;

		/* Set the address reserved */
		log.info(String.format("Setting address %s on %s", configuration.getAddress(), ip.getName()));
		ip.setAddresses(configuration.getAddress());

		/* Bring up the interface (will set the given MTU) */
		ip.setMtu(configuration.getMtu());
		log.info(String.format("Bringing up %s", ip.getName()));
		ip.up();

		/*
		 * Wait for the first handshake. As soon as we have it, we are 'connected'. If
		 * we don't get a handshake in that time, then consider this a failed
		 * connection. We don't know WHY, just it has failed
		 */
		log.info(String.format("Waiting for first handshake on %s (starts at %d)", ip.getName(), connectionStarted));
		BrewOSXIP ok = waitForFirstHandshake(configuration, ip, connectionStarted);

		/* Set the routes */
		try {
			log.info(String.format("Setting routes for %s", ip.getName()));
			setRoutes(session, ip);
		}
		catch(IOException | RuntimeException ioe) {
			try {
				doDisconnect(ip, session);
			}
			catch(Exception e) {
			}
			throw ioe;
		}
		
		if(ip.isAutoRoute4() || ip.isAutoRoute6()) {
			ip.setEndpointDirectRoute();
		}
		
		/* DNS */
		try {
			dns(configuration, ip);
		}
		catch(IOException | RuntimeException ioe) {
			try {
				doDisconnect(ip, session);
			}
			catch(Exception e) {
			}
			throw ioe;
		}
		
//		monitor_daemon
//		execute_hooks "${POST_UP[@]}"

		return ok;
	}

	void setRoutes(VPNSession session, BrewOSXIP ip) throws IOException {

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
	public BrewOSXIP getByPublicKey(String publicKey) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void runHook(VPNSession session, String hookScript) throws IOException {
		runHookViaPipeToShell(session, OsUtil.getPathOfCommandInPathOrFail("bash").toString(), "-c", hookScript);
	}
}
