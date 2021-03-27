package com.logonbox.vpn.client.cli.commands;

import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "exit", mixinStandardHelpOptions = true, description = "Exit interactive shell.")
public class Exit implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		if(!cli.isQuiet())
			cli.getConsole().out().println("Goodbye!");
		cli.exitWhenDone();
		return 0;
	}
}
