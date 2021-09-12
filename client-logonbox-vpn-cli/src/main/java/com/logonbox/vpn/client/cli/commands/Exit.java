package com.logonbox.vpn.client.cli.commands;

import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "exit", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Exit interactive shell.")
public class Exit implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		if(!cli.isQuiet()) {
			ConsoleProvider console = cli.getConsole();
			console.out().println("Goodbye!");
			console.flush();
		}
		cli.exitWhenDone();
		return 0;
	}
}
