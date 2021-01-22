package com.logonbox.vpn.client.service;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.HypersocketClient;
import com.hypersocket.client.HypersocketClientAdapter;
import com.hypersocket.client.HypersocketClientListener;
import com.hypersocket.client.UserCancelledException;
import com.hypersocket.netty.NettyClientTransport;
import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.DefaultNonBlockingProcessListener;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;
import com.sshtools.forker.client.NonBlockingProcess;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.common.IO;

public class LogonBoxVPNSession extends AbstractConnectionJob implements Closeable {

	public static final int MAX_INTERFACES = Integer.parseInt(System.getProperty("wireguard.maxInterfaces", "10"));

	static Logger log = LoggerFactory.getLogger(AbstractConnectionJob.class);

	private List<String> allows;
	private VirtualInetAddress ip;

	private LocalContext localContext;
	private Connection connection;

	public LocalContext getLocalContext() {
		return localContext;
	}

	public Connection getConnection() {
		return connection;
	}
	
	public LogonBoxVPNSession(Connection connection, LocalContext localContext) {
		this(connection, localContext, null);
	}

	public LogonBoxVPNSession(Connection connection, LocalContext localContext, VirtualInetAddress ip) {
		this.localContext = localContext;
		this.connection = connection;
		this.ip = ip;
	}

	@Override
	public void close() throws IOException {
		log.info(String.format("Closing VPN session for %s", ip.getName()));
		try {
			ip.setRoutes(new ArrayList<>());
		} finally {
			ip.down();
		}
	}

	@Override
	public void run() {

		if (log.isInfoEnabled()) {
			log.info("Connecting to " + connection);
		}

		LocalContext cctx = getLocalContext();
		LogonBoxVPNClientServiceImpl clientServiceImpl = (LogonBoxVPNClientServiceImpl) cctx.getClientService();

		HypersocketClientListener<Connection> listener = new HypersocketClientAdapter<Connection>() {
			@Override
			public void disconnected(HypersocketClient<Connection> client, boolean onError) {
				clientServiceImpl.disconnected(connection, client);
				log.info("Client has disconnected, informing GUI");
				cctx.getGuiRegistry().disconnected(connection, onError ? "Error occured during connection." : null);
				if (client.getAttachment().isStayConnected() && onError) {
					try {
						clientServiceImpl.scheduleConnect(connection);
					} catch (RemoteException e1) {
					}
				}
			}
		};

		ServiceClient client = null;
		try {
			/*
			 * This is the HTTPs connection to the server, it is effectively now only used
			 * to get the current version for updates.
			 */
			client = new ServiceClient(new NettyClientTransport(cctx.getBoss(), cctx.getWorker()), clientServiceImpl,
					Locale.getDefault(), listener, connection, cctx.getGuiRegistry());

			client.connect(connection.getHostname(), connection.getPort(), connection.getPath(), Locale.getDefault());

			if (log.isInfoEnabled()) {
				log.info("Connected to " + connection);
			}

			start(connection);
			cctx.getGuiRegistry().transportConnected(connection);

			/*
			 * Tell the GUI we are now completely connected. The GUI should NOT yet load any
			 * resources, as we need to check if there are any updates to do firstContext
			 */
			cctx.getGuiRegistry().ready(connection);

			/*
			 * Now check for updates. If there are any, we don't start any plugins for this
			 * connection
			 */

			clientServiceImpl.update(connection, client);
//			if (!clientServiceImpl.update(connection, clientServiceImpl)) {
//				clientServiceImpl.startPlugins(client);
//			}
			clientServiceImpl.finishedConnecting(connection, this);

		} catch (Throwable e) {
			if (log.isErrorEnabled()) {
				log.error("Failed to connect " + connection, e);
			}
			cctx.getGuiRegistry().failedToConnect(connection, e.getMessage());
			clientServiceImpl.failedToConnect(connection, e);

			if (!(e instanceof UserCancelledException)) {
				if (connection.isStayConnected()) {
					try {
						cctx.getClientService().scheduleConnect(connection);
						return;
					} catch (RemoteException e1) {
					}
				}
			}
		}
	}

	void start(Connection configuration) throws IOException {

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		int maxIface = -1;
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = "wg" + i;
			log.info(String.format("Looking for %s.", name));
			if (getLocalContext().getPlatformService().exists(name)) {
				/* Interface exists, is it connected? */
				String publicKey = getLocalContext().getPlatformService().getPublicKey(name);
				if (publicKey == null) {
					/* No addresses, wireguard not using it */
					log.info(String.format("%s is free.", name));
					ip = getLocalContext().getPlatformService().get(name);
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
			String name = "wg" + maxIface;
			log.info(String.format("No existing unused interfaces, creating new one (%s) for public key .", name,
					configuration.getUserPublicKey()));
			ip = getLocalContext().getPlatformService().add(name, "wireguard");
			if (ip == null)
				throw new IOException("Failed to create virtual IP address.");
			log.info(String.format("Created %s", name));
		} else
			log.info(String.format("Using %s", ip.getName()));

		/* Set the address reserved */
		ip.setAddresses(configuration.getAddress());

		Path tempFile = Files.createTempFile("wg", "cfg");
		try {
			try (Writer writer = Files.newBufferedWriter(tempFile)) {
				write(configuration, writer);
			}
			log.info(String.format("Activating Wireguard configuration for %s (in %s)", ip.getName(), tempFile));
			OSCommand.runCommand("cat", tempFile.toString());
			OSCommand.runCommand("wg", "setconf", ip.getName(), tempFile.toString());
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
		setRoutes(ip);
	}

	void write(Connection configuration, Writer writer) {
		PrintWriter pw = new PrintWriter(writer, true);
		pw.println("[Interface]");
		pw.println(String.format("PrivateKey = %s", configuration.getUserPrivateKey()));
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
	}

	void setRoutes(VirtualInetAddress ip) throws IOException {

		/* Set routes from the known allowed-ips supplies by Wireguard. */
		allows = new ArrayList<>();

		for (String s : OSCommand.runCommandAndCaptureOutput("wg", "show", ip.getName(), "allowed-ips")) {
			StringTokenizer t = new StringTokenizer(s);
			if (t.hasMoreTokens()) {
				t.nextToken();
				while (t.hasMoreTokens())
					allows.add(t.nextToken());
			}
		}

		/*
		 * Sort by network subnet size (biggest first)
		 */
		Collections.sort(allows, (a, b) -> {
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
		ip.setRoutes(allows);
	}

	protected ForkerProcess runCommand(String... command) throws IOException {
		ForkerBuilder builder = new ForkerBuilder().io(IO.NON_BLOCKING).redirectErrorStream(true).command(command);
		return builder.start(new DefaultNonBlockingProcessListener() {
			@Override
			public void onStdout(NonBlockingProcess process, ByteBuffer buffer, boolean closed) {
				if (!closed) {
					byte[] bytes = new byte[buffer.remaining()];
					buffer.get(bytes);
					System.out.println(new String(bytes));
				}
			}
		});
	}
}
