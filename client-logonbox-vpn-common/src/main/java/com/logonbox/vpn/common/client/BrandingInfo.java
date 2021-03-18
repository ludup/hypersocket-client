package com.logonbox.vpn.common.client;

import java.io.Serializable;

@SuppressWarnings("serial")
public class BrandingInfo implements Serializable {

	public static final String DEFAULT_FOREGROUND = "#ffffff";
	public static final String DEFAULT_BACKGROUND = "#1e0c51";
	
	private String foreground = DEFAULT_FOREGROUND;
	private String background = DEFAULT_BACKGROUND;
	private String name = "LogonBox";

	public BrandingInfo() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getForeground() {
		return foreground;
	}

	public void setForeground(String foreground) {
		this.foreground = foreground;
	}

	public String getBackground() {
		return background;
	}

	public void setBackground(String background) {
		this.background = background;
	}

}
