package com.logonbox.vpn.client.wireguard;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.AbstractIp;
import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;
import com.github.jgonian.ipmath.Ipv6Range;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.ReauthorizeException;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.StatusDetail;
import com.logonbox.vpn.common.client.Util;
import com.sshtools.forker.client.EffectiveUserFactory.DefaultEffectiveUserFactory;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.OSCommand;

public abstract class AbstractPlatformServiceImpl<I extends VirtualInetAddress<?>> implements PlatformService<I> {

	protected static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("logonbox.vpn.maxInterfaces", "250"));

	final static Logger LOG = LoggerFactory.getLogger(AbstractPlatformServiceImpl.class);
	
	private String interfacePrefix;
	protected Path tempCommandDir;
	protected LocalContext context;
	
	protected AbstractPlatformServiceImpl(String interfacePrefix) {
		this.interfacePrefix = interfacePrefix;
	}

	protected Path extractCommand(String platform, String arch, String name) throws IOException {
		LOG.info(String.format("Extracting command %s for platform %s on arch %s", name, platform, arch));
		try(InputStream in = getClass().getResource("/" + platform + "-" + arch + "/" + name).openStream()) {
			Path path = getTempCommandDir().resolve(name);
			try(OutputStream out = Files.newOutputStream(path)) {
				in.transferTo(out);
			}
			path.toFile().deleteOnExit();
			Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)));
			LOG.info(String.format("Extracted command %s for platform %s on arch %s to %s", name, platform, arch, path));
			return path;
		}
	}

	protected Path getTempCommandDir() throws IOException {
		if(tempCommandDir == null)
			tempCommandDir = Files.createTempDirectory("vpn");
		return tempCommandDir;
	}

	@Override
	public final I connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		I vpn = onConnect(logonBoxVPNSession, configuration);
		if(configuration.isRouteAll()) {
			try {
				addRouteAll(configuration);
			}
			catch(Exception e) { 
				LOG.error("Failed to setup routing.", e);
			}
		}
		return vpn;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final void disconnect(VPNSession session) throws IOException {
		doDisconnect((I) session.getIp(), session);
	}

	protected void doDisconnect(I ip, VPNSession session) throws IOException {
		try {
			try {
				ip.down();
			}
			finally {
				ip.delete();
			}
		}
		finally {
			if(session.getConnection().isRouteAll()) {
				try {
					removeRouteAll(session);
				}
				catch(Exception e) { 
					LOG.error("Failed to tear down routing.", e);
				}
			}
			onDisconnect();
		}
	}

	@Override
	public I getByPublicKey(String publicKey) {
		try {
			for (I ip : ips(true)) {
				if (publicKey.equals(getPublicKey(ip.getName()))) {
					return ip;
				}
			}
		} catch (IOException ioe) {
			throw new IllegalStateException("Failed to list interface names.", ioe);
		}
		return null;
	}

	@Override
	public List<I> ips(boolean wireguardInterface) {
		List<I> ips = new ArrayList<>();
		try {
			for (Enumeration<NetworkInterface> nifEn = NetworkInterface.getNetworkInterfaces(); nifEn
					.hasMoreElements();) {
				NetworkInterface nif = nifEn.nextElement();
				if ((wireguardInterface && isWireGuardInterface(nif)) || (!wireguardInterface && isMatchesPrefix(nif))) {
					I vaddr = createVirtualInetAddress(nif);
					configureVirtualAddress(vaddr);
					if (vaddr != null)
						ips.add(vaddr);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to get interfaces.", e);
		}
		return ips;
	}

	protected void configureVirtualAddress(I vaddr) {
		try {
			vaddr.method(DNSIntegrationMethod.valueOf(context.getClientService().getValue(ConfigurationRepository.DNS_INTEGRATION_METHOD, DNSIntegrationMethod.AUTO.name())));
		}
		catch(Exception e) {
			LOG.error("Failed to set DNS integeration method, reverting to AUTO.", e);
			vaddr.method(DNSIntegrationMethod.AUTO);
		}
	}

	@Override
	public boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		long lastHandshake = getLatestHandshake(logonBoxVPNSession.getIp().getName(), configuration.getPublicKey());
		return lastHandshake >= System.currentTimeMillis() - ( ClientService.HANDSHAKE_TIMEOUT * 1000);
	}

	@Override
	public final String pubkey(String privateKey) {
		return Keys.pubkey(privateKey).getBase64PublicKey();
	}
	
	@Override
	public final Collection<VPNSession> start(LocalContext context) {
		LOG.info(String.format("Starting platform services %s", getClass().getName()));
		this.context = context;
		beforeStart(context);

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
				try {
					String publicKey = getPublicKey(name);
					if (publicKey != null) {
						LOG.info(String.format("%s has public key of %s.", name, publicKey));
						try {
							ConnectionStatus status = context.getClientService().getStatusForPublicKey(publicKey);
							Connection connection = status.getConnection();
							LOG.info(String.format(
									"Existing wireguard session on %s for %s, adding back to internal list", name, publicKey));
							I ip = get(name);
							sessions.add(configureExistingSession(context, connection, ip));
						}
						catch(Exception e) {
							LOG.info(String.format(
									"No known public key of %s on %s, so likely managed outside of LogonBox VPN.",
									publicKey, name));
						}
					} else {
						LOG.info(
								String.format("%s has no public key, so it likely used by another application.", name));
					}
				} catch (Exception e) {
					LOG.error("Failed to get peer configuration for existing wireguard interface.", e);
				}
			}
		}
		return onStart(context, sessions);
	}

	protected VPNSession configureExistingSession(LocalContext context, Connection connection, I ip) {
		return new VPNSession(connection, context, ip);
	}

	@Override
	public StatusDetail status(String iface) throws IOException {
		return new WireguardPipe(iface);
	}
	
	protected void beforeStart(LocalContext ctx) {
	}
	
	protected Collection<VPNSession> onStart(LocalContext ctx, List<VPNSession> sessions) {
		return sessions;
	}

	protected void addRouteAll(Connection connection) throws IOException {
		LOG.info("Routing traffic all through VPN");
		String gw = getDefaultGateway();
		LOG.info(String.join(" ", Arrays.asList("route", "add", connection.getEndpointAddress(), "gw", gw)));
		OSCommand.admin("route", "add", connection.getEndpointAddress(), "gw", gw);
	}

	protected abstract I createVirtualInetAddress(NetworkInterface nif) throws IOException;

	protected void dns(Connection configuration, I ip) throws IOException {
		if(configuration.getDns().isEmpty()) {
			if(configuration.isRouteAll())
				LOG.warn("No DNS servers configured for this connection and all traffic is being routed through the VPN. DNS is unlikely to work.");
			else  
				LOG.info("No DNS servers configured for this connection.");
		}
		else {
			LOG.info(String.format("Configuring DNS servers for %s as %s", ip.getName(), configuration.getDns()));
		}
		ip.dns(configuration.getDns().toArray(new String[0]));
		
	}

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

	protected abstract String getDefaultGateway() throws IOException;

	protected String getInterfacePrefix() {
		return System.getProperty("logonbox.vpn.interfacePrefix", interfacePrefix);
	}

	protected String getPublicKey(String interfaceName) throws IOException {
		try {
			return new WireguardPipe(interfaceName).getUserPublicKey();
		}
		catch(FileNotFoundException fnfe) {
			return null;
		}
	}

	protected long getLatestHandshake(String iface, String publicKey) throws IOException {
		WireguardPipe pipe = new WireguardPipe(iface);
		return Objects.equals(publicKey, pipe.getPublicKey()) ? pipe.getLastHandshake() : 0;
	}
	
	protected String getWGCommand() {
		return "wg";
	}

	protected boolean isMatchesPrefix(NetworkInterface nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}
	
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return isMatchesPrefix(nif);
	}

	protected abstract I onConnect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException;

	protected void onDisconnect() throws IOException {
	}

	protected void removeRouteAll(VPNSession session) throws IOException {
		LOG.info("Removing routing of all traffic through VPN");
		String gw = getDefaultGateway();
		LOG.info(String.join(" ", Arrays.asList("route", "del", session.getConnection().getEndpointAddress(), "gw", gw)));
		OSCommand.admin("route", "del", session.getConnection().getEndpointAddress(), "gw", gw);
	}
	
	protected I waitForFirstHandshake(Connection configuration, I ip, long connectionStarted)
			throws IOException {
		for(int i = 0 ; i < ClientService.CONNECT_TIMEOUT ; i++) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new IOException(String.format("Interrupted connecting to %s", ip.getName()));
			}
			long lastHandshake = getLatestHandshake(ip.getName(), configuration.getPublicKey());
			if(lastHandshake >= connectionStarted) {
				/* Ready ! */
				return ip;
			}
		}

		/* Failed to connect in the given time. Clean up and report an exception */
		try {
			ip.down();
		}
		catch(Exception e) {
			LOG.error("Failed to stop after timeout.", e);
		}
		throw new ReauthorizeException(String.format("No timeout received for %s within %d seconds.", ip.getName(), ClientService.CONNECT_TIMEOUT));
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
		List<String> allowedIps = new ArrayList<>(configuration.getAllowedIps());
		if(configuration.isRouteAll()) {
			pw.println("AllowedIPs = 0.0.0.0/0");
		}	
		else {
			if(context.getClientService().getValue(ConfigurationRepository.IGNORE_LOCAL_ROUTES, "true").equals("true")) {
				/* Filter out any routes that would cover the addresses of any interfaces
				 * we already have
				 */
				Set<AbstractIp<?, ?>> localAddresses = new HashSet<>();
				try {
					for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
						NetworkInterface ni = en.nextElement();
						if(!ni.isLoopback() && ni.isUp()) { 
							for(Enumeration<InetAddress> addrEn = ni.getInetAddresses(); addrEn.hasMoreElements(); ) {
								InetAddress addr = addrEn.nextElement();
								try {
									localAddresses.add(IpUtil.parse(addr.getHostAddress()));
								}
								catch(IllegalArgumentException iae) {
									// Ignore
								}
							}
						}
					}
				}
				catch(SocketException se) {
					//
				}

				for(String route : new ArrayList<>(allowedIps)) {
					try {
						try {
							Ipv4Range range = Ipv4Range.parseCidr(route);
							for(AbstractIp<?, ?> laddr : localAddresses) {
								if(laddr instanceof Ipv4 && range.contains((Ipv4)laddr)) {
									// Covered by route. 
									LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
									allowedIps.remove(route);
									break;
								}
							}
						}
						catch(IllegalArgumentException iae) {
							/* Single ipv4 address? */
							Ipv4 routeIpv4 = Ipv4.of(route);
							if(localAddresses.contains(routeIpv4)) {
								// Covered by route. 
								LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
								allowedIps.remove(route);
								break;
							}
						}
					}
					catch(IllegalArgumentException iae) {
						try {
							Ipv6Range range = Ipv6Range.parseCidr(route);
							for(AbstractIp<?, ?> laddr : localAddresses) {
								if(laddr instanceof Ipv6 && range.contains((Ipv6)laddr)) {
									// Covered by route. 
									LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
									allowedIps.remove(route);
									break;
								}
							}
						}
						catch(IllegalArgumentException iae2) {
							/* Single ipv6 address? */
							Ipv6 routeIpv6 = Ipv6.of(route);
							if(localAddresses.contains(routeIpv6)) {
								// Covered by route. 
								LOG.info(String.format("Filtering out route %s as it covers an existing local interface address.", route));
								allowedIps.remove(route);
								break;
							}
						}
					}
				}
			}
			
			String ignoreAddresses = System.getProperty("logonbox.vpn.ignoreAddresses", "");
			if(ignoreAddresses.length() > 0) {
				for(String ignoreAddress : ignoreAddresses.split(",")) {
					allowedIps.remove(ignoreAddress);
				}
			}
			if (!allowedIps.isEmpty())
				pw.println(String.format("AllowedIPs = %s", String.join(", ", allowedIps)));
		}
		writePeer(configuration, writer);
	}
	
	protected void writeInterface(Connection configuration, Writer writer) {
	}

	protected void writePeer(Connection configuration, Writer writer) {
	}

	protected void runHookViaPipeToShell(VPNSession session, String... args) throws IOException {
		if(LOG.isDebugEnabled()) {
			LOG.debug("Executing hook");
			for(String arg : args) {
				LOG.debug(String.format("    %s", arg));
			}
		}
		ForkerBuilder cmd = new ForkerBuilder(args);
		cmd.redirectErrorStream(true);
		Connection connection = session.getConnection();
		Map<String, String> env = cmd.environment();
		if(connection != null) {
			env.put("LBVPN_ADDRESS", connection.getAddress());
			env.put("LBVPN_DEFAULT_DISPLAY_NAME", connection.getDefaultDisplayName());
			env.put("LBVPN_DISPLAY_NAME", connection.getDisplayName());
			env.put("LBVPN_ENDPOINT_ADDRESS", connection.getEndpointAddress());
			env.put("LBVPN_ENDPOINT_PORT", String.valueOf(connection.getEndpointPort()));
			env.put("LBVPN_HOSTNAME", connection.getHostname());
			env.put("LBVPN_NAME", connection.getName() == null ? "": connection.getName());
			env.put("LBVPN_PEER_PUBLIC_KEY", connection.getPublicKey());
			env.put("LBVPN_USER_PUBLIC_KEY", connection.getUserPublicKey());
			env.put("LBVPN_ID", String.valueOf(connection.getId()));
			env.put("LBVPN_PORT", String.valueOf(connection.getPort()));
			env.put("LBVPN_DNS", String.join(" ", connection.getDns()));
			env.put("LBVPN_MTU", String.valueOf(connection.getMtu()));
		}
		@SuppressWarnings("unchecked")
		I addr = (I)session.getIp();
		if(addr != null) {
			env.put("LBVPN_IP_MAC", addr.getMac());
			env.put("LBVPN_IP_NAME", addr.getName());
			env.put("LBVPN_IP_DISPLAY_NAME", addr.getDisplayName());
			env.put("LBVPN_IP_PEER", addr.getPeer());
			env.put("LBVPN_IP_TABLE", addr.getTable());
		}
		if(LOG.isDebugEnabled()) {
			LOG.debug("Environment:-");
			for(Map.Entry<String, String> en : env.entrySet()) {
				LOG.debug("    %s = %s", en.getKey(), en.getValue());
			}
		}
 		cmd.effectiveUser(DefaultEffectiveUserFactory.getDefault().administrator());
		Process p = cmd.start();
		String errorMessage = null;
		BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
		String line = null;
		LOG.debug("Command Output: ");
		while ((line = reader.readLine()) != null) {
			LOG.debug(String.format("    %s", line));
			if(line.startsWith("[ERROR] ")) {
				errorMessage = line.substring(8);
			}
		}
		try {
			int ret = p.waitFor();
			LOG.debug(String.format("Exit: %d", ret));
			if(ret != 0) {
				if(errorMessage == null)
					throw new IOException(String.format("Hook exited with non-zero status of %d.", ret));
				else
					throw new IOException(errorMessage);
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted.", e);
		}
		
	}

	@Override
	public void runHook(VPNSession session, String hookScript) throws IOException {
		for(String cmd : split(hookScript)) {
			OSCommand.admin(Util.parseQuotedString(cmd));
		}
	}
	
	private Collection<? extends String> split(String str) {
		str = str == null ? "" : str.trim();
		return str.equals("") ? Collections.emptyList() : Arrays.asList(str.split("\n"));
	}
}
