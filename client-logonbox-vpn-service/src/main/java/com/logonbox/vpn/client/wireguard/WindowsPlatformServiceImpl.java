package com.logonbox.vpn.client.wireguard;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;

public class WindowsPlatformServiceImpl extends AbstractPlatformServiceImpl<WindowsIP> {

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

	private static final String INTERFACE_PREFIX = "net";

	public WindowsPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	@Override
	protected String getPublicKey(String interfaceName) throws IOException {
		try {
			if (WindowsTunneler.getInterfacesNode().nodeExists(interfaceName)) {
				Preferences ifNode = WindowsTunneler.getInterfaceNode(interfaceName);
				String mac = ifNode.get(WindowsTunneler.PREF_MAC, "");
				NetworkInterface iface = NetworkInterface.getByName(interfaceName);
				if (iface != null) {
					if (mac.equals(IpUtil.toIEEE802(iface.getHardwareAddress()))) {
						return ifNode.get(WindowsTunneler.PREF_PUBLIC_KEY, null);
					} else
						/* Mac, changed, might as well get rid */
						ifNode.removeNode();
				} else
					return ifNode.get(WindowsTunneler.PREF_PUBLIC_KEY, null);
			}
		} catch (BackingStoreException bse) {
			throw new IOException("Failed to get public key.", bse);
		}
		return null;
	}

	@Override
	protected WindowsIP createVirtualInetAddress(NetworkInterface nif) throws IOException {
		WindowsIP ip = new WindowsIP(nif.getName(), nif.getIndex());
		return ip;
	}

	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return super.isWireGuardInterface(nif) && nif.getDisplayName().equals("Wintun Userspace Tunnel");
	}

	@Override
	public WindowsIP connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
		WindowsIP ip = null;

		/*
		 * Look for wireguard interfaces that are available but not connected. If we
		 * find none, try to create one.
		 */
		int maxIface = -1;
		for (int i = 0; i < MAX_INTERFACES; i++) {
			String name = getInterfacePrefix() + i;
			LOG.info(String.format("Looking for %s.", name));

			/*
			 * Get ALL the interfaces because on Windows the interface name is netXXX, and
			 * 'net' isn't specific to wireguard, nor even to WinTun.
			 */
			if (exists(name, false)) {
				/* Get if this is actually a Wireguard interface. */
				if (isWireGuardInterface(NetworkInterface.getByName(name))) {
					/* Interface exists and is wireguard, is it connected? */

					// TODO check service state, we can't rely on the public key
					// as we manage storage of it ourselves (no wg show command)
					String publicKey = getPublicKey(name);
					if (publicKey == null) {
						/* No addresses, wireguard not using it */
						LOG.info(String.format("%s is free.", name));
						ip = get(name);
						maxIface = i;
						break;
					} else if (publicKey.equals(configuration.getUserPublicKey())) {
						throw new IllegalStateException(
								String.format("Peer with public key %s on %s is already active.", publicKey, name));
					} else {
						LOG.info(String.format("%s is already in use.", name));
					}
				} else
					LOG.info(String.format("%s is already in use by something other than WinTun.", name));
			} else if (maxIface == -1) {
				/* This one is the next free number */
				maxIface = i;
				LOG.info(String.format("%s is next free interface.", name));
			}
		}
		if (maxIface == -1)
			throw new IOException(String.format("Exceeds maximum of %d interfaces.", MAX_INTERFACES));

		if (ip == null) {
			String name = getInterfacePrefix() + maxIface;
			LOG.info(String.format("No existing unused interfaces, creating new one (%s) for public key .", name,
					configuration.getUserPublicKey()));
			ip = new WindowsIP(name, maxIface);
			LOG.info(String.format("Created %s", name));
		} else
			LOG.info(String.format("Using %s", ip.getName()));

		Path confDir = Paths.get("conf").resolve("connections");
		if (!Files.exists(confDir))
			Files.createDirectories(confDir);
		Path confFile = confDir.resolve(ip.getName() + ".conf");
		try (Writer writer = Files.newBufferedWriter(confFile)) {
			write(configuration, writer);
		}

//		Service Name:  "WireGuardTunnel$SomeTunnelName"
//			Display Name:  "Some Service Name"
//			Service Type:  SERVICE_WIN32_OWN_PROCESS
//			Start Type:    StartAutomatic
//			Error Control: ErrorNormal,
//			Dependencies:  [ "Nsi", "TcpIp" ]
//			Sid Type:      SERVICE_SID_TYPE_UNRESTRICTED
//			Executable:    "C:\path\to\example\vpnclient.exe /service configfile.conf"

//		commons-daemon\prunsrv //IS//OpenDataPusher --DisplayName="OpenData Pusher" --Description="OpenData Pusher"^
//		     --Install="%cd%\commons-daemon\prunsrv.exe" --Jvm="%cd%\jre1.8.0_91\bin\client\jvm.dll" --StartMode=jvm --StopMode=jvm^
//		     --Startup=auto --StartClass=bg.government.opendatapusher.Pusher --StopClass=bg.government.opendatapusher.Pusher^
//		     --StartParams=start --StopParams=stop --StartMethod=windowsService --StopMethod=windowsService^
//		     --Classpath="%cd%\opendata-ckan-pusher.jar" --LogLevel=DEBUG^ --LogPath="%cd%\logs" --LogPrefix=procrun.log^
//		     --StdOutput="%cd%\logs\stdout.log" --StdError="%cd%\logs\stderr.log"
//		      

