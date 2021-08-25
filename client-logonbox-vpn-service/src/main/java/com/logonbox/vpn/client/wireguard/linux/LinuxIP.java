package com.logonbox.vpn.client.wireguard.linux;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.jgonian.ipmath.Ipv4;
import com.logonbox.vpn.client.dbus.NetworkManager;
import com.logonbox.vpn.client.dbus.NetworkManager.Ipv6Address;
import com.logonbox.vpn.client.dbus.Resolve1Manager;
import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.logonbox.vpn.common.client.Util;
import com.sshtools.forker.client.EffectiveUserFactory;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.common.IO;

public class LinuxIP extends AbstractVirtualInetAddress<LinuxPlatformServiceImpl> {
	private static final String NETWORK_MANAGER_BUS_NAME = "org.freedesktop.NetworkManager";

	enum IpAddressState {
		HEADER, IP, MAC
	}

	public final static String TABLE_AUTO = "auto";
	public final static String TABLE_OFF = "off";

	final static Logger LOG = LoggerFactory.getLogger(LinuxIP.class);

	private static final String END_HYPERSOCKET_WIREGUARD_RESOLVCONF = "###### END-HYPERSOCKET-WIREGUARD ######";
	private static final String START_HYPERSOCKET_WIREGUARD_RESOLVECONF = "###### START-HYPERSOCKET-WIREGUARD ######";

	private Set<String> addresses = new LinkedHashSet<>();

	private boolean dnsSet;

	public LinuxIP(String name, LinuxPlatformServiceImpl platform) {
		super(platform, name);
	}

	public void addAddress(String address) throws IOException {
		if (addresses.contains(address))
			throw new IllegalStateException(String.format("Interface %s already has address %s", getName(), address));
		if (addresses.size() > 0 && StringUtils.isNotBlank(getPeer()))
			throw new IllegalStateException(String.format(
					"Interface %s is configured to have a single peer %s, so cannot add a second address %s", getName(),
					getPeer(), address));

		if (StringUtils.isNotBlank(getPeer()))
			OSCommand.adminCommand("ip", "address", "add", "dev", getName(), address, "peer", getPeer());
		else
			OSCommand.adminCommand("ip", "address", "add", "dev", getName(), address);
		addresses.add(address);
	}

	@Override
	public void delete() throws IOException {
		OSCommand.adminCommand("ip", "link", "del", "dev", getName());
	}

	public void dns(String[] dns) throws IOException {
		if (dns == null || dns.length == 0) {
			if (dnsSet)
				unsetDns();
		} else {
			DNSIntegrationMethod method = calcDnsMethod();
			LOG.info(String.format("Setting DNS for %s (iface prefix %s) to %s using %s", getName(),
					getPlatform().resolvconfIfacePrefix(), String.join(", ", dns), method));
			switch (method) {
			case NETWORK_MANAGER:
				updateNetworkManager(dns);
				break;
			case RESOLVCONF:
				updateResolvConf(dns);
				break;
			case SYSTEMD:
				updateSystemd(dns);
				break;
			case RAW:
				updateResolvDotConf(dns);
				break;
			case NONE:
				break;
			default:
				/* TODO */
				throw new UnsupportedOperationException();
			}

			dnsSet = true;
		}
	}

	@Override
	public void down() throws IOException {
		if (dnsSet) {
			unsetDns();
		}
		/*
		 * TODO
		 * 
		 * [[ $HAVE_SET_FIREWALL -eq 0 ]] || remove_firewall
		 * 
		 * TODO
		 * 
		 * if [[ -z $TABLE || $TABLE == auto ]] && get_fwmark table && [[ $(wg show
		 * "$INTERFACE" allowed-ips) =~ /0(\ |$'\n'|$) ]]; then while [[ $(ip -4 rule
		 * show 2>/dev/null) == *"lookup $table"* ]]; do cmd ip -4 rule delete table
		 * $table done while [[ $(ip -4 rule show 2>/dev/null) ==
		 * *"from all lookup main suppress_prefixlength 0"* ]]; do cmd ip -4 rule delete
		 * table main suppress_prefixlength 0 done while [[ $(ip -6 rule show
		 * 2>/dev/null) == *"lookup $table"* ]]; do cmd ip -6 rule delete table $table
		 * done while [[ $(ip -6 rule show 2>/dev/null) ==
		 * *"from all lookup main suppress_prefixlength 0"* ]]; do cmd ip -6 rule delete
		 * table main suppress_prefixlength 0 done fi cmd ip link delete dev
		 * "$INTERFACE"
		 */
		setRoutes(new ArrayList<>());
		delete();
	}

	public boolean hasAddress(String address) {
		return addresses.contains(address);
	}

	@Override
	public boolean isUp() {
		return true;
	}

	public void removeAddress(String address) throws IOException {
		if (!addresses.contains(address))
			throw new IllegalStateException(String.format("Interface %s not not have address %s", getName(), address));
		if (addresses.size() > 0 && StringUtils.isNotBlank(getPeer()))
			throw new IllegalStateException(String.format(
					"Interface %s is configured to have a single peer %s, so cannot add a second address %s", getName(),
					getPeer(), address));

		OSCommand.adminCommand("ip", "address", "del", address, "dev", getName());
		addresses.remove(address);
	}

	public void setAddresses(String... addresses) {
		List<String> addr = Arrays.asList(addresses);
		List<Exception> exceptions = new ArrayList<>();
		for (String a : addresses) {
			if (!hasAddress(a)) {
				try {
					addAddress(a);
				} catch (Exception e) {
					exceptions.add(e);
				}
			}
		}

		for (String a : new ArrayList<>(this.addresses)) {
			if (!addr.contains(a)) {
				try {
					removeAddress(a);
				} catch (Exception e) {
					exceptions.add(e);
				}
			}
		}

		if (!exceptions.isEmpty()) {
			Exception e = exceptions.get(0);
			if (e instanceof RuntimeException)
				throw (RuntimeException) e;
			else
				throw new IllegalArgumentException("Failed to set addresses.", e);
		}
	}

	@Override
	public void setPeer(String peer) {
		if (!Objects.equals(peer, this.getPeer())) {
			if (StringUtils.isNotBlank(peer) && addresses.size() > 1)
				throw new IllegalStateException(String.format(
						"Interface %s is already configured to have multiple addresses, so cannot have a single peer %s",
						getName(), peer));
			super.setPeer(peer);
		}
	}

	public void setRoutes(Collection<String> allows) throws IOException {

		/* Remove all the current routes for this interface */
		for (String row : OSCommand.adminCommandAndCaptureOutput("ip", "route", "show", "dev", getName())) {
			String[] l = row.split("\\s+");
			if (l.length > 0) {
				LOG.info(String.format("Removing route %s for %s", l[0], getName()));
				OSCommand.adminCommand("ip", "route", "del", l[0], "dev", getName());
			}
		}

		for (String route : allows) {
			addRoute(route);
		}
	}

	@Override
	public String toString() {
		return "Ip [name=" + getName() + ", addresses=" + addresses + ", peer=" + getPeer() + "]";
	}

	@Override
	public void up() throws IOException {
		if (getMtu() > 0) {
			OSCommand.adminCommand("ip", "link", "set", "mtu", String.valueOf(getMtu()), "up", "dev", getName());
		} else {
			/*
			 * First detect MTU, then bring up. First try from existing Wireguard
			 * connections?
			 */
			int tmtu = 0;
			// TODO
//				for (String line : OSCommand.runCommandAndCaptureOutput("wg", "show", name, "endpoints")) {
//				 [[ $endpoint =~ ^\[?([a-z0-9:.]+)\]?:[0-9]+$ ]] || continue
//	                output="$(ip route get "${BASH_REMATCH[1]}" || true)"
//	                [[ ( $output =~ mtu\ ([0-9]+) || ( $output =~ dev\ ([^ ]+) && $(ip link show dev "${BASH_REMATCH[1]}") =~ mtu\ ([0-9]+) ) ) && ${BASH_REMATCH[1]} -gt $mtu ]] && mtu="${BASH_REMATCH[1]}"
			// TODO
//				}

			if (tmtu == 0) {
				/* Not found, try the default route */
				for (String line : OSCommand.adminCommandAndCaptureOutput("ip", "route", "show", "default")) {
					StringTokenizer t = new StringTokenizer(line);
					while (t.hasMoreTokens()) {
						String tk = t.nextToken();
						if (tk.equals("dev")) {
							for (String iline : OSCommand.adminCommandAndCaptureOutput("ip", "link", "show", "dev",
									t.nextToken())) {
								StringTokenizer it = new StringTokenizer(iline);
								while (it.hasMoreTokens()) {
									String itk = it.nextToken();
									if (itk.equals("mtu")) {
										tmtu = Integer.parseInt(it.nextToken());
										break;
									}
								}
								break;
							}
							break;
						}
					}
					break;
				}
			}

			/* Still not found, use generic default */
			if (tmtu == 0)
				tmtu = 1500;

			/* Subtract 80, because .. */
			tmtu -= 80;

			/* Bring it up! */
			OSCommand.adminCommand("ip", "link", "set", "mtu", String.valueOf(tmtu), "up", "dev", getName());
		}
	}

