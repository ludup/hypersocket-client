package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import com.logonbox.vpn.client.LocalContext;
import com.logonbox.vpn.client.service.VPNSession;
import com.logonbox.vpn.common.client.Connection;

public interface PlatformService<I extends VirtualInetAddress> {

	/**
	 * Process any command line arguments for this platform.
	 * 
	 * @param args arguments
	 * @return error code or {@link Integer.MIN_VALUE} to ignore.
	 */
	default int processCLI(String[] args) {
		return Integer.MIN_VALUE;
	}

	/**
	 * Get a list of the common names of any 3rd party or distribution packages that
	 * are needed on this platform.
	 * 
	 * @return message packages
	 */
	String[] getMissingPackages();

	/**
	 * Get a public key given it's private key.
	 * 
	 * @param privateKey private key
	 * @return public key
	 */
	String pubkey(String privateKey);

	/**
	 * Start the services for this platform.
	 * 
	 * @param ctx context
	 * @return any sessions that are already active.
	 */
	Collection<VPNSession> start(LocalContext ctx);

	/**
	 * Connect.
	 * 
	 * @param logonBoxVPNSession the session
	 * @param configuration      the configuration
	 * @return the virtual interface
	 * @throws IOException on any error
	 */
	I connect(VPNSession logonBoxVPNSession, Connection configuration) throws IOException;

	/**
	 * Get an interface that is using this public key, or <code>null</code> if no
	 * interface is using this public key at the moment.
	 * 
	 * @param public key return interface
	 */
	I getByPublicKey(String publicKey);

	/**
	 * Get if a session is actually an active connect (handshake has occured and has not timed out).
	 * 
	 * @param logonBoxVPNSession the session
	 * @param configuration      the configuration
	 * @return up
	 * @throws IOException on any error
	 */
	boolean isAlive(VPNSession logonBoxVPNSession, Connection configuration) throws IOException;

	
	/**
	 * Disconnect from the VPN.
	 * 
	 * @param session session
	 * @throws IOException on any error
	 */
	void disconnect(VPNSession session) throws IOException;

	/**
	 * Get all interfaces.
	 * 
	 * @param wireguardOnly only wireguard interfaces
	 * @return interfaces
	 */
	List<I> ips(boolean wireguardOnly);

}