package com.logonbox.vpn.client.wireguard.windows.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import org.apache.commons.io.FilenameUtils;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

public class NetworkConfigurationService {

	public static interface TunnelInterface extends Library {
		/** Unused, keys are generated using Java */
		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);

		boolean WireGuardTunnelService(WString confFile);
	}

	public static TunnelInterface INSTANCE;

	static {
		try {
			Native.extractFromResourcePath("tunnel.dll");
			Native.extractFromResourcePath("wintun.dll");
			INSTANCE = Native.load("tunnel", TunnelInterface.class);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load support library.", e);
		}
	}
	public static final String PREF_MAC = "mac";

	public static final String PREF_PUBLIC_KEY = "publicKey";
	private static Preferences PREFS = null;

	public static Preferences getPreferences() {
		if (PREFS == null) {
			/* Test whether we can write to system preferences */
			try {
				PREFS = Preferences.systemRoot();
				PREFS.put("test", "true");
				PREFS.flush();
				PREFS.remove("test");
				PREFS.flush();
			} catch (Exception bse) {
				System.out.println("Fallback to usering user preferences for public key -> interface mapping.");
				PREFS = Preferences.userRoot();
			}
		}
		return PREFS;
	}

	public static Preferences getInterfaceNode(String name) {
		return getInterfacesNode().node(name);
	}

	public static Preferences getInterfacesNode() {
		return getPreferences().node("interfaces");
	}

	private static void log(String msgFmt, Object... args) {
		System.out.println(String.format(msgFmt, args));
	}

	/**
	 * main.
	 *
	 * @param args arguments
	 */
	public static void main(String[] args) throws Exception {
		File confFile = null;
		if (args.length == 3 && args[0].equals("/service")) {
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					System.out.println("Shutting down tunneler");
					System.out.flush();
					System.err.flush();
				}
			});

			confFile = new File(args[1]);
			File logFile = new File(args[2]);
			FileOutputStream fos = new FileOutputStream(logFile);
			System.setErr(new PrintStream(fos, true));
			System.setOut(new PrintStream(fos, true));
			if (!confFile.exists())
				throw new FileNotFoundException(String.format("No configuration file %s", confFile));

		} else if (args.length == 1) {
			confFile = new File(args[0]);
		} else {
			System.err.println(String.format(
					"%s: Unexpected arguments (%d supplied). Use /service <interface-name>.conf <logFile>",
					NetworkConfigurationService.class.getName(), args.length));
			System.exit(1);
		}

		NetworkConfigurationService service = new NetworkConfigurationService(confFile);
		System.exit(service.startNetworkService());
	}

	private String name;
	private File confFile;

	public NetworkConfigurationService() {
	}

	public NetworkConfigurationService(File confFile) {
		this.confFile = confFile;
		name = FilenameUtils.getBaseName(confFile.getName());
		System.out.println(String.format("Preparing Wireguard configuration for %s (in %s)", name, confFile));
	}

	private int startNetworkService() {
		log("Activating Wireguard configuration for %s (in %s)", name, confFile);
		if (INSTANCE.WireGuardTunnelService(new WString(confFile.getPath()))) {
			log("Activated Wireguard configuration for %s", name);
			return 0;
		} else {
			log("%s: Failed to activate %s", NetworkConfigurationService.class.getName(), name);
			return 1;
		}
	}
}
