package com.hypersocket.client;

import java.io.IOException;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

public class MapResourceBundle extends ResourceBundle implements Serializable {

	private static final long serialVersionUID = 1L;

	private Map<String, Object> lookup;

	public MapResourceBundle() throws IOException {

	}

	public MapResourceBundle(Map<String, Object> lookup) throws IOException {
		this.lookup = lookup;
	}

	public Object handleGetObject(String key) {
		if (key == null) {
			throw new NullPointerException();
		}
		return lookup.get(key);
	}

	public Enumeration<String> getKeys() {
		final Iterator<String> it = lookup.keySet().iterator();
		return new Enumeration<String>() {

			@Override
			public boolean hasMoreElements() {
				return it.hasNext();
			}

			@Override
			public String nextElement() {
				return it.next();
			}
		};
	}

	protected Set<String> handleKeySet() {
		return lookup.keySet();
	}
}