package com.logonbox.vpn.client;

import java.beans.Introspector;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.URL;
import java.rmi.AccessException;
import java.rmi.NoSuchObjectException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationServiceImpl;
import com.logonbox.vpn.client.service.GUIRegistryImpl;
import com.logonbox.vpn.client.service.vpn.ConnectionServiceImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.linux.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.osx.OSXPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationService;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionService;
import com.logonbox.vpn.common.client.GUIRegistry;

public class Main implements LocalContext {

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

	protected static Main instance;
	static Logger log = LoggerFactory.getLogger(Main.class);
	
	public static Main getInstance() {
		return instance;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		instance = new Main(new DefaultRestartCallback(), new DefaultShutdownCallback(), args);
		instance.run();
	}
	
	public static int randInt(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}
	
	public static void runApplication(Runnable restartCallback, Runnable shutdownCallback, String[] args)
			throws IOException {
		new Main(restartCallback, shutdownCallback, args).run();
	}
	
	private String[] args;
	private ExecutorService bossExecutor;
	private ClientServiceImpl clientService;
	private ConfigurationServiceImpl configurationService;
	private ConnectionService connectionService;
	private GUIRegistry guiRegistry;
	private PlatformService<?> platform;
	private int port;
	private Properties properties = new Properties();
	private Registry registry;
	private Runnable restartCallback;
	private Runnable shutdownCallback;
	private ExecutorService workerExecutor;
	private RMIServerSocketFactory regFactory;

	public Main(Runnable restartCallback, Runnable shutdownCallback, String[] args) {
		this.restartCallback = restartCallback;
		this.shutdownCallback = shutdownCallback;
		this.args = args;
		this.guiRegistry = new GUIRegistryImpl();

		bossExecutor = Executors.newCachedThreadPool();
		workerExecutor = Executors.newCachedThreadPool();
		
		if (SystemUtils.IS_OS_LINUX) {
			platform = new LinuxPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_WINDOWS) {
			platform = new WindowsPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			platform = new OSXPlatformServiceImpl();
		} else
			throw new UnsupportedOperationException(
					String.format("%s not currently supported.", System.getProperty("os.name")));

		regFactory = new RMIServerSocketFactory() {
			@Override
			public ServerSocket createServerSocket(int port) throws IOException {
				return new ServerSocket(port + 1, 0, InetAddress.getByName("127.0.0.1"));
			}
		};
	}

	@Override
	public ExecutorService getBoss() {
		return bossExecutor;
	}

	public ClientService getClientService() {
		return clientService;
	}

	@Override
	public ConfigurationService getConfigurationService() {
		return configurationService;
	}

	@Override
	public ConnectionService getConnectionService() {
		return connectionService;
	}

	@Override
	public GUIRegistry getGuiRegistry() {
		return guiRegistry;
	}

	@Override
	public PlatformService<?> getPlatformService() {
		return platform;
	}

	@Override
	public Runnable getRestartCallback() {
		return restartCallback;
	}

	@Override
	public ExecutorService getWorker() {
		return workerExecutor;
	}

	public void restart() {
		close();
		restartCallback.run();
	}

	/**
	 */
	public void run() {
		
		int ret = platform.processCLI(args);
		if(ret != Integer.MIN_VALUE) {
			System.exit(ret);
		}

		File logs = new File("logs");
		logs.mkdirs();

		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if(logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		}
		else {
			File logConfigFile = new File(logConfigPath);
			if(logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		}

		System.setProperty("java.rmi.server.hostname", "localhost");

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}

		while (true) {
			try {
				if (!buildServices()) {
					System.exit(3);
				}

				if (!publishDefaultServices()) {
					System.exit(1);
				}

				if (!clientService.startSavedConnections()) {
					log.warn("Not all connections started.");
				}

				poll();
			} catch (Exception e) {
				log.error("Failed to start", e);
				try {
					Thread.sleep(2500L);
				} catch (InterruptedException e1) {
				}
			}
		}
	}

	public void shutdown() {
		close();
		shutdownCallback.run();
	}

	protected void close() {
		bossExecutor.shutdown();
		workerExecutor.shutdown();
	}

	protected Registry getRegistry() {
		return registry;
	}

	protected <T extends Remote> void publishService(Class<T> type, T obj) throws Exception {

		String name = Introspector.decapitalize(type.getSimpleName());

		if (log.isInfoEnabled()) {
			log.info(String.format("Publishing service %s (%s)", name, type.getName()));
		}

		Remote stub = UnicastRemoteObject.exportObject(obj, port);
		registry.rebind(name, stub);

		if (log.isInfoEnabled()) {
			log.info(String.format("Published service %s (%s)", name, type.getName()));
		}

	}

	protected void publishServices() throws Exception {
		publishService(ClientService.class, getClientService());
	}

	protected void unpublishServices() {
		try {
			getRegistry().unbind("connectionService");
		} catch (Exception e) {
		}
	}

