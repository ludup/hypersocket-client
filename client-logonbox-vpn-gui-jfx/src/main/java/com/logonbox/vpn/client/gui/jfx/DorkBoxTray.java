package com.logonbox.vpn.client.gui.jfx;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.JMenu;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import javafx.application.Platform;

public class DorkBoxTray extends AbstractTray implements AutoCloseable, Tray, BusLifecycleListener {


	final static Logger log = LoggerFactory.getLogger(DorkBoxTray.class);
	
	private static final int DEFAULT_ICON_SIZE = 48;
	private List<Entry> menuEntries = new ArrayList<>();
	private SystemTray systemTray;

	static {
		SystemTray.DEBUG = true;
		SystemTray.getVersion();
	}

	public DorkBoxTray(Client context) throws Exception {
		super(context);
		queueGuiOp(() -> {
			boolean connected = context.getDBus().isBusAvailable();
			List<VPNConnection> conx = connected ? context.getDBus().getVPNConnections() : Collections.emptyList();
			adjustTray(connected, conx);
		});
	}

	@Override
	public void onClose() throws Exception {
		if (systemTray != null) {
			systemTray.shutdown();
			systemTray = null;
		}
	}

	public boolean isActive() {
		return systemTray != null;
	}

	protected void queueGuiOp(Runnable r) {
		if (com.sun.jna.Platform.isMac()) {
			Platform.runLater(r);
		} else if(!context.getOpQueue().isShutdown())
			context.getOpQueue().execute(r);
		else
			log.debug("Ignoring request to queue task, queue is shutdown.");
	}

	public void reload() {
		queueGuiOp(() -> {
			try {
				boolean connected = context.getDBus().isBusAvailable();
				List<VPNConnection> conx = connected ? context.getDBus().getVPNConnections() : Collections.emptyList();
				rebuildMenu(connected, conx);
				setImage(connected, conx);
			} catch (Exception re) {
				queueGuiOp(() -> {
					rebuildMenu(false, Collections.emptyList());
					if (systemTray != null)
						setImage(false, Collections.emptyList());
				});
			}
		});
	}

	Menu addDevice(VPNConnection device, Menu toMenu, List<VPNConnection> devs) throws IOException {
		Menu menu = null;
		if (toMenu == null) {
			if (menu == null)
				menu = new Menu(device.getDisplayName());
		} else
			menu = toMenu;

		/* Open */
		Type status = Type.valueOf(device.getStatus());
		if (status == Type.CONNECTED) {
			var disconnectDev = new MenuItem(bundle.getString("disconnect"), (e) -> Platform.runLater(() -> {
				context.getOpQueue().execute(() -> device.disconnect(""));
			}));
			menu.add(disconnectDev);
			menuEntries.add(disconnectDev);
		} else if (devs.size() > 0 && status == Type.DISCONNECTED) {
			var openDev = new MenuItem(bundle.getString("connect"), (e) -> Platform.runLater(() -> {
				context.getOpQueue().execute(() -> device.connect());
			}));
			menu.add(openDev);
			menuEntries.add(openDev);
		}

		return menu;
	}

	void adjustTray(boolean connected, List<VPNConnection> devs) {
		String icon = Configuration.getDefault().trayModeProperty().get();
		if (systemTray == null && !Objects.equals(icon, Configuration.TRAY_MODE_OFF)) {
			systemTray = SystemTray.get();
			if (systemTray == null) {
				throw new RuntimeException("Unable to load SystemTray!");
			}

			setImage(connected, devs);

			systemTray.setStatus(bundle.getString("title"));
			Menu menu = systemTray.getMenu();
			if (menu == null) {
				systemTray.setMenu(new JMenu(bundle.getString("title")));
			}

			rebuildMenu(connected, devs);

		} else if (systemTray != null && Configuration.TRAY_MODE_OFF.equals(icon)) {
			systemTray.setEnabled(false);
		} else if (systemTray != null) {
			systemTray.setEnabled(true);
			setImage(connected, devs);
			rebuildMenu(connected, devs);
		}
	}

	private void addSeparator(Menu menu) {
		Separator sep = new Separator();
		menu.add(sep);
		menuEntries.add(sep);

	}

	private void clearMenus() {
		if (systemTray != null) {
			var menu = systemTray.getMenu();
			for (Entry dev : menuEntries) {
				menu.remove(dev);
			}
		}
		menuEntries.clear();
	}

	private void rebuildMenu(boolean connected, List<VPNConnection> devs) {
		clearMenus();
		if (systemTray != null) {
			var menu = systemTray.getMenu();

			var open = new MenuItem(bundle.getString("open"), (e) -> context.open());
			menuEntries.add(open);
			menu.add(open).setShortcut('o');
			addSeparator(menu);

			try {

				boolean devices = false;
				if (connected) {
					if (devs.size() == 1) {
						addDevice(devs.get(0), menu, devs);
						devices = true;
					} else {
						for (VPNConnection dev : devs) {
							var devmenu = addDevice(dev, null, devs);
							systemTray.getMenu().add(devmenu);
							menuEntries.add(devmenu);
							devices = true;
						}
					}
				}
				if (devices)
					addSeparator(menu);
			} catch (Exception e) {
				// TODO add error item / tooltip?
				systemTray.setTooltip("Error!");
				e.printStackTrace();
			}

			var options = new MenuItem(bundle.getString("options"), (e) -> context.options());
			menuEntries.add(options);
			menu.add(options).setShortcut('o');

			var quit = new MenuItem(bundle.getString("quit"), (e) -> {
				Platform.runLater(() -> context.confirmExit());
			});
			menuEntries.add(quit);
			menu.add(quit).setShortcut('q');
		}
	}

	private void setImage(boolean connected, List<VPNConnection> devs) {
		if (context.getDBus().isBusAvailable()) {
			String icon = Configuration.getDefault().trayModeProperty().get();
			if (Configuration.TRAY_MODE_LIGHT.equals(icon)) {
				systemTray.setImage(overlay(DorkBoxTray.class.getResource("light-logonbox-icon64x64.png"), 48, devs));
			} else if (Configuration.TRAY_MODE_DARK.equals(icon)) {
				systemTray.setImage(
						overlay(DorkBoxTray.class.getResource("dark-logonbox-icon64x64.png"), DEFAULT_ICON_SIZE, devs));
			} else if (Configuration.TRAY_MODE_COLOR.equals(icon)) {
				systemTray.setImage(overlay(DorkBoxTray.class.getResource("logonbox-icon64x64.png"), 48, devs));
			} else {
				if (isDark())
					systemTray
							.setImage(overlay(DorkBoxTray.class.getResource("light-logonbox-icon64x64.png"), 48, devs));
				else
					systemTray
							.setImage(overlay(DorkBoxTray.class.getResource("dark-logonbox-icon64x64.png"), 48, devs));
			}
		} else {
			systemTray.setImage(createAwesomeIcon(FontAwesome.EXCLAMATION_CIRCLE, 48, 100, Color.RED, 0));
		}
	}

}
