package com.logonbox.vpn.client.cli.commands;

import java.util.List;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "edit", mixinStandardHelpOptions = true, description = "Edit connections.")
public class Edit extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Option(names = { "-n", "--name" }, description = "Set the name.")
	private String name;

	@Option(names = { "-u", "--uri" }, description = "Set the URI.")
	private String uri;

	@Option(names = { "-s",
			"--connect-at-startup" }, negatable = true, description = "Set the connect at startup optioj.")
	private Boolean connectAtStartup;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		String pattern = getPattern(cli, names);

		final List<VPNConnection> c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("No connection matches %s", pattern));
			return 1;
		} else if (c.size() == 1) {
			VPNConnection connection = c.get(0);
			connection.update(name == null ? connection.getName() : name, uri == null ? connection.getUri(false) : uri,
					connectAtStartup == null ? connection.isConnectAtStartup() : connectAtStartup);
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("Updated %s", connection.getUri(false)));
		} else {
			if (!cli.isQuiet())
				cli.getConsole().err().println("May only edit a single connection.");
			return 1;
		}
		return 0;

	}
}
