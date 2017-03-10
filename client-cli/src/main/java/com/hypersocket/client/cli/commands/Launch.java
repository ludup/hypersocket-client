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
		if (cli.getCommandLine().getArgs().length == 1) {
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
				String answer = console.readLine("Resource (or RETURN to exit): ");
				if (!answer.isEmpty()) {
					Resource r = m.get(Integer.parseInt(answer));
					r.getResourceLauncher().launch();
				}
			} catch (Exception e) {
				System.err.println("Failed to get resources.");
				e.printStackTrace();
			}

		}
		// TODO Auto-generated method stub

	}

	@Override
	public void buildOptions(Options options) {
		// TODO Auto-generated method stub
		
	}

}
