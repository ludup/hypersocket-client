package com.logonbox.vpn.client.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "save", mixinStandardHelpOptions = true, description = "Save a temporary connection as a permanent one.")
public class Save extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(description = "Connection names to save.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		String pattern = getPattern(cli, names);

		final List<VPNConnection> c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty())
			throw new IllegalArgumentException(String.format("No connection matches %s", pattern));
		for (VPNConnection connection : c) {
			boolean wasTransient = connection.isTransient();
			long id = connection.save();
			if (wasTransient) {
				connection = cli.getVPNConnection(id);
				if(!cli.isQuiet())
					cli.getConsole().out()
							.println(String.format("Saved %s, ID is now %d", connection.getUri(true), connection.getId()));
			}
		}

		return null;

	}
}
