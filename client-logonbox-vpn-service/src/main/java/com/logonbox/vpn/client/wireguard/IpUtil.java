package com.logonbox.vpn.client.wireguard;

public class IpUtil {

	public static String toIEEE802(byte[] mac) {
		return mac == null ? null : String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
	}

}
