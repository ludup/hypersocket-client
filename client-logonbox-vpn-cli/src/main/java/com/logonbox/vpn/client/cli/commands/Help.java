package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "help", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, helpCommand = true, description = "Shows help.")
public class Help implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(index = "0", arity = "0..1", description = "Displays help for a particular command.")
	private String command;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		ConsoleProvider console = cli.getConsole();
		PrintWriter out = console.out();
		if(StringUtils.isNotBlank(command)) {
			CommandLine cmd = spec.parent().subcommands().get(command);
			if(cmd == null)
				throw new IllegalArgumentException("No such command.");
			out.println(cmd.getUsageHelpWidth());
		}
		else {
			Map<String, CommandLine> parentSubcommands = spec.parent().subcommands();
			for (Map.Entry<String, CommandLine> en : parentSubcommands.entrySet()) {
				out.println(String.format("%-12s %s", en.getKey(), en.getValue().getHelp().description().trim()));
			}
		}
		console.flush();
		return 0;
	}
}
