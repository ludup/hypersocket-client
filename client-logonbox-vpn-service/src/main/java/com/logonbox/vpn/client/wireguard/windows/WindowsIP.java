package com.logonbox.vpn.client.wireguard.windows;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;
import com.logonbox.vpn.client.wireguard.DNSIntegrationMethod;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.sshtools.forker.services.Service;
import com.sshtools.forker.services.Services;

public class WindowsIP extends AbstractVirtualInetAddress implements VirtualInetAddress {
	enum IpAddressState {
		HEADER, IP, MAC
	}

	final static Logger LOG = LoggerFactory.getLogger(WindowsIP.class);

	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private WindowsPlatformServiceImpl platform;
	private Object lock = new Object();

	public WindowsIP(String name, WindowsPlatformServiceImpl platform) {
		this.platform = platform;
		this.name = name;
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

}