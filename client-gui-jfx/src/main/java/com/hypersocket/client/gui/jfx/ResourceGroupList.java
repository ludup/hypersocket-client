package com.hypersocket.client.gui.jfx;

import java.util.ArrayList;
import java.util.List;

import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.ResourceRealm;

public class ResourceGroupList {
	private ResourceGroupKey key;
	private List<ResourceItem> items = new ArrayList<>();
	private String icon;
	private ResourceRealm resourceRealm;

	public ResourceGroupList(ResourceRealm resourceRealm, ResourceGroupKey key, String icon) {
		this.key = key;
		this.icon = icon;
		this.resourceRealm = resourceRealm;
	}
	
	public ResourceRealm getRealm() {
		return resourceRealm;
	}
	
	public String getIcon() {
		return icon;
	}

	public List<ResourceItem> getItems() {
		return items;
	}

	public ResourceGroupKey getKey() {
		return key;
	}

	public ResourceItem getItemForResource(Resource resource) {
		for(ResourceItem ri : items) {
			if(ri.getResource().getUid().equals(resource.getUid())) {
				return ri;
			}
		}
		return null;
	}
}