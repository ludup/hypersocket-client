package com.hypersocket.client.rmi;

public class ResourceGroupImpl implements ResourceGroup {
	private String name;
	private String logo;
	ResourceRealm realm;

	public ResourceGroupImpl(String name, String logo) {
		super();
		this.name = name;
		this.logo = logo;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}

	public ResourceRealm getRealm() {
		return realm;
	}

	public void setResourceRealm(ResourceRealm realm) {
		this.realm = realm;
	}
}
