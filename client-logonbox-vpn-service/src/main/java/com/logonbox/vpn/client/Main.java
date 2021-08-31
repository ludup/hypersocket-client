package com.logonbox.vpn.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
import org.freedesktop.dbus.bin.EmbeddedDBusDaemon;
import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.BusAddress.AddressBusTypes;
import org.freedesktop.dbus.connections.SASL;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnection.DBusBusType;
import org.freedesktop.dbus.connections.impl.DirectConnection;
import org.freedesktop.dbus.connections.transports.TransportFactory;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.Message;
import org.freedesktop.dbus.utils.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.extensions.ExtensionTarget;
import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.client.dbus.VPNConnectionImpl;
import com.logonbox.vpn.client.dbus.VPNImpl;
import com.logonbox.vpn.client.service.ClientServiceImpl;
import com.logonbox.vpn.client.service.ConfigurationRepositoryImpl;
import com.logonbox.vpn.client.service.updates.ClientUpdater;
import com.logonbox.vpn.client.service.vpn.ConnectionRepositoryImpl;
import com.logonbox.vpn.client.wireguard.PlatformService;
import com.logonbox.vpn.client.wireguard.linux.LinuxPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.osx.BrewOSXPlatformServiceImpl;
import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;
import com.logonbox.vpn.common.client.ClientService;
import com.logonbox.vpn.common.client.ConfigurationRepository;
import com.logonbox.vpn.common.client.Connection;
import com.logonbox.vpn.common.client.ConnectionRepository;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.dbus.VPNFrontEnd;
import com.sshtools.forker.common.OS;
import com.sun.jna.Platform;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "logonbox-vpn-server", mixinStandardHelpOptions = true, description = "Command line interface to the LogonBox VPN service.")
public class Main implements Callable<Integer>, LocalContext, X509TrustManager {

	final static int DEFAULT_TIMEOUT = 10000;

	static Logger log = LoggerFactory.getLogger(Main.class);

	public static void main(String[] args) throws Exception {
		Main cli = new Main();
		int ret = new CommandLine(cli).execute(args);
		if (ret > 0)
			System.exit(ret);

		/* If ret = 0, we just wait for threads to die naturally */
	}

	protected static Main instance;

	public static int randInt(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}

	private ClientServiceImpl clientService;
	private PlatformService<?> platform;
	private DBusConnection conn;
	private Map<String, VPNFrontEnd> frontEnds = Collections.synchronizedMap(new HashMap<>());
	private EmbeddedDBusDaemon daemon;
	private Level defaultLogLevel;

	@Option(names = { "-a", "--address" }, description = "Address of Bus.")
	private String address;

	@Option(names = { "-e",
			"--embedded-bus" }, description = "Force use of embedded DBus service. Usually it is enabled by default for anything other than Linux.")
	private boolean embeddedBus;

	@Option(names = { "-sb",
			"--session-bus" }, description = "Force use of session DBus service. Usually it is automatically detected.")
	private boolean sessionBus;

	@Option(names = { "-R",
			"--no-registration-required" }, description = "Enable this to allow unregistered DBus clients to control the VPN. Not recommended for security reasons, anyone would be able to control anyone else's VPN connections.")
	private boolean noRegistrationRequired;

	@Option(names = { "-t",
			"--tcp-bus" }, description = "Force use of TCP DBus service. Usually it is enabled by default for Windows only.")
	private boolean tcpBus;

	@Option(names = { "-u",
			"--auth" }, description = "Mask of SASL authentication method to use.")
	private int authTypes= SASL.AUTH_ANON;

	private ConfigurationRepositoryImpl configurationRepository;
	
	public Main() throws Exception {
		instance = this;
		if (SystemUtils.IS_OS_LINUX) {
			platform = new LinuxPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_WINDOWS) {
			platform = new WindowsPlatformServiceImpl();
		} else if (SystemUtils.IS_OS_MAC_OSX) {
//			if(OsUtil.doesCommandExist("wg"))
				platform = new BrewOSXPlatformServiceImpl();
//			else
//				platform = new OSXPlatformServiceImpl();
		} else
			throw new UnsupportedOperationException(
					String.format("%s not currently supported.", System.getProperty("os.name")));
	}
	
