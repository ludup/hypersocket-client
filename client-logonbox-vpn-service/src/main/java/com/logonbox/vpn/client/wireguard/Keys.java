package com.logonbox.vpn.client.wireguard;

import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base64;

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
			return Base64.encodeBase64String(publicKey);
		}

		public byte[] getPrivateKey() {
			return privateKey;
		}

		public String getBase64PrivateKey() {
			return Base64.encodeBase64String(privateKey);
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
		return pubkey(Base64.decodeBase64(base64PrivateKey));
	}

	public static KeyPair pubkey(byte[] privateKey) {
		KeyPair kp = new KeyPair(privateKey);
		kp.publicKey = Curve25519.scalarBaseMult(kp.privateKey);
		return kp;
	}
}
