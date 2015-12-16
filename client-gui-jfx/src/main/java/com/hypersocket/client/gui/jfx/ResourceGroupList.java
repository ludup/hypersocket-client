package com.hypersocket.client.gui.jfx;

import java.util.ArrayList;
import java.util.List;

import com.hypersocket.client.rmi.Resource;

public class ResourceGroupList {
	private ResourceGroupKey key;
	private List<ResourceItem> items = new ArrayList<>();

	public ResourceGroupList(ResourceGroupKey key) {
		this.key = key;
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