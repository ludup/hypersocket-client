package com.logonbox.vpn.client.gui.jfx;

import java.util.ResourceBundle;

public interface Tray extends AutoCloseable {

	final static ResourceBundle bundle = ResourceBundle.getBundle(Tray.class.getName());
	
	boolean isActive();
	
	void reload();
	
	default boolean isConfigurable() {
		return true;
	}
}
