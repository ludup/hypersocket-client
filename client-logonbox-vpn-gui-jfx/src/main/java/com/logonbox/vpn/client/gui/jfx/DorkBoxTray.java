package com.logonbox.vpn.client.gui.jfx;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
//import java.awt.AlphaComposite;
//import java.awt.Color;
//import java.awt.Font;
//import java.awt.Graphics2D;
//import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.imageio.ImageIO;
import javax.swing.JMenu;

import org.kordamp.ikonli.fontawesome.FontAwesome;
import org.kordamp.ikonli.fontawesome.FontAwesomeIkonHandler;

import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;

import dorkbox.systemTray.Entry;
import dorkbox.systemTray.Menu;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.Separator;
import dorkbox.systemTray.SystemTray;
import javafx.application.Platform;

public class DorkBoxTray implements AutoCloseable, com.logonbox.vpn.client.gui.jfx.Bridge.Listener, Tray {

	private static final int DEFAULT_ICON_SIZE = 48;
	private Client context;
	private ScheduledExecutorService executor;
	private Font font;
	private List<Entry> menuEntries = new ArrayList<>();
	private SystemTray systemTray;

	public DorkBoxTray(Client context) throws Exception {
		this.context = context;
		executor = Executors.newScheduledThreadPool(1);
		context.getBridge().addListener(this);
		executor.execute(() -> adjustTray(false, Collections.emptyList()));
	}

	@Override
	public void bridgeEstablished() {
		reload();
	}

	@Override
	public void bridgeLost() {
		reload();
	}

	@Override
	public void close() throws Exception {
		context.getBridge().removeListener(this);
		if (systemTray != null) {
			systemTray.shutdown();
			systemTray = null;
		}
		executor.shutdown();
	}

	@Override
	public void configurationUpdated(String name, String value) {
		if (name.equals(ConfigurationService.TRAY_MODE)) {
			reload();
		}
	}

	@Override
	public void connectionAdded(Connection connection) {
		reload();
	}

	@Override
	public void connectionRemoved(Connection connection) {
		reload();
	}

	@Override
	public void connectionUpdated(Connection connection) {
		reload();
	}

	@Override
	public void disconnected(Connection connection, Exception e) {
		reload();
	}

	public boolean isActive() {
		return systemTray != null;
	}

	@Override
	public void started(Connection connection) {
		reload();
	}

	protected String getTrayIconMode() {
		if (!context.getBridge().isConnected())
			return ConfigurationService.TRAY_MODE_AUTO;
		try {
			String icon = context.getBridge().getConfigurationService().getValue(ConfigurationService.TRAY_MODE,
					ConfigurationService.TRAY_MODE_AUTO);
			return icon;
		} catch (RemoteException re) {
			return ConfigurationService.TRAY_MODE_AUTO;
		}
	}

	protected void reload() {
		executor.execute(() -> {
			try {
				List<ConnectionStatus> conx = context.getBridge().getClientService().getStatus();
				boolean connected = context.getBridge().isConnected();
				rebuildMenu(connected, conx);
				setImage(connected, conx);
			} catch (RemoteException re) {
				executor.execute(() -> {
					rebuildMenu(false, Collections.emptyList());
					setImage(false, Collections.emptyList());
				});
			}
		});
	}

	Menu addDevice(ConnectionStatus statusObj, Menu toMenu, List<ConnectionStatus> devs) throws IOException {
		Connection device = statusObj.getConnection();
		Menu menu = null;
		if (toMenu == null) {
			if (menu == null)
				menu = new Menu(device.getDisplayName());
		} else
			menu = toMenu;

		/* Open */
		Type status = context.getBridge().getClientService().getStatus(device);
		if (status == Type.CONNECTED) {
			var disconnectDev = new MenuItem(bundle.getString("disconnect"), (e) -> Platform.runLater(() -> {
				UI.getInstance().disconnect(device);
			}));
			menu.add(disconnectDev);
			menuEntries.add(disconnectDev);
		} else if (devs.size() > 0 && status == Type.DISCONNECTED) {
			var openDev = new MenuItem(bundle.getString("connect"), (e) -> Platform.runLater(() -> {
				UI.getInstance().connect(device);
			}));
			menu.add(openDev);
			menuEntries.add(openDev);
		}

		return menu;
	}

