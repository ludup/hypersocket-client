package com.hypersocket.client.cli.commands;

import java.net.URI;
import java.rmi.RemoteException;

import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.cli.CLI;
import com.hypersocket.client.cli.Command;
import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionStatus;

public class Delete implements Command {
	static Logger log = LoggerFactory.getLogger(CLI.class);

	@Override
	public void run(CLI cli) throws Exception {
		
		cli.exitWhenDone();
		String realUri = cli.getCommandLine().getArgs()[1];
		if (!realUri.startsWith("https://")) {
			if (realUri.indexOf("://") != -1) {
				throw new IllegalArgumentException("Only HTTPS is supported.");
			}
			realUri = "https://" + realUri;
		}
		URI uri = new URI(realUri);
		if (!uri.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		Connection connection = cli.getConnectionService().getConnection(uri.getHost());
		
		if(connection==null) {
			throw new IllegalArgumentException(String.format("Connection for %s does not exist", uri.getHost()));
		}
		
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
		
	}

	@Override
	public void buildOptions(Options options) {
	}
}
