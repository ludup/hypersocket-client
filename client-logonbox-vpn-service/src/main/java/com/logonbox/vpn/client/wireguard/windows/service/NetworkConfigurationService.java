package com.logonbox.vpn.client.wireguard.windows.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;

import org.apache.commons.io.FilenameUtils;

import com.sshtools.forker.common.XKernel32;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.WString;

public class NetworkConfigurationService {

	public static interface TunnelInterface extends Library {
		/* Unused, keys are generated using Java */
		void WireGuardGenerateKeyPair(ByteBuffer publicKey, ByteBuffer privateKey);

		boolean WireGuardTunnelService(WString confFile);
	}

	public static TunnelInterface INSTANCE;

	static {
		INSTANCE = Native.load("tunnel", TunnelInterface.class);
	}

	private static void log(String msgFmt, Object... args) {
		System.out.println(String.format(msgFmt, args));
	}

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
			String cwd = args[1];
			String name = args[2];

			/*
			 * Set current directory (the .dlls are expected to be here so both Java can
			 * find the embedded DLL, and the embedded DLL can find the wintun DLL)
			 */
			XKernel32.INSTANCE.SetCurrentDirectoryW(cwd);

			/* Capture stdout and stderr to a log file */
			FileOutputStream fos = new FileOutputStream(new File("logs" + File.separator + name + "-service.log"));
			System.setErr(new PrintStream(fos, true));
			System.setOut(new PrintStream(fos, true));

			/* Configuration path */
			confFile = new File("conf" + File.separator + "connections" + File.separator + name + ".conf");

			System.out
					.println(String.format("Running from %s for interface %s (configuration %s)", cwd, name, confFile));
			if (!confFile.exists())
				throw new FileNotFoundException(String.format("No configuration file %s", confFile));

		} else if (args.length == 1) {
			confFile = new File(args[0]);
		} else {
			System.err.println(String.format("%s: Unexpected arguments (%d supplied). Use /service <dir> <name>",
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
			log("Wireguard shutdown cleanly for %s", name);
			return 0;
		} else {
			log("%s: Failed to activate %s", NetworkConfigurationService.class.getName(), name);
			return 1;
		}
	}
}
