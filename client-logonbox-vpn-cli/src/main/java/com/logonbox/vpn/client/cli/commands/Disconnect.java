package com.logonbox.vpn.client.cli.commands;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "disconnect", usageHelpAutoWidth = true, mixinStandardHelpOptions = true, description = "Disconnect from a VPN.")
public class Disconnect extends AbstractConnectionCommand {

	@Parameters(description = "Connection names to delete.")
	private String[] names;

	@Option(names = { "-d", "--delete" }, description = "Permanently delete the connection as well.")
	private boolean delete;

	@Override
	public Integer call() throws Exception {

		CLIContext cli = getCLI();
		if ((names == null || names.length == 0) && isSingleConnection(cli)) {
			disconnect(cli.getVPNConnections().iterator().next(), cli);
		} else {
			String pattern = getPattern(cli, names);
			for (VPNConnection c : getConnectionsMatching(pattern, cli)) {
				Type statusType = Type.valueOf(c.getStatus());
				ConsoleProvider console = cli.getConsole();
				if (statusType != Type.DISCONNECTED && statusType != Type.DISCONNECTING) {
					disconnect(c, cli);
				} else {
					if (!cli.isQuiet()) {
						console.err().println(String.format("%s is not connected.", c.getUri(true)));
						console.flush();
					}
				}

				if (delete) {
					c.delete();
					if (!cli.isQuiet()) {
						console.out().println(String.format("Deleted %s", c.getUri(true)));
						console.flush();
					}
				}
			}
		}
		return 0;
	}

}
