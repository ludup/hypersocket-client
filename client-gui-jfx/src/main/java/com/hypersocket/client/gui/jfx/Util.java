package com.hypersocket.client.gui.jfx;

import java.net.URI;
import java.net.URISyntaxException;

import com.hypersocket.client.rmi.Connection;

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
}
