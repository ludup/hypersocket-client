package com.hypersocket.client.rmi;

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
		return new URI(uriString);
	}
	
	public static String getUri(Connection connection) {
		if (connection == null) {
			return "";
		}
		String uri = "https://" + connection.getHostname();
		if (connection.getPort() != 443) {
			uri += ":" + connection.getPort();
		}
		uri += connection.getPath();
		return uri;
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
