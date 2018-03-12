package com.hypersocket.client.cli.commands;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.cli.Options;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.cli.Command;
import com.hypersocket.client.cli.ConsoleProvider;
import com.hypersocket.client.rmi.Resource;
import com.hypersocket.client.rmi.Resource.Type;
import com.hypersocket.client.rmi.ResourceRealm;
import com.hypersocket.client.rmi.ResourceService;

public class Launch implements Command {

	@Override
	public void run(CLI cli) throws Exception {
		ConsoleProvider console = cli.getConsole();
		
		ResourceService resourceService = cli.getResourceService();
		
		String answer = null;
		if(cli.getCommandLine().getArgList().size() > 1) {
			answer = cli.getCommandLine().getArgs()[1];
		}
		
		int i = 0;
		try {
			Map<Integer, Resource> m = new HashMap<>();
			for (ResourceRealm resourceRealm : resourceService.getResourceRealms()) {
				for (Resource r : resourceRealm.getResources()) {
					if (r.getType() != Type.ENDPOINT && r.isLaunchable()) {
						if(answer==null) {
							System.out.println(String.format("%s - %s", i, r.getName()));
						}
						m.put(++i, r);
					}
				}
			}
			
			if(answer==null) {
				answer = console.readLine("Resource (or RETURN to exit): ");
			}
			if (answer!=null && !answer.isEmpty()) {
				Resource r = m.get(Integer.parseInt(answer));
				r.getResourceLauncher().launch();
			}
		} catch (Exception e) {
			System.out.println(String.format("Error: %s", e.getMessage()));
		}

		
		


	}

	@Override
	public void buildOptions(Options options) {
		
	}

}
