package com.logonbox.vpn.common.client.dbus;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public interface DBusClient {

	default String getServerDBusAddress() {
		Properties properties = new Properties();
		String path;
		if (System.getProperty("hypersocket.dbus") != null) {
			path = System.getProperty("hypersocket.dbus");
		} else if (Boolean.getBoolean("hypersocket.development")) {
			path = System.getProperty("user.home") + File.separator + ".logonbox-vpn-client" + File.separator + "conf"
					+ File.separator + "dbus.properties";
		} else {
			path = "conf" + File.separator + "dbus.properties";
		}
		File file = new File(path);
		if (file.exists()) {
			try (FileInputStream in = new FileInputStream(file)) {
				properties.load(in);
				String addr = properties.getProperty("address", "");
				if (addr.equals(""))
					throw new IllegalStateException("DBus address file exists, but has no content.");
				return addr;
			} catch (IOException ioe) {
				throw new IllegalStateException("Failed to read DBus address file.", ioe);
			}
		} else
			return null;
	}

}
