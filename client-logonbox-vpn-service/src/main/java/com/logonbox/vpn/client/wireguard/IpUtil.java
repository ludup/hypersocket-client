package com.logonbox.vpn.client.wireguard;

import java.util.ArrayList;
import java.util.List;

import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv6;

public class IpUtil {

	public static String toIEEE802(byte[] mac) {
		return mac == null ? null
				: String.format("%02x:%02x:%02x:%02x:%02x:%02x", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
	}

	public static String[] filterAddresses(String[] address) {
		List<String> l = new ArrayList<>();
		if (address != null) {
			for (String a : address) {
				try {
					Ipv4.of(a);
					l.add(a);
				} catch (IllegalArgumentException iae) {
					try {
						Ipv6.of(a);
						l.add(a);
					} catch (IllegalArgumentException iae2) {

					}
				}
			}
		}
		return l.toArray(new String[0]);
	}

	public static String[] filterNames(String[] address) {
		List<String> l = new ArrayList<>();
		if (address != null) {
			for (String a : address) {
				try {
					Ipv4.of(a);
					continue;
				} catch (IllegalArgumentException iae) {
					try {
						Ipv6.of(a);
						continue;
					} catch (IllegalArgumentException iae2) {

					}
				}
				l.add(a);
			}
		}
		return l.toArray(new String[0]);
	}

}
