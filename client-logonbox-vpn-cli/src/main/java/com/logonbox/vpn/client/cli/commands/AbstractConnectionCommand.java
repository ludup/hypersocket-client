package com.logonbox.vpn.client.cli.commands;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	protected CommandSpec spec;

	protected CLIContext getCLI() {
		return (CLIContext) spec.parent().userObject();
	}

	protected String getPattern(CLIContext cli, String[] args) throws IOException {
		ConsoleProvider console = cli.getConsole();
		String pattern = null;
		if (args == null || args.length == 0) {
			pattern = console.readLine("Enter connection ID or hostname: ");
		} else if (args.length > 0) {
			pattern = args[0];
		}
		return pattern;
	}

	protected boolean isSingleConnection(CLIContext cli) throws RemoteException {
		return cli.getVPN().getNumberOfConnections() == 1;
	}

	protected List<VPNConnection> getConnectionsMatching(String pattern, CLIContext cli) throws RemoteException {
		List<VPNConnection> l = new ArrayList<>();
		try {
			long id = Long.parseLong(pattern);
			VPNConnection connection = cli.getVPNConnection(id);
			if (connection == null)
				throw new IllegalArgumentException(String.format("No connection %d (hint: use 'list' command)", id));
			l.add(connection);
		} catch (NumberFormatException nfe) {
			for (VPNConnection c : cli.getVPNConnections()) {
				if (pattern == null || pattern.equals("") || c.getUri(true).matches(pattern)
						|| (c.getName() != null && c.getName().matches(pattern)) || c.getUri(true).matches(pattern)) {
					l.add(c);
				}
			}
		}
		return l;
	}
}