	void adjustTray(boolean connected, List<ConnectionStatus> devs) {
		String icon = getTrayIconMode();
		if (systemTray == null && !Objects.equals(icon, ConfigurationService.TRAY_MODE_OFF)) {
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

		} else if (systemTray != null && ConfigurationService.TRAY_MODE_OFF.equals(icon)) {
			systemTray.setEnabled(false);
		} else if (systemTray != null) {
			systemTray.setEnabled(true);
			setImage(connected, devs);
			rebuildMenu(connected, devs);
		}
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
		var szf = (int) ((float) sz * 1f);
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
			if (ConfigurationService.TRAY_MODE_LIGHT.equals(icon)) {
				graphics.setColor(Color.WHITE);
			} else if (ConfigurationService.TRAY_MODE_DARK.equals(icon)) {
				graphics.setColor(Color.BLACK);
			}
		} else {
			graphics.setColor(col);
		}

		graphics.translate(sz / 2, sz / 2);
		graphics.rotate(Math.toRadians(rot));
		graphics.translate(-(graphics.getFontMetrics().stringWidth(str) / 2f),
				(graphics.getFontMetrics().getHeight() / 3f));
		graphics.drawString(str, 0, 0);
		return bim;
	}

	AlphaComposite makeComposite(float alpha) {
		int type = AlphaComposite.SRC_OVER;
		return (AlphaComposite.getInstance(type, alpha));
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

	private boolean isDark() {
		// TODO
		return true;
	}

	private Image overlay(URL resource, int sz, List<ConnectionStatus> devs) {
		try {
			int connecting = 0;
			int connected = 0;
			int authorizing = 0;
			int total = 0;
			for (ConnectionStatus s : devs) {
				if (s.getStatus() == Type.CONNECTED)
					connected++;
				else if (s.getStatus() == Type.AUTHORIZING)
					authorizing++;
				else if (s.getStatus() == Type.CONNECTING)
					connecting++;
				total++;
			}
			Color col = null;
			if (total > 0) {
				if (authorizing > 0)
					col = Color.GREEN;
				else if (connecting > 0)
					col = Color.BLUE;
				else if (connected == total)
					col = Color.GREEN;
				else if (connected > 0)
					col = Color.ORANGE;
				else if (total > 0)
					col = Color.RED;
			}
			Image original = ImageIO.read(resource);
			var bim = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_ARGB);
			var graphics = (Graphics2D) bim.getGraphics();
			graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			graphics.drawImage(original, 0, 0, sz, sz, null);
			if (col != null) {
				graphics.setColor(col);
				graphics.fillOval(0, 0, sz / 3, sz / 3);
			}
			return bim;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to load image.", e);
		}
	}

	private void rebuildMenu(boolean connected, List<ConnectionStatus> devs) {
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
						for (ConnectionStatus dev : devs) {
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

	private void setImage(boolean connected, List<ConnectionStatus> devs) {
		if (context.getBridge().isConnected()) {
			String icon = getTrayIconMode();
			if (ConfigurationService.TRAY_MODE_LIGHT.equals(icon)) {
				systemTray.setImage(overlay(DorkBoxTray.class.getResource("light-logonbox-icon64x64.png"), 48, devs));
			} else if (ConfigurationService.TRAY_MODE_DARK.equals(icon)) {
				systemTray.setImage(
						overlay(DorkBoxTray.class.getResource("dark-logonbox-icon64x64.png"), DEFAULT_ICON_SIZE, devs));
			} else if (ConfigurationService.TRAY_MODE_COLOR.equals(icon)) {
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
