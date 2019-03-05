package com.hypersocket.client.gui.jfx;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;

import com.hypersocket.client.rmi.Connection;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;

public class Configuration {

	public enum BrowserType {
		SYSTEM_BROWSER, RUN_COMMAND
	}

	private BooleanProperty showSSO = new SimpleBooleanProperty();
	private BooleanProperty showFiles = new SimpleBooleanProperty();
	private BooleanProperty showNetwork = new SimpleBooleanProperty();
	private BooleanProperty showBrowser = new SimpleBooleanProperty();
	private BooleanProperty top = new SimpleBooleanProperty();
	private BooleanProperty bottom = new SimpleBooleanProperty();
	private BooleanProperty left = new SimpleBooleanProperty();
	private BooleanProperty right = new SimpleBooleanProperty();
	private BooleanProperty autoHide = new SimpleBooleanProperty();
	private BooleanProperty hoverToReveal = new SimpleBooleanProperty();
	private BooleanProperty alwaysOnTop = new SimpleBooleanProperty();
	private BooleanProperty avoidReserved = new SimpleBooleanProperty();
	private StringProperty browserCommand = new SimpleStringProperty();
	private IntegerProperty size = new SimpleIntegerProperty();
	private IntegerProperty monitor = new SimpleIntegerProperty();
	private Property<Color> color = new SimpleObjectProperty<>();
	private Property<BrowserType> browserType = new SimpleObjectProperty<>();
	private StringProperty temporaryOnStartConnection = new SimpleStringProperty();
	private StringProperty saveCredentialsConnections = new SimpleStringProperty();

