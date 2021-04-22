package com.logonbox.vpn.client.wireguard.windows;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
import com.sshtools.forker.services.Service;
import com.sshtools.forker.services.Services;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class WindowsIP extends AbstractVirtualInetAddress implements VirtualInetAddress {
	enum IpAddressState {
		HEADER, IP, MAC
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsIP.class);

	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private WindowsPlatformServiceImpl platform;
	private Object lock = new Object();
	private String displayName;
	private Set<String> domainsAdded = new LinkedHashSet<String>();
	
	public WindowsIP(String name, String displayName, WindowsPlatformServiceImpl platform) {
		this.platform = platform;
		this.name = name;
		this.displayName = displayName;
	}

	@Override
	public void delete() throws IOException {
		synchronized (lock) {
			if (isUp()) {
				down();
			}
			platform.uninstall(getServiceName());
		}
	}

	@Override
	public void down() throws IOException {
		synchronized (lock) {
			try {

				String currentDomains = Advapi32Util.registryGetStringValue
		                (WinReg.HKEY_LOCAL_MACHINE,
		                        "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList");
				Set<String> currentDomainList = new LinkedHashSet<>(StringUtils.isBlank(currentDomains) ? Collections.emptySet() : Arrays.asList(currentDomains));
				for(String dnsName : domainsAdded) {
					LOG.info(String.format("Removing domain %s from search", dnsName));
					currentDomainList.remove(dnsName);
				}
				String newDomains = String.join(",", currentDomainList);
				if(!Objects.equals(currentDomains, newDomains)) {
					LOG.info(String.format("Final domain search %s", newDomains));
					Advapi32Util.registrySetStringValue
		            (WinReg.HKEY_LOCAL_MACHINE,
		                    "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList", newDomains);
				}
				Services.get().stopService(getService());
			} catch (IOException ioe) {
				throw ioe;
			} catch (Exception e) {
				throw new IOException("Failed to take interface down.", e);
			}
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((peer == null) ? 0 : peer.hashCode());
		return result;
	}

	@Override
	public boolean isUp() {
		synchronized (lock) {
			try {
				return getService().getStatus().isRunning();
			} catch (IOException e) {
				return false;
			}
		}
	}

	protected Service getService() throws IOException {
		Service service = Services.get().getService(getServiceName());
		if (service == null)
			throw new IOException(String.format("No service for interface %s.", name));
		return service;
	}

	protected String getServiceName() {
		return WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name;
	}

	public boolean isInstalled() {
		synchronized (lock) {
			try {
				getService();
				return true;
			} catch (IOException ioe) {
				return false;
			}
		}
	}

	public DNSIntegrationMethod method() {
		return method;
	}

	public VirtualInetAddress method(DNSIntegrationMethod method) {
		this.method = method;
		return this;
	}

	@Override
	public String toString() {
		return "Ip [name=" + name + ", peer=" + peer + "]";
	}

	@Override
	public void up() throws IOException {
		synchronized (lock) {
			try {
				Services.get().startService(getService());
			} catch (IOException e) {
				throw e;
			} catch (Exception e) {
				throw new IOException("Failed to bring up interface service.", e);
			}
		}
	}

	@Override
	public String getMac() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public void dns(String[] dns) throws IOException {
		String[] dnsAddresses = IpUtil.filterAddresses(dns);
		if(dnsAddresses.length > 2) {
			LOG.warn("Windows only supports a maximum of 2 DNS servers. %d were supplied, the last %d will be ignored.", dnsAddresses.length, dnsAddresses.length - 2);
		}
		if(dnsAddresses.length > 1) {
			OSCommand.adminCommand("netsh", "interface", "ipv4", "set", "dnsservers", name, "static", dnsAddresses[0], "secondary");	
		} 
		else if(dnsAddresses.length < 2) {
			OSCommand.adminCommand("netsh", "interface", "ipv4", "set", "dnsservers", name, "static", "none", "secondary");	
		}
		if(dnsAddresses.length > 0) {
			OSCommand.adminCommand("netsh", "interface", "ipv4", "set", "dnsservers", name, "static", dnsAddresses[0], "primary");	
		} 
		else if(dnsAddresses.length < 1) {
			OSCommand.adminCommand("netsh", "interface", "ipv4", "set", "dnsservers", name, "static", "none", "primary");	
		}

		String[] dnsNames = IpUtil.filterNames(dns);
		String currentDomains = null;
		try {
			currentDomains = Advapi32Util.registryGetStringValue
	                (WinReg.HKEY_LOCAL_MACHINE,
	                        "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList");
		}
		catch(Exception e) {
			//
		}
		Set<String> newDomainList = new LinkedHashSet<>(StringUtils.isBlank(currentDomains) ? Collections.emptySet() : Arrays.asList(currentDomains));
		for(String dnsName : dnsNames) {
			if(!newDomainList.contains(dnsName)) {
				LOG.info(String.format("Adding domain %s to search", dnsName));
				newDomainList.add(dnsName);
			}
		}
		String newDomains = String.join(",", newDomainList);
		if(!Objects.equals(currentDomains, newDomains)) {
			domainsAdded.clear();
			domainsAdded.addAll(newDomainList);
			LOG.info(String.format("Final domain search %s", newDomains));
			Advapi32Util.registrySetStringValue
            (WinReg.HKEY_LOCAL_MACHINE,
                    "System\\CurrentControlSet\\Services\\TCPIP\\Parameters", "SearchList", newDomains);
		}
	}

}
