package com.logonbox.vpn.client.cli.commands;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.concurrent.TimeUnit;

import org.freedesktop.dbus.exceptions.DBusException;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(name = "disconnect", mixinStandardHelpOptions = true, description = "Disconnect from a VPN.")
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
				if (statusType != Type.DISCONNECTED && statusType != Type.DISCONNECTING) {
					disconnect(c, cli);
				} else {
					if (!cli.isQuiet())
						cli.getConsole().err().println(String.format("%s is not connected.", c.getUri(true)));
				}

				if (delete) {
					c.delete();
					if (!cli.isQuiet())
						cli.getConsole().out().println(String.format("Deleted %s", c.getUri(true)));
				}
			}
		}
		return 0;
	}

	private void disconnect(VPNConnection c, CLIContext cli)
			throws RemoteException, InterruptedException, IOException, DBusException {
		if (!cli.isQuiet())
			cli.getConsole().out().println(String.format("Disconnecting from %s", c.getUri(true)));
		try (StateHelper helper = new StateHelper(c, cli.getBus())) {
			c.disconnect("");
			helper.waitForState(1, TimeUnit.MINUTES, Type.DISCONNECTED);
			if (!cli.isQuiet())
				cli.getConsole().out().println("Disconnected");
		}
	}

}
