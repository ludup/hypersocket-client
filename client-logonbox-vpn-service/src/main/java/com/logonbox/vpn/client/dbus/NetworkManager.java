package com.logonbox.vpn.client.dbus;

import java.util.Map;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.Position;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

@DBusInterfaceName("org.freedesktop.NetworkManager")
public interface NetworkManager extends DBusInterface {
	DBusPath GetDeviceByIpIface(String iface);

	@DBusInterfaceName("org.freedesktop.NetworkManager.Settings")
	public interface Settings extends DBusInterface {
		DBusPath[] ListConnections();
		
		@DBusInterfaceName("org.freedesktop.NetworkManager.Settings.Connection")
		public interface Connection extends DBusInterface {
			Map<String, Map<String, Variant<?>>> GetSettings();
			void Update(Map<String, Map<String, Variant<?>>> settings);
			void Save();
		}
	}

	public static class Ipv6Address extends Struct {
		@Position(0)
		private byte[] unknown1;
		@Position(1)
		private UInt32 unknown2;
		@Position(2)
		private byte[] unknown3;
		@Position(1)
		private UInt32 unknown4;
		
	}
}
