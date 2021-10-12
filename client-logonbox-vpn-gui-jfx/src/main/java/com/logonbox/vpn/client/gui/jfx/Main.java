package com.logonbox.vpn.client.gui.jfx;

import java.awt.Taskbar;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.prefs.Preferences;

import javax.swing.UIManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.common.client.AbstractDBusClient;
import com.logonbox.vpn.common.client.PromptingCertManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert.AlertType;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "logonbox-vpn-gui", mixinStandardHelpOptions = true, description = "Start the LogonBox VPN graphical user interface.")
public class Main extends AbstractDBusClient implements Callable<Integer> {
	static Logger log;
	
	/**
	 * Used to get version from Maven meta-data
	 */
	public static final String ARTIFACT_COORDS = "com.hypersocket/client-logonbox-vpn-gui-jfx";

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
	private String size;

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

	private Level defaultLogLevel;

	public Main() {
		super(ExtensionTarget.CLIENT_GUI);
		instance = this;
		setSupportsAuthorization(true);

		if(Taskbar.isTaskbarSupported()) {
	        try {
	    		final Taskbar taskbar = Taskbar.getTaskbar();
	    		if(SystemUtils.IS_OS_MAC_OSX)
		            taskbar.setIconImage(java.awt.Toolkit.getDefaultToolkit()
							.getImage(Main.class.getResource("mac-logo128px.png")));
	    		else
		            taskbar.setIconImage(java.awt.Toolkit.getDefaultToolkit()
							.getImage(Main.class.getResource("logonbox-icon128x128.png")));
	        } catch (final UnsupportedOperationException e) {
	        } catch (final SecurityException e) {
	        }
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
		
		String cfgLevel = Configuration.getDefault().logLevelProperty().get();
		defaultLogLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
		if(StringUtils.isNotBlank(cfgLevel)) {
			org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(cfgLevel));
		}
		log = LoggerFactory.getLogger(Main.class);

		log.info(String.format("LogonBox VPN Client GUI, version %s", HypersocketVersion.getVersion(ARTIFACT_COORDS)));
		log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")"));
		try {
			log.info(String.format("CWD: %s", new File(".").getCanonicalPath()));
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
		Application.launch(Client.class, new String[0]);
		return 0;
	}
	
	public Level getDefaultLogLevel() {
		return defaultLogLevel;
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

	@Override
	protected PromptingCertManager createCertManager() {
		return new PromptingCertManager(Client.BUNDLE) {

			@Override
			protected boolean isToolkitThread() {
				return Platform.isFxApplicationThread();
			}

			@Override
			protected void runOnToolkitThread(Runnable r) {
				Platform.runLater(r);
			}

			@Override
			protected boolean promptForCertificate(PromptType alertType, String title,
					String content, String key, String hostname, String message, Preferences preference) {
				return Client.get().promptForCertificate(AlertType.valueOf(alertType.name()), title, content, key, hostname, message, preference);
			}
			
		};
	}
}
