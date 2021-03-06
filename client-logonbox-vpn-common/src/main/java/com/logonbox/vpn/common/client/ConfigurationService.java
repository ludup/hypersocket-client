package com.logonbox.vpn.common.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ConfigurationService extends Remote {

	public final static String AUTOMATIC_UPDATES = "automaticUpdates";
	public final static String PHASE = "phase";
	public static final String TRAY_MODE = "trayMode";
	public static final String TRAY_MODE_DARK = "dark";
	public static final String TRAY_MODE_COLOR = "color";
	public static final String TRAY_MODE_LIGHT = "light";
	public static final String TRAY_MODE_AUTO = "auto";
	public static final String TRAY_MODE_OFF = "off";

	String getValue(String name, String defaultValue) throws RemoteException;
	
	void setValue(String name, String value) throws RemoteException; 
}
