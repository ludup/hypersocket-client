package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.forker.client.OSCommand;

public class WindowsIP implements VirtualInetAddress {
	enum IpAddressState {
		HEADER, IP, MAC
	}

	public final static String TABLE_AUTO = "auto";
	public final static String TABLE_OFF = "off";

	final static Logger LOG = LoggerFactory.getLogger(WindowsIP.class);

//	private static final String END_HYPERSOCKET_WIREGUARD_RESOLVCONF = "###### END-HYPERSOCKET-WIREGUARD ######";
//	private static final String START_HYPERSOCKET_WIREGUARD_RESOLVECONF = "###### START-HYPERSOCKET-WIREGUARD ######";

	Set<String> addresses = new LinkedHashSet<>();

//	private boolean dnsSet;
	private int id;
	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private int mtu;
	private String name;
	private String peer;
	private WindowsPlatformServiceImpl platform;
	private String table = TABLE_AUTO;
	
	public WindowsIP(WindowsPlatformServiceImpl platform) {
		this.platform = platform;
	}

	public WindowsIP(String name, int id) {
		this.name = name;
		this.id = id;
	}

	@Override
	public void addAddress(String address) throws IOException {
		if (addresses.contains(address))
			throw new IllegalStateException(String.format("Interface %s already has address %s", name, address));
		if (addresses.size() > 0 && StringUtils.isNotBlank(peer))
			throw new IllegalStateException(String.format(
					"Interface %s is configured to have a single peer %s, so cannot add a second address %s", name,
					peer, address));

		if (StringUtils.isNotBlank(peer))
			OSCommand.adminCommand("ip", "address", "add", "dev", name, address, "peer", peer);
		else
			OSCommand.adminCommand("ip", "address", "add", "dev", name, address);
		addresses.add(address);
	}

	@Override
	public void delete() throws IOException {
		OSCommand.adminCommand("ip", "link", "del", "dev", getName());
	}

	@Override
	public void dns(String[] dns) throws IOException {
//		if (dns == null || dns.length == 0) {
//			if (dnsSet)
//				unsetDns();
//		} else {
//			DNSIntegrationMethod method = calcDnsMethod();
//			LOG.info(String.format("Setting DNS for %s (iface prefix %s) to %s using %s", name,
//					platform.resolvconfIfacePrefix(), String.join(", ", dns), method));
//			switch (method) {
//			case RESOLVCONF:
//				updateResolvConf(dns);
//				break;
//			case RAW:
//				updateResolvDotConf(dns);
//				break;
//			default:
//				/* TODO */
//				throw new UnsupportedOperationException();
//			}
//		}
	}

	@Override
	public void down() throws IOException {
//		if (dnsSet) {
//			unsetDns();
//		}
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
		delete();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		WindowsIP other = (WindowsIP) obj;
		if (addresses == null) {
			if (other.addresses != null)
				return false;
		} else if (!addresses.equals(other.addresses))
			return false;
		if (id != other.id)
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (peer == null) {
			if (other.peer != null)
				return false;
		} else if (!peer.equals(other.peer))
			return false;
		return true;
	}

	@Override
	public int getId() {
		return id;
	}

