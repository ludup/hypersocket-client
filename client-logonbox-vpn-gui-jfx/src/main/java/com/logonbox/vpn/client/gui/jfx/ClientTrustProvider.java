package com.logonbox.vpn.client.gui.jfx;

import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;

@SuppressWarnings("serial")
public final class ClientTrustProvider extends Provider {
	static final String TRUST_PROVIDER_ALG = "ClientTrustAlgorithm";
	private static final String TRUST_PROVIDER_ID = "ClientTrustProvider";

	public ClientTrustProvider() {
		super(TRUST_PROVIDER_ID, "0.1", "Delegates to UI.");
		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			public Void run() {
				put("TrustManagerFactory." + ClientTrustManagerFactory.getAlgorithm(),
						ClientTrustManagerFactory.class.getName());
				return null;
			}
		});
	}

	public final static class ClientTrustManagerFactory extends TrustManagerFactorySpi {
		public ClientTrustManagerFactory() {
		}

		protected void engineInit(ManagerFactoryParameters mgrparams) {
		}

		protected void engineInit(KeyStore keystore) {
		}

		protected TrustManager[] engineGetTrustManagers() {
			return new TrustManager[] { Client.get() };
		}

		public static String getAlgorithm() {
			return TRUST_PROVIDER_ALG;
		}
	}
}