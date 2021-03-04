package com.logonbox.vpn.client.gui.jfx;

import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.swing.UIManager;

import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
	static Logger log = LoggerFactory.getLogger(Main.class);

	Runnable restartCallback;
	Runnable shutdownCallback;
	ClassLoader classLoader;
	static Main instance;

	public Main(Runnable restartCallback, Runnable shutdownCallback) {
		instance = this;

		this.restartCallback = restartCallback;
		this.shutdownCallback = shutdownCallback;

		// http://stackoverflow.com/questions/24159825/changing-application-dock-icon-javafx-programatically
		try {
			if (SystemUtils.IS_OS_MAC_OSX) {
				Class<?> appClazz = Class.forName("com.apple.eawt.Application");
				Object app = appClazz.getMethod("getApplication").invoke(null);
				appClazz.getMethod("setDockIconImage", Image.class)
						.invoke(app,
								java.awt.Toolkit
										.getDefaultToolkit()
										.getImage(
												Main.class
														.getResource("hypersocket-icon128x128.png")));
			}
		} catch (Exception e) {
			// Won't work on Windows or Linux.
		}



		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if(logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(Main.class.getResource("/default-log4j-gui.properties"));
		}
		else {
			File logConfigFile = new File(logConfigPath);
			if(logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(Main.class.getResource("/default-log4j-gui.properties"));
		}
		
		try {
			log.info("I am currently using working directory " + new File(".").getCanonicalPath());
		} catch (IOException e) {
		}
		
		/* This is a work around to some weird problem where JavaFX (no longer)
		 * loads relative resources from the classpath in webview. Meaning adding
		 * stuff like Bootstrap, Fontawesome to any local content is a frickin 
		 * nightmare.
		 * 
		 * All local resources can use app://<relativeResourceName> instead of
		 * just <relativeResourceName> to work around this.
		 */
		URL.setURLStreamHandlerFactory(protocol -> {
		    if(protocol.equals("app")) {
		        return new AppStreamHandler();
		    } else {
		        return null;
		    }
		});
	}

	/*
	 * NOTE: LauncherImpl has to be used, as Application.launch() tests where
	 * the main() method was invoked from by examining the stack (stupid stupid
	 * stupid technique!). Because we are launched from BoostrapMain, this is
	 * what it detects. To work around this LauncherImpl.launchApplication() is
	 * used directly, which is an internal API.
	 */
	public void run() {

		try {
			// :(
			com.sun.javafx.application.LauncherImpl.launchApplication(
					Client.class, null, new String[0]);
			System.out.println("Exiting");
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Failed to start client", e);
			System.exit(1);
		}
	}

	public static Main getInstance() {
		return instance;
	}

	public void restart() {
		restartCallback.run();
	}

	public void shutdown() {
		shutdownCallback.run();
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		new Main(new DefaultRestartCallback(), new DefaultShutdownCallback())
				.run();

	}

	public static void runApplication(Runnable restartCallback,
			Runnable shutdownCallback) throws IOException {

		new Main(restartCallback, shutdownCallback).run();

	}

	static class DefaultRestartCallback implements Runnable {

		@Override
		public void run() {

			if (log.isInfoEnabled()) {
				log.info("Shutting down with forker restart code.");
			}

			System.exit(90);
		}

	}

	static class DefaultShutdownCallback implements Runnable {

		@Override
		public void run() {

			if (log.isInfoEnabled()) {
				log.info("Shutting down using default shutdown mechanism");
			}

			System.exit(0);
		}

	}
}
