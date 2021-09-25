package com.logonbox.vpn.common.client;

import java.security.AccessController;
import java.security.KeyStore;
import java.security.PrivilegedAction;
import java.security.Provider;
import java.security.Security;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactorySpi;

@SuppressWarnings("serial")
public final class ClientTrustProvider extends Provider {
	static final String TRUST_PROVIDER_ALG = "ClientTrustAlgorithm";
	private static final String TRUST_PROVIDER_ID = "ClientTrustProvider";
	private static TrustManager trustManager;

	public ClientTrustProvider(TrustManager trustManager) {
		super(TRUST_PROVIDER_ID, "0.1", "Delegates to UI.");
		Security.setProperty("ssl.TrustManagerFactory.algorithm", ClientTrustProvider.TRUST_PROVIDER_ALG);
		AccessController.doPrivileged(new PrivilegedAction<Void>() {
			@Override
			public Void run() {
				put("TrustManagerFactory." + ClientTrustManagerFactory.getAlgorithm(),
						ClientTrustManagerFactory.class.getName());
				return null;
			}
		});
		ClientTrustProvider.trustManager = trustManager;
	}

	public final static class ClientTrustManagerFactory extends TrustManagerFactorySpi {
		public ClientTrustManagerFactory() {
		}

		@Override
		protected void engineInit(ManagerFactoryParameters mgrparams) {
		}

		@Override
		protected void engineInit(KeyStore keystore) {
		}

		@Override
		protected TrustManager[] engineGetTrustManagers() {
			return new TrustManager[] { trustManager };
		}

		public static String getAlgorithm() {
			return TRUST_PROVIDER_ALG;
		}
	}
}
