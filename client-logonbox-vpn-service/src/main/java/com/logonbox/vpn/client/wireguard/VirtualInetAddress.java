package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.apache.commons.lang3.SystemUtils;

public interface VirtualInetAddress {

	boolean isUp();

	/**
	 * Entirely disconnect and delete the interface.
	 * 
	 * @throws on any I/O error
	 */
	void delete() throws IOException;

	void down() throws IOException;

	default String getMac() {
		try {
			NetworkInterface iface = getByName(getName());
			return iface == null ? null : IpUtil.toIEEE802(iface.getHardwareAddress());
		} catch (IOException ioe) {
			return null;
		}
	}

	default NetworkInterface getByName(String name) throws IOException {
		/* NOTE: This is pretty much useless  to lookup the network by the 
		 * name we know it as on Windows, as for some bizarre reason,
		 * net8 for example (as would show ip "ipconfig /all") comes back 
		 * here as net7!
		 */
		if(SystemUtils.IS_OS_WINDOWS)
			throw new UnsupportedOperationException("Do not use this on Windows.");
		
		for(Enumeration<NetworkInterface> nifEnum = NetworkInterface.getNetworkInterfaces(); nifEnum.hasMoreElements(); ) {
			NetworkInterface nif = nifEnum.nextElement();
			if(nif.getName().equals(name))
				return nif;
		}
		return null;
	}

	int getMtu();

	String getName();

	String getPeer();

	String getTable();

	void setMtu(int mtu);

	void setName(String name);

	void setPeer(String peer);

	void setTable(String table);

	void up() throws IOException;

}