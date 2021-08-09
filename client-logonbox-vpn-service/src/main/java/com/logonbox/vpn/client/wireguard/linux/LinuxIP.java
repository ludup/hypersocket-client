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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.dbus.Resolve1Manager;
import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;
import com.logonbox.vpn.client.wireguard.DNSIntegrationMethod;
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.sshtools.forker.client.EffectiveUserFactory;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.common.IO;

public class LinuxIP extends AbstractVirtualInetAddress {
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
	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private LinuxPlatformServiceImpl platform;

	public LinuxIP(String name, LinuxPlatformServiceImpl platform) {		
		super(name);
		this.platform = platform;
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
					platform.resolvconfIfacePrefix(), String.join(", ", dns), method));
			switch (method) {
			case RESOLVCONF:
				updateResolvConf(dns);
				break;
			case SYSTEMD:
				updateSystemd(dns);
				break;
			case RAW:
				updateResolvDotConf(dns);
				break;
			default:
				/* TODO */
				throw new UnsupportedOperationException();
			}
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

	public DNSIntegrationMethod method() {
		return method;
	}

	public VirtualInetAddress method(DNSIntegrationMethod method) {
		this.method = method;
		return this;
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
		for(String line : OSCommand.runCommandAndCaptureOutput("ip", "addr")) {
			line = line.trim();
			String[] args = line.split(":");
			if(args.length > 1) {
				try {
					int idx = Integer.parseInt(args[0].trim());
					if(args[1].trim().equals(getName()))
						return idx;
				}
				catch(Exception e) {
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

	private DNSIntegrationMethod calcDnsMethod() {
		if (method == DNSIntegrationMethod.AUTO) {
			File f = new File("/etc/resolv.conf");
			try {
				String p = f.getCanonicalFile().getAbsolutePath();
				if (p.equals(f.getAbsolutePath())) {
					return DNSIntegrationMethod.RAW;
				} else if (p.equals("/run/NetworkManager/resolv.conf")) {
					return DNSIntegrationMethod.NETWORK_MANAGER;
				} else if (p.equals("/run/systemd/resolve/stub-resolv.conf")) {
					return DNSIntegrationMethod.SYSTEMD;
				} else if (p.equals("/run/resolvconf/resolv.conf")) {
					return DNSIntegrationMethod.RESOLVCONF;
				}
			} catch (IOException ioe) {
			}
			return DNSIntegrationMethod.RAW;
		} else
			return method;
	}

	private void unsetDns() throws IOException {
		try {
			LOG.info(String.format("unsetting DNS for %s (iface prefix %s)", getName(), platform.resolvconfIfacePrefix()));
			switch (calcDnsMethod()) {
			case RESOLVCONF:
				OSCommand.adminCommand("resolvconf", "-d", platform.resolvconfIfacePrefix() + getName(), "-f");
				break;
			case SYSTEMD:
				updateSystemd(null);
				break;
			case RAW:
				updateResolvDotConf(null);
				break;
			default:
				throw new UnsupportedOperationException();
			}
		} finally {
			dnsSet = false;
		}
	}
	
	private void updateSystemd(String[] dns) throws IOException {
		try(
			DBusConnection conn = DBusConnection.getConnection(DBusBusType.SYSTEM)) {
			Resolve1Manager mgr = conn.getRemoteObject("org.freedesktop.resolve1", "/org/freedesktop/resolve1", Resolve1Manager.class);
			int index = getIndexForName(); // TODO
			if(dns == null)
				mgr.RevertLink(index);
			else {
				mgr.SetLinkDNS(index, Arrays.asList(IpUtil.filterAddresses(dns)).stream().map((addr) -> new Resolve1Manager.SetLinkDNSStruct(addr)).collect(Collectors.toList()));
				mgr.SetLinkDomains(index, Arrays.asList(IpUtil.filterNames(dns)).stream().map((addr) -> new Resolve1Manager.SetLinkDomainsStruct(addr, true)).collect(Collectors.toList()));
			}
			
		} catch(DBusException dbe) {
			throw new IOException("Failed to connect to system bus.", dbe);
		}
	}

	private void updateResolvConf(String[] dns) throws IOException {
		ForkerBuilder b = new ForkerBuilder("resolvconf", "-a", platform.resolvconfIfacePrefix() + getName(), "-m", "0",
				"-x");
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
		dnsSet = true;
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
