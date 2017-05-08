package com.hypersocket.client.gui.jfx;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.Resource.Type;

public class FileResources extends AbstractResourceListController {
	
	static Logger LOG = LoggerFactory.getLogger(FileResources.class);

	private static FileResources instance;
	
	public FileResources() {
		instance = this;
	}
	
	public static FileResources getInstance() {
		return instance;
	}
	
	@Override
	public void setResources(Map<ResourceGroupKey, ResourceGroupList> icons) {
		populateResource(icons, Type.FILE);
	}

}
