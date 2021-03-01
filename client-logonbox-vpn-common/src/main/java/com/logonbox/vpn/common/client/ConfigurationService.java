package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ConfigurationService extends Remote {

	public final static String AUTOMATIC_UPDATES = "automaticUpdates";
	public final static String PHASE = "phase";
	public static final String TRAY_ICON = "trayIcon";
	public static final String TRAY_ICON_DARK = "dark";
	public static final String TRAY_ICON_COLOR = "color";
	public static final String TRAY_ICON_LIGHT = "light";
	public static final String TRAY_ICON_AUTO = "auto";
	public static final String TRAY_ICON_OFF = "off";

	String getValue(String name, String defaultValue) throws RemoteException;
	
	void setValue(String name, String value) throws RemoteException; 
}
