package com.logonbox.vpn.client.wireguard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.lang3.SystemUtils;

public class OsUtil {

	public static boolean doesCommandExist(String command) {
		return getPathOfCommandInPath(command) != null;
	}

	public static Path getPathOfCommandInPathOrFail(String command) throws IOException {
		Path p = getPathOfCommandInPath(command);
		if(p == null)
			throw new IOException("Could not location command '" + command + "'.");
		return p;
	}
	
	public static Path getPathOfCommandInPath(String command) {
		Set<String> path = new LinkedHashSet<>(Arrays.asList(System.getenv("PATH").split(File.pathSeparator)));
		if (SystemUtils.IS_OS_MAC_OSX) {
			/* Hack for brew */
			path.add("/usr/local/bin");
		}
		for (String dir : path) {
			Path wg = Paths.get(dir, command);
			if (Files.exists(wg))
				return wg;
		}
		return null;
	}
}
