package com.logonbox.vpn.client.gui.jfx;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.apache.commons.lang3.StringUtils;

import com.hypersocket.client.rmi.Connection;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.paint.Color;

public class Configuration {

	private StringProperty temporaryOnStartConnection = new SimpleStringProperty();
	private StringProperty saveCredentialsConnections = new SimpleStringProperty();
	private UUID deviceUUID;

	//
	private final static Configuration DEFAULT_INSTANCE = new Configuration(
			Preferences.userNodeForPackage(Configuration.class));

	class IntegerPreferenceUpdateChangeListener implements ChangeListener<Number> {

		private Preferences node;
		private String key;

		IntegerPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
			node.putInt(key, newValue.intValue());
		}

	}

	class BooleanPreferenceUpdateChangeListener implements ChangeListener<Boolean> {

		private Preferences node;
		private String key;

		BooleanPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
			node.putBoolean(key, newValue);
		}

	}

	class StringPreferenceUpdateChangeListener implements ChangeListener<String> {

		private Preferences node;
		private String key;

		StringPreferenceUpdateChangeListener(Preferences node, String key) {
			this.node = node;
			this.key = key;
		}

		@Override
		public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
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
		public void changed(ObservableValue<? extends Color> observable, Color oldValue, Color newValue) {
			putColor(key, node, newValue);
		}

	}

	public Configuration(Preferences node) {
		temporaryOnStartConnection.set(node.get("temporaryOnStartConnection", ""));
		temporaryOnStartConnection
				.addListener(new StringPreferenceUpdateChangeListener(node, "temporaryOnStartConnection"));

		saveCredentialsConnections.set(node.get("saveCredentialsConnections", ""));
		saveCredentialsConnections
				.addListener(new StringPreferenceUpdateChangeListener(node, "saveCredentialsConnections"));

		String deviceUUIDString = node.get("deviceUUID", "");
		if(deviceUUIDString.equals("")) {
			deviceUUID = UUID.randomUUID();
			try {
				node.put("deviceUUID", deviceUUID.toString());
				node.flush();
			}
			catch(BackingStoreException bse) {
				throw new IllegalStateException("Failed to save device UUID.", bse);
			}
		}
		else
			deviceUUID = UUID.fromString(deviceUUIDString);
	}

	public static Configuration getDefault() {
		return DEFAULT_INSTANCE;
	}

	public boolean isSaveCredentials(Connection connection) {
		String creds = saveCredentialsConnections.get();
		return (StringUtils.isBlank(creds) ? Collections.emptyList() : Arrays.asList(creds.split(",")))
				.contains(String.valueOf(connection.getId()));
	}

	public void setSaveCredentials(Connection connection, boolean saveCredentials) {
		String creds = saveCredentialsConnections.get();
		saveCredentialsConnections.set(String.join(",",
				new HashSet<>(StringUtils.isBlank(creds) ? Collections.emptyList() : Arrays.asList(creds.split(",")))));
	}
	
	public UUID getDeviceUUID() {
		return deviceUUID;
	}

	public StringProperty temporaryOnStartConnectionProperty() {
		return temporaryOnStartConnection;
	}

	static void putColor(String key, Preferences p, Color color) {
		p.putDouble(key + "_r", color.getRed());
		p.putDouble(key + "_g", color.getGreen());
		p.putDouble(key + "_b", color.getBlue());
		p.putDouble(key + "_a", color.getOpacity());
	}

	static Color getColor(String key, Preferences p, Color defaultColour) {
		return new Color(p.getDouble(key + "_r", defaultColour == null ? 1.0 : defaultColour.getRed()),
				p.getDouble(key + "_g", defaultColour == null ? 1.0 : defaultColour.getGreen()),
				p.getDouble(key + "_b", defaultColour == null ? 1.0 : defaultColour.getBlue()),
				p.getDouble(key + "_a", defaultColour == null ? 1.0 : defaultColour.getOpacity()));
	}
}
