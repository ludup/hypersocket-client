package com.logonbox.vpn.client.wireguard.osx;

import java.io.IOException;
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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;
import com.logonbox.vpn.client.wireguard.DNSIntegrationMethod;
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.sshtools.forker.client.OSCommand;

public class BrewOSXIP extends AbstractVirtualInetAddress {
	enum IpAddressState {
		HEADER, IP, MAC
	}

	public final static String TABLE_AUTO = "auto";
	public final static String TABLE_MAIN = "main";
	public final static String TABLE_OFF = "off";

	final static Logger LOG = LoggerFactory.getLogger(BrewOSXIP.class);

	private Set<String> addresses = new LinkedHashSet<>();
	private boolean autoRoute4;
	private boolean autoRoute6;
	private boolean dnsSet;
	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private BrewOSXPlatformServiceImpl platform;
	private Map<String, String> serviceDns = new HashMap<>();
	private Map<String, String> serviceDnsSearch = new HashMap<>();

	public BrewOSXIP(String name, BrewOSXPlatformServiceImpl platform) {		
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

		if(address.matches(".*:.*"))
			OSCommand.adminCommand("ifconfig", getName(), "inet6", address, "alias");
		else
			OSCommand.adminCommand("ifconfig", getName(), "inet", address, address.replace("/*", ""), "alias");
		addresses.add(address);
	}

	@Override
	public void delete() throws IOException {
		OSCommand.adminCommand("rm", "-f", "/var/run/wireguard/" + getName() + ".sock");
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
			case NETWORKSETUP:
				collectNewServiceDns();
				String[] dnsAddresses = IpUtil.filterAddresses(dns);
				String[] dnsSearchDomains = IpUtil.filterNames(dns);
				for(Map.Entry<String,String> serviceEn : new HashMap<>(serviceDns).entrySet()) {
					checkForError(OSCommand.runCommandAndCaptureOutput("networksetup", "-setdnsservers", serviceEn.getKey(), String.join(" ", dnsAddresses)));
					dnsSet = true;
					if(dnsSearchDomains.length > 0) {
						checkForError(OSCommand.runCommandAndCaptureOutput("networksetup", "-setsearchdomains", serviceEn.getKey(), "Empty"));
					}
					else {
						checkForError(OSCommand.runCommandAndCaptureOutput("networksetup", "-setsearchdomains", serviceEn.getKey(), String.join(" ", dnsSearchDomains)));
					}
				}
				break;
			default:
				throw new UnsupportedOperationException(String.format("DNS integration method %s not supported.", method));
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

	public Set<String> getAddresses() {
		return addresses;
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

	@Override
	public String getMac() {
		try {
			NetworkInterface iface = getByName(getName());
			return iface == null ? null : IpUtil.toIEEE802(iface.getHardwareAddress());
		} catch (IOException ioe) {
			return null;
		}
	}

	public boolean hasAddress(String address) {
		return addresses.contains(address);
	}

	public boolean isAutoRoute4() {
		return autoRoute4;
	}

	public boolean isAutoRoute6() {
		return autoRoute6;
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

		OSCommand.adminCommand("ifconfig", getName(), "-alias", address);
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

	public void setEndpointDirectRoute() {
		// TODO
		LOG.warn("TODO: setEndpointDirectRoute() not implemented.");
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
		boolean ipv6 = false;
		for (String row : OSCommand.adminCommandAndCaptureOutput("netstat", "-nr")) {
			String[] l = row.trim().split("\\s+");
			if(l[0].equals("Destination") || l[0].equals("Routing"))
				continue;
			if(l.length > 0 && l[0].equals("Internet6:")) {
				ipv6 = true;
			}
			else if (l.length > 3 && l[3].equals(getName())) {
				String gateway = l[1];
				if(gateway.equals(getName())) {
					if(getAddresses().isEmpty())
						continue;
					else
						gateway = getAddresses().iterator().next();
				}
				LOG.info(String.format("Removing route %s %s for %s", l[0], gateway, getName()));
				if(ipv6) {
					OSCommand.adminCommand("route", "-qn", "delete", "-inet6", "-ifp", getName(), l[0], gateway);
				}
				else {
					OSCommand.adminCommand("route", "-qn", "delete", "-ifp", getName(), l[0], gateway);
				}
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
		setMtu();
		

		OSCommand.adminCommand("ifconfig", getName(), "up");
	}

	protected void setMtu() throws IOException {
	
		int currentMtu = 0;
		for(String line : OSCommand.runCommandAndCaptureOutput("ifconfig", getName())) {
			List<String> parts = Arrays.asList(line.split("\\s+"));
			int idx = parts.indexOf("mtu");
			if(idx == -1 && idx < parts.size() - 1)
				LOG.warn("Could not find MTU on vpn interface");
			else
				currentMtu = Integer.parseInt(parts.get(idx + 1));
			break;
		}

		int tmtu = 0;
		if (getMtu() > 0) {
			tmtu = getMtu();
		} else {
			String defaultIf = null;
			for(String line : OSCommand.runCommandAndCaptureOutput("netstat", "-nr", "-f", "inet")) {
				String[] arr = line.split("\\s+");
				if(arr[0].equals("default")) {
					defaultIf = arr[3];
					break;
				}
			}
			if(StringUtils.isBlank(defaultIf))
				LOG.warn("Could not determine default interface to get MTU from.");
			else {
				for(String line : OSCommand.runCommandAndCaptureOutput("ifconfig", defaultIf)) {
					List<String> parts = Arrays.asList(line.split("\\s+"));
					int idx = parts.indexOf("mtu");
					if(idx == -1 && idx < parts.size() - 1)
						LOG.warn("Could not find MTU on default interface");
					else
						tmtu = Integer.parseInt(parts.get(idx + 1));
					break;
				}
			}
			
			/* Still not found, use generic default */
			if (tmtu == 0)
				tmtu = 1500;

			/* Subtract 80, because .. */
			tmtu -= 80;
		}

		/* Bring it up! */
		if(currentMtu > 0 && tmtu != currentMtu) {
			LOG.info(String.format("Setting MTU to %d", tmtu));
			OSCommand.adminCommand("ifconfig", getName(), "mtu", String.valueOf(tmtu));
		}
		else
			LOG.info(String.format("MTU already set to %d", tmtu));
	}
	
	private void addRoute(String route) throws IOException {
		String proto = "inet";
		if (route.matches(".*:.*"))
			proto = "inet6";
		if (TABLE_OFF.equals(getTable()))
			return;
		
		
		if(route.endsWith("/0") && (StringUtils.isBlank(getTable()) || TABLE_AUTO.equals(getTable()))) {
			if(route.matches(".*:.*")) {
				autoRoute6 = true;
				OSCommand.adminCommand("route", "-q", "-n", "add", "-inet6", "::/1:", "-interface", getName());
				OSCommand.adminCommand("route", "-q", "-m", "add", "-inet6", "8000::/1", "-interface", getName());
			}
			else {
				autoRoute4 = true;
				OSCommand.adminCommand("route", "-q", "-n", "add", "-inet", "0.0.0.0/1", "-interface", getName());
				OSCommand.adminCommand("route", "-q", "-m", "add", "-inet", "128.0.0.1/1", "-interface", getName());
			}
		}
		else {
			if(!TABLE_MAIN.equals(getTable()) && !TABLE_AUTO.equals(getTable()) && !StringUtils.isBlank(getTable())) {
				throw new IOException("Darwin only supports TABLE=auto|main|off");
			}

			for(String line : OSCommand.runCommandAndCaptureOutput("route", "-n", "get", "-" + proto,  route)) {
				line = line.trim();
				String[] args = line.split(":");
				if(args.length > 1 && args[0].equals("interface:") && args[1].equals(getName())) {
					// Already have route
					return;
				}
			}
			
			
			LOG.info(String.format("Adding route %s to %s for %s", route, getName(), proto));
			OSCommand.adminCommand("route", "-q", "-n", "add", "-" + proto, route, "-interface", getName());
		}
		
	}

	private DNSIntegrationMethod calcDnsMethod() {
		if (method == DNSIntegrationMethod.AUTO) {
			return DNSIntegrationMethod.NETWORKSETUP;
		} else
			return method;
	}

	private void checkForError(Iterable<String> output) throws IOException {
		for(String line : output) {
			if(line.contains("Error"))
				throw new IOException(line);
		}
	}

	private void collectNewServiceDns() throws IOException {
		Set<String> foundServices = new HashSet<>();
		for(String service : OSCommand.runCommandAndCaptureOutput("networksetup", "-listallnetworkservices")) {
			if(service.startsWith("*")) {
				service = service.substring(1);
			}
			else if(service.startsWith("An asterisk")) {
				continue;
			}
			foundServices.add(service);
			if(serviceDns.containsKey(service)) {
				/* Already have */
				continue;
			}
			
			for(String out : OSCommand.runCommandAndCaptureOutput("networksetup", "-getdnsservers", service)) {
				if(out.indexOf(' ') != -1) {
					/* Multi-word message indicating no Dns servers */
					break;
				}
				else {
					serviceDns.put(service, out);
				}
 			}
			
			for(String out : OSCommand.runCommandAndCaptureOutput("networksetup", "-getsearchdomains", service)) {
				if(out.indexOf(' ') != -1) {
					/* Multi-word message indicating no Dns servers */
					break;
				}
				else {
					serviceDnsSearch.put(service, out);
				}
 			}
		}
		
		/* Remove anything that doesn't exist */
		for(Map.Entry<String,String> serviceEn : new HashMap<>(serviceDns).entrySet()) {
			if(!foundServices.contains(serviceEn.getKey())) {
				serviceDns.remove(serviceEn.getKey());
				serviceDnsSearch.remove(serviceEn.getKey());
			}
		}
	}

	private void unsetDns() throws IOException {
		try {
			LOG.info(String.format("unsetting DNS for %s (iface prefix %s)", getName(), platform.resolvconfIfacePrefix()));
			switch (calcDnsMethod()) {
			case NETWORKSETUP:
				for(Map.Entry<String,String> serviceEn : new HashMap<>(serviceDns).entrySet()) {
					checkForError(OSCommand.runCommandAndCaptureOutput("networksetup", "-setdnsservers", serviceEn.getKey(), serviceEn.getValue()));
					checkForError(OSCommand.runCommandAndCaptureOutput("networksetup", "-setsearchdomains", serviceEn.getKey(), serviceDnsSearch.get(serviceEn.getKey())));
				}
				break;
			default:
				throw new UnsupportedOperationException();
			}
		} finally {
			dnsSet = false;
		}
	}
}
