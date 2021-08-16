package com.logonbox.vpn.client.gui.jfx;

import java.awt.Color;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.awt.Taskbar;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.SwingUtilities;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.AbstractDBusClient.BusLifecycleListener;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import javafx.application.Platform;

public class AWTTaskbarTray extends AbstractTray implements AutoCloseable, Tray, BusLifecycleListener {

	private Taskbar taskbar;

	final static Logger log = LoggerFactory.getLogger(UI.class);

	public AWTTaskbarTray(Client context) throws Exception {
		super(context);
		queueGuiOp(() -> {
			boolean connected = context.getDBus().isBusAvailable();
			List<VPNConnection> conx = connected ? context.getDBus().getVPNConnections() : Collections.emptyList();
			adjustTray(connected, conx);
		});
	}

	@Override
	public void onClose() throws Exception {
		taskbar = null;
	}

	public boolean isActive() {
		return taskbar != null;
	}

	protected void queueGuiOp(Runnable r) {
		if (SwingUtilities.isEventDispatchThread()) {
			r.run();
		} else
			SwingUtilities.invokeLater(r);
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
					if (taskbar != null)
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
			disconnectDev.addActionListener((e) -> Platform.runLater(() -> {
				context.getOpQueue().execute(() -> device.disconnect(""));
			}));
			menu.add(disconnectDev);
		} else if (devs.size() > 0 && status == Type.DISCONNECTED) {
			var openDev = new MenuItem(bundle.getString("connect"));
			openDev.addActionListener((e) -> Platform.runLater(() -> {
				context.getOpQueue().execute(() -> device.connect());
			}));
			menu.add(openDev);
		}

		return menu;
	}

	void adjustTray(boolean connected, List<VPNConnection> devs) {
		String icon = Configuration.getDefault().trayModeProperty().get();
		if (taskbar == null && !Objects.equals(icon, Configuration.TRAY_MODE_OFF)) {
			taskbar = Taskbar.getTaskbar();
			if (taskbar == null) {
				throw new RuntimeException("Unable to load SystemTray!");
			}

			setImage(connected, devs);

			PopupMenu menu = taskbar.getMenu();
			if (menu == null) {
				taskbar.setMenu(new PopupMenu(bundle.getString("title")));
			}

			rebuildMenu(connected, devs);

		} else if (taskbar != null && Configuration.TRAY_MODE_OFF.equals(icon)) {
			taskbar = null;
		} else if (taskbar != null) {
			setImage(connected, devs);
			rebuildMenu(connected, devs);
		}
	}

	private void clearMenus() {
		if (taskbar != null) {
			var menu = taskbar.getMenu();
			menu.removeAll();
		}
	}

	private void rebuildMenu(boolean connected, List<VPNConnection> devs) {
		clearMenus();
		if (taskbar != null) {
			var menu = taskbar.getMenu();

			var open = new MenuItem(bundle.getString("open"), new MenuShortcut('o'));
			open.addActionListener((e) -> context.open());
			menu.add(open);
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
							taskbar.getMenu().add(devmenu);
							devices = true;
						}
					}
				}
				if (devices)
					addSeparator(menu);
			} catch (Exception e) {
				log.error("Taskbar error.", e);
			}

			var options = new MenuItem(bundle.getString("vpnOptions"), new MenuShortcut('p'));
			options.addActionListener((e) -> context.options());
			menu.add(options);

		}
	}

	protected void addSeparator(PopupMenu menu) {
		menu.addSeparator();
	}

	private void setImage(boolean connected, List<VPNConnection> devs) {
		if (context.getDBus().isBusAvailable()) {
			taskbar.setIconImage(overlay(Main.class.getResource("logonbox-icon128x128.png"), 128, devs));
		} else {
			taskbar.setIconImage(createAwesomeIcon(FontAwesome.EXCLAMATION_CIRCLE, 48, 100, Color.RED, 0));
		}
	}

}
