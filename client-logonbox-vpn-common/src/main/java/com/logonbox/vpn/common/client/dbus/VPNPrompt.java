package com.logonbox.vpn.common.client.dbus;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

@DBusInterfaceName("com.logonbox.vpn.Prompt")
public interface VPNPrompt extends DBusInterface {

	String getTitle();

	String getText();

	String[] getChoices();

	void choice(String choice);

}
