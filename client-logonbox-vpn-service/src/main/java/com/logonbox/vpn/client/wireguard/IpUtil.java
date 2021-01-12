package com.logonbox.vpn.client.wireguard;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import com.github.jgonian.ipmath.Ipv4;
import com.github.jgonian.ipmath.Ipv4Range;
import com.github.jgonian.ipmath.Ipv6;

public class IpUtil {

	public static String addMaskIfMissing(String ip) {
		if (!ip.contains("/"))
			return ip + "/32";
		else
			return ip;
	}

	public static Set<String> optimizeIps(String... allowedIps) {
		return optimizeIps(new LinkedHashSet<>(Arrays.asList(allowedIps)));
	}

	public static Set<String> optimizeIps(Set<String> allowedIps) {
		Set<String> done = new LinkedHashSet<>();
		Set<String> todo = new LinkedHashSet<>(allowedIps);
		for (String ip : allowedIps) {
			ip = addMaskIfMissing(ip);

			/* Is this address covered by any other with a wider (smaller) prefix */
			boolean covered = false;
			for (String other : todo) {
				other = addMaskIfMissing(other);

				if (!other.equals(ip)) {
					Ipv4Range ipRange = Ipv4Range.parse(ip);
					Ipv4Range otherRange = Ipv4Range.parse(other);
					if (otherRange.overlaps(ipRange) && otherRange.size() > ipRange.size()) {
						covered = true;
					}
				}
			}
			if (!covered)
				done.add(ip);
		}
		return done;
	}

	public static boolean four(String addr) {
		try {
			Ipv4.of(addr);
			return true;
		} catch (IllegalArgumentException iae) {
		}
		return true;
	}

	public static boolean six(String addr) {
		try {
			Ipv6.of(addr);
			return true;
		} catch (IllegalArgumentException iae) {
		}
		return true;
	}

}
