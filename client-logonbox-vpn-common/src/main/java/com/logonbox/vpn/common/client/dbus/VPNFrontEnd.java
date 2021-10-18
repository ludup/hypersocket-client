package com.logonbox.vpn.common.client.dbus;

public class VPNFrontEnd {

	private String source;
	private boolean interactive;
	private boolean supportsAuthorization;
	private String username;
	private long lastPing = System.currentTimeMillis();
	private boolean updated = false;

	public VPNFrontEnd(String source) {
		this.source = source;
	}

	public boolean isUpdated() {
		return updated;
	}

	public void setUpdated(boolean updated) {
		this.updated = updated;
	}

	public boolean isSupportsAuthorization() {
		return supportsAuthorization;
	}

	public void setSupportsAuthorization(boolean supportsAuthorization) {
		this.supportsAuthorization = supportsAuthorization;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public boolean isInteractive() {
		return interactive;
	}

	public void setInteractive(boolean interactive) {
		this.interactive = interactive;
	}

	public String getSource() {
		return source;
	}

	@Override
	public String toString() {
		return "VPNFrontEnd [source=" + source + ", interactive=" + interactive + ", username="
				+ username + "]";
	}

	public long getLastPing() {
		return lastPing;
	}

	public void ping() {
		lastPing = System.currentTimeMillis();
	}

}
