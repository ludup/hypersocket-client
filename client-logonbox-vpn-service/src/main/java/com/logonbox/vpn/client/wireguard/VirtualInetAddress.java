package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.NetworkInterface;
import java.util.Enumeration;

import org.apache.commons.lang3.SystemUtils;

import com.logonbox.vpn.common.client.DNSIntegrationMethod;

public interface VirtualInetAddress<P extends PlatformService<?>> {

	boolean isUp();

	/**
	 * Entirely disconnect and delete the interface.
	 */
	void delete() throws IOException;

	void down() throws IOException;

	String getMac();

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
	
	String getDisplayName();

	String getPeer();

	String getTable();

	void setMtu(int mtu);

	void setName(String name);

	void setPeer(String peer);

	void setTable(String table);

	void up() throws IOException;
	
	void dns(String[] dns) throws IOException;

	VirtualInetAddress<P> method(DNSIntegrationMethod method);

	DNSIntegrationMethod method();

	P getPlatform();

}