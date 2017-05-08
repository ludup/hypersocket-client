package com.hypersocket.client.gui.jfx;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.Resource.Type;

public class BrowserResources extends AbstractResourceListController {
	
	static Logger LOG = LoggerFactory.getLogger(BrowserResources.class);

	private static BrowserResources instance;
	
	public BrowserResources() {
		instance = this;
	}
	
	public static BrowserResources getInstance() {
		return instance;
	}
	
	public void setResources(Map<ResourceGroupKey, ResourceGroupList> icons) {
		populateResource(icons, Type.BROWSER);
	}

}