	//
	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			Preferences.userNodeForPackage(Configuration.class));

	class IntegerPreferenceUpdateChangeListener implements
			ChangeListener<Number> {

		private Preferences node;
		private String key;

		IntegerPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Number> observable,
				Number oldValue, Number newValue) {
			node.putInt(key, newValue.intValue());
		}

	}

	class BooleanPreferenceUpdateChangeListener implements
			ChangeListener<Boolean> {

		private Preferences node;
		private String key;

		BooleanPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Boolean> observable,
				Boolean oldValue, Boolean newValue) {
			node.putBoolean(key, newValue);
		}

	}

	class StringPreferenceUpdateChangeListener implements
			ChangeListener<String> {

		private Preferences node;
		private String key;

		StringPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends String> observable,
				String oldValue, String newValue) {
			node.put(key, newValue);
		}

	}

	class ColorPreferenceUpdateChangeListener implements ChangeListener<Color> {

		private Preferences node;
		private String key;

		ColorPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Color> observable,
				Color oldValue, Color newValue) {
			putColor(key, node, newValue);
		}

	}

	public Configuration(Preferences node) {
		browserType.setValue(BrowserType.valueOf(node.get("browserType",
				BrowserType.SYSTEM_BROWSER.name())));
		browserType.addListener(new ChangeListener<BrowserType>() {
			@Override
			public void changed(
					ObservableValue<? extends BrowserType> observable,
					BrowserType oldValue, BrowserType newValue) {
				node.put("browserType", newValue.name());
			}
		});

		browserCommand.set(node.get("browserCommand", ""));
		browserCommand.addListener(new StringPreferenceUpdateChangeListener(
				node, "browserCommand"));

		color.setValue(getColor("color", node, Color.web("#000000")));
		color.addListener(new ColorPreferenceUpdateChangeListener(node, "color"));

		showSSO.set(node.getBoolean("showSSO", true));
		showSSO.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"showSSO"));

		showBrowser.set(node.getBoolean("showBrowser", true));
		showBrowser.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"showBrowser"));

		showNetwork.set(node.getBoolean("showNetwork", true));
		showNetwork.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"showNetwork"));

		showFiles.set(node.getBoolean("showFiles", true));
		showFiles.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"showFiles"));

		autoHide.set(node.getBoolean("autoHide", true));
		autoHide.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"autoHide"));
		
		hoverToReveal.set(node.getBoolean("hoverToReveal", true));
		hoverToReveal.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"hoverToReveal"));
		
		temporaryOnStartConnection.set(node.get("temporaryOnStartConnection", ""));
		temporaryOnStartConnection.addListener(new StringPreferenceUpdateChangeListener(node, "temporaryOnStartConnection"));

		saveCredentialsConnections.set(node.get("saveCredentialsConnections", ""));
		saveCredentialsConnections.addListener(new StringPreferenceUpdateChangeListener(node, "saveCredentialsConnections"));

		alwaysOnTop.set(node.getBoolean("alwaysOnTop", true));
		alwaysOnTop.addListener(new BooleanPreferenceUpdateChangeListener(node,
				"alwaysOnTop"));

		avoidReserved.set(node.getBoolean("avoidReserved", true));
		avoidReserved.addListener(new BooleanPreferenceUpdateChangeListener(
				node, "avoidReserved"));
		
		monitor.set(node.getInt("monitor", 0));
		monitor.addListener(new IntegerPreferenceUpdateChangeListener(node, "monitor"));

		size.set((int)node.getDouble("size", 48));
		size.addListener(new ChangeListener<Number>() {

			@Override
			public void changed(ObservableValue<? extends Number> observable,
					Number oldValue, Number newValue) {
				node.putDouble("size", newValue.doubleValue());
			}
		});

		String position = node.get("position", "top");
		top.set(position.equals("top"));
		bottom.set(position.equals("bottom"));
		left.set(position.equals("left"));
		right.set(position.equals("right"));
		ChangeListener<Boolean> positionListener = new ChangeListener<Boolean>() {
			@Override
			public void changed(
					final ObservableValue<? extends Boolean> observable,
					Boolean oldValue, Boolean newValue) {
				if (newValue) {
					if (observable == left) {
						node.put("position", "left");
						top.set(false);
						bottom.set(false);
						right.set(false);
					} else if (observable == right) {
						node.put("position", "right");
						top.set(false);
						bottom.set(false);
						left.set(false);
					} else if (observable == top) {
						node.put("position", "top");
						right.set(false);
						bottom.set(false);
						left.set(false);
					} else if (observable == bottom) {
						node.put("position", "bottom");
						right.set(false);
						top.set(false);
						left.set(false);
					}
				}
			}
		};
		top.addListener(positionListener);
		bottom.addListener(positionListener);
		left.addListener(positionListener);
		right.addListener(positionListener);
	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
	}
	
	public boolean isSaveCredentials(Connection connection) {
		String creds = saveCredentialsConnections.get();
		return (StringUtils.isBlank(creds) ? Collections.emptyList() : Arrays.asList(creds.split(","))).contains(String.valueOf(connection.getId()));
	}
	
	public void setSaveCredentials(Connection connection, boolean saveCredentials) {
		String creds = saveCredentialsConnections.get();
		saveCredentialsConnections.set(String.join(",", new HashSet<>(StringUtils.isBlank(creds) ? Collections.emptyList() : Arrays.asList(creds.split(",")))));
	}

	public StringProperty temporaryOnStartConnectionProperty() {
		return temporaryOnStartConnection;
	}

	public StringProperty browserCommandProperty() {
		return browserCommand;
	}

	public Property<BrowserType> browserTypeProperty() {
		return browserType;
	}

	public IntegerProperty sizeProperty() {
		return size;
	}

	public IntegerProperty monitorProperty() {
		return monitor;
	}

	public BooleanProperty autoHideProperty() {
		return autoHide;
	}
	
	public BooleanProperty hoverToRevealProperty() {
		return hoverToReveal;
	}
	
	public BooleanProperty alwaysOnTopProperty() {
		return alwaysOnTop;
	}

	public BooleanProperty avoidReservedProperty() {
		return avoidReserved;
	}

	public BooleanProperty topProperty() {
		return top;
	}

	public BooleanProperty bottomProperty() {
		return bottom;
	}

	public BooleanProperty leftProperty() {
		return left;
	}

	public BooleanProperty rightProperty() {
		return right;
	}

	public BooleanProperty showSSOProperty() {
		return showSSO;
	}

	public BooleanProperty showWebProperty() {
		return showBrowser;
	}

	public BooleanProperty showNetworkProperty() {
		return showNetwork;
	}

	public BooleanProperty showFilesProperty() {
		return showFiles;
	}

	public Property<Color> colorProperty() {
		return color;
	}

	public boolean isVertical() {
		return left.get() || right.get();
	}

	static void putColor(String key, Preferences p, Color color) {
		p.putDouble(key + "_r", color.getRed());
		p.putDouble(key + "_g", color.getGreen());
		p.putDouble(key + "_b", color.getBlue());
		p.putDouble(key + "_a", color.getOpacity());
	}

	static Color getColor(String key, Preferences p, Color defaultColour) {
		return new Color(p.getDouble(key + "_r", defaultColour == null ? 1.0
				: defaultColour.getRed()), p.getDouble(key + "_g",
				defaultColour == null ? 1.0 : defaultColour.getGreen()),
				p.getDouble(key + "_b", defaultColour == null ? 1.0
						: defaultColour.getBlue()), p.getDouble(
						key + "_a",
						defaultColour == null ? 1.0 : defaultColour
								.getOpacity()));
	}
}
