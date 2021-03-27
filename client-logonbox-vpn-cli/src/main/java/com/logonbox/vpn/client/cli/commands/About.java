package com.logonbox.vpn.client.cli.commands;

import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "about", mixinStandardHelpOptions = true, description = "Show version information.")
public class About implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		cli.about();
		return 0;
	}
}
