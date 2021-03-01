package com.logonbox.vpn.client.gui.jfx;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
//import java.awt.AlphaComposite;
//import java.awt.Color;
//import java.awt.Font;
//import java.awt.Graphics2D;
//import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.JMenu;
import javax.swing.SwingUtilities;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.fontawesome.FontAwesomeIkonHandler;

import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import javafx.application.Platform;

public class DorkBoxTray implements AutoCloseable, com.logonbox.vpn.client.gui.jfx.Bridge.Listener, Tray {

	final static System.Logger LOG = System.getLogger(DorkBoxTray.class.getName());


	private Client context;
	private SystemTray systemTray;
	private List<Entry> menuEntries = new ArrayList<>();
	private Font font;

	public DorkBoxTray(Client context) throws Exception {
		this.context = context;

		context.getBridge().addListener(this);
		adjustTray();

	}

	@Override
	public void close() throws Exception {
		context.getBridge().removeListener(this);
		if (systemTray != null) {
			systemTray.shutdown();
			systemTray = null;
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
			var disconnectDev = new MenuItem(bundle.getString("disconnect"), (e) -> Platform.runLater(() -> {
				UI.getInstance().disconnect(device);
			}));
			menu.add(disconnectDev);
			menuEntries.add(disconnectDev);
		} else {
			var openDev = new MenuItem(bundle.getString("connect"), (e) -> Platform.runLater(() -> {
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
		if (systemTray == null && !Objects.equals(icon, ConfigurationService.TRAY_ICON_OFF)) {
			systemTray = SystemTray.get();
			if (systemTray == null) {
				throw new RuntimeException("Unable to load SystemTray!");
			}

			setImage();

			systemTray.setStatus(bundle.getString("title"));
			Menu menu = systemTray.getMenu();
			if (menu == null) {
				systemTray.setMenu(new JMenu(bundle.getString("title")));
			}

			rebuildMenu();

		} else if (systemTray != null && ConfigurationService.TRAY_ICON_OFF.equals(icon)) {
			systemTray.setEnabled(false);
		} else if (systemTray != null) {
			systemTray.setEnabled(true);
			setImage();
			rebuildMenu();
		}
	}

	private void rebuildMenu() {
		clearMenus();
		if (systemTray != null) {
			var menu = systemTray.getMenu();

			var open = new MenuItem(bundle.getString("open"), (e) -> context.open());
			menuEntries.add(open);
			menu.add(open).setShortcut('o');
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

			var quit = new MenuItem(bundle.getString("quit"), (e) -> context.confirmExit());
			menuEntries.add(quit);
			menu.add(quit).setShortcut('q');
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

	private void setImage() {
		String icon = getTrayIconMode();
		if (ConfigurationService.TRAY_ICON_LIGHT.equals(icon)) {
			systemTray.setImage(DorkBoxTray.class.getResource("light-logonbox-icon64x64.png"));
		} else if (ConfigurationService.TRAY_ICON_DARK.equals(icon)) {
			systemTray.setImage(DorkBoxTray.class.getResource("dark-logonbox-icon64x64.png"));
		} else if (ConfigurationService.TRAY_ICON_COLOR.equals(icon)) {
			systemTray.setImage(DorkBoxTray.class.getResource("logonbox-icon64x64.png"));
		} else {
			if (isDark())
				systemTray.setImage(DorkBoxTray.class.getResource("light-logonbox-icon64x64.png"));
			else
				systemTray.setImage(DorkBoxTray.class.getResource("dark-logonbox-icon64x64.png"));
		}
	}

	@Override
	public void started(Connection connection) {
		SwingUtilities.invokeLater(() -> rebuildMenu());
	}

	@Override
	public void disconnected(Connection connection, Exception e) {
		SwingUtilities.invokeLater(() -> rebuildMenu());
	}

	@Override
	public void connectionAdded(Connection connection) {
		SwingUtilities.invokeLater(() -> rebuildMenu());
	}

	@Override
	public void connectionRemoved(Connection connection) {
		SwingUtilities.invokeLater(() -> rebuildMenu());
	}

	@Override
	public void connectionUpdated(Connection connection) {
		SwingUtilities.invokeLater(() -> rebuildMenu());
	}

	@Override
	public void configurationUpdated(String name, String value) {
		if (name.equals(ConfigurationService.TRAY_ICON)) {
			SwingUtilities.invokeLater(() -> rebuildMenu());
		}
	}

	private boolean isDark() {
		// TODO
		return true;
	}

	public boolean isActive() {
		return systemTray != null;
	}

}
