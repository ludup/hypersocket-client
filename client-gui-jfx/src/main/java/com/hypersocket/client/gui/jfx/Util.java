package com.hypersocket.client.gui.jfx;

import java.net.URI;
import java.net.URISyntaxException;

public class Util {

	public static URI getURI(String uriString) throws URISyntaxException {
		if (!uriString.startsWith("https://")) {
			if (uriString.indexOf("://") != -1) {
				throw new IllegalArgumentException(
						"Only HTTPS is supported.");
			}
			uriString = "https://" + uriString;
		}
		return new URI(uriString);
	}
}
