package com.logonbox.vpn.client.wireguard.osx;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sshtools.forker.client.EffectiveUserFactory.DefaultEffectiveUserFactory;
import com.sshtools.forker.client.ForkerBuilder;
import com.sshtools.forker.client.ForkerProcess;

public class SCUtil implements Closeable {
	final static Logger LOG = LoggerFactory.getLogger(SCUtil.class);

	private ForkerProcess process;
	private Thread thread;
	private String iface;
	private PrintWriter out;

	public SCUtil(String iface) throws IOException {
		this.iface = iface;
		LOG.info("Running scutil");
		ForkerBuilder builder = new ForkerBuilder("scutil");
		builder.effectiveUser(DefaultEffectiveUserFactory.getDefault().administrator());
		builder.redirectErrorStream(true);
		process = builder.start();
		thread = new Thread() {
			public void run() {
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ( ( line = reader.readLine() ) != null) {
						LOG.info("SCUTIL: " + line);	
					}
				}
				catch(IOException ioe) {
				}
			}
		};
		out = new PrintWriter(process.getOutputStream(), true);
		thread.start();
	}
	
	public void compatible(String dnsServers[], String[] domains) {
		LOG.info("Creating compatible resolver");
		out.println(String.format("d.add ServerAddresses * %s", String.join(" ", dnsServers)));
		out.println(String.format("d.add SearchDomains %s", String.join(" ", domains)));
		out.println(String.format("set State:/Network/Service/%s/DNS", iface));
	}
	
	public void split(String dnsServers[], String[] domains) {
		LOG.info("Creating split resolver");
		out.println(String.format("d.add ServerAddresses * %s", String.join(" ", dnsServers)));
		out.println(String.format("d.add SupplementalMatchDomains * %s", String.join(" ", domains)));
		out.println(String.format("set State:/Network/Service/%s/DNS", iface));
	}
	
	public void remove() {
		LOG.info("Removing resolver");
		out.println(String.format("remove State:/Network/Service/%s/DNS", iface));
	}

	@Override
	public void close() throws IOException {
		out.println("quit");
		try {
			if(process.waitFor() != 0) {
				throw new IOException(String.format("scutil exited with non-zero code %d.", process.exitValue()));
			}
		} catch (InterruptedException e) {
			throw new IOException("Interrupted.", e);
		} 
	}

}
