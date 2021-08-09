package com.logonbox.vpn.client.wireguard;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;

public class OsUtil {

	public static boolean doesCommandExist(String command) {
		Set<String> path = new LinkedHashSet<>(Arrays.asList(System.getenv("PATH").split(File.pathSeparator)));
		if (SystemUtils.IS_OS_MAC_OSX) {
			path.add("/usr/local/bin");
		}
		for (String dir : path) {
			File wg = new File(dir, command);
			if (wg.exists())
				return true;
		}
		return false;
	}
}
