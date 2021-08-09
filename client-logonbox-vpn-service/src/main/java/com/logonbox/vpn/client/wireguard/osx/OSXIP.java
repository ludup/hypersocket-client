package com.logonbox.vpn.client.wireguard.osx;

import java.io.IOException;

import com.logonbox.vpn.client.wireguard.AbstractVirtualInetAddress;

public class OSXIP extends AbstractVirtualInetAddress {

	public OSXIP() {
	}

	@Override
	public boolean isUp() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void delete() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void down() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void up() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getMac() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getDisplayName() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void dns(String[] dns) throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

}