	private int getIndexForName() throws IOException {
		for (String line : OSCommand.runCommandAndCaptureOutput("ip", "addr")) {
			line = line.trim();
			String[] args = line.split(":");
			if (args.length > 1) {
				try {
					int idx = Integer.parseInt(args[0].trim());
					if (args[1].trim().equals(getName()))
						return idx;
				} catch (Exception e) {
				}
			}
		}
		throw new IOException(String.format("Could not find interface index for %s", getName()));
	}

	private void addDefault(String route) {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	private void addRoute(String route) throws IOException {
		String proto = "-4";
		if (route.matches(".*:.*"))
			proto = "-6";
		if (TABLE_OFF.equals(getTable()))
			return;
		if (!TABLE_AUTO.equals(getTable())) {
			OSCommand.adminCommand("ip", proto, "route", "add", route, "dev", getName(), "table", getTable());
		} else if (route.endsWith("/0")) {
			addDefault(route);
		} else {
			try {
				String res = OSCommand
						.adminCommandAndCaptureOutput("ip", proto, "route", "show", "dev", getName(), "match", route)
						.iterator().next();
				if (StringUtils.isNotBlank(res)) {
					// Already have
					return;
				}
			} catch (Exception e) {
			}
			LOG.info(String.format("Adding route %s to %s for %s", route, getName(), proto));
			OSCommand.adminCommand("ip", proto, "route", "add", route, "dev", getName());
		}
	}

	private void unsetDns() throws IOException {
		try {
			if (dnsSet) {
				LOG.info(String.format("unsetting DNS for %s (iface prefix %s)", getName(),
						getPlatform().resolvconfIfacePrefix()));
				switch (calcDnsMethod()) {
				case NETWORK_MANAGER:
					updateNetworkManager(null);
					break;
				case RESOLVCONF:
					OSCommand.adminCommand("resolvconf", "-d", getPlatform().resolvconfIfacePrefix() + getName(), "-f");
					break;
				case SYSTEMD:
					updateSystemd(null);
					break;
				case RAW:
					updateResolvDotConf(null);
					break;
				case NONE:
					break;
				default:
					throw new UnsupportedOperationException();
				}
			}
		} finally {
			dnsSet = false;
		}
	}

	private void updateNetworkManager(String[] dns) throws IOException {
		
		/* This will be using split DNS if the backend is systemd or dnsmasq, or
		 * compatible for default backend.
		 * 
		 * TODO we need to check the backend in use if NetworkManager is chosen
		 * to know if we can do split DNS. 
		 * 
		 * https://wiki.gnome.org/Projects/NetworkManager/DNS
		 */
		try (DBusConnection conn = DBusConnection.getConnection(DBusBusType.SYSTEM)) {
			LOG.info("Updating DNS via NetworkManager");
			NetworkManager mgr = conn.getRemoteObject(NETWORK_MANAGER_BUS_NAME, "/org/freedesktop/NetworkManager",
					NetworkManager.class);
			DBusPath path = mgr.GetDeviceByIpIface(getName());
			if (path == null)
				throw new IOException(String.format("No interface %s", getName()));

			LOG.info(String.format("DBus device path is %s", path.getPath()));

			Properties props = conn.getRemoteObject(NETWORK_MANAGER_BUS_NAME, path.getPath(), Properties.class);
			Map<String, Variant<?>> propsMap = props.GetAll("org.freedesktop.NetworkManager.Device");
			@SuppressWarnings("unchecked")
			List<DBusPath> availableConnections = (List<DBusPath>) propsMap.get("AvailableConnections").getValue();
			for (DBusPath availableConnectionPath : availableConnections) {
				NetworkManager.Settings.Connection settings = conn.getRemoteObject(NETWORK_MANAGER_BUS_NAME,
						availableConnectionPath.getPath(), NetworkManager.Settings.Connection.class);
				Map<String, Map<String, Variant<?>>> settingsMap = settings.GetSettings();

				if(LOG.isDebugEnabled()) {
					for (Map.Entry<String, Map<String, Variant<?>>> en : settingsMap.entrySet()) {
						LOG.debug("  " + en.getKey());
						for (Map.Entry<String, Variant<?>> en2 : en.getValue().entrySet()) {
							LOG.debug("    " + en2.getKey() + " = " + en2.getValue().getValue());
						}
					}
				}
				
				Map<String, Map<String, Variant<?>>> newSettingsMap = new HashMap<>(settingsMap);

				if (settingsMap.containsKey("ipv4")
						&& "manual".equals(settingsMap.get("ipv4").get("method").getValue())) {
					Map<String, Variant<?>> ipv4Map = new HashMap<>(settingsMap.get("ipv4"));
					ipv4Map.put("dns-search", new Variant<String[]>(IpUtil.filterNames(dns)));
					ipv4Map.put("dns",
							new Variant<UInt32[]>(Arrays.asList(IpUtil.filterIpV4Addresses(dns)).stream()
									.map((addr) -> ipv4AddressToUInt32(addr)).collect(Collectors.toList())
									.toArray(new UInt32[0])));
					newSettingsMap.put("ipv4", ipv4Map);
				}
				if (settingsMap.containsKey("ipv6")
						&& "manual".equals(settingsMap.get("ipv6").get("method").getValue())) {
					Map<String, Variant<?>> ipv6Map = new HashMap<>(settingsMap.get("ipv6"));
					ipv6Map.put("dns-search", new Variant<String[]>(IpUtil.filterNames(dns)));
					ipv6Map.put("dns",
							new Variant<Ipv6Address[]>(Arrays.asList(IpUtil.filterIpV6Addresses(dns)).stream()
									.map((addr) -> ipv6AddressToStruct(addr)).collect(Collectors.toList())
									.toArray(new Ipv6Address[0])));
					newSettingsMap.put("ipv6", ipv6Map);
				}

				settings.Update(newSettingsMap);
				settings.Save();
			}
		} catch (DBusException dbe) {
			throw new IOException("Failed to connect to system bus.", dbe);
		}
	}

	private UInt32 ipv4AddressToUInt32(String address) {
		Ipv4 ipv4 = Ipv4.of(address);
		int ipv4val = ipv4.asBigInteger().intValue();
		return new UInt32(Util.byteSwap(ipv4val));
	}

	private Ipv6Address ipv6AddressToStruct(String address) {
		/* TODO */
		throw new UnsupportedOperationException("TODO");
	}

	private void updateSystemd(String[] dns) throws IOException {
		try (DBusConnection conn = DBusConnection.getConnection(DBusBusType.SYSTEM)) {
			Resolve1Manager mgr = conn.getRemoteObject("org.freedesktop.resolve1", "/org/freedesktop/resolve1",
					Resolve1Manager.class);
			int index = getIndexForName(); // TODO
			if (dns == null) {
				LOG.info(String.format("Reverting DNS via SystemD. Index is %d", index));
				mgr.RevertLink(index);
			} else {
				LOG.info(String.format("Setting DNS via SystemD. Index is %d", index));
				mgr.SetLinkDNS(index, Arrays.asList(IpUtil.filterAddresses(dns)).stream()
						.map((addr) -> new Resolve1Manager.SetLinkDNSStruct(addr)).collect(Collectors.toList()));
				mgr.SetLinkDomains(index,
						Arrays.asList(IpUtil.filterNames(dns)).stream()
								.map((addr) -> new Resolve1Manager.SetLinkDomainsStruct(addr, false))
								.collect(Collectors.toList()));
			}

		} catch (DBusException dbe) {
			throw new IOException("Failed to connect to system bus.", dbe);
		}
	}

	private void updateResolvConf(String[] dns) throws IOException {
		ForkerBuilder b = new ForkerBuilder("resolvconf", "-a", getPlatform().resolvconfIfacePrefix() + getName(), "-m",
				"0", "-x");
		b.redirectErrorStream(true);
		b.io(IO.IO);
		b.effectiveUser(EffectiveUserFactory.getDefault().administrator());
		ForkerProcess p = b.start();
		try (PrintWriter pw = new PrintWriter(p.getOutputStream(), true)) {
			pw.println(String.format("nameserver %s", String.join(" ", dns)));
		}
		String res = IOUtils.toString(p.getInputStream(), "UTF-8");
		int v;
		try {
			v = p.waitFor();
		} catch (InterruptedException e) {
			throw new IOException(String.format("Failed to set DNS. %s", res), e);
		}
		if (StringUtils.isNotBlank(res) || v != 0)
			throw new IOException(String.format("Failed to set DNS. Exit %d. %s", v, res));
	}

	private void updateResolvDotConf(String[] dns) {
		synchronized (LinuxPlatformServiceImpl.lock) {
			List<String> headlines = new ArrayList<>();
			List<String> bodylines = new ArrayList<>();
			List<String> taillines = new ArrayList<>();
			List<String> dnslist = new ArrayList<>();
			File file = new File("/etc/resolv.conf");
			String line;
			int sidx = -1;
			int eidx = -1;
			Set<String> rowdns = new HashSet<>();
			try (BufferedReader r = new BufferedReader(new FileReader(file))) {
				int lineNo = 0;
				while ((line = r.readLine()) != null) {
					if (line.startsWith(START_HYPERSOCKET_WIREGUARD_RESOLVECONF)) {
						sidx = lineNo;
					} else if (line.startsWith(END_HYPERSOCKET_WIREGUARD_RESOLVCONF)) {
						eidx = lineNo;
					} else {
						line = line.trim();
						if (line.startsWith("nameserver")) {
							List<String> l = Arrays.asList(line.split("\\s+"));
							rowdns.addAll(l.subList(1, l.size()));
						}
						dnslist.addAll(rowdns);
						if (sidx != -1 && eidx == -1)
							bodylines.add(line);
						else {
							if (sidx == -1 && eidx == -1)
								headlines.add(line);
							else
								taillines.add(line);
						}
					}
					lineNo++;
				}
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to read resolv.conf", ioe);
			}

			File oldfile = new File("/etc/resolv.conf");
			oldfile.delete();

			if (file.renameTo(oldfile)) {
				LOG.info(String.format("Failed to backup resolv.conf by moving %s to %s", file, oldfile));
			}

			try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
				for (String l : headlines) {
					pw.println(l);
				}
				if (dns != null && dns.length > 0) {
					pw.println(START_HYPERSOCKET_WIREGUARD_RESOLVECONF);
					for (String d : dns) {
						if (!rowdns.contains(d))
							pw.println(String.format("nameserver %s", d));
					}
					pw.println(END_HYPERSOCKET_WIREGUARD_RESOLVCONF);
				}
				for (String l : taillines) {
					pw.println(l);
				}
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to write resolv.conf", ioe);
			}
		}
	}

	@Override
	public String getMac() {
		try {
			NetworkInterface iface = getByName(getName());
			return iface == null ? null : IpUtil.toIEEE802(iface.getHardwareAddress());
		} catch (IOException ioe) {
			return null;
		}
	}

	@Override
	public String getDisplayName() {
		try {
			NetworkInterface iface = getByName(getName());
			return iface == null ? "Unknown" : iface.getDisplayName();
		} catch (IOException ioe) {
			return "Unknown";
		}
	}

	public Set<String> getAddresses() {
		return addresses;
	}
}
