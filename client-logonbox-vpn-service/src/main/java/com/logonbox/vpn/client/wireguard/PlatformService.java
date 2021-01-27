package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.util.Collection;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;

public interface PlatformService<I extends VirtualInetAddress> {
	
	default int processCLI(String[] args) {
		return Integer.MIN_VALUE;
	}
	
	String[] getMissingPackages();

	String pubkey(String privateKey);

	Collection<VPNSession> start(LocalContext ctx);

	I connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException;

}