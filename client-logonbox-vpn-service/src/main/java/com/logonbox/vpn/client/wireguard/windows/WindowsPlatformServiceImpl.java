package com.logonbox.vpn.client.wireguard.windows;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.client.wireguard.AbstractPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.windows.service.NetworkConfigurationService;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.Connection;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.client.impl.jna.win32.Kernel32;
import com.sshtools.forker.common.XAdvapi32;
import com.sshtools.forker.common.XWinsvc;
import com.sshtools.forker.services.Services;
import com.sshtools.forker.services.impl.Win32ServiceService;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.SC_HANDLE;
import com.sun.jna.ptr.PointerByReference;

public class WindowsPlatformServiceImpl extends AbstractPlatformServiceImpl<WindowsIP> {

	public static final String TUNNEL_SERVICE_NAME_PREFIX = "LogonBoxVPNTunnel";

	private static final String INTERFACE_PREFIX = "net";

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

	private static final int SERVICE_INSTALL_TIMEOUT = Integer.parseInt(System.getProperty("logonbox.vpn.serviceInstallTimeout", "10"));

	private static Preferences PREFS = null;
	private Object lock = new Object();

	public static Preferences getInterfaceNode(String name) {
		return getInterfacesNode().node(name);
	}

	public static Preferences getInterfacesNode() {
		return getPreferences().node("interfaces");
	}

	public static Preferences getPreferences() {
		if (PREFS == null) {
			/* Test whether we can write to system preferences */
			try {
				PREFS = Preferences.systemRoot();
				PREFS.put("test", "true");
				PREFS.flush();
				PREFS.remove("test");
				PREFS.flush();
			} catch (Exception bse) {
				System.out.println("Fallback to usering user preferences for public key -> interface mapping.");
				PREFS = Preferences.userRoot();
			}
		}
		return PREFS;
	}

	private WindowsWireGuardNamedPipe pipe;
	private File wgFile;

	public WindowsPlatformServiceImpl() {
		super(INTERFACE_PREFIX);
	}

	
	@Override
	public List<WindowsIP> ips(boolean wireguardInterface) {
		Set<WindowsIP> ips = new LinkedHashSet<>(super.ips(wireguardInterface));
		try {
			String name = null;
			
			/* NOTE: Workaround. NetworkInterface.getNetworkInterfaces() doesn't discover 
			 * active WireGuard interfaces for some reason, so use ipconfig /all to 
			 * create a merged list. 
			 */
			for(String line : OSCommand.adminCommandAndCaptureOutput("ipconfig", "/all")) {
				line = line.trim();
				if(line.startsWith("Unknown adapter")) {
					String[] args = line.split("\\s+");
					if(args.length > 1 && args[2].startsWith(getInterfacePrefix())) {
						name = args[2].split(":")[0];
					}
				}
				else if(name != null && line.startsWith("Description ")) {
					String[] args = line.split(":");
					if(args.length > 1) {
						String description = args[1].trim();
						if(description.equals("WireGuard Tunnel")) {
							WindowsIP vaddr = new WindowsIP(name, description, this);
							ips.add(vaddr);
							break;
						}
					}
				}
			}
		} catch (Exception e) {
			throw new IllegalStateException("Failed to list interfaces.", e);
		}
		return new ArrayList<WindowsIP>(ips);
	}

	@Override
	protected void addRouteAll(Connection connection) throws IOException {
		LOG.info("Routing traffic all through VPN");
		OSCommand.admin("route", "add", connection.getEndpointAddress(), getDefaultGateway());
	}

	@Override
	protected void removeRouteAll(VPNSession session) throws IOException {
		LOG.info("Removing routing of all traffic through VPN");
		OSCommand.admin("route", "delete", session.getConnection().getEndpointAddress(), getDefaultGateway());
	}

	@Override
	protected String getDefaultGateway() throws IOException {
		String gw = null;
		for(String line : OSCommand.adminCommandAndIterateOutput("ipconfig")) {
			if(gw == null) {
				line = line.trim();
				if(line.startsWith("Default Gateway ")) {
					int idx = line.indexOf(":");
					if(idx != -1) {
						line = line.substring(idx + 1).trim();
						if(!line.equals("0.0.0.0"))
							gw = line;
					}
				}
			}
		}
		if(gw == null)
			throw new IOException("Could not get default gateway.");
		else
			return gw;
	}
	