	public static Main get() {
		return instance;
	}

	public Level getDefaultLogLevel() {
		return defaultLogLevel;
	}

	@Override
	public DBusConnection getConnection() {
		return conn;
	}

	public ClientService getClientService() {
		return clientService;
	}

	@Override
	public PlatformService<?> getPlatformService() {
		return platform;
	}

	@Override
	public Integer call() throws Exception {

		File logs = new File("logs");
		logs.mkdirs();

		String logConfigPath = System.getProperty("hypersocket.logConfiguration", "");
		if (logConfigPath.equals("")) {
			/* Load default */
			PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		} else {
			File logConfigFile = new File(logConfigPath);
			if (logConfigFile.exists())
				PropertyConfigurator.configureAndWatch(logConfigPath);
			else
				PropertyConfigurator.configure(Main.class.getResource("/default-log4j-service.properties"));
		}
		
		

		try {

			if (!buildServices()) {
				System.exit(3);
			}

			/* Have database, so enough to get configuration for log level, we can start logging now */
			String cfgLevel = configurationRepository.getValue(ConfigurationRepository.LOG_LEVEL, "");
			defaultLogLevel = org.apache.log4j.Logger.getRootLogger().getLevel();
			if(StringUtils.isNotBlank(cfgLevel)) {
				org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.toLevel(cfgLevel));
			}	
			log.info(String.format("LogonBox VPN Client, version %s", HypersocketVersion.getVersion(ClientUpdater.ARTIFACT_COORDS)));
			log.info(String.format("OS: %s", System.getProperty("os.name") + " / " + System.getProperty("os.arch") + " (" + System.getProperty("os.version") + ")"));

			if (!startServices()) {
				System.exit(3);
			}

			if (!configureDBus()) {
				System.exit(3);
			}

			if (!publishDefaultServices()) {
				System.exit(1);
			}

			if (!clientService.startSavedConnections()) {
				log.warn("Not all connections started.");
			}

		} catch (Exception e) {
			log.error("Failed to start", e);
			try {
				Thread.sleep(2500L);
			} catch (InterruptedException e1) {
			}
			return 1;
		}

