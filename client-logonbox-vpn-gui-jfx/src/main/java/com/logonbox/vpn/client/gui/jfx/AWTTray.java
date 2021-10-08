package com.logonbox.vpn.client.gui.jfx;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingUtilities;

import org.kordamp.ikonli.fontawesome.FontAwesome;

import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import javafx.application.Platform;

public class AWTTray extends AbstractTray {

	private static final int DEFAULT_ICON_SIZE = 48;
	private List<MenuItem> menuEntries = new ArrayList<>();
	private SystemTray systemTray;
	private TrayIcon trayIcon;
	
	public AWTTray(Client context) throws Exception {
		super(context);
		SwingUtilities.invokeLater(() -> {
			boolean connected = context.getDBus().isBusAvailable();
			List<VPNConnection> conx = connected ? context.getDBus().getVPNConnections() : Collections.emptyList();
			adjustTray(connected, conx);
		});
	}

	@Override
	public void onClose() throws Exception {
		if(trayIcon != null) {
			SystemTray tr = systemTray;
			SwingUtilities.invokeLater(() -> {
				tr.remove(trayIcon);
			});
			trayIcon = null;
		}
		systemTray = null;
	}

	public boolean isActive() {
		return systemTray != null;
	}
	
	public void reload() {
		SwingUtilities.invokeLater(() -> {
			try {
				boolean connected = context.getDBus().isBusAvailable();
				List<VPNConnection> conx = connected ? context.getDBus().getVPNConnections() : Collections.emptyList();
				rebuildMenu(connected, conx);
				setImage(connected, conx);
			} catch (Exception re) {
				SwingUtilities.invokeLater(() -> {
					rebuildMenu(false, Collections.emptyList());
					if(systemTray != null)
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
			var disconnectDev = new MenuItem(bundle.getString("disconnect"));
			disconnectDev.setShortcut(new MenuShortcut('d'));
			disconnectDev.addActionListener((e) -> {
				context.getOpQueue().execute(() -> device.disconnect(""));
			});
			menu.add(disconnectDev);
			menuEntries.add(disconnectDev);
		} else if (devs.size() > 0 && status == Type.DISCONNECTED) {
			var openDev = new MenuItem(bundle.getString("connect"));
			openDev.setShortcut(new MenuShortcut('c'));
			openDev.addActionListener((e) -> Platform.runLater(() -> {
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
			systemTray = SystemTray.getSystemTray();
			if (systemTray == null) {
				throw new RuntimeException("Unable to load SystemTray!");
			}
			trayIcon = new TrayIcon(getImage(connected, devs));
			trayIcon.setToolTip(bundle.getString("title"));
			trayIcon.setImageAutoSize(true);
			
			var menu = trayIcon.getPopupMenu();
			if (menu == null) {
				trayIcon.setPopupMenu(new PopupMenu(bundle.getString("title")));
			}

			rebuildMenu(connected, devs);
			try {
				systemTray.add(trayIcon);
			} catch (AWTException e) {
				throw new IllegalStateException("Failed to add system tray.", e);
			}

		} else if (systemTray != null && Configuration.TRAY_MODE_OFF.equals(icon)) {
			systemTray.remove(trayIcon);
			systemTray = null;
//			trayIcon.setEnabled(false);
		} else if (systemTray != null) {
//			systemTray.setEnabled(true);
			setImage(connected, devs);
			rebuildMenu(connected, devs);
		}
	}

	private void clearMenus() {
		if (systemTray != null) {
			var menu = trayIcon.getPopupMenu();
			menu.removeAll();
		}
		menuEntries.clear();
	}

	private void rebuildMenu(boolean connected, List<VPNConnection> devs) {
		clearMenus();
		if (systemTray != null) {
			var menu = trayIcon.getPopupMenu();

			var open = new MenuItem(bundle.getString("open"));
			open.addActionListener((e) -> context.open());
			menuEntries.add(open);
			menu.add(open).setShortcut(new MenuShortcut('o'));
			menu.addSeparator();

			try {

				boolean devices = false;
				if (connected) {
					if (devs.size() == 1) {
						addDevice(devs.get(0), menu, devs);
						devices = true;
					} else {
						for (VPNConnection dev : devs) {
							var devmenu = addDevice(dev, null, devs);
							menu.add(devmenu);
							menuEntries.add(devmenu);
							devices = true;
						}
					}
				}
				if (devices)
					menu.addSeparator();
			} catch (Exception e) {
				// TODO add error item / tooltip?
				trayIcon.setToolTip("Error!");
				e.printStackTrace();
			}

			var options = new MenuItem(bundle.getString("options"));
			options.addActionListener((e) -> context.options());
			options.setShortcut(new MenuShortcut('p'));
			menuEntries.add(options);
			menu.add(options);

			var quit = new MenuItem(bundle.getString("quit"));
			quit.setShortcut(new MenuShortcut('q'));
			quit.addActionListener((e) -> {
				Platform.runLater(() -> context.confirmExit());
			});
			menuEntries.add(quit);
			menu.add(quit);
		}
	}

	private void setImage(boolean connected, List<VPNConnection> devs) {
		trayIcon.setImage(getImage(connected, devs));
	}
	
	private Image getImage(boolean connected, List<VPNConnection> devs) {
		if (context.getDBus().isBusAvailable()) {
			String icon = Configuration.getDefault().trayModeProperty().get();
			if (Configuration.TRAY_MODE_LIGHT.equals(icon)) {
				return overlay(AWTTray.class.getResource("light-logonbox-icon64x64.png"), 48, devs);
			} else if (Configuration.TRAY_MODE_DARK.equals(icon)) {
				return
						overlay(AWTTray.class.getResource("dark-logonbox-icon64x64.png"), DEFAULT_ICON_SIZE, devs);
			} else if (Configuration.TRAY_MODE_COLOR.equals(icon)) {
				return overlay(AWTTray.class.getResource("logonbox-icon64x64.png"), 48, devs);
			} else {
				if (isDark())
					return overlay(AWTTray.class.getResource("light-logonbox-icon64x64.png"), 48, devs);
				else
					return overlay(AWTTray.class.getResource("dark-logonbox-icon64x64.png"), 48, devs);
			}
		} else {
			return createAwesomeIcon(FontAwesome.EXCLAMATION_CIRCLE, 48, 100, Color.RED, 0);
		}
	}

}
