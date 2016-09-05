package com.hypersocket.client;

public class JsonResourceGroup {

	private String name;
	private String logo;

	public JsonResourceGroup(String name, String logo) {
		super();
		this.name = name;
		this.logo = logo;
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

}
