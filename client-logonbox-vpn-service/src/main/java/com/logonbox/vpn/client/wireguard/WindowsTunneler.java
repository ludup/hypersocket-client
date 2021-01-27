package com.logonbox.vpn.client.wireguard;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

/**
 * A small helper application that is execute as a service and starts the
 * WireGuard interface given a configuration.
 */
public class WindowsTunneler {

	final static Logger LOG = LoggerFactory.getLogger(WindowsPlatformServiceImpl.class);

	public static final String PREF_MAC = "mac";
	public static final String PREF_PUBLIC_KEY = "publicKey";

	public static TunnelInterface INSTANCE;
	static {
		try {
			Native.extractFromResourcePath("tunnel.dll");
			INSTANCE = Native.load("tunnel", TunnelInterface.class);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load support library.", e);
		}
	}

	public static interface TunnelInterface extends Library {
		boolean WireGuardTunnelService(WString confFile);

		/** Unused, keys are generated using Java */
		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);
	}

	static Preferences PREFS = null;

	static {
		/* Test whether we can write to system preferences */
		try {
			PREFS = Preferences.systemRoot();
			PREFS.put("test", "true");
			PREFS.flush();
			PREFS.remove("test");
			PREFS.flush();
		} catch (Exception bse) {
			LOG.warn("Fallback to usering user preferences for public key -> interface mapping.");
			PREFS = Preferences.userRoot();
		}
	}

	WindowsTunneler() {
	}

	public static Preferences getPreferences() {
		return PREFS;
	}

	public static Preferences getInterfaceNode(String name) {
		return getInterfacesNode().node(name);
	}

	public static Preferences getInterfacesNode() {
		return getPreferences().node("interfaces");
	}

	public static void main(String[] args) throws IOException {
		if (args.length == 2 && args[0].equals("/service")) {
			String name = FilenameUtils.getBaseName(args[2]);
			System.out.println(String.format("Activating Wireguard configuration for %s (in %s)", name, args[1]));
			if (INSTANCE.WireGuardTunnelService(new WString(args[1]))) {
				System.out.println(String.format("Activated Wireguard configuration for %s", name));

				// TODO might need to wait/sleep wait for this interface?

				/*
				 * Store the public key being used for this interface name so we can later
				 * retrieve it to determine this interface was started by LogonBox VPN (gets
				 * around the fact there doesn't seem to be a 'wg show' command available).
				 * 
				 * Also record the mac, in case it changes and another interface takes that name
				 * while LB VPN is not watching.
				 */
//				Preferences ifNode = getInterfaceNode(name);
//				ifNode.put(PREF_PUBLIC_KEY, configuration.getPublicKey());
//				ifNode.put(PREF_MAC, ip.getMac());
//				LOG.info(String.format("Recording public key %s against MAC %s", configuration.getPublicKey(),
//						ip.getMac()));

			} else {
				System.err.println(String.format("%s: Failed to activate %s", WindowsTunneler.class.getName(), name));
				System.exit(2);
			}
		} else {
			System.err.println(String.format("%s: Unexpected arguments. Use /service <interface-name>.conf",
					WindowsTunneler.class.getName()));
			System.exit(1);
		}
	}
}
