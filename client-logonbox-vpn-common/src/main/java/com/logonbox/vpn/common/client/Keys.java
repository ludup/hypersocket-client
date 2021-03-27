package com.logonbox.vpn.common.client;

import java.security.SecureRandom;
import java.util.Base64;

public class Keys {

	public static class KeyPair {
		private byte[] publicKey;
		private final byte[] privateKey;

		private KeyPair(byte[] privateKey) {
			this.privateKey = privateKey;
		}

		private KeyPair() {
			privateKey = new byte[32];
		}

		public byte[] getPublicKey() {
			return publicKey;
		}
		
		public String getBase64PublicKey() {
			return Base64.getEncoder().encodeToString(publicKey);
		}

		public byte[] getPrivateKey() {
			return privateKey;
		}

		public String getBase64PrivateKey() {
			return Base64.getEncoder().encodeToString(privateKey);
		}

	}

	private Keys() {
	}

	public static KeyPair genkey() {
		KeyPair kp = new KeyPair();
		SecureRandom random = new SecureRandom();
		random.nextBytes(kp.privateKey);
		kp.publicKey = Curve25519.scalarBaseMult(kp.privateKey);
		return kp;
	}

	public static KeyPair pubkey(String base64PrivateKey) {
		return pubkey(Base64.getDecoder().decode(base64PrivateKey));
	}

	public static KeyPair pubkey(byte[] privateKey) {
		KeyPair kp = new KeyPair(privateKey);
		kp.publicKey = Curve25519.scalarBaseMult(kp.privateKey);
		return kp;
	}
}
