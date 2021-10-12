package com.logonbox.vpn.common.client;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ini4j.Profile.Section;

public class Util {

	private static final boolean IS_64BIT = is64bit0();


	public static boolean isAdministrator() {
		if (SystemUtils.IS_OS_WINDOWS) {
			try {
				String programFiles = System.getenv("ProgramFiles");
				if (programFiles == null) {
					programFiles = "C:\\Program Files";
				}
				Path temp = Files.createTempFile(Paths.get(programFiles), "foo", "txt");
				temp.toFile().deleteOnExit();
				Files.delete(temp);
				return true;
			} catch (Exception e) {
				return false;
			}
		}
		if (SystemUtils.IS_OS_UNIX) {
			return System.getProperty("forker.administratorUsername", System.getProperty("vm.rootUser", "root"))
					.equals(System.getProperty("user.name"));
		}
		return false;
	}
	
	public static String titleUnderline(int len) {
		return repeat(len, '=');
	}
	
	public static String repeat(int times, char ch) {
		StringBuilder l = new StringBuilder();
		for(int i = 0 ; i < times; i++) {
			l.append('=');
		}
		return l.toString();
	}

	public static String hash(byte[] in) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			md.update(in);
			byte[] bytes = md.digest();
			return Base64.getEncoder().encodeToString(bytes);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to hash.", e);
		}
	}

	public static List<String> toStringList(Section section, String key) {
		List<String> n = new ArrayList<>();
		String val = section.get(key, "");
		if (!val.equals("")) {
			for (String a : val.split(",")) {
				n.add(a.trim());
			}
		}
		return n;
	}
	
	public static String getOS() {
		if (SystemUtils.IS_OS_WINDOWS) {
			return "windows";
		} else if (SystemUtils.IS_OS_LINUX) {
			return "linux";
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			return "osx";
		} else {
			return "other";
		}
	}
	
	public static String getDeviceName() {
		String hostname = SystemUtils.getHostName();
		if (StringUtils.isBlank(hostname)) {
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (Exception e) {
				hostname = "Unknown Host";
			}
		}
		String os = System.getProperty("os.name");
		if (SystemUtils.IS_OS_WINDOWS) {
			os = "Windows";
		} else if (SystemUtils.IS_OS_LINUX) {
			os = "Linux";
		} else if (SystemUtils.IS_OS_MAC_OSX) {
			os = "Mac OSX";
		}
		return os + " " + hostname;
	}
	
	public static byte[] decodeHexString(String hexString) {
		if (hexString.length() % 2 == 1) {
			throw new IllegalArgumentException("Invalid hexadecimal String supplied.");
		}

		byte[] bytes = new byte[hexString.length() / 2];
		for (int i = 0; i < hexString.length(); i += 2) {
			bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
		}
		return bytes;
	}

	public static byte hexToByte(String hexString) {
		int firstDigit = toDigit(hexString.charAt(0));
		int secondDigit = toDigit(hexString.charAt(1));
		return (byte) ((firstDigit << 4) + secondDigit);
	}

	private static int toDigit(char hexChar) {
		int digit = Character.digit(hexChar, 16);
		if (digit == -1) {
			throw new IllegalArgumentException("Invalid Hexadecimal Character: " + hexChar);
		}
		return digit;
	}

	public static int byteSwap(int a) {
		return ((a & 0xff000000) >>> 24) | ((a & 0x00ff0000) >>> 8) | ((a & 0x0000ff00) << 8)
				| ((a & 0x000000ff) << 24);
	}

	/**
	 * Parse a space separated string into a list, treating portions quotes with
	 * single quotes as a single element. Single quotes themselves and spaces can be
	 * escaped with a backslash.
	 * 
	 * @param command command to parse
	 * @return parsed command
	 */
	public static List<String> parseQuotedString(String command) {
		List<String> args = new ArrayList<String>();
		boolean escaped = false;
		boolean quoted = false;
		StringBuilder word = new StringBuilder();
		for (int i = 0; i < command.length(); i++) {
			char c = command.charAt(i);
			if (c == '"' && !escaped) {
				if (quoted) {
					quoted = false;
				} else {
					quoted = true;
				}
			} else if (c == '\\' && !escaped) {
				escaped = true;
			} else if (c == ' ' && !escaped && !quoted) {
				if (word.length() > 0) {
					args.add(word.toString());
					word.setLength(0);
					;
				}
			} else {
				word.append(c);
			}
		}
		if (word.length() > 0)
			args.add(word.toString());
		return args;
	}

	public static String getUri(Connection connection) {
		if (connection == null) {
			return "";
		}
		return connection.getUri(false);
	}

	public static void prepareConnectionWithURI(URI uriObj, Connection connection) {
		if (!uriObj.getScheme().equals("https")) {
			throw new IllegalArgumentException("Only HTTPS is supported.");
		}

		connection.setHostname(uriObj.getHost());
		connection.setPort(uriObj.getPort() <= 0 ? 443 : uriObj.getPort());
		connection.setConnectAtStartup(false);
		connection.setPath(uriObj.getPath());
	}

	public static URI getUri(String uriString) throws URISyntaxException {
		if (!uriString.startsWith("https://")) {
			if (uriString.indexOf("://") != -1) {
				throw new IllegalArgumentException("Only HTTPS is supported.");
			}
			uriString = "https://" + uriString;
		}
		while (uriString.endsWith("/"))
			uriString = uriString.substring(0, uriString.length() - 1);
		URI uri = new URI(uriString);
		if ("".equals(uri.getPath()))
			uri = uri.resolve("/app");
		return uri;
	}

	public static String toHumanSize(long bytes) {
		long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
		if (absB < 1024) {
			return bytes + " B";
		}
		long value = absB;
		CharacterIterator ci = new StringCharacterIterator("KMGTPE");
		for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
			value >>= 10;
			ci.next();
		}
		value *= Long.signum(bytes);
		return String.format("%.1f %ciB", value / 1024.0, ci.current());
	}

	public static boolean is64bit() {
		return IS_64BIT;
	}

	private static boolean is64bit0() {
		String systemProp = System.getProperty("com.ibm.vm.bitmode");
		if (systemProp != null) {
			return "64".equals(systemProp);
		}
		systemProp = System.getProperty("sun.arch.data.model");
		if (systemProp != null) {
			return "64".equals(systemProp);
		}
		systemProp = System.getProperty("java.vm.version");
		return systemProp != null && systemProp.contains("_64");
	}
}
