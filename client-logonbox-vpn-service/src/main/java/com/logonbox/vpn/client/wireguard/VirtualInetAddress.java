package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.util.Collection;

public interface VirtualInetAddress {

	boolean isUp();

	void addAddress(String address) throws IOException;

	void setRoutes(Collection<String> allows) throws IOException;

	void delete() throws IOException;

	void dns(String[] dns) throws IOException;

	void down() throws IOException;

	int getId();

	String getMac();

	int getMtu();

	String getName();

	String getPeer();

	String getTable();

	boolean hasAddress(String address);

	void removeAddress(String address) throws IOException;

	void setAddresses(String... addresses);

	void setId(int id);

	void setMac(String mac);

	void setMtu(int mtu);

	void setName(String name);

	void setPeer(String peer);

	void setTable(String table);

	void up() throws IOException;

}