package com.hypersocket.client.rmi;

import java.io.Serializable;

@SuppressWarnings("serial")
public class BrowserLauncher implements ResourceLauncher, Serializable {

	public interface BrowserLauncherFactory {
		ResourceLauncher create(String uri);
	}

	private static BrowserLauncherFactory factory;

	public static void setFactory(BrowserLauncherFactory factory) {
		BrowserLauncher.factory = factory;
	}

	private String launchUri;

	public BrowserLauncher(String launchUri) {
		this.launchUri = launchUri;
	}

	@Override
	public int launch() {
		if (factory == null) {
			return new AWTBrowserLauncher(launchUri).launch();
		} else {
			return factory.create(launchUri).launch();
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((launchUri == null) ? 0 : launchUri.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BrowserLauncher other = (BrowserLauncher) obj;
		if (launchUri == null) {
			if (other.launchUri != null)
				return false;
		} else if (!launchUri.equals(other.launchUri))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "BrowserLauncher [launchUri=" + launchUri + "]";
	}

}
