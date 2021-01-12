package com.hypersocket.client;

import java.io.IOException;
import java.rmi.RemoteException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.client.rmi.DefaultClientService;
import com.hypersocket.client.rmi.FavouriteItemService;
import com.hypersocket.client.rmi.ResourceService;
import com.hypersocket.client.rmi.VPNService;
import com.hypersocket.client.service.DefaultClientServiceImpl;
import com.hypersocket.client.service.FavouriteItemServiceImpl;
import com.hypersocket.client.service.ResourceServiceImpl;
import com.hypersocket.client.service.vpn.VPNServiceImpl;

public class Main extends AbstractMain<DefaultClientService, DefaultContext> implements DefaultContext {

	static Logger log = LoggerFactory.getLogger(Main.class);

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

	public static void main(String[] args) {
		instance = new Main(new DefaultRestartCallback(), new DefaultShutdownCallback(), args);
		instance.run();
	}

	public static void runApplication(Runnable restartCallback, Runnable shutdownCallback, String[] args)
			throws IOException {

		new Main(restartCallback, shutdownCallback, args).run();

	}

	private FavouriteItemService favouriteItemService;

	private ResourceService resourceService;

	private VPNServiceImpl vpnService;

	public Main(Runnable restartCallback, Runnable shutdownCallback, String[] args) {
		super(restartCallback, shutdownCallback, args);
	}

	@Override
	public ResourceService getResourceService() {
		return resourceService;
	}

	@Override
	public VPNService getVPNService() {
		return vpnService;
	}

	@Override
	public boolean start() {
		return ((DefaultClientServiceImpl) getClientService()).startService();
	}

	@Override
	protected DefaultClientServiceImpl createServiceImpl() {
		return new DefaultClientServiceImpl(this);
	}

	protected void createServices() throws RemoteException {

		if (log.isInfoEnabled()) {
			log.info("Creating ResourceService");
		}

		resourceService = new ResourceServiceImpl();

		if (log.isInfoEnabled()) {
			log.info("Creating VPNService");
		}
		vpnService = new VPNServiceImpl(resourceService);

		if (log.isInfoEnabled()) {
			log.info("Creating FavouriteItemService");
		}

		favouriteItemService = new FavouriteItemServiceImpl();

	}

	protected void publishServices() throws Exception {
		publishService(DefaultClientService.class, getClientService());
		publishService(ResourceService.class, resourceService);
		publishService(FavouriteItemService.class, favouriteItemService);
	}

	@Override
	protected void startServices() throws RemoteException {
	}

	protected void unpublishServices() {
		try {
			getRegistry().unbind("vpnService");
		} catch (Exception e) {
		}

		try {
			getRegistry().unbind("resourceService");
		} catch (Exception e) {
		}

		try {
			getRegistry().unbind("favouriteItemService");
		} catch (Exception e) {
		}
	}

}
