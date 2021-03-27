package com.logonbox.vpn.client.cli;

import java.io.IOException;
import java.util.List;

import org.freedesktop.dbus.connections.impl.DBusConnection;

import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public interface CLIContext {
	VPN getVPN();

	VPNConnection getVPNConnection(long connectionId);

	ConsoleProvider getConsole();

	List<VPNConnection> getVPNConnections();

	void about() throws IOException;

	void exitWhenDone();

	boolean isQuiet();

	DBusConnection getBus();
}
