package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collection;
import java.util.List;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.LogonBoxVPNSession;

public interface PlatformService {
	
	String[] getMissingPackages();

	VirtualInetAddress add(String name, String type) throws IOException;

	boolean exists(String name);

	VirtualInetAddress get(String name);

	InetAddress getBestAddress(NetworkInterface nif);

	List<NetworkInterface> getBestLocalNic();

	List<String> getBestLocalAddresses(boolean network, String... exclude);

	List<VirtualInetAddress> ips();

	String genkey(String privateKey);

	Collection<LogonBoxVPNSession> start(LocalContext ctx);

	String getPublicKey(String interfaceName) throws IOException;

}