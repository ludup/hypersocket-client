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
import com.logonbox.vpn.client.wireguard.IpUtil;
import com.logonbox.vpn.client.wireguard.OsUtil;
import com.logonbox.vpn.common.client.DNSIntegrationMethod;
import com.sshtools.forker.client.OSCommand;
import com.sshtools.forker.services.Service;
import com.sshtools.forker.services.Services;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

public class WindowsIP extends AbstractVirtualInetAddress<WindowsPlatformServiceImpl> {
	enum IpAddressState {
		HEADER, IP, MAC
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsIP.class);

	private Object lock = new Object();
	private String displayName;
	private Set<String> domainsAdded = new LinkedHashSet<String>();
	
	public WindowsIP(String name, String displayName, WindowsPlatformServiceImpl platform) {
		super(platform, name); 
		this.displayName = displayName;
	}

	@Override
	public void delete() throws IOException {
		synchronized (lock) {
			if (isUp()) {
				down();
			}
			getPlatform().uninstall(getServiceName());
		}
	}

	@Override
	public void down() throws IOException {
		synchronized (lock) {
			try {
				unsetDns();
				Services.get().stopService(getService());
			} catch (IOException ioe) {
				throw ioe;
			} catch (Exception e) {
				throw new IOException("Failed to take interface down.", e);
			}
		}
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
			throw new IOException(String.format("No service for interface %s.", getName()));
		return service;
	}

	protected String getServiceName() {
		return WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + getName();
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

	@Override
	public String toString() {
		return "Ip [name=" + getName() + ", peer=" + getPeer() + "]";
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
		if (dns == null || dns.length == 0) {
			unsetDns();
		} else {
			DNSIntegrationMethod method = calcDnsMethod();
			try {
				LOG.info(String.format("Setting DNS for %s to %s using %s", getName(),
						String.join(", ", dns), method));
				switch (method) {
				case NETSH:
					/* Ipv4 */
					String[] dnsAddresses = IpUtil.filterIpV4Addresses(dns);
					if(dnsAddresses.length > 2) {
						LOG.warn("Windows only supports a maximum of 2 DNS servers. %d were supplied, the last %d will be ignored.", dnsAddresses.length, dnsAddresses.length - 2);
					}

					OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv4", "delete", "dnsservers", getName(), "all"));
					if(dnsAddresses.length > 0) {
						OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv4", "add", "dnsserver", getName(), dnsAddresses[0], "index=1", "no"));	
					} 
					if(dnsAddresses.length > 1) {
						OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv4", "add", "dnsserver", getName(), dnsAddresses[1], "index=2", "no"));	
					} 

					/* Ipv6 */
					dnsAddresses = IpUtil.filterIpV6Addresses(dns);
					if(dnsAddresses.length > 2) {
						LOG.warn("Windows only supports a maximum of 2 DNS servers. %d were supplied, the last %d will be ignored.", dnsAddresses.length, dnsAddresses.length - 2);
					}

					OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv6", "delete", "dnsservers", getName(), "all"));
					if(dnsAddresses.length > 0) {
						OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv6", "add", "dnsserver", getName(), dnsAddresses[0], "index=1", "no"));	
					} 
					if(dnsAddresses.length > 1) {
						OSCommand.adminCommand(OsUtil.debugCommandArgs("netsh", "interface", "ipv6", "add", "dnsserver", getName(), dnsAddresses[1], "index=2", "no"));	
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
					Set<String> newDomainList = new LinkedHashSet<>(StringUtils.isBlank(currentDomains) ? Collections.emptySet() : Arrays.asList(currentDomains.split(",")));
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
					break;
				case NONE:
					break;
				default:
					throw new UnsupportedOperationException(String.format("DNS integration method %s not supported.", method));
				}
			}
			finally {
				LOG.info("Done setting DNS");
			}
		}
		
	}

	private void unsetDns() {
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
	}
}