		return 0;
	}

	public void shutdown() {
		shutdownEmbeddeDaemon();
		System.exit(0);
	}

	@Override
	public boolean hasFrontEnd(String source) {
		return frontEnds.containsKey(source);
	}

	@Override
	public VPNFrontEnd getFrontEnd(String source) {
		return frontEnds.get(source);
	}

	@Override
	public void deregisterFrontEnd(String source) {
		synchronized (frontEnds) {
			VPNFrontEnd fe = frontEnds.remove(source);
			if (fe == null) {
				throw new IllegalArgumentException(String.format("Front end '%s' not registered.", source));
			}
			else
				log.info(String.format("De-registered front-end %s.", source));
		}
	}

	@Override
	public VPNFrontEnd registerFrontEnd(String source, String target) {
		synchronized (frontEnds) {
			VPNFrontEnd fe = frontEnds.get(source);
			if (fe != null) {
				throw new IllegalArgumentException(String.format("Front end '%s' already registered.", source));
			}
			fe = new VPNFrontEnd(source, ExtensionTarget.valueOf(target));
			frontEnds.put(source, fe);
			return fe;
		}
	}

	@Override
	public void sendMessage(Message message) {
		conn.sendMessage(message);
	}

	private void shutdownEmbeddeDaemon() {
		if (daemon != null) {
			try {
				daemon.close();
			} catch (IOException e) {
				log.error("Failed to shutdown DBus service.", e);
			} finally {
				daemon = null;
			}
		}
	}

	private boolean configureDBus() throws Exception {
		/*
		 * Workaround for windows. It's unlikely there will be a 'machine id' available,
		 * so create one if it appears that this will cause an issue. <p> Better fixes
		 * are going to require patching java DBC or extracting when DBusConnection does
		 * (fortunately this is only called in a few places)
		 */
		try {
			DBusConnection.getDbusMachineId();
		} catch (Exception e) {
			File etc = new File(File.separator + "etc");
			if (!etc.exists()) {
				etc.mkdirs();
			}
			File machineId = new File(etc, "machine-id");
			if (machineId.exists())
				throw e;
			else {
				Random randomService = new Random();
				StringBuilder sb = new StringBuilder();
				while (sb.length() < 32) {
					sb.append(Integer.toHexString(randomService.nextInt()));
				}
				sb.setLength(32);
				try (PrintWriter w = new PrintWriter(new FileWriter(machineId), true)) {
					w.println(sb.toString());
				} catch (IOException ioe) {
					throw new RuntimeException(String.format("Failed to create machine ID file %s.", machineId), ioe);
				}
			}
		}

		File dbusPropertiesFile = getDBusPropertiesFile();

		if (SystemUtils.IS_OS_LINUX && !embeddedBus) {
			if (address != null) {
				log.info(String.format("Connected to DBus @%s", address));
				conn = DBusConnection.getConnection(address, true, true);
				log.info(String.format("Ready to DBus @%s", address));
			} else if (OS.isAdministrator()) {
				if(sessionBus) {
					log.info("Per configuration, connecting to Session DBus");
					conn = DBusConnection.getConnection(DBusBusType.SESSION);
					log.info("Ready to Session DBus");
					address = conn.getAddress().getRawAddress();
				}
				else {
					log.info("Connected to System DBus");
					conn = DBusConnection.getConnection(DBusBusType.SYSTEM);
					log.info("Ready to System DBus");
					address = conn.getAddress().getRawAddress();
				}
			} else {
				log.info("Not administrator, connecting to Session DBus");
				conn = DBusConnection.getConnection(DBusBusType.SESSION);
				log.info("Ready to Session DBus");
				address = conn.getAddress().getRawAddress();
			}
		} else {
			if (address == null) {
				if (SystemUtils.IS_OS_UNIX && !tcpBus) {
					address = DirectConnection.createDynamicSession();
				} else {
					address = DirectConnection.createDynamicTCPSession();
				}
			}

			BusAddress busAddress = new BusAddress(address);
			if (!busAddress.hasGuid()) {
				address += ",guid=" + TransportFactory.genGUID();
				busAddress = new BusAddress(address);
			}

			log.info(String.format("Starting embedded bus on address %s (auth types: %s)", busAddress.getRawAddress(), toAuthTypesString(authTypes)));
			daemon = new EmbeddedDBusDaemon();
			daemon.setAuthTypes(authTypes);
			daemon.setAddress(busAddress);
			daemon.startInBackground();
			log.info(String.format("Started embedded bus on address %s", busAddress.getRawAddress()));

			log.info("Connecting to embedded DBus");
			for (int i = 0; i < 6; i++) {
				try {
					conn = DBusConnection.getConnection(busAddress.getRawAddress());
					log.info("Connected to embedded DBus");
					break;
				} catch (DBusException dbe) {
					if (i > 4)
						throw dbe;
					Thread.sleep(500);
				}
			}
			
			/* Not ideal but we need read / write access to the domain socket from
			 * non-root users. 
			 * 
			 * TODO secure this a bit. at least use a group permission
			 */
			if( ( Platform.isLinux() || Util.isMacOs() ) && busAddress.getBusType().equals(AddressBusTypes.UNIX)) {
				Path path = Paths.get(busAddress.getPath());
				Files.setPosixFilePermissions(path, new LinkedHashSet<>(Arrays.asList(PosixFilePermission.values())));
			}
		}
		log.info(String.format("Requesting name from Bus %s", address));

		Properties properties = new Properties();
		properties.put("address", address);
		try (FileOutputStream out = new FileOutputStream(dbusPropertiesFile)) {
			properties.store(out, "LogonBox VPN Client Service");
		}

		try {
			conn.requestBusName("com.logonbox.vpn");
		} catch (Exception e) {
			log.error("Failed to connect to DBus. No remote state monitoring or management.", e);
			return false;
		}
		return true;
	}

	private String toAuthTypesString(int authTypes) {
		List<String> l = new ArrayList<>();
		if((authTypes & SASL.AUTH_ANON) > 0) {
			l.add("ANON");
		}
		if((authTypes & SASL.AUTH_EXTERNAL) > 0) {
			l.add("EXTERNAL");
		}
		if((authTypes & SASL.AUTH_SHA) > 0) {
			l.add("SHA");
		}
		if((authTypes & SASL.AUTH_NONE) > 0) {
			l.add("NONE");
		}
		return String.join(",", l);
	}

	private boolean startServices() {
		if (!"true".equals(System.getProperty("logonbox.vpn.strictSSL", "true"))) {
			installAllTrustingCertificateVerifier();
		}
		
		try {
			clientService.start();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to start client configuration service.", e);
		}

		return true;
	}

	private boolean buildServices() throws Exception {

		ConnectionRepository connectionRepository = new ConnectionRepositoryImpl();

		configurationRepository = new ConfigurationRepositoryImpl(this);
		clientService = new ClientServiceImpl(this, connectionRepository, configurationRepository);

		return true;

	}

	private File getDBusPropertiesFile() {
		return getPropertiesFile("dbus");
	}

	private File getPropertiesFile(String type) {
		File file;
		String path = System.getProperty("hypersocket." + type);

		if (log.isInfoEnabled()) {
			log.info(String.format("%s Path: %s", type.toUpperCase(), path));
		}
		if (path != null) {
			file = new File(path);
		} else if (Boolean.getBoolean("hypersocket.development")) {
			file = new File(System.getProperty("user.home") + File.separator + ".logonbox-vpn-client" + File.separator
					+ "conf" + File.separator + type + ".properties");
		} else {
			file = new File("conf" + File.separator + type + ".properties");
		}
		if (log.isInfoEnabled()) {
			log.info(String.format("Writing %s info to %s", type.toUpperCase(), file));
			String username = System.getProperty("user.name");
			log.info("Running as " + username);
		}

		file.getParentFile().mkdirs();
		return file;
	}

	private boolean publishDefaultServices() {

		try {
			/* DBus */
			if (log.isInfoEnabled()) {
				log.info(String.format("Exporting VPN service to DBus"));
			}
			conn.exportObject("/com/logonbox/vpn", new VPNImpl(this));
			if (log.isInfoEnabled()) {
				log.info(String.format("Exported VPN service to DBus"));
			}
			for (ConnectionStatus connectionStatus : clientService.getStatus(null)) {
				Connection connection = connectionStatus.getConnection();
				conn.exportObject(String.format("/com/logonbox/vpn/%d", connection.getId()),
						new VPNConnectionImpl(this, connection));
			}

			return true;
		} catch (Exception e) {
			log.error("Failed to publish service", e);
			return false;
		}
	}

	@Override
	public boolean isRegistrationRequired() {
		return !noRegistrationRequired;
	}

	@Override
	public Collection<VPNFrontEnd> getFrontEnds() {
		return frontEnds.values();
	}
	
	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		List<String> chainSubjectDN = new ArrayList<>();
		for (X509Certificate c : chain) {
			try {
				if(log.isDebugEnabled())
					log.debug(String.format("Validating: %s", c));
				chainSubjectDN.add(c.getSubjectDN().toString());
				c.checkValidity();
			} catch (CertificateException ce) {
				log.error("Certificate error. " + String.join(" -> ", chainSubjectDN), ce);
				throw ce;
			}
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		X509Certificate[] NO_CERTS = new X509Certificate[0];
		return NO_CERTS;
	}

	protected void installAllTrustingCertificateVerifier() {
		
		log.warn("NOT FOR PRODUCTION USE. All SSL certificates will be trusted regardless of status. This should only be used for testing.");

		Security.insertProviderAt(new ServiceTrustProvider(), 1);
		Security.setProperty("ssl.TrustManagerFactory.algorithm", ServiceTrustProvider.TRUST_PROVIDER_ALG);

		try {
			SSLContext sc = SSLContext.getInstance("SSL");
			sc.init(null, new TrustManager[] { this }, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		} catch (GeneralSecurityException e) {
		}

		// Create all-trusting host name verifier
		HostnameVerifier allHostsValid = new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				log.debug(String.format("Verify hostname %s: %s", hostname, session));
				return true;
			}
		};

		// Install the all-trusting host verifier
		HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
	}
}
