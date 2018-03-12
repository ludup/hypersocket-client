 package com.hypersocket.client.cli.commands;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.Resource.Type;
import com.hypersocket.client.rmi.ResourceRealm;
import com.hypersocket.client.rmi.ResourceService;

public class Resources extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {

		ResourceService resourceService = cli.getResourceService();
		int i = 0;
		try {
			Map<Integer, Resource> m = new HashMap<>();
			for (ResourceRealm resourceRealm : resourceService.getResourceRealms()) {
				for (Resource r : resourceRealm.getResources()) {
					if (r.getType() != Type.ENDPOINT && r.isLaunchable()) {
						System.out.println(String.format("%s - %s", i, r.getName()));
						m.put(++i, r);
					}
				}
			}
		} catch (Exception e) {
			System.out.println(String.format("Error: %s", e.getMessage()));
			log.error("Failed to get resources", e);
		}

	}
}
