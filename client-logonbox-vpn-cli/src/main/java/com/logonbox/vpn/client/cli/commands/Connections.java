package com.logonbox.vpn.client.cli.commands;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "connections", mixinStandardHelpOptions = true, description = "Display all information about all connections.")
public class Connections implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		PrintWriter writer = cli.getConsole().out();
		writer.println(String.format("%10s %-35s %-5s %-14s %s", "ID", "Name", "Flags", "Status", "URL"));
		writer.println("=======================================================================================");
		for (VPNConnection connection : cli.getVPNConnections()) {
			writer.println(String.format("%10d %-35s %-5s %-14s %s", connection.getId(),
					trimMax(connection.getDisplayName(), 25), 
					getFlags(connection),
					Type.valueOf(connection.getStatus()), connection.getUri(true), 20));
		}

		return 0;
	}

	private String getFlags(VPNConnection connection) {
		String flags = "";
		if(connection.isTransient())
			flags += "T";
		if(connection.isConnectAtStartup())
			flags += "C";
		if(connection.isAuthorized())
			flags += "A";
		if(connection.isShared())
			flags += "S";
		return flags;
	}

	private static String trimMax(String str, int max) {
		if (str.length() > max)
			return str.substring(0, max);
		else
			return str;
	}
}
