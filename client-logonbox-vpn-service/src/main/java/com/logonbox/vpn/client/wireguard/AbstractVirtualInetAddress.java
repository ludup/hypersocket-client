package com.logonbox.vpn.client.wireguard;

import java.util.Objects;

import com.logonbox.vpn.common.client.DNSIntegrationMethod;

public abstract class AbstractVirtualInetAddress<P extends PlatformService<?>> implements VirtualInetAddress<P> {

	public final static String TABLE_AUTO = "auto";
	public final static String TABLE_OFF = "off";
	
	private int mtu;
	private String name;
	private String peer;
	private String table = TABLE_AUTO;
	private DNSIntegrationMethod method = DNSIntegrationMethod.AUTO;
	private P platform;

	public AbstractVirtualInetAddress(P platform) {
		super();
		this.platform = platform;
	}

	public AbstractVirtualInetAddress(P platform, String name) {
		super();
		this.name = name;
		this.platform = platform;
	}

	@Override
	public P getPlatform() {
		return platform;
	}
	

	@Override
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		return result;
	}

	@Override
	public final boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractVirtualInetAddress<?> other = (AbstractVirtualInetAddress<?>) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

	@Override
	public final int getMtu() {
		return mtu;
	}

	@Override
	public final String getName() {
		return name;
	}

	@Override
	public final String getPeer() {
		return peer;
	}

	@Override
	public final String getTable() {
		return table;
	}

	@Override
	public final void setMtu(int mtu) {
		this.mtu = mtu;
	}

	@Override
	public final void setName(String name) {
		this.name = name;
	}

	@Override
	public void setPeer(String peer) {
		if (!Objects.equals(peer, this.peer)) {
			this.peer = peer;
		}
	}

	@Override
	public final void setTable(String table) {
		this.table = table;
	}


	@Override
	public final DNSIntegrationMethod method() {
		return method;
	}

	@Override
	public final VirtualInetAddress<P> method(DNSIntegrationMethod method) {
		this.method = method;
		return this;
	}

	protected final DNSIntegrationMethod calcDnsMethod() {
		if (method() == DNSIntegrationMethod.AUTO) {
			return platform.dnsMethod();
		} else
			return method();
	}
}