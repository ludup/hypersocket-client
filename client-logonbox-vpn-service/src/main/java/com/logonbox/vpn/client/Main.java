package com.logonbox.vpn.client;

import java.io.IOException;
import java.rmi.RemoteException;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.AbstractMain;
import com.hypersocket.client.rmi.ResourceService;
import com.logonbox.vpn.client.service.LogonBoxVPNClientServiceImpl;
import com.logonbox.vpn.client.service.vpn.PeerConfigurationServiceImpl;
import com.logonbox.vpn.client.wireguard.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.common.client.LogonBoxVPNClientService;
import com.logonbox.vpn.common.client.PeerConfigurationService;

public class Main extends AbstractMain<LogonBoxVPNClientService, LogonBoxVPNContext> implements LogonBoxVPNContext {

	static Logger log = LoggerFactory.getLogger(Main.class);

	private PeerConfigurationServiceImpl peerConfigurationService;
	private PlatformService platform;

	public Main(Runnable restartCallback, Runnable shutdownCallback, String[] args) {
		super(restartCallback, shutdownCallback, args);
	}

	@Override
	protected void createServices() throws RemoteException {

		if (log.isInfoEnabled()) {
			log.info("Creating Peer Configuration Service");
		}

		if (SystemUtils.IS_OS_LINUX) {
			platform = new LinuxPlatformServiceImpl();
		} else
			throw new UnsupportedOperationException(
					String.format("%s not currently supported.", System.getProperty("os.name")));

		peerConfigurationService = new PeerConfigurationServiceImpl(this);

	}

	@Override
	protected LogonBoxVPNClientServiceImpl createServiceImpl() {
		return new LogonBoxVPNClientServiceImpl(this);
	}

	@Override
	protected void unpublishServices() {
		try {
			getRegistry().unbind("peerConfigurationService");
		} catch (Exception e) {
		}
	}

	@Override
	protected void publishServices() throws Exception {
		publishService(PeerConfigurationService.class, peerConfigurationService);
		publishService(LogonBoxVPNClientService.class, getClientService());
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
				log.info("There is no restart mechanism available. Shutting down");
			}

			System.exit(0);
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
			peerConfigurationService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start peer configuration service.", e);
		}
		try {
			((LogonBoxVPNClientServiceImpl) getClientService()).start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}
	}

	@Override
	public PeerConfigurationService getPeerConfigurationService() {
		return peerConfigurationService;
	}

	@Override
	public PlatformService getPlatformService() {
		return platform;
	}

	@Override
	public boolean start() {
		return ((LogonBoxVPNClientServiceImpl) getClientService()).startService();
	}

	@Override
	public ResourceService getResourceService() {
		throw new UnsupportedOperationException();
	}

}
