package com.logonbox.vpn.client.wireguard.osx;

import java.io.IOException;

import com.logonbox.vpn.client.wireguard.VirtualInetAddress;

public class OSXIP implements VirtualInetAddress {

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
	public int getMtu() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getName() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getPeer() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public String getTable() {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void setMtu(int mtu) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void setName(String name) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void setPeer(String peer) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void setTable(String table) {
		throw new UnsupportedOperationException("TODO");
	}

	@Override
	public void up() throws IOException {
		throw new UnsupportedOperationException("TODO");
	}

}
