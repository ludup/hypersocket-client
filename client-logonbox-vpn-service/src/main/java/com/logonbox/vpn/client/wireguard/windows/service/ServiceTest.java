package com.logonbox.vpn.client.wireguard.windows.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.logonbox.vpn.client.wireguard.windows.WindowsPlatformServiceImpl;

public class ServiceTest {

	public static void main(String[] args) throws Exception {
		if(args.length > 0 && args[0].equals("uninstall")) {

			WindowsPlatformServiceImpl w = new WindowsPlatformServiceImpl();
			if(args.length > 1) {
				w.uninstall(WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$" + args[1]);
			}
			else {
				for(int i = 0 ; i < 50; i++) {
					System.out.println("Uninstall net" + i);
					try {
						w.uninstall(WindowsPlatformServiceImpl.TUNNEL_SERVICE_NAME_PREFIX + "$net" + i);
						w.uninstall("WireGuardTunnel" + "$net" + i);
					} catch (Exception e) {
					}	
				}
			}
		}
		else if(args.length > 0 && args[0].equals("install")) {
			Path cfgFile = Paths.get("TEMP-TEST-WIREGUARD.conf");
			if(!Files.exists(cfgFile)) {
				throw new IllegalStateException("Need " + cfgFile + ", run this tool from the root of its project.");
			}
			
			String name = args.length > 1 ? args[1] : "net10";
			
			/* Create a temp directory for the actual config file */
			Path tmpDir = Paths.get("tmp");
			if(!Files.exists(tmpDir)) {
				Files.createDirectories(tmpDir);
			}
			
			/* Copy the test config file to a filename with the name of the intferace in it */
			Path netCfgFile = tmpDir.resolve(name + ".conf");
			try(OutputStream o = Files.newOutputStream(netCfgFile)) {
				try(InputStream i = Files.newInputStream(cfgFile)) {
					i.transferTo(o);
				}
			}
			
			/* Log file for the Java part of the service helper (doesnt log DLL messages) */
			Path logFile = tmpDir.resolve(name + ".log");
			
			/* Install the service */
			WindowsPlatformServiceImpl w = new WindowsPlatformServiceImpl();
			w.installService(name, netCfgFile, logFile);
		} 
		else 
			throw new IllegalArgumentException(String.format("Usage: %s <install [netX]|uninstall [netX]>", ServiceTest.class.getName()));
	}
}
