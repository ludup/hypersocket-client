package com.logonbox.vpn.client.gui.jfx;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.logonbox.vpn.common.client.ClientService;

public class CustomCookieStore implements CookieStore {
	static Logger log = LoggerFactory.getLogger(CustomCookieStore.class);

	public static class CookieWrapper implements Serializable {
		private static final long serialVersionUID = 1L;

		private String comment;
		private int version;
		private String value;
		private boolean secure;
		private String path;
		private String name;
		private long maxAge;
		private String portlist;
		private String domain;
		private boolean discard;
		private String commentUrl;

		public CookieWrapper() {
		}

		public CookieWrapper(HttpCookie cookie) {
			comment = cookie.getComment();
			commentUrl = cookie.getCommentURL();
			discard = cookie.getDiscard();
			domain = cookie.getDomain();
			maxAge = cookie.getMaxAge();
			name = cookie.getName();
			path = cookie.getPath();
			portlist = cookie.getPortlist();
			secure = cookie.getSecure();
			value = cookie.getValue();
			version = cookie.getVersion();
		}

		@Override
		public int hashCode() {
			return Objects.hash(name);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CookieWrapper other = (CookieWrapper) obj;
			return Objects.equals(name, other.name);
		}

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public int getVersion() {
			return version;
		}

		public void setVersion(int version) {
			this.version = version;
		}

		public String getValue() {
			return value;
		}

		public void setValue(String value) {
			this.value = value;
		}

		public boolean isSecure() {
			return secure;
		}

		public void setSecure(boolean secure) {
			this.secure = secure;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public long getMaxAge() {
			return maxAge;
		}

		public void setMaxAge(long maxAge) {
			this.maxAge = maxAge;
		}

		public String getPortlist() {
			return portlist;
		}

		public void setPortlist(String portlist) {
			this.portlist = portlist;
		}

		public String getDomain() {
			return domain;
		}

		public void setDomain(String domain) {
			this.domain = domain;
		}

		public boolean isDiscard() {
			return discard;
		}

		public void setDiscard(boolean discard) {
			this.discard = discard;
		}

		public String getCommentUrl() {
			return commentUrl;
		}

		public void setCommentUrl(String commentUrl) {
			this.commentUrl = commentUrl;
		}

		public HttpCookie toHttpCookie() {
			HttpCookie c = new HttpCookie(name, value);
			c.setComment(comment);
			c.setCommentURL(commentUrl);
			c.setDiscard(discard);
			c.setDomain(domain);
			c.setMaxAge(maxAge);
			c.setPath(path);
			c.setPortlist(portlist);
			c.setSecure(secure);
			c.setVersion(version);
			
			return c;
		}
	}

	private Map<URI, List<CookieWrapper>> cookies = new HashMap<>();

	@SuppressWarnings("unchecked")
	public CustomCookieStore() {
		File file = getFile();
		if (file.exists()) {
			try (ObjectInputStream oin = new ObjectInputStream(new FileInputStream(file))) {
				cookies = (Map<URI, List<CookieWrapper>>) oin.readObject();
			} catch (Exception e) {
				log.warn("Could not load cookie jar, resetting.", e);
				save();
			}
		}
	}

	void save() {
		File f = getFile();
		File p = f.getParentFile();
		if (!p.exists() && !p.mkdirs())
			throw new IllegalStateException("Could not create directory for cookie jar. " + p);
		try (ObjectOutputStream oout = new ObjectOutputStream(new FileOutputStream(f))) {
			oout.writeObject(cookies);
		} catch (IOException ioe) {
			throw new IllegalStateException("Could not save cookie jar.", ioe);
		}
	}

	public File getFile() {
		return new File(ClientService.CLIENT_HOME, "web-cookies.dat");
	}

	@Override
	public boolean removeAll() {
		synchronized (cookies) {
			int size = cookies.size();
			cookies.clear();
			save();
			return size > 0;
		}
	}

	@Override
	public boolean remove(URI uri, HttpCookie cookie) {
		synchronized (cookies) {
			List<CookieWrapper> l = cookies.get(uri);
			CookieWrapper cookieW = new CookieWrapper(cookie);
			if (l != null && l.contains(cookieW)) {
				l.remove(cookieW);
				if (l.isEmpty())
					cookies.remove(uri);
				save();
				return true;
			}
			return false;
		}
	}

	@Override
	public List<URI> getURIs() {
		return new ArrayList<>(cookies.keySet());
	}

	@Override
	public List<HttpCookie> getCookies() {
		List<HttpCookie> l = new ArrayList<>();
		for (List<CookieWrapper> v : cookies.values()) {
			for (CookieWrapper w : v) {
				l.add(w.toHttpCookie());
			}
		}
		return l;
	}

	@Override
	public List<HttpCookie> get(URI uri) {
		synchronized (cookies) {
			return cookies.containsKey(uri)
					? cookies.get(uri).stream().map(o -> o.toHttpCookie()).collect(Collectors.toList())
					: Collections.emptyList();
		}
	}

	@Override
	public void add(URI uri, HttpCookie cookie) {
		synchronized (cookies) {
			List<CookieWrapper> l = cookies.get(uri);
			if (l == null) {
				l = new ArrayList<>();
				cookies.put(uri, l);
			}
			l.add(new CookieWrapper(cookie));
			save();
		}
	}
}