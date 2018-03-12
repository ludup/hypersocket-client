package com.hypersocket.client.cli.commands;

import java.rmi.RemoteException;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionStatus;

public class Delete extends AbstractConnectionCommand {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		
		String  pattern = getPattern(cli);
		
		for(Connection connection : getConnectionsMatching(pattern, cli)) {
			
			int status;
			try {
				status = cli.getClientService().getStatus(connection);
			} catch (RemoteException e1) {
				status = ConnectionStatus.DISCONNECTED;
			}
			if (status != ConnectionStatus.DISCONNECTED) {
				cli.getClientService().disconnect(connection);
			}
			
			cli.getConnectionService().delete(connection);
			
			System.out.println(String.format("Deleted %s", connection.getHostname()));
		}
		
	}

	@Override
	public void buildOptions(Options options) {
	}
}
