package com.logonbox.vpn.common.client;

import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	public static URI getUri(String uriString) throws URISyntaxException {
		if (!uriString.startsWith("https://")) {
			if (uriString.indexOf("://") != -1) {
				throw new IllegalArgumentException(
						"Only HTTPS is supported.");
			}
			uriString = "https://" + uriString;
		}
		URI uri = new URI(uriString);
		if("".equals(uri.getPath()))
			uri = uri.resolve("/app");
		return uri;
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
}
