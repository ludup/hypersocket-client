package com.logonbox.vpn.client.cli.commands;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.lang3.StringUtils;
import org.freedesktop.dbus.exceptions.DBusException;

import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.common.client.Connection.Mode;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "connect", mixinStandardHelpOptions = true, description = "Connect a VPN.")
public class Connect extends AbstractConnectionCommand implements Callable<Integer> {

	@Spec
	private CommandSpec spec;

	@Parameters(index = "0", arity = "0..1", description = "Connect to a particular server using a URI. Acceptable formats include <server[<port>]> or https://<server[<port>]>[/path]. If a pre-configured connection matching this URI already exists, it will be used.")
	private String uri;

	@Override
	public Integer call() throws Exception {
		CLIContext cli = (CLIContext) spec.parent().userObject();
		PrintWriter out = cli.getConsole().out();
		PrintWriter err = cli.getConsole().err();

		VPNConnection connection = null;

		String pattern = getPattern(cli, new String[] { uri });
		final List<VPNConnection> c = getConnectionsMatching(pattern, cli);
		if (c.isEmpty()) {
			if (StringUtils.isNotEmpty(uri)) {
				if (!uri.startsWith("https://")) {
					if (uri.indexOf("://") != -1) {
						throw new IllegalArgumentException("Only HTTPS is supported.");
					}
					uri = "https://" + uri;
				}
				URI uriObj = new URI(uri);
				if (!uriObj.getScheme().equals("https")) {
					throw new IllegalArgumentException("Only HTTPS is supported.");
				}

				long connectionId = cli.getVPN().getConnectionIdForURI(uri);
				connection = connectionId < 0 ? null : cli.getVPNConnection(connectionId);

				if (connection == null) {
					connectionId = cli.getVPN().connect(uri);
					connection = cli.getVPNConnection(connectionId);
					if (!cli.isQuiet())
						out.println(String.format("Created new connection for %s", uri));
				}
			} else {
				throw new IllegalStateException("Connection information is required");
			}
		} else
			connection = c.get(0);

		return doConnect(cli, out, err, connection);

	}

	Integer doConnect(CLIContext cli, PrintWriter out, PrintWriter err, VPNConnection connection)
			throws DBusException, InterruptedException, IOException {
		Type status = Type.valueOf(connection.getStatus());
		if (status == Type.DISCONNECTED) {
			try (StateHelper stateHelper = new StateHelper(connection, cli.getBus())) {
				if (!cli.isQuiet())
					out.println(String.format("Connecting to %s", connection.getUri(true)));
				stateHelper.on(Type.AUTHORIZING, (state, mode) -> {
					if(mode.equals(Mode.SERVICE)) {
						out.println("Service auth");
					}
					else {
						throw new UnsupportedOperationException(
								String.format("This connection requires an authorization type, %s,  which is not currently supported by the CLI tools.", mode));
					}
				});
				stateHelper.start(Type.CONNECTING);
				connection.connect();
				status = stateHelper.waitForState(Type.CONNECTED, Type.DISCONNECTED);
				if (status == Type.CONNECTED) {
					if (!cli.isQuiet())
						out.println("Ready");
					return 0;
				} else {
					if (!cli.isQuiet())
						err.println(String.format("Failed to connect to %s", connection.getUri(true)));
					return 1;
				}
			}
		} else {
			err.println(String.format("Request to connect an already connected or connecting to %s",
					connection.getUri(true)));
			return 1;
		}
	}
}
