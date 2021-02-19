package com.logonbox.vpn.client.wireguard;

import java.util.Objects;

import com.logonbox.vpn.client.wireguard.windows.WindowsIP;

public abstract class AbstractVirtualInetAddress implements VirtualInetAddress {

	public final static String TABLE_AUTO = "auto";
	public final static String TABLE_OFF = "off";
	
	private int mtu;
	protected String name;
	protected String peer;
	private String table = TABLE_AUTO;

	public AbstractVirtualInetAddress() {
		super();
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
			this.peer = peer;
		}
	}

	@Override
	public void setTable(String table) {
		this.table = table;
	}

}