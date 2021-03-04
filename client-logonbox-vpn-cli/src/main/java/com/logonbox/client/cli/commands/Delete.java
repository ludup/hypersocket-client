package com.logonbox.client.cli.commands;

import java.rmi.RemoteException;
import java.util.List;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.client.cli.CLI;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;

public class Delete extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		
		String  pattern = getPattern(cli);
		
		final List<Connection> c = getConnectionsMatching(pattern, cli);
		if(c.isEmpty())
			throw new IllegalArgumentException(String.format("No connection matches %s", pattern));
		for(Connection connection : c) {
			
			Type status;
			try {
				status = cli.getClientService().getStatus(connection);
			} catch (RemoteException e1) {
				status = Type.DISCONNECTED;
			}
			if (status != Type.DISCONNECTED) {
				cli.getClientService().disconnect(connection, null);
			}
			
			cli.getConnectionService().delete(connection);
			
			System.out.println(String.format("Deleted %s", connection.getHostname()));
		}
		
	}

	@Override
	public void buildOptions(Options options) {
	}
}
