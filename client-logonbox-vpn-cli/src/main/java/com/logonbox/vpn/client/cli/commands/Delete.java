package com.logonbox.vpn.client.cli.commands;

import java.util.List;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "delete", mixinStandardHelpOptions = true, description = "Delete connections.")
public class Delete extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = getCLI();
		String pattern = getPattern(cli, names);

		final List<VPNConnection> c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
			if (!cli.isQuiet())
				cli.getConsole().err().println(String.format("No connection matches %s", pattern));
			return 1;
		}
		for (VPNConnection connection : c) {
			Type status = Type.valueOf(connection.getStatus());
			if (status != Type.DISCONNECTED) {
				connection.disconnect("");
			}
			String uri = connection.getUri(true);
			connection.delete();
			if (!cli.isQuiet()) {
				cli.getConsole().out().println(String.format("Deleted %s", uri));
			}
		}

		return 0;

	}
}
