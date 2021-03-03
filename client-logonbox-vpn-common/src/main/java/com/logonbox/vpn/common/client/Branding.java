package com.logonbox.vpn.common.client;

import java.io.Serializable;

import com.hypersocket.json.ResourceStatus;

@SuppressWarnings("serial")
public class Branding extends ResourceStatus<BrandingInfo> implements Serializable {

	private String logo;
	
	public Branding() {
		setResource(new BrandingInfo());
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

}
