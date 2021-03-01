package com.logonbox.vpn.client.gui.jfx;

import java.util.ResourceBundle;

public interface Tray {

	final static ResourceBundle bundle = ResourceBundle.getBundle(Tray.class.getName());
	
	boolean isActive();
}
