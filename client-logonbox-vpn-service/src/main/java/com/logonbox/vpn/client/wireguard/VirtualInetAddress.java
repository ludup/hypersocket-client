package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.NetworkInterface;

public interface VirtualInetAddress {

	boolean isUp();

	void delete() throws IOException;

	void down() throws IOException;

	int getId();

	default String getMac() {
		try {
			NetworkInterface iface = NetworkInterface.getByName(getName());
			return iface == null ? null : IpUtil.toIEEE802(iface.getHardwareAddress());
		} catch (IOException ioe) {
			return null;
		}
	}

	int getMtu();

	String getName();

	String getPeer();

	String getTable();

	void setId(int id);

	void setMtu(int mtu);

	void setName(String name);

	void setPeer(String peer);

	void setTable(String table);

	void up() throws IOException;

}