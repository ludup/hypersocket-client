/*
 * The contents of this file is dual-licensed under 2
 * alternative Open Source/Free licenses: LGPL 2.1 or later and
 * Apache License 2.0. (starting with JNA version 4.0.0).
 *
 * You can freely decide which license you want to apply to
 * the project.
 *
 * You may obtain a copy of the LGPL License at:
 *
 * http://www.gnu.org/licenses/licenses.html
 *
 * A copy is also included in the downloadable source code package
 * containing JNA, in file "LGPL2.1".
 *
 * You may obtain a copy of the Apache License at:
 *
 * http://www.apache.org/licenses/
 *
 * A copy is also included in the downloadable source code package
 * containing JNA, in file "AL2.0".
 */
package com.logonbox.vpn.client.wireguard.windows.service;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.util.prefs.Preferences;

import org.apache.commons.io.FilenameUtils;

import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Kernel32Util;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.Winsvc;
import com.sun.jna.platform.win32.Winsvc.HandlerEx;
import com.sun.jna.platform.win32.Winsvc.SERVICE_MAIN_FUNCTION;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS;
import com.sun.jna.platform.win32.Winsvc.SERVICE_STATUS_HANDLE;
import com.sun.jna.platform.win32.Winsvc.SERVICE_TABLE_ENTRY;

/**
 * Baseclass for a Win32 service.
 */
public class NetworkConfigurationService {

	/**
	 * Implementation of the service control function.
	 */
	private class ServiceControl implements HandlerEx {

		/**
		 * Called when the service get a control code.
		 *
		 * @param dwControl
		 * @param dwEventType
		 * @param lpEventData
		 * @param lpContext
		 */
		public int callback(int dwControl, int dwEventType, Pointer lpEventData, Pointer lpContext) {
			log("ServiceControl.callback() - %d, %d", dwControl, dwEventType);
			switch (dwControl) {
			case Winsvc.SERVICE_CONTROL_STOP:
			case Winsvc.SERVICE_CONTROL_SHUTDOWN:
				onStop();
				synchronized (waitObject) {
					waitObject.notifyAll();
				}
				break;
			case Winsvc.SERVICE_CONTROL_PAUSE:
				onPause();
				break;
			case Winsvc.SERVICE_CONTROL_CONTINUE:
				onContinue();
				break;
			}
			return WinError.NO_ERROR;
		}
	}

	/**
	 * Implementation of the service main function.
	 */
	private class ServiceMain implements SERVICE_MAIN_FUNCTION {

		/**
		 * Called when the service is starting.
		 *
		 * @param dwArgc   number of arguments
		 * @param lpszArgv pointer to arguments
		 */
		public void callback(int dwArgc, Pointer lpszArgv) {
			log("ServiceMain.callback() - %d", dwArgc);

			serviceControl = new ServiceControl();
			serviceStatusHandle = Advapi32.INSTANCE.RegisterServiceCtrlHandlerEx(getServiceName(), serviceControl,
					null);

			reportStatus(Winsvc.SERVICE_START_PENDING, WinError.NO_ERROR, 25000);

			log("callback() - starting network");
			startNetworkService();
			onStart();

			try {
				synchronized (waitObject) {
					waitObject.wait();
				}
			} catch (InterruptedException ex) {
			}
			log("callback() - reporting SERVICE_STOPPED");
			reportStatus(Winsvc.SERVICE_STOPPED, WinError.NO_ERROR, 0);

			// Avoid returning from ServiceMain, which will cause a crash
			// See http://support.microsoft.com/kb/201349, which recommends
			// having init() wait for this thread.
			// Waiting on this thread in init() won't fix the crash, though.
			// System.exit(0);
		}
	}

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
		if (args.length == 1) {
			service.startNetworkService();
		} else {
			service.init();
		}
	}

	private final Object waitObject = new Object();

	private ServiceMain serviceMain;
	private ServiceControl serviceControl;
	private SERVICE_STATUS_HANDLE serviceStatusHandle;
	private String name;
	private File confFile;

	public NetworkConfigurationService() {
	}

	public NetworkConfigurationService(File confFile) {
		this.confFile = confFile;
		name = FilenameUtils.getBaseName(confFile.getName());
		System.out.println(String.format("Preparing Wireguard configuration for %s (in %s)", name, confFile));
	}

	private String getServiceName() {
		return WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + name;
	}

	/**
	 * Initialize the service, connect to the ServiceControlManager.
	 */
	public void init() {
		log("init() - setting up table. name: %s service name: %s  conf: %s", name, getServiceName(), confFile);
		serviceMain = new ServiceMain();
		SERVICE_TABLE_ENTRY entry = new SERVICE_TABLE_ENTRY();
		entry.lpServiceName = getServiceName();
		entry.lpServiceProc = serviceMain;

		log("init() - starting the dispatcher");
		Advapi32.INSTANCE.StartServiceCtrlDispatcher((SERVICE_TABLE_ENTRY[]) entry.toArray(2));
		log("init() - started the dispatcher");
	}

	/*
	 * Called when service should stop.
	 */
	public void onContinue() {
		log("init() - continue");
		reportStatus(Winsvc.SERVICE_RUNNING, WinError.NO_ERROR, 0);
	}

	/*
	 * Called when service should stop.
	 */
	public void onPause() {
		log("init() - paused");
		reportStatus(Winsvc.SERVICE_PAUSED, WinError.NO_ERROR, 0);
	}

	/**
	 * Called when service is starting.
	 */
	public void onStart() {
		log("init() - service starting");
		reportStatus(Winsvc.SERVICE_RUNNING, WinError.NO_ERROR, 0);
	}

	/*
	 * Called when service should stop.
	 */
	public void onStop() {
		log("init() - stop pending");
		reportStatus(Winsvc.SERVICE_STOP_PENDING, WinError.NO_ERROR, 25000);
	}

	/**
	 * Report service status to the ServiceControlManager.
	 *
	 * @param status        status
	 * @param win32ExitCode exit code
	 * @param waitHint      time to wait
	 */
	private void reportStatus(int status, int win32ExitCode, int waitHint) {
		SERVICE_STATUS serviceStatus = new SERVICE_STATUS();
		serviceStatus.dwServiceType = WinNT.SERVICE_WIN32_OWN_PROCESS;
		serviceStatus.dwControlsAccepted = status == Winsvc.SERVICE_START_PENDING ? 0
				: (Winsvc.SERVICE_ACCEPT_STOP | Winsvc.SERVICE_ACCEPT_SHUTDOWN | Winsvc.SERVICE_CONTROL_PAUSE
						| Winsvc.SERVICE_CONTROL_CONTINUE);
		serviceStatus.dwWin32ExitCode = win32ExitCode;
		serviceStatus.dwWaitHint = waitHint;
		serviceStatus.dwCurrentState = status;

		Advapi32.INSTANCE.SetServiceStatus(serviceStatusHandle, serviceStatus);
	}

	private void startNetworkService() {
		log("Activating Wireguard configuration for %s (in %s)", name, confFile);
		if (INSTANCE.WireGuardTunnelService(new WString(confFile.getPath()))) {
			log("Activated Wireguard configuration for %s", name);

		} else {
			log("%s: Failed to activate %s", NetworkConfigurationService.class.getName(), name);
			log("Err: %d. %s", Native.getLastError(),
					Kernel32Util.formatMessageFromLastErrorCode(Native.getLastError()));
		}
	}
}