	boolean buildServices() throws RemoteException {
		port = -1;

		File rmiPropertiesFile;
		String rmiPath = System.getProperty("hypersocket.rmi");

		if (log.isInfoEnabled()) {
			log.info("RMI PATH: " + rmiPath);
		}
		if (rmiPath != null) {
			rmiPropertiesFile = new File(rmiPath);
		} else if (Boolean.getBoolean("hypersocket.development")) {
			rmiPropertiesFile = new File(System.getProperty("user.home") + File.separator + ".logonbox" + File.separator
					+ "conf" + File.separator + "rmi.properties");
		} else {
			rmiPropertiesFile = new File("conf" + File.separator + "rmi.properties");
		}
		if (log.isInfoEnabled()) {
			log.info("Writing RMI info to " + rmiPropertiesFile);
			String username = System.getProperty("user.name");
			log.info("Running as " + username);
		}

		rmiPropertiesFile.getParentFile().mkdirs();

		/*
		 * Get the existing port number, if any. If it is still active, and appears to
		 * be an RMI server, then fail as this means there is already a client running.
		 */
		if (rmiPropertiesFile.exists()) {
			try {
				InputStream in = new FileInputStream(rmiPropertiesFile);
				try {
					properties.load(in);
				} finally {
					in.close();
				}
			} catch (IOException ioe) {
				throw new RemoteException("Failed to read existing " + rmiPropertiesFile + ".");
			}

			port = Integer.parseInt(properties.getProperty("port", "50000"));

			try {
				ServerSocket ss = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
				ss.close();
				// The port is free, we will try to use it again first
			} catch (SocketException se) {
				boolean isExistingClient = false;
				// Already running, see if it is RMI
				try {
					@SuppressWarnings("unused")
					Registry r = LocateRegistry.getRegistry("127.0.0.1", port, RMISocketFactory.getDefaultSocketFactory());
					isExistingClient = true;
				} catch (RemoteException re) {
					/*
					 * Port is in use, but not an RMI server, so consider it unusable and choose
					 * another random port
					 */
				}

				if (isExistingClient) {
					// It does appear to RMI registry, so we'll assume it's an existing running
					// client
					log.error("The VPN client is already running on port " + port);
					return false;
				}
			} catch (IOException ioe) {
				/*
				 * Some other error, let the attempt to create the registry fail if there really
				 * is a problem
				 */
			}
		}

		if (port == -1) {
			port = randInt(49152, 65535);
		}

		int attempts = 100;
		while (attempts > 0) {
			try {
				if (log.isInfoEnabled()) {
					log.info("Trying RMI server on port " + port);
				}
				registry = LocateRegistry.createRegistry(port, RMISocketFactory.getDefaultSocketFactory(), regFactory);
				if (log.isInfoEnabled()) {
					log.info("RMI server started on port " + port);
				}
				properties.put("port", String.valueOf(port));
				FileOutputStream out = new FileOutputStream(rmiPropertiesFile);
				try {
					properties.store(out, "Hypersocket Client Service");
				} finally {
					out.close();
				}
				break;
			} catch (Exception e) {
				attempts--;
				port = randInt(49152, 65535);
				continue;
			}
		}

		if (registry == null) {
			throw new RemoteException("Failed to startup after 100 attempts");
		}

		if (log.isInfoEnabled()) {
			log.info("Creating ConnectionService");
		}
		
		connectionService = new ConnectionServiceImpl(this);

		if (log.isInfoEnabled()) {
			log.info("Creating ConfigurationService");
		}

		configurationService = new ConfigurationServiceImpl(this);

		if (log.isInfoEnabled()) {
			log.info("Creating ClientService");
		}

		buildDefaultConnections();

		clientService = new ClientServiceImpl(this);

		try {
			connectionService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start peer configuration service.", e);
		}

		try {
			clientService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}

		return true;

	}

	void poll() {

		try {
			while (true) {
				try {
					Thread.sleep(2500L);
				} catch (InterruptedException e) {

				}

				registry.list();

			}
		} catch (AccessException e) {
			log.error("RMI server seems to have failed", e);
		} catch (RemoteException e) {
			log.error("RMI server seems to have failed", e);
		}

		try {
			registry.unbind("clientService");
		} catch (Exception e) {
		}

		try {
			registry.unbind("configurationService");
		} catch (Exception e) {
		}

		try {
			registry.unbind("connectionService");
		} catch (Exception e) {
		}

		unpublishServices();

		try {
			UnicastRemoteObject.unexportObject(registry, true);
		} catch (NoSuchObjectException e) {
		}
	}

	boolean publishDefaultServices() {

		try {
			publishService(ConnectionService.class, connectionService);
			publishService(ConfigurationService.class, configurationService);
			publishServices();
			return true;
		} catch (Exception e) {
			log.error("Failed to publish service", e);
			return false;
		}
	}


	private void buildDefaultConnections() {

		for (String arg : args) {

			if (arg.startsWith("url=")) {
				try {
					URL url = new URL(arg.substring(4));

					Connection con = connectionService.getConnection(url.getHost());
					if (con == null) {
						con = connectionService.createNew();
						String name = url.getHost();
						if (url.getPort() > 0 && url.getPort() != 443)
							name += ":" + url.getPort();
						con.setName(name);
						con.setStayConnected(true);

						con.setConnectAtStartup(false);
						con.setHostname(url.getHost());
						con.setPort(url.getPort() <= 0 ? 443 : url.getPort());

						String path = url.getPath();
						if (path.equals("") || path.equals("/")) {
							path = "/hypersocket";
						} else if (path.indexOf('/', 1) > -1) {
							path = path.substring(0, path.indexOf('/', 1));
						}
						con.setPath(path);

						connectionService.save(con);
					}
				} catch (Exception e) {
					log.error("Failed to process url app parameter", e);
				}
			}
		}
	}


}
