package com.logonbox.vpn.client.wireguard;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.ReauthorizeException;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.Keys;
import com.logonbox.vpn.common.client.StatusDetail;
import com.sshtools.forker.client.OSCommand;

public abstract class AbstractPlatformServiceImpl<I extends VirtualInetAddress> implements PlatformService<I> {

	protected static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("logonbox.vpn.maxInterfaces", "250"));

	final static Logger LOG = LoggerFactory.getLogger(AbstractPlatformServiceImpl.class);
	
	private String interfacePrefix;
	
	protected AbstractPlatformServiceImpl(String interfacePrefix) {
		this.interfacePrefix = interfacePrefix;
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

	@Override
	public final void disconnect(VPNSession session) throws IOException {
		try {
			session.getIp().down();
			session.getIp().delete();
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
					if (vaddr != null)
						ips.add(vaddr);
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to get interfaces.", e);
		}
		return ips;
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
				try {
					String publicKey = getPublicKey(name);
					if (publicKey != null) {
						LOG.info(String.format("%s has public key of %s.", name, publicKey));
						try {
							ConnectionStatus status = ctx.getClientService().getStatusForPublicKey(publicKey);
							Connection connection = status.getConnection();
							LOG.info(String.format(
									"Existing wireguard session on %s for %s, adding back to internal list", name, publicKey));
							sessions.add(new VPNSession(connection, ctx, get(name)));
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
		return onStart(ctx, sessions);
	}

	@Override
	public StatusDetail status(String iface) throws IOException {
		return new WireguardPipe(iface);
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

	protected void dns(Connection configuration, VirtualInetAddress ip) throws IOException {
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
}
