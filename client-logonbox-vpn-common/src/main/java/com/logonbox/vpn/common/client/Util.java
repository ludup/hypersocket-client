package com.logonbox.vpn.common.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Util {


	/**
	 * Parse a space separated string into a list, treating portions quotes with
	 * single quotes as a single element. Single quotes themselves and spaces can be
	 * escaped with a backslash.
	 * 
	 * @param command command to parse
	 * @return parsed command
	 */
	public static List<String> parseQuotedString(String command) {
		List<String> args = new ArrayList<String>();
		boolean escaped = false;
		boolean quoted = false;
		StringBuilder word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '"' && !escaped) {
				if (quoted) {
					quoted = false;
				} else {
					quoted = true;
				}
			} else if (c == '\\' && !escaped) {
				escaped = true;
			} else if (c == ' ' && !escaped && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString());
					word.setLength(0);
					;
				}
			} else {
				word.append(c);
			}
		}
		if (word.length() > 0)
			args.add(word.toString());
		return args;
	}
	
	public static String getUri(Connection connection) {
		if (connection == null) {
			return "";
		}
		return connection.getUri(false);
	}
	
	public static void prepareConnectionWithURI(URI uriObj, Connection connection) {
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException(
					"Only HTTPS is supported.");
		}

		connection.setHostname(uriObj.getHost());
		connection.setPort(uriObj.getPort() <= 0 ? 443 : uriObj.getPort());
		connection.setConnectAtStartup(false);
		connection.setPath(uriObj.getPath());
	}

	public static URI getUri(String uriString) throws URISyntaxException {
		if (!uriString.startsWith("https://")) {
			if (uriString.indexOf("://") != -1) {
				throw new IllegalArgumentException("Only HTTPS is supported.");
			}
			uriString = "https://" + uriString;
		}
		while (uriString.endsWith("/"))
			uriString = uriString.substring(0, uriString.length() - 1);
		URI uri = new URI(uriString);
		if ("".equals(uri.getPath()))
			uri = uri.resolve("/app");
		return uri;
	}
}
