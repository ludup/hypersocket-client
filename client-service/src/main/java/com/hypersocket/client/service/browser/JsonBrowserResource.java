package com.hypersocket.client.service.browser;

import java.util.Calendar;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonBrowserResource {

	String name;
	String launchUrl;
	String logo;
	String type;
	boolean requireVPNAccess;
	Long id;
	Calendar modifiedDate;
	
	public Long getId() {
		return id;
	}
	
	public void setId(Long id) {
		this.id = id;
	}
	
	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLaunchUrl() {
		return launchUrl;
	}

	public void setLaunchUrl(String url) {
		this.launchUrl = url;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean isRequireVPNAccess() {
		return requireVPNAccess;
	}

	public void setRequireVPNAccess(boolean requireVPNAccess) {
		this.requireVPNAccess = requireVPNAccess;
	}


	public Calendar getModifiedDate() {
		return modifiedDate;
	}

	public void setModifiedDate(Calendar modifiedDate) {
		this.modifiedDate = modifiedDate;
	}
	
	

}