	@Override
	protected WindowsIP onConnect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException {
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
				WindowsIP nicByName = get(name);
				if (isWireGuardInterface(nicByName)) {
					/* Interface exists and is wireguard, is it connected? */

					// TODO check service state, we can't rely on the public key
					// as we manage storage of it ourselves (no wg show command)
					String publicKey = getPublicKey(name);
					if (publicKey == null) {
						/* No addresses, wireguard not using it */
						LOG.info(String.format("%s (%s) is free.", name, nicByName.getDisplayName()));
						ip = get(name);
						maxIface = i;
						break;
					} else if (publicKey.equals(configuration.getUserPublicKey())) {
						LOG.warn(
								String.format("Peer with public key %s on %s is already active (by %s).", publicKey, name, nicByName.getDisplayName()));
						return get(name);
					} else {
						LOG.info(String.format("%s is already in use (by %s).", name, nicByName.getDisplayName()));
					}
				} else
					LOG.info(String.format("%s is already in use by something other than WinTun (%s).", name,
							nicByName.getDisplayName()));
			} else if (maxIface == -1) {
				/* This one is the next free number */
				maxIface = i;
				LOG.info(String.format("%s is next free interface.", name));
				break;
			}
		}
		if (maxIface == -1)
			throw new IOException(String.format("Exceeds maximum of %d interfaces.", MAX_INTERFACES));

		if (ip == null) {
			String name = getInterfacePrefix() + maxIface;
			LOG.info(String.format("No existing unused interfaces, creating new one (%s) for public key .", name,
					configuration.getUserPublicKey()));
			ip = new WindowsIP(name, "Wintun Userspace Tunnel", this);
			LOG.info(String.format("Created %s", name));
		} else
			LOG.info(String.format("Using %s", ip.getName()));

		Path cwd = Paths.get(System.getProperty("user.dir"));
		Path confDir = cwd.resolve("conf").resolve("connections");
		if (!Files.exists(confDir))
			Files.createDirectories(confDir);

		/*
		 * We need to set up file descriptors here so that
		 * the pipe has correct 'security descriptor' in windows. It derives this from
		 * the permissions on the folder the configuration file is stored in.
		 * 
		 * This took a lot of finding :\
		 * 
		 */
		PointerByReference securityDescriptor = new PointerByReference();
		XAdvapi32.INSTANCE.ConvertStringSecurityDescriptorToSecurityDescriptor(
				"O:BAG:BAD:PAI(A;OICI;FA;;;BA)(A;OICI;FA;;;SY)", 1, securityDescriptor, null);
		if (!Advapi32.INSTANCE.SetFileSecurity(confDir.toFile().getPath(),
				WinNT.OWNER_SECURITY_INFORMATION | WinNT.GROUP_SECURITY_INFORMATION | WinNT.DACL_SECURITY_INFORMATION,
				securityDescriptor.getValue())) {
			int err = Kernel32.INSTANCE.GetLastError();
			throw new IOException(String.format("Failed to set file security on '%s'. %d. %s", confDir, err,
					Kernel32Util.formatMessageFromLastErrorCode(err)));
		}

		Path confFile = confDir.resolve(ip.getName() + ".conf");
		try (Writer writer = Files.newBufferedWriter(confFile)) {
			write(configuration, writer);
		}

		/* Install service for the network interface */
		boolean install = false;
		if (!Services.get().hasService(TUNNEL_SERVICE_NAME_PREFIX + "$" + ip.getName())) {
			install = true;
			installService(ip.getName(), cwd);
		} else
			LOG.info(String.format("Service for %s already exists.", ip.getName()));

		/* The service may take a short while to appear */
		int i = 0;
		for (; i < SERVICE_INSTALL_TIMEOUT; i++) {
			if (Services.get().hasService(TUNNEL_SERVICE_NAME_PREFIX + "$" + ip.getName()))
				break;
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new IOException("Interrupted.", e);
			}
		}
		if (i == 10)
			throw new IOException(
					String.format("Service for %s cannot be found, suggesting installation failed, please check logs.",
							ip.getName()));

		/*
		 * About to start connection. The "last handshake" should be this value or later
		 * if we get a valid connection
		 */
		long connectionStarted = ((System.currentTimeMillis() / 1000l) - 1) * 1000l;

		LOG.info(String.format("Waiting %d seconds for service to settle.", ClientService.SERVICE_WAIT_TIMEOUT));
		try {
			Thread.sleep(TimeUnit.SECONDS.toMillis(ClientService.SERVICE_WAIT_TIMEOUT));
		} catch (InterruptedException e) {
		}		
		LOG.info("Service should be settled.");

		if (ip.isUp()) {
			LOG.info(String.format("Service for %s is already up.", ip.getName()));
		} else {
			LOG.info(String.format("Bringing up %s", ip.getName()));
			try {
				ip.up();
			}
			catch(IOException ioe) {
				/* Just installed service failed, clean it up */
				if(install) {
					ip.delete(); 
				}
				throw ioe;
			}
		}

		/*
		 * TODO the pipe is not yet working, falling back to using wg.exe for now
		 */
		//pipe = new WindowsWireGuardNamedPipe(ip.getName());

		/*
		 * Wait for the first handshake. As soon as we have it, we are 'connected'. If
		 * we don't get a handshake in that time, then consider this a failed
		 * connection. We don't know WHY, just it has failed
		 */
		LOG.info(String.format("Waiting for handshake for %d seconds. Hand shake should be after %d", ClientService.CONNECT_TIMEOUT, connectionStarted));
		ip = waitForFirstHandshake(configuration, ip, connectionStarted);
		
		/* DNS */
		dns(configuration, ip);
		
		return ip;

	}

	@Override
	protected void onDisconnect() throws IOException {
		if (pipe != null)
			pipe.close();
	}

	@Override
	protected WindowsIP createVirtualInetAddress(NetworkInterface nif) throws IOException {
		return new WindowsIP(nif.getName(), nif.getDisplayName(), this);
	}

	@Override
	public String[] getMissingPackages() {
		return new String[0];
	}

	void install(String serviceName, String displayName, String description, String[] dependencies, String account,
			String password, String command, int winStartType, boolean interactive,
			Winsvc.SERVICE_FAILURE_ACTIONS failureActions, boolean delayedAutoStart, DWORD sidType) throws IOException {

		XAdvapi32 advapi32 = XAdvapi32.INSTANCE;

		XWinsvc.SERVICE_DESCRIPTION desc = new XWinsvc.SERVICE_DESCRIPTION();
		desc.lpDescription = description;

		SC_HANDLE serviceManager = Win32ServiceService.getManager(null, Winsvc.SC_MANAGER_ALL_ACCESS);
		try {

			int dwServiceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
			if (interactive)
				dwServiceType |= WinNT.SERVICE_INTERACTIVE_PROCESS;

			SC_HANDLE service = advapi32.CreateService(serviceManager, serviceName, displayName,
					Winsvc.SERVICE_ALL_ACCESS, dwServiceType, winStartType, WinNT.SERVICE_ERROR_NORMAL, command, null,
					null, (dependencies == null ? "" : String.join("\0", dependencies)) + "\0", account, password);

			if (service != null) {
				try {
					boolean success = false;
					if (failureActions != null) {
						success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_FAILURE_ACTIONS,
								failureActions);
						if (!success) {
							int err = Native.getLastError();
							throw new IOException(String.format("Failed to set failure actions. %d. %s", err,
									Kernel32Util.formatMessageFromLastErrorCode(err)));
						}
					}

					success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_DESCRIPTION, desc);
					if (!success) {
						int err = Native.getLastError();
						throw new IOException(String.format("Failed to set description. %d. %s", err,
								Kernel32Util.formatMessageFromLastErrorCode(err)));
					}

					if (SystemUtils.IS_OS_WINDOWS_VISTA && delayedAutoStart) {
						XWinsvc.SERVICE_DELAYED_AUTO_START_INFO delayedDesc = new XWinsvc.SERVICE_DELAYED_AUTO_START_INFO();
						delayedDesc.fDelayedAutostart = true;
						success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_DELAYED_AUTO_START_INFO,
								delayedDesc);
						if (!success) {
							int err = Native.getLastError();
							throw new IOException(String.format("Failed to set autostart. %d. %s", err,
									Kernel32Util.formatMessageFromLastErrorCode(err)));
						}
					}

					/*
					 * https://github.com/WireGuard/wireguard-windows/tree/master/embeddable-dll-
					 * service
					 */
					if (sidType != null) {
						XWinsvc.SERVICE_SID_INFO info = new XWinsvc.SERVICE_SID_INFO();
						info.dwServiceSidType = sidType;
						success = advapi32.ChangeServiceConfig2(service, Winsvc.SERVICE_CONFIG_SERVICE_SID_INFO, info);
						if (!success) {
							int err = Native.getLastError();
							throw new IOException(String.format("Failed to set SERVICE_SID_INFO. %d. %s", err,
									Kernel32Util.formatMessageFromLastErrorCode(err)));
						}
					}

				} finally {
					advapi32.CloseServiceHandle(service);
				}
			} else {
				int err = Kernel32.INSTANCE.GetLastError();
				throw new IOException(String.format("Failed to install. %d. %s", err,
						Kernel32Util.formatMessageFromLastErrorCode(err)));

			}
		} finally {
			advapi32.CloseServiceHandle(serviceManager);
		}
	}

	public void installService(String name, Path cwd) throws IOException {
		LOG.info(String.format("Installing service for %s", name));
		StringBuilder cmd = new StringBuilder();
		cmd.append('"');
		cmd.append(System.getProperty("java.home") + "\\bin\\java.exe");
		cmd.append('"');
		cmd.append(' ');
		cmd.append("-cp");
		cmd.append(' ');
		cmd.append(reconstructClassPath());
		cmd.append(' ');
		// cmd.append(WindowsTunneler.class.getName());
		cmd.append(NetworkConfigurationService.class.getName());
		cmd.append(' ');
		cmd.append("/service");
		cmd.append(' ');
		cmd.append('"');
		cmd.append(cwd);
		cmd.append('"');
		cmd.append(' ');
		cmd.append('"');
		cmd.append(name);
		cmd.append('"');

		install(TUNNEL_SERVICE_NAME_PREFIX + "$" + name, "LogonBox VPN Tunnel for " + name,
				"Manage a single tunnel LogonBox VPN (" + name + ")", new String[] { "Nsi", "TcpIp" }, "LocalSystem",
				null, cmd.toString(), WinNT.SERVICE_DEMAND_START, false, null, false,
				XWinsvc.SERVICE_SID_TYPE_UNRESTRICTED);

		LOG.info(String.format("Installed service for %s", name));
	}

	@Override
	protected boolean isWireGuardInterface(NetworkInterface nif) {
		return super.isWireGuardInterface(nif) && nif.getDisplayName().startsWith("Wintun Userspace Tunnel");
	}

	protected boolean isWireGuardInterface(WindowsIP nif) {
		return isMatchesPrefix(nif) && nif.getDisplayName().startsWith("Wintun Userspace Tunnel");
	}

	protected boolean isMatchesPrefix(WindowsIP nif) {
		return nif.getName().startsWith(getInterfacePrefix());
	}

	@Override
	protected String getWGCommand() {
		synchronized (lock) {
			if (wgFile == null) {
				try {
					wgFile = File.createTempFile("wgx", ".exe");
					try (InputStream in = WindowsPlatformServiceImpl.class.getResourceAsStream(getWGExeResource())) {
						try (OutputStream out = new FileOutputStream(wgFile)) {
							in.transferTo(out);
						}
					}
				} catch (IOException ioe) {
					throw new IllegalStateException("Failed to get wg.exe.", ioe);
				}
			}
			return wgFile.toString();
		}
	}

	private String getWGExeResource() {
		if (System.getProperty("os.arch").indexOf("64") == -1)
			return "/win32-x86/wg.exe";
		else
			return "/win32-x86-64/wg.exe";
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

		/* Sort so that directories come first - helps with development */
		List<URL> sortedUrls = new ArrayList<>(urls);
		Collections.sort(sortedUrls, (o1, o2) -> {
			int i1 = o1.getPath().indexOf("/target/classes") != -1 ? 1 : 0;
			int i2 = o2.getPath().indexOf("/target/classes") != -1 ? 1 : 0;
			if (i1 == i2) {
				return o1.getPath().compareTo(o2.getPath());
			} else {
				return i1 > i2 ? -1 : 1;
			}
		});
		urls.clear();
		urls.addAll(sortedUrls);

		/* Only include the jars we need for this tool */
		StringBuilder path = new StringBuilder();
		for (URL url : urls) {
			try {
				String fullPath = new File(url.toURI()).getAbsolutePath();
				{
					if (fullPath.matches(".*client-logonbox-vpn-service.*") || fullPath.matches(".*jna.*")
							|| fullPath.matches(".*forker-common.*") || fullPath.matches(".*forker-client.*")
							|| fullPath.matches(".*commons-io.*")) {
						if (path.length() > 0)
							path.append(File.pathSeparator);
						path.append('"');
						path.append(fullPath);
						path.append('"');
					}
				}
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

	public void uninstall(String serviceName) throws IOException {
		XAdvapi32 advapi32 = XAdvapi32.INSTANCE;
		SC_HANDLE serviceManager, service;
		serviceManager = Win32ServiceService.getManager(null, WinNT.GENERIC_ALL);
		try {
			service = advapi32.OpenService(serviceManager, serviceName, WinNT.GENERIC_ALL);
			if (service != null) {
				try {
					if (!advapi32.DeleteService(service)) {
						int err = Kernel32.INSTANCE.GetLastError();
						throw new IOException(String.format("Failed to find service to uninstall '%s'. %d. %s",
								serviceName, err, Kernel32Util.formatMessageFromLastErrorCode(err)));
					}
				} finally {
					advapi32.CloseServiceHandle(service);
				}
			} else {
				int err = Kernel32.INSTANCE.GetLastError();
				throw new IOException(String.format("Failed to find service to uninstall '%s'. %d. %s", serviceName,
						err, Kernel32Util.formatMessageFromLastErrorCode(err)));
			}
		} finally {
			advapi32.CloseServiceHandle(serviceManager);
		}
	}

	@Override
	protected void writeInterface(Connection configuration, Writer writer) {
		PrintWriter pw = new PrintWriter(writer, true);
		pw.println(String.format("Address = %s", configuration.getAddress()));
	}
}
