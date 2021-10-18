package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "update", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Update the client.")
public class Update implements Callable<Integer> {

	@Spec
	private CommandSpec spec;
	
	@Option(names = { "y", "yes" })
	private boolean yes;
	
	@Option(names = { "c", "check" })
	private boolean checkOnly;
	
	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		ConsoleProvider console = cli.getConsole();
		PrintWriter writer = console.out();
		cli.getUpdateService().checkForUpdate();
		if(cli.getUpdateService().isNeedsUpdating()) {
			if(checkOnly) {
				writer.println(String.format("Version %s available.", cli.getUpdateService().getAvailableVersion()));
				console.flush();
				return 0;
			}
			else if(!yes) {
				String answer = console.readLine("Version %s available. Update? (Y)/N: ", cli.getUpdateService().getAvailableVersion()).toLowerCase();
				if(!answer.equals("") && !answer.equals("y") && !answer.equals("yes")) {
					writer.println("Cancelled");
					console.flush();
					return 1;
				}
			}
		}
		else {
			writer.println("You are on the latest version.");
			console.flush();
			return 3;
		}
		
		cli.getUpdateService().update();
		return 0;
	}
}
