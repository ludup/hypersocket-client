package com.logonbox.vpn.client.gui.jfx;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Menu;
import java.awt.MenuComponent;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.fontawesome.FontAwesomeIkonHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;

import javafx.application.Platform;

public class WindowsTray implements AutoCloseable, com.logonbox.vpn.client.gui.jfx.Bridge.Listener, Tray {

	static Logger log = LoggerFactory.getLogger(Client.class);

	private Client context;
	private List<MenuComponent> menuEntries = new ArrayList<>();
	private Font font;

	private TrayIcon trayIcon;

	public WindowsTray(Client context) throws Exception {
		this.context = context;

		context.getBridge().addListener(this);
		Platform.runLater(() -> adjustTray());

	}

	@Override
	public void close() throws Exception {
		context.getBridge().removeListener(this);
		removeTray();
	}

	void removeTray() {
		if (trayIcon != null) {
			SystemTray.getSystemTray().remove(trayIcon);
			trayIcon = null;
		}
	}

	Menu addDevice(Connection device, Menu toMenu) throws IOException {
		Menu menu = null;
		if (toMenu == null) {
			if (menu == null)
				menu = new Menu(device.getDisplayName());
		} else
			menu = toMenu;

		/* Open */
		boolean connected = context.getBridge().getClientService().isConnected(device);
		if (connected) {
			var disconnectDev = createMenuItem(bundle.getString("disconnect"), (e) -> Platform.runLater(() -> {
				UI.getInstance().disconnect(device);
			}));
			menu.add(disconnectDev);
			menuEntries.add(disconnectDev);
		} else {
			var openDev = createMenuItem(bundle.getString("connect"), (e) -> Platform.runLater(() -> {
				UI.getInstance().joinNetwork(device);
			}));
			menu.add(openDev);
			menuEntries.add(openDev);
		}

//		menu.add(new JSeparator());

		return menu;
	}

	BufferedImage createAwesomeIcon(FontAwesome string, int sz) {
		return createAwesomeIcon(string, sz, 100);
	}

	BufferedImage createAwesomeIcon(FontAwesome string, int sz, int opac) {
		return createAwesomeIcon(string, sz, opac, null, 0);
	}

	BufferedImage createAwesomeIcon(FontAwesome fontAwesome, int sz, int opac, Color col, double rot) {
		var bim = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
		var graphics = (Graphics2D) bim.getGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		var szf = (int) ((float) sz * 0.75f);
		String str = Character.toString(fontAwesome.getCode());
		FontAwesomeIkonHandler handler = new FontAwesomeIkonHandler();
		if (font == null) {
			try {
				InputStream stream = handler.getFontResourceAsStream();
				font = Font.createFont(Font.TRUETYPE_FONT, stream);
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
				stream.close();
				handler.setFont(font);
			} catch (FontFormatException | IOException ffe) {
				throw new IllegalStateException(ffe);
			}
		}
		var fnt = font.deriveFont((float) szf);
		graphics.setFont(fnt);
		if (opac != 100) {
			graphics.setComposite(makeComposite((float) opac / 100.0f));
		}
		String icon = getTrayIconMode();
		if (col == null) {
			if (ConfigurationService.TRAY_ICON_LIGHT.equals(icon)) {
				graphics.setColor(Color.WHITE);
			} else if (ConfigurationService.TRAY_ICON_DARK.equals(icon)) {
				graphics.setColor(Color.BLACK);
			}
		} else {
			graphics.setColor(col);
		}

		graphics.translate(sz / 2, sz / 2);
		graphics.rotate(Math.toRadians(rot));
		graphics.translate(-(graphics.getFontMetrics().stringWidth(str) / 2f),
				(graphics.getFontMetrics().getHeight() / 2f));
		graphics.drawString(str, 0, 0);
		return bim;
	}

	protected String getTrayIconMode() {
		if (!context.getBridge().isConnected())
			return ConfigurationService.TRAY_ICON_AUTO;
		try {
			String icon = context.getBridge().getConfigurationService().getValue(ConfigurationService.TRAY_ICON,
					ConfigurationService.TRAY_ICON_AUTO);
			return icon;
		} catch (RemoteException re) {
			return ConfigurationService.TRAY_ICON_AUTO;
		}
	}

	AlphaComposite makeComposite(float alpha) {
		int type = AlphaComposite.SRC_OVER;
		return (AlphaComposite.getInstance(type, alpha));
	}

	void adjustTray() {
		String icon = getTrayIconMode();
		if (trayIcon == null && !Objects.equals(icon, ConfigurationService.TRAY_ICON_OFF)) {
			if (SystemTray.isSupported()) {
				SystemTray systemTray = SystemTray.getSystemTray();

				try {
					trayIcon = new TrayIcon(ImageIO.read(getImage()));
				} catch (IOException e) {
					log.error("Failed to set tray image.", e);
				}
				try {
					systemTray.add(trayIcon);
				} catch (AWTException e) {
					log.error("Failed to add tray image.", e);
				}

				PopupMenu menu = trayIcon.getPopupMenu();
				if (menu == null) {
					trayIcon.setPopupMenu(new PopupMenu(bundle.getString("title")));
				} else
					menu.setLabel(bundle.getString("title"));

				rebuildMenu();
			} else {
				log.warn("DorkBoxTray icon is on, but this platform has no implementation.");
			}

		} else if (trayIcon != null && ConfigurationService.TRAY_ICON_OFF.equals(icon)) {
			removeTray();
		} else if (trayIcon != null) {
			try {
				trayIcon.setImage(ImageIO.read(getImage()));
			} catch (IOException e) {
				log.error("Failed to set tray image.", e);
			}
			rebuildMenu();
		}
	}

	private void rebuildMenu() {
		clearMenus();
		if (trayIcon != null) {
			var menu = trayIcon.getPopupMenu();

			var open = createMenuItem(bundle.getString("open"), (e) -> context.open());
			open.setShortcut(new MenuShortcut('o'));
			menuEntries.add(open);
			addSeparator(menu);

			try {

				boolean devices = false;
				if (context.getBridge().isConnected()) {
					List<Connection> devs = context.getBridge().getConnectionService().getConnections();
					if (devs.size() == 1) {
						addDevice(devs.get(0), menu);
						devices = true;
					} else {
						for (Connection dev : devs) {
							var devmenu = addDevice(dev, null);
							trayIcon.getPopupMenu().add(devmenu);
							menuEntries.add(devmenu);
							devices = true;
						}
					}
				}
				if (devices)
					addSeparator(menu);
			} catch (Exception e) {
				// TODO add error item / tooltip?
				trayIcon.setToolTip("Error!");
				e.printStackTrace();
			}

			var options = createMenuItem(bundle.getString("options"), (e) -> context.options());
			options.setShortcut(new MenuShortcut('o'));
			menuEntries.add(options);

			var quit = createMenuItem(bundle.getString("quit"), (e) -> context.confirmExit());
			menuEntries.add(quit);
			options.setShortcut(new MenuShortcut('q'));
		}
	}

	private MenuItem createMenuItem(String label, ActionListener listener) {
		MenuItem item = new MenuItem(label);
		item.addActionListener(listener);
		return item;
	}

	private void addSeparator(Menu menu) {
		menu.addSeparator();
		menuEntries.add(null);

	}

	private void clearMenus() {
		if (trayIcon != null) {
			var menu = trayIcon.getPopupMenu();
			menu.removeAll();
		}
		menuEntries.clear();
	}

	private URL getImage() {
		String icon = getTrayIconMode();
		if (ConfigurationService.TRAY_ICON_LIGHT.equals(icon)) {
			return WindowsTray.class.getResource("light-logonbox-icon64x64.png");
		} else if (ConfigurationService.TRAY_ICON_DARK.equals(icon)) {
			return WindowsTray.class.getResource("dark-logonbox-icon64x64.png");
		} else if (ConfigurationService.TRAY_ICON_COLOR.equals(icon)) {
			return WindowsTray.class.getResource("logonbox-icon64x64.png");
		} else {
			if (isDark())
				return WindowsTray.class.getResource("light-logonbox-icon64x64.png");
			else
				return WindowsTray.class.getResource("dark-logonbox-icon64x64.png");
		}
	}

	@Override
	public void started(Connection connection) {
		Platform.runLater(() -> rebuildMenu());
	}

	@Override
	public void disconnected(Connection connection, Exception e) {
		Platform.runLater(() -> rebuildMenu());
	}

	@Override
	public void connectionAdded(Connection connection) {
		Platform.runLater(() -> rebuildMenu());
	}

	@Override
	public void connectionRemoved(Connection connection) {
		Platform.runLater(() -> rebuildMenu());
	}

	@Override
	public void connectionUpdated(Connection connection) {
		Platform.runLater(() -> rebuildMenu());
	}

	@Override
	public void configurationUpdated(String name, String value) {
		if (name.equals(ConfigurationService.TRAY_ICON)) {
			Platform.runLater(() -> rebuildMenu());
		}
	}

	private boolean isDark() {
		// TODO
		return true;
	}

	public boolean isActive() {
		return trayIcon != null;
	}

}