//		     --Classpath="%cd%\opendata-ckan-pusher.jar"
//		      

		/* Install service for the network interface */
		

		LOG.info(String.format("Installing service for %s", ip.getName()));
		ForkerBuilder builder = new ForkerBuilder();
		builder.command().add(getPrunsrv().toString());
		builder.command().add("//IS//LogonBoxVPNTunnel$" + ip.getName());
		builder.command().add("--Install=" + getPrunsrv().toString());
		builder.command().add("--DisplayName=LogonBox VPN Tunnel for " + ip.getName());
		builder.command().add("--Description=Managed a single tunnel LogonBox VPN (" + ip.getName() + ")");
		builder.command().add("--Jvm=" + System.getProperty("java.home") + "\\bin\\client\\jvm.dll");
		builder.command().add("--StartMode=jvm");
		builder.command().add("--StopMode=jvm");
		builder.command().add("--Startup=auto");
		builder.command().add("--StartClass=" + WindowsTunneler.class.getName());
		builder.command().add("--StopClass=" + WindowsTunneler.class.getName());
		builder.command().add("--LogLevel=" + toLevel());
		builder.command().add("--Classpath=" + reconstructClassPath());
		builder.command().add("--LogPath=" + System.getProperty("user.dir") + "\\logs");
		builder.command().add("--LogPrefix=tunneler-" + ip.getName() + ".log");
		builder.command()
				.add("--StdOutput=" + System.getProperty("user.dir") + "\\log\\stdout-" + ip.getName() + ".log");
		builder.command()
				.add("--StdError=" + System.getProperty("user.dir") + "\\log\\stderr-" + ip.getName() + ".log");
		builder.command().add("--StartParams=/service;" + confFile.toAbsolutePath());
		builder.command().add("--Stop=stop");
		builder.command().add("--StartMethod=main");
		builder.command().add("--StopMethod=main");
		builder.redirectErrorStream(true);

		ForkerProcess process = builder.start();
		try {
			IOUtils.copy(process.getInputStream(), System.out);
		} finally {
			try {
				if (process.waitFor() != 0)
					throw new IOException(String.format("Failed to install tunnel service for %s, exited with code %d.",
							ip.getName(), process.exitValue()));
			} catch (InterruptedException e) {
				throw new IOException("Interrupted waiting for process to finish.");
			}
		}

		LOG.info(String.format("Installed service for %s", ip.getName()));

//		/* Bring up the interface (will set the given MTU) */
//		ip.setMtu(configuration.getMtu());
//		LOG.info(String.format("Bringing up %s", ip.getName()));
//		ip.up();

		/* Set the routes */
//		LOG.info(String.format("Setting routes for %s", ip.getName()));
//		setRoutes(session, ip);

		return ip;
	}

	private String reconstructClassPath() {

		Set<URL> urls = new LinkedHashSet<>();

		/*
		 * Get all of the locations on java.class.path and turn them into URL's
		 */
		for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
			try {
				urls.add(new File(path).toURI().toURL());
			} catch (MalformedURLException e) {
			}
		}

		/*
		 * Traverse the class loader heirarchy looking for URLClassLoader and adding
		 * those as well
		 */
		reconstructFromClassLoader(WindowsPlatformServiceImpl.class.getClassLoader(), urls);
		if (Thread.currentThread().getContextClassLoader() != null)
			reconstructFromClassLoader(Thread.currentThread().getContextClassLoader(), urls);

		/* Only include the jars we need for this tool */
		StringBuilder path = new StringBuilder();
		for (URL url : urls) {
			try {
				String fullPath = new File(url.toURI()).getAbsolutePath();
				if (fullPath.matches(".*jna.*") || fullPath.matches("slf4j") || fullPath.matches("commons-io"))
					if (path.length() > 0)
						path.append(File.pathSeparator);
				path.append(fullPath);
			} catch (URISyntaxException e) {
			}

		}

		return path.toString();
	}

	private void reconstructFromClassLoader(ClassLoader classLoader, Set<URL> urls) {
		if (classLoader instanceof URLClassLoader) {
			URLClassLoader ucl = (URLClassLoader) classLoader;
			urls.addAll(Arrays.asList(ucl.getURLs()));
		}
		if (classLoader.getParent() != null)
			reconstructFromClassLoader(classLoader.getParent(), urls);
	}

	private Path getPrunsrv() {
		Path f = Paths.get("prunsrv.exe");
		if (Files.exists(f)) {
			/* Installed */
			return f;
		}

		/* Development */
		f = Paths.get("src").resolve("main").resolve("exe");
		if (SystemUtils.OS_ARCH.equals("x86"))
			f = f.resolve("win32-x86");
		else
			f = f.resolve("win32-x86-64");
		return f.resolve("prunsrv.exe");
	}

	protected String toLevel() {
		if (LOG.isDebugEnabled() || LOG.isTraceEnabled())
			return "DEBUG";
		else if (LOG.isInfoEnabled())
			return "INFO";
		else if (LOG.isWarnEnabled())
			return "WARN";
		else
			return "ERROR";
	}

	@Override
	protected void writeInterface(Connection configuration, Writer writer) {
		PrintWriter pw = new PrintWriter(writer, true);
		pw.println(String.format("Address = %s", configuration.getAddress()));
	}
}