	@Override
	public int getMtu() {
		return mtu;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPeer() {
		return peer;
	}

	@Override
	public String getTable() {
		return table;
	}

	@Override
	public boolean hasAddress(String address) {
		return addresses.contains(address);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((addresses == null) ? 0 : addresses.hashCode());
		result = prime * result + id;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((peer == null) ? 0 : peer.hashCode());
		return result;
	}

	@Override
	public boolean isUp() {
		// TODO does mere prescence mean it's up?
		return true;
	}

	public DNSIntegrationMethod method() {
		return method;
	}

	public VirtualInetAddress method(DNSIntegrationMethod method) {
		this.method = method;
		return this;
	}

	@Override
	public void removeAddress(String address) throws IOException {
		if (!addresses.contains(address))
			throw new IllegalStateException(String.format("Interface %s not not have address %s", name, address));
		if (addresses.size() > 0 && StringUtils.isNotBlank(peer))
			throw new IllegalStateException(String.format(
					"Interface %s is configured to have a single peer %s, so cannot add a second address %s", name,
					peer, address));

		OSCommand.adminCommand("ip", "address", "del", address, "dev", getName());
		addresses.remove(address);
	}

	@Override
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
	public void setId(int id) {
		this.id = id;
	}

	@Override
	public void setMtu(int mtu) {
		this.mtu = mtu;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public void setPeer(String peer) {
		if (!Objects.equals(peer, this.peer)) {
			if (StringUtils.isNotBlank(peer) && addresses.size() > 1)
				throw new IllegalStateException(String.format(
						"Interface %s is already configured to have multiple addresses, so cannot have a single peer %s",
						name, peer));
			this.peer = peer;
		}
	}

	@Override
	public void setRoutes(Collection<String> allows) throws IOException {

		/* Remove all the current routes for this interface */
		for (String row : OSCommand.adminCommandAndCaptureOutput("ip", "route", "show", "dev", name)) {
			String[] l = row.split("\\s+");
			if (l.length > 0) {
				LOG.info(String.format("Removing route %s for %s", l[0], name));
				OSCommand.adminCommand("ip", "route", "del", l[0], "dev", name);
			}
		}

		for (String route : allows) {
			addRoute(route);
		}
	}

	@Override
	public void setTable(String table) {
		this.table = table;
	}

	@Override
	public String toString() {
		return "Ip [name=" + name + ", id=" + id + ", addresses=" + addresses + ", peer=" + peer + "]";
	}

	@Override
	public void up() throws IOException {
		if (mtu > 0) {
			OSCommand.adminCommand("ip", "link", "set", "mtu", String.valueOf(mtu), "up", "dev", getName());
		} else {
			/*
			 * First detect MTU, then bring up. First try from existing Wireguard
			 * connections?
			 */
			OSCommand.elevate();
			try {
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
				OSCommand.runCommand("ip", "link", "set", "mtu", String.valueOf(tmtu), "up", "dev", getName());
			} finally {
				OSCommand.restrict();
			}
		}
	}

	private void addDefault(String route) {
		throw new UnsupportedOperationException("Not yet implemented.");
	}

	private void addRoute(String route) throws IOException {
		String proto = "-4";
		if (route.equals("*:*"))
			proto = "-6";
		if (TABLE_OFF.equals(table))
			return;
		if (!TABLE_AUTO.equals(table)) {
			OSCommand.adminCommand("ip", proto, "route", "add", route, "dev", name, "table", table);
		} else if ("*/0".equals(route)) {
			addDefault(route);
		} else {
			try {
				String res = OSCommand
						.adminCommandAndCaptureOutput("ip", proto, "route", "show", "dev", name, "match", route)
						.iterator().next();
				if (StringUtils.isNotBlank(res)) {
					// Already have
					return;
				}
			} catch (Exception e) {
			}
			LOG.info(String.format("Adding route %s to %s for %s", route, name, proto));
			OSCommand.adminCommand("ip", proto, "route", "add", route, "dev", name);
		}
	}

//	private DNSIntegrationMethod calcDnsMethod() {
//		if (method == DNSIntegrationMethod.AUTO) {
//			File f = new File("/etc/resolv.conf");
//			try {
//				String p = f.getCanonicalFile().getAbsolutePath();
//				if (p.equals(f.getAbsolutePath())) {
//					return DNSIntegrationMethod.RAW;
//				} else if (p.equals("/run/NetworkManager/resolv.conf")) {
//					return DNSIntegrationMethod.NETWORK_MANAGER;
//				} else if (p.equals("/run/resolvconf/resolv.conf")) {
//					return DNSIntegrationMethod.RESOLVCONF;
//				}
//			} catch (IOException ioe) {
//			}
//			return DNSIntegrationMethod.RAW;
//		} else
//			return method;
//	}

//	private void unsetDns() throws IOException {
//		try {
//			LOG.info(
//					String.format("Insetting DNS for %s (iface prefix %s)", name, platform.resolvconfIfacePrefix()));
//			switch (calcDnsMethod()) {
//			case RESOLVCONF:
//				OSCommand.adminCommand("resolvconf", "-d", platform.resolvconfIfacePrefix() + name, "-f");
//				break;
//			case RAW:
//				updateResolvDotConf(new String[0]);
//				break;
//			default:
//				throw new UnsupportedOperationException();
//			}
//		} finally {
//			dnsSet = false;
//		}
//	}
//
//	private void updateResolvConf(String[] dns) throws IOException {
//		ForkerBuilder b = new ForkerBuilder("resolvconf", "-a", platform.resolvconfIfacePrefix() + name, "-m", "0",
//				"-x");
//		b.redirectErrorStream(true);
//		b.io(IO.IO);
//		b.effectiveUser(EffectiveUserFactory.getDefault().administrator());
//		ForkerProcess p = b.start();
//		try (PrintWriter pw = new PrintWriter(p.getOutputStream(), true)) {
//			pw.println(String.format("nameserver %s", String.join(" ", dns)));
//		}
//		String res = IOUtils.toString(p.getInputStream(), "UTF-8");
//		int v;
//		try {
//			v = p.waitFor();
//		} catch (InterruptedException e) {
//			throw new IOException(String.format("Failed to set DNS. %s", res), e);
//		}
//		if (StringUtils.isNotBlank(res) || v != 0)
//			throw new IOException(String.format("Failed to set DNS. Exit %d. %s", v, res));
//		dnsSet = true;
//	}

//	private void updateResolvDotConf(String[] dns) {
//		synchronized (WindowsPlatformServiceImpl.lock) {
//			List<String> headlines = new ArrayList<>();
//			List<String> bodylines = new ArrayList<>();
//			List<String> taillines = new ArrayList<>();
//			List<String> dnslist = new ArrayList<>();
//			File file = new File("/etc/resolv.conf");
//			String line;
//			int sidx = -1;
//			int eidx = -1;
//			Set<String> rowdns = new HashSet<>();
//			try (BufferedReader r = new BufferedReader(new FileReader(file))) {
//				int lineNo = 0;
//				while ((line = r.readLine()) != null) {
//					if (line.startsWith(START_HYPERSOCKET_WIREGUARD_RESOLVECONF)) {
//						sidx = lineNo;
//					} else if (line.startsWith(END_HYPERSOCKET_WIREGUARD_RESOLVCONF)) {
//						eidx = lineNo;
//					} else {
//						line = line.trim();
//						if (line.startsWith("nameserver")) {
//							List<String> l = Arrays.asList(line.split("\\s+"));
//							rowdns.addAll(l.subList(1, l.size()));
//						}
//						dnslist.addAll(rowdns);
//						if (sidx != -1 && eidx == -1)
//							bodylines.add(line);
//						else {
//							if (sidx == -1 && eidx == -1)
//								headlines.add(line);
//							else
//								taillines.add(line);
//						}
//					}
//					lineNo++;
//				}
//			} catch (IOException ioe) {
//				throw new IllegalStateException("Failed to read resolv.conf", ioe);
//			}
//
//			File oldfile = new File("/etc/resolv.conf");
//			oldfile.delete();
//
//			if (file.renameTo(oldfile)) {
//				LOG.info(String.format("Failed to backup resolv.conf by moving %s to %s", file, oldfile));
//			}
//
//			try (PrintWriter pw = new PrintWriter(new FileWriter(file, true))) {
//				for (String l : headlines) {
//					pw.println(l);
//				}
//				if (dns.length > 0) {
//					pw.println(START_HYPERSOCKET_WIREGUARD_RESOLVECONF);
//					for (String d : dns) {
//						if (!rowdns.contains(d))
//							pw.println(String.format("nameserver %s", d));
//					}
//					pw.println(END_HYPERSOCKET_WIREGUARD_RESOLVCONF);
//				}
//				for (String l : taillines) {
//					pw.println(l);
//				}
//			} catch (IOException ioe) {
//				throw new IllegalStateException("Failed to write resolv.conf", ioe);
//			}
//		}
//	}

}
