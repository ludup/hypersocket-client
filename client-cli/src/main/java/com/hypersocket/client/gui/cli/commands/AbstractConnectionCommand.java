package com.hypersocket.client.gui.cli.commands;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.Options;

import com.hypersocket.client.gui.cli.CLI;
import com.hypersocket.client.gui.cli.Command;
import com.hypersocket.client.gui.cli.ConsoleProvider;
import com.hypersocket.client.rmi.Connection;

public abstract class AbstractConnectionCommand implements Command {

	protected String getConnectionURI(Connection connection) {
		return String.format("%s:%d%s",  connection.getHostname(), connection.getPort(), connection.getPath());
	}

	@Override
	public void buildOptions(Options options) {
	}

	protected String getPattern(CLI cli) throws IOException {
		ConsoleProvider console = cli.getConsole();
		String pattern = null;
		if (cli.getCommandLine().getArgs().length == 1) {
			pattern = console.readLine("Enter connection id, hostname[:port][/path]: ");
		} else if (cli.getCommandLine().getArgs().length == 2) {
			pattern = cli.getCommandLine().getArgList().get(1);
		}
		return pattern;
	}
	
	protected List<Connection> getConnectionsMatching(String pattern, CLI cli) throws RemoteException {
		List<Connection> l = new ArrayList<Connection>();
		try {
			long id = Long.parseLong(pattern);
			Connection connection = cli.getConnectionService().getConnection(id);
			if(connection == null)
				throw new IllegalArgumentException(String.format("No connection %d (hint: use 'list' command)", id));
			l.add(connection);
		}
		catch(NumberFormatException nfe) {
			for(Connection c : cli.getConnectionService().getConnections()) {
				if(pattern == null || pattern.equals("") || c.getHostname().matches(pattern) || 
				   ( c.getHostname() + ":" + c.getPort()).matches(pattern) ||
				   ( c.getHostname() + ":" + c.getPort() + c.getPath()).matches(pattern)) {
					l.add(c);
				}
			}
		}
		return l;
	}
}
