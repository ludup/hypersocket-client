package com.logonbox.vpn.client;

import java.util.Collection;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.messages.Message;

import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;

public interface LocalContext {

	PlatformService<?> getPlatformService();
	
	ClientService getClientService();

	void sendMessage(Message message);
	
	VPNFrontEnd registerFrontEnd(String source);
	
	VPNFrontEnd getFrontEnd(String source);

	boolean hasFrontEnd(String source);

	void deregisterFrontEnd(String source);
	
	boolean isRegistrationRequired();

	DBusConnection getConnection();

	Collection<VPNFrontEnd> getFrontEnds();
}
