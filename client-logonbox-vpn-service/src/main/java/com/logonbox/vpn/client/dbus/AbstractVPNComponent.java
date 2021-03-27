package com.logonbox.vpn.client.dbus;

import org.freedesktop.dbus.connections.impl.DBusConnection;

import com.logonbox.vpn.client.LocalContext;

public abstract class AbstractVPNComponent {

	private LocalContext ctx;

	protected AbstractVPNComponent(LocalContext ctx) {
		this.ctx = ctx;
	}

	void assertRegistered() {
		if (ctx.isRegistrationRequired() && !ctx.hasFrontEnd(DBusConnection.getCallInfo().getSource())) {
			throw new IllegalStateException("Not registered.");
		}
	}

	String getOwner() {
		String src = DBusConnection.getCallInfo().getSource();
		if (ctx.hasFrontEnd(src)) {
			return ctx.getFrontEnd(src).getUsername();
		}
		return System.getProperty("user.name");
	}
}
