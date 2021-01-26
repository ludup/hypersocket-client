package com.logonbox.vpn.client;

import java.io.IOException;
import java.rmi.RemoteException;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.wireguard.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.OSXPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.WindowsPlatformServiceImpl;
import com.logonbox.vpn.common.client.ClientService;

public class Main extends AbstractMain {

	static Logger log = LoggerFactory.getLogger(Main.class);
	private PlatformService platform;

	public Main(Runnable restartCallback, Runnable shutdownCallback, String[] args) {
		super(restartCallback, shutdownCallback, args);
	}

	@Override
	protected void createServices() throws RemoteException {

		if (SystemUtils.IS_OS_LINUX) {
			platform = new LinuxPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_WINDOWS) {
			platform = new WindowsPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			platform = new OSXPlatformServiceImpl();
		} else
			throw new UnsupportedOperationException(
					String.format("%s not currently supported.", System.getProperty("os.name")));


	}

	@Override
	protected ClientServiceImpl createServiceImpl() {
		return new ClientServiceImpl(this);
	}

	@Override
	protected void unpublishServices() {
		try {
			getRegistry().unbind("connectionService");
		} catch (Exception e) {
		}
	}

	@Override
	protected void publishServices() throws Exception {
		publishService(ClientService.class, getClientService());
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		instance = new Main(new DefaultRestartCallback(), new DefaultShutdownCallback(), args);
		instance.run();
	}

	public static void runApplication(Runnable restartCallback, Runnable shutdownCallback, String[] args)
			throws IOException {
		new Main(restartCallback, shutdownCallback, args).run();
	}

	static class DefaultRestartCallback implements Runnable {

		@Override
		public void run() {

			if (log.isInfoEnabled()) {
				log.info("Shutting down with forker restart code.");
			}

			System.exit(90);
		}

	}

	static class DefaultShutdownCallback implements Runnable {

		@Override
		public void run() {

			if (log.isInfoEnabled()) {
				log.info("Shutting down using default shutdown mechanism");
			}

			System.exit(0);
		}

	}

	@Override
	protected void startServices() {
		try {
			((ClientServiceImpl) getClientService()).start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}
	}

	@Override
	public PlatformService getPlatformService() {
		return platform;
	}

	@Override
	public boolean start() {
		return ((ClientServiceImpl) getClientService()).startService();
	}

}
