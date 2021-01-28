package com.logonbox.vpn.client.wireguard.windows;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;
import com.logonbox.vpn.client.wireguard.DNSIntegrationMethod;
import com.logonbox.vpn.client.wireguard.VirtualInetAddress;
import com.sshtools.forker.client.OSCommand;

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
			OSCommand.adminCommand(WindowsPlatformServiceImpl.getPrunsrv().toString(), "//DS",
					WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name);
		}
	}

	@Override
	public void down() throws IOException {
		synchronized (lock) {
			OSCommand.adminCommand(WindowsPlatformServiceImpl.getPrunsrv().toString(), "//SS",
					WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name);
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
				for (String line : OSCommand.adminCommandAndCaptureOutput("sc", "query",
						WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name)) {
					line = line.trim();
					if (line.startsWith("STATE") && line.endsWith("RUNNING"))
						return true;
				}
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to test service state.");
			}
		}
		return false;
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
				OSCommand.adminCommand("sc", "start",
						WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name);
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to test service state.");
			}
		}
	}

}
