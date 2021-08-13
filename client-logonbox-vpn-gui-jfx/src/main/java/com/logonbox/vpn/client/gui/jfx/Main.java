package com.logonbox.vpn.client.gui.jfx;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;

import javax.swing.UIManager;

import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionTarget;
import com.logonbox.vpn.common.client.AbstractDBusClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "logonbox-vpn-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.")
public class Main extends AbstractDBusClient implements Callable<Integer> {
	static Logger log = LoggerFactory.getLogger(Main.class);

	private static Main instance;

	@Option(names = { "-C", "--no-close" }, description = "Do not allow the window to be closed manually.")
	private boolean noClose;

	@Option(names = { "-x", "--exit-on-connection" }, description = "Exit on successful connection.")
	private boolean exitOnConnection;

	@Option(names = { "-A", "--no-add-when-no-connections" }, description = "Do not show the Add connection page when no connections are configured.")
	private boolean noAddWhenNoConnections;

	@Option(names = { "-M", "--no-minimize" }, description = "Do not allow the window to be minimized manually.")
	private boolean noMinimize;

	@Option(names = { "-S",
			"--no-systray" }, description = "Do not start the system tray, the application will immediately exit when the window is closed.")
	private boolean noSystemTray;

	@Option(names = { "-B", "--no-sidebar" }, description = "Do not show the burger menu or side menu.")
	private boolean noSidebar;

	@Option(names = { "-R", "--no-resize" }, description = "Do not allow the window to be resized.")
	private boolean noResize;

	@Option(names = { "-D",
			"--no-drag" }, description = "Do not allow the window to be moved. Will be centred on primary monitor.")
	private boolean noMove;

	@Option(names = { "-s", "--size" }, description = "Size of window, in the format <width>X<height>.")
	private String size = "460x200";

	@Option(names = { "-T", "--always-on-top" }, description = "Keep the window on top of others.")
	private boolean alwaysOnTop;

	@Option(names = { "-U", "--no-updates" }, description = "Do not perform any updates.")
	private boolean noUpdates;

	@Option(names = { "-c", "--connect" }, description = "Connect to the first available pre-configured connection.")
	private boolean connect;

	@Option(names = { "-n", "--create" }, description = "Create a new connection if one with the provided URI does not exist (requires URI parameter).")
	private boolean createIfDoesntExist;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	public Main() {
		super(ExtensionTarget.CLIENT_GUI);
		instance = this;
		setSupportsAuthorization(true);

		// http://stackoverflow.com/questions/24159825/changing-application-dock-icon-javafx-programatically
		try {
			if (SystemUtils.IS_OS_MAC_OSX) {
				Class<?> appClazz = Class.forName("com.apple.eawt.Application");
				Object app = appClazz.getMethod("getApplication").invoke(null);
				appClazz.getMethod("setDockIconImage", Image.class).invoke(app, java.awt.Toolkit.getDefaultToolkit()
						.getImage(Main.class.getResource("logonbox-icon128x128.png")));
			}
		} catch (Exception e) {
			// Won't work on Windows or Linux.
		}

		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if (logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(Main.class.getResource("/default-log4j-gui.properties"));
		} else {
			File logConfigFile = new File(logConfigPath);
			if (logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(Main.class.getResource("/default-log4j-gui.properties"));
		}

		try {
			log.info("I am currently using working directory " + new File(".").getCanonicalPath());
		} catch (IOException e) {
		}
	}

	/*
	 * NOTE: LauncherImpl has to be used, as Application.launch() tests where the
	 * main() method was invoked from by examining the stack (stupid stupid stupid
	 * technique!). Because we are launched from BoostrapMain, this is what it
	 * detects. To work around this LauncherImpl.launchApplication() is used
	 * directly, which is an internal API.
	 * 
	 * TODO With the new forker arrangement, this is no longer the case. So
	 *      check if this is still required.  This is one of the reasons for 
	 *      the new bootstrap arrangement.
	 */
	public Integer call() throws Exception {
		com.sun.javafx.application.LauncherImpl.launchApplication(Client.class, null, new String[0]);
		return 0;
	}

	public boolean isNoAddWhenNoConnections() {
		return noAddWhenNoConnections;
	}

	public boolean isExitOnConnection() {
		return exitOnConnection;
	}

	public boolean isConnect() {
		return connect;
	}

	public String getUri() {
		return uri;
	}

	public boolean isNoUpdates() {
		return noUpdates;
	}

	public boolean isNoResize() {
		return noResize;
	}

	public boolean isAlwaysOnTop() {
		return alwaysOnTop;
	}

	public boolean isNoMove() {
		return noMove;
	}

	public String getSize() {
		return size;
	}

	public boolean isNoClose() {
		return noClose;
	}

	public boolean isNoSidebar() {
		return noSidebar;
	}

	public boolean isNoMinimize() {
		return noMinimize;
	}

	public boolean isNoSystemTray() {
		return noSystemTray;
	}

	public boolean isCreateIfDoesntExist() {
		return createIfDoesntExist;
	}

	public static Main getInstance() {
		return instance;
	}

	public void restart() {
		System.exit(99);
	}

	public void shutdown() {
		System.exit(0);
	}

	@Override
	protected boolean isInteractive() {
		return true;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		if(Client.get() == null) {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			System.exit(
					new CommandLine(new Main()).execute(args));
		}
		else {
			/* Re-use, a second instance was started but intercepted by forker */
			Client.get().open();
		}

	}
}
