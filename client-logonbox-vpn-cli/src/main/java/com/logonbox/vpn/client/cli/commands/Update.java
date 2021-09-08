package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "update", mixinStandardHelpOptions = true, description = "Update the client.")
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
		PrintWriter writer = cli.getConsole().out();
		cli.getVPN().checkForUpdate();
		if(cli.getVPN().isNeedsUpdating()) {
			if(checkOnly) {
				writer.println(String.format("Version %s available.", cli.getVPN().getAvailableVersion()));
				return 0;
			}
			else if(!yes) {
				String answer = cli.getConsole().readLine("Version %s available. Update? (Y)/N: ", cli.getVPN().getAvailableVersion()).toLowerCase();
				if(!answer.equals("") && !answer.equals("y") && !answer.equals("yes")) {
					writer.println("Cancelled");
					return 1;
				}
			}
		}
		else {
			writer.println("You are on the latest version.");
			return 3;
		}
		
		cli.getVPN().update();
		
		return 0;
	}
}
