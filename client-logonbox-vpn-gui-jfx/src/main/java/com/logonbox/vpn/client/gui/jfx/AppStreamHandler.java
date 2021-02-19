package com.logonbox.vpn.client.gui.jfx;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class AppStreamHandler extends URLStreamHandler {

	public AppStreamHandler() {
	}

	@Override
	protected URLConnection openConnection(URL u) throws IOException {
		String path = u.toExternalForm().substring(6);
		int idx = path.indexOf('/');
		if(idx != -1) {
			/* HACK: I cant get relative resource loading from classpath
			 * working correctly without loading the page via a custom
			 * URL and processing the (bad) paths it gets to correct them
			 */
			String n = path.substring(0, idx);
			if(n.endsWith(".html")) {
				path = path.substring(idx + 1);
			}
		}
		System.out.println("u " + u + " = " + path);
		URL resourceUrl = AppStreamHandler.class.getResource(path);
		if (resourceUrl == null)
			throw new IOException("Resource not found: " + u);

		return resourceUrl.openConnection();
	}
}