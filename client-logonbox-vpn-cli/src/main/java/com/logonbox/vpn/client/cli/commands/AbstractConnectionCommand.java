package com.logonbox.vpn.client.cli.commands;

import java.io.EOFException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;

import org.apache.http.message.BasicNameValuePair;
import org.freedesktop.dbus.exceptions.DBusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.input.InputField;
import com.hypersocket.json.input.Option;
import com.hypersocket.json.input.SelectInputField;
import com.logonbox.vpn.client.cli.CLI;
import com.logonbox.vpn.client.cli.CLIContext;
import com.logonbox.vpn.client.cli.ConsoleProvider;
import com.logonbox.vpn.client.cli.StateHelper;
import com.logonbox.vpn.common.client.ConnectionStatus.Type;
import com.logonbox.vpn.common.client.ServiceClient;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

public abstract class AbstractConnectionCommand implements Callable<Integer> {
	static Logger log = LoggerFactory.getLogger(AbstractConnectionCommand.class);

	@Spec
	protected CommandSpec spec;

	protected AbstractConnectionCommand() {
	}

	protected CLIContext getCLI() {
		return (CLIContext) spec.parent().userObject();
	}

	protected String getPattern(CLIContext cli, String... args) throws IOException {
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

	protected void disconnect(VPNConnection c, CLIContext cli)
			throws RemoteException, InterruptedException, IOException, DBusException {
		ConsoleProvider console = cli.getConsole();
		if (!cli.isQuiet())
			console.out().println(String.format("Disconnecting from %s", c.getUri(true)));
		console.flush();
		try (StateHelper helper = new StateHelper(c, cli.getBus())) {
			c.disconnect("");
			helper.waitForState(1, TimeUnit.MINUTES, Type.DISCONNECTED);
			if (!cli.isQuiet())
				console.out().println("Disconnected");
		}
		console.flush();
	}

	protected void register(CLIContext cli, VPNConnection connection, PrintWriter out, PrintWriter err)
			throws IOException, URISyntaxException {

		ServiceClient sc = new ServiceClient(new ServiceClient.Authenticator() {

			@Override
			public String getUUID() {
				return cli.getVPN().getUUID();
			}

			@Override
			public HostnameVerifier getHostnameVerifier() {
				return cli.getCertManager();
			}

			@Override
			public void error(JsonNode i18n, AuthenticationResult logonResult) {
				if (logonResult.isLastErrorIsResourceKey())
					out.println(String.format("Login error. %s", i18n.get(logonResult.getErrorMsg()).asText()));
				else
					out.println(String.format("Login error. %s", logonResult.getErrorMsg()));

			}

			@Override
			public void collect(JsonNode i18n, AuthenticationRequiredResult result,
					Map<InputField, BasicNameValuePair> results) throws IOException {
				out.println(MessageFormat.format(CLI.BUNDLE.getString("authenticationRequired"),
						i18n.get("authentication." + result.getFormTemplate().getResourceKey()).asText()));
				int fieldNo = 0;
				for (InputField field : result.getFormTemplate().getInputFields()) {
					boolean loop = true;
					while (loop) {
						switch (field.getType()) {
						case select:
							out.println(field.getLabel());
							SelectInputField sel = (SelectInputField) field;
							int idx = 1;
							for (Option opt : sel.getOptions()) {
								out.println(String.format("%d. %s",
										idx,
										opt.getIsNameResourceKey() ? i18n.get(opt.getName()).asText() : opt.getName()));
								idx++;
							}
							String reply;
							while(true) {
								reply = cli.getConsole().readLine("Option> ");
								if(reply.equals("")) {
									reply = field.getDefaultValue();
									break;
								}
								else {
									try {
										reply = sel.getOptions().get(Integer.parseInt(reply) -1).getValue();
										break;
									}
									catch(Exception e) {
									}
								}
							}
							results.put(field, new BasicNameValuePair(field.getResourceKey(), reply));
							break;
						case password:
							char[] pw = cli.getConsole().readPassword(field.getLabel() + ": ");
							if (pw == null)
								throw new EOFException();
							if (pw.length > 0 || !field.isRequired()) {
								results.put(field, new BasicNameValuePair(field.getResourceKey(), new String(pw)));
								loop = false;
							}
							break;
						case a:
							out.println(field.getLabel() + ": " + field.getDefaultValue());
							break;
						case text:
						case textarea:
							String input = cli.getConsole().readLine(field.getLabel() + ": ");
							if (input == null)
								throw new EOFException();
							if (input.length() > 0 || !field.isRequired()) {
								results.put(field, new BasicNameValuePair(field.getResourceKey(),
										input.equals("") ? field.getDefaultValue() : input));
								loop = false;
							} else if (input.length() == 0 && fieldNo == 0) {
								throw new IllegalStateException("Aborted.");
							}
							break;
						case hidden:
							results.put(field, new BasicNameValuePair(field.getResourceKey(), field.getDefaultValue()));
							break;
						case p:
						case div:
						case pre:
							out.println(field.getDefaultValue());
							break;
						case checkbox:
							if ("true".equals(field.getDefaultValue()))
								input = cli.getConsole().readLine(field.getLabel() + " (Y)/N: ");
							else
								input = cli.getConsole().readLine(field.getLabel() + " Y/(N): ");
							if (input == null)
								throw new EOFException();
							input = input.toLowerCase();
							if (input.equals("y") || input.equals("yes"))
								input = "true";
							else if (input.equals("n") || input.equals("no"))
								input = "false";
							else if (input.equals(""))
								input = field.getDefaultValue().equals("true") ? "true" : "false";
							else
								input = "";
							if (!input.equals("")) {
								results.put(field, new BasicNameValuePair(field.getResourceKey(), input));
								loop = false;
							}
							break;
						default:
							throw new UnsupportedOperationException(
									String.format("Field type %s is not supported.", field.getType()));
						}
					}
					fieldNo++;
				}

			}

			@Override
			public void authorized() throws IOException {
				out.println("Authorized.");
				cli.getConsole().flush();

			}
		});
		sc.register(connection);
	}
}
