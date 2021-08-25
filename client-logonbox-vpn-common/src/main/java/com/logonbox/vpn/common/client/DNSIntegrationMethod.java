package com.logonbox.vpn.common.client;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.SystemUtils;

public enum DNSIntegrationMethod {
	AUTO, NETSH, NETWORK_MANAGER, SYSTEMD, RESOLVCONF, RAW, NETWORKSETUP, SCUTIL_COMPATIBLE, SCUTIL_SPLIT, NONE;
	
	public boolean isForOS() {
		switch(this) {
		case NETWORKSETUP:
		case SCUTIL_COMPATIBLE:
		case SCUTIL_SPLIT:
			return SystemUtils.IS_OS_MAC_OSX;
		case NETWORK_MANAGER:
		case SYSTEMD:
		case RAW:
		case RESOLVCONF:
			return SystemUtils.IS_OS_LINUX;
		case NETSH:
			return SystemUtils.IS_OS_WINDOWS;
		case AUTO:
		case NONE:
			return true;
		default:
			return false;
		}
	}
	
	public static DNSIntegrationMethod[] valuesForOs() {
		List<DNSIntegrationMethod> l = new ArrayList<>();
		for(DNSIntegrationMethod m : values()) {
			if(m.isForOS())
				l.add(m);
		}
		return l.toArray(new DNSIntegrationMethod[0]);
	}
	
}
