package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;

@Entity
@Table(name = "peer_configurations")
public class ConnectionImpl implements Connection, Serializable {

	private static final long serialVersionUID = 1007856764641094257L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@Column(nullable = true)
	private String usernameHint;

	@Column(nullable = true)
	private String userPrivateKey;

	@Column(nullable = true)
	private String userPublicKey;

	@Column(nullable = true)
	private String publicKey;

	@Column(nullable = true)
	private String address;

	@Column(nullable = true)
	private String endpointAddress;

	@Column
	private int endpointPort;

	@Column(nullable = false)
	private int mtu;

    @Column(columnDefinition="varchar(10240)")
	private String dns;

	@Column
	private int peristentKeepalive;

	@Column(columnDefinition = "boolean default false")
	private boolean shared;

    @Column(columnDefinition="varchar(10240)")
	private String owner;

    @Column(columnDefinition="varchar(10240)")
	private String allowedIps;
	
	@Column
	private String name;

	@Column(nullable = false)
	private String hostname;

	@Column(nullable = false)
	private Integer port = Integer.valueOf(443);

	@Column(nullable = false)
	private String path = "/app";

	@Column(columnDefinition = "boolean default false")
	private boolean stayConnected;

	@Column(columnDefinition = "boolean default false")
	private boolean connectAtStartup;

	@Column(columnDefinition = "boolean default false")
	private boolean routeAll;

    @Column(columnDefinition="varchar(10240)")
	private String preUp;

    @Column(columnDefinition="varchar(10240)")
	private String postUp;

    @Column(columnDefinition="varchar(10240)")
	private String preDown;

    @Column(columnDefinition="varchar(10240)")
	private String postDown;

	@Override
	public boolean isRouteAll() {
		return routeAll;
	}

	@Override
	public void setRouteAll(boolean routeAll) {
		this.routeAll = routeAll;
	}

	@Override
	public String getPreUp() {
		return preUp;
	}

	@Override
	public void setPreUp(String preUp) {
		this.preUp = preUp;
	}

	@Override
	public String getPostUp() {
		return postUp;
	}

	@Override
	public void setPostUp(String postUp) {
		this.postUp = postUp;
	}

	@Override
	public String getPreDown() {
		return preDown;
	}

	@Override
	public void setPreDown(String preDown) {
		this.preDown = preDown;
	}

	@Override
	public String getPostDown() {
		return postDown;
	}

	@Override
	public void setPostDown(String postDown) {
		this.postDown = postDown;
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public String getOwner() {
		return owner;
	}

	@Override
	public void setOwner(String owner) {
		this.owner = owner;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String getHostname() {
		return hostname;
	}

	@Override
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public void setPath(String path) {
		this.path = path;
	}

	@Override
	public boolean isStayConnected() {
		return stayConnected;
	}

	@Override
	public void setStayConnected(boolean stayConnected) {
		this.stayConnected = stayConnected;
	}

	@Override
	public boolean isConnectAtStartup() {
		return connectAtStartup;
	}

	@Override
	public void setConnectAtStartup(boolean connectAtStartup) {
		this.connectAtStartup = connectAtStartup;
	}

	@Override
	public void setPort(Integer port) {
		this.port = port;
	}

	@Override
	public boolean isShared() {
		return shared;
	}

	@Override
	public void setShared(boolean publicToAll) {
		this.shared = publicToAll;
	}

	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
	}

	private void writeObject(ObjectOutputStream out) throws IOException {
		out.defaultWriteObject();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((hostname == null) ? 0 : hostname.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		result = prime * result + ((port == null) ? 0 : port.hashCode());

		/*
		 * TODO other things prevent this from being useful. If this is renabled, make
		 * sure that activeClients in ClientService updates properly (particularly when
		 * saving a connection during logon - as the username will not be available in
		 * the active map)
		 */

//		result = prime * result
//				+ ((username == null) ? 0 : username.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ConnectionImpl other = (ConnectionImpl) obj;
		if (hostname == null) {
			if (other.hostname != null)
				return false;
		} else if (!hostname.equals(other.hostname))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		if (port == null) {
			if (other.port != null)
				return false;
		} else if (!port.equals(other.port))
			return false;

		/*
		 * TODO other things prevent this from being useful. If this is renabled, make
		 * sure that activeClients in ClientService updates properly (particularly when
		 * saving a connection during logon - as the username will not be available in
		 * the active map)
		 */

//		if (username == null) {
//			if (other.username != null)
//				return false;
//		} else if (!username.equals(other.username))
//			return false;
		return true;
	}

	@Override
	public String getUri(boolean withUsername) {
		String uri = "https://";
		uri += getHostname();
		if (getPort() != 443) {
			uri += ":" + getPort();
		}
		uri += getPath();
		return uri;
	}

	public ConnectionImpl() {
	}

	public String getUsernameHint() {
		return usernameHint;
	}

	public void setUsernameHint(String usernameHint) {
		this.usernameHint = usernameHint;
	}

	public int getMtu() {
		return mtu;
	}

	public void setMtu(int mtu) {
		this.mtu = mtu;
	}

	public String getEndpointAddress() {
		return endpointAddress;
	}

	public void setEndpointAddress(String endpointAddress) {
		this.endpointAddress = endpointAddress;
	}

	public int getEndpointPort() {
		return endpointPort;
	}

	public void setEndpointPort(int endpointPort) {
		this.endpointPort = endpointPort;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUserPrivateKey() {
		return userPrivateKey;
	}

	public void setUserPrivateKey(String userPrivateKey) {
		this.userPrivateKey = userPrivateKey;
	}

	public String getUserPublicKey() {
		return userPublicKey;
	}

	public void setUserPublicKey(String userPublicKey) {
		this.userPublicKey = userPublicKey;
	}

	public String getPublicKey() {
		return publicKey;
	}

	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public List<String> getDns() {
		return dns == null || dns.length() == 0 ? Collections.emptyList() : Arrays.asList(dns.split(","));
	}

	public void setDns(List<String> dns) {
		this.dns = dns == null || dns.size() == 0 ? null : String.join(",", dns);
	}

	public int getPersistentKeepalive() {
		return peristentKeepalive;
	}

	public void setPeristentKeepalive(int peristentKeepalive) {
		this.peristentKeepalive = peristentKeepalive;
	}

	@Override
	public String toString() {
		return "ConnectionImpl [id=" + id + ", userPrivateKey=" + userPrivateKey + ", userPublicKey="
				+ userPublicKey + ", publicKey=" + publicKey + ", address=" + address + ", endpointAddress="
				+ endpointAddress + ", endpointPort=" + endpointPort + ", dns=" + dns + ", peristentKeepalive="
				+ peristentKeepalive + ", allowedIps=" + allowedIps + "]";
	}

	public List<String> getAllowedIps() {
		return allowedIps == null || allowedIps.length() == 0 ? Collections.emptyList()
				: Arrays.asList(allowedIps.split(","));
	}

	public void setAllowedIps(List<String> allowedIps) {
		this.allowedIps = allowedIps == null || allowedIps.size() == 0 ? null : String.join(",", allowedIps);
	}

	@Override
	public boolean isAuthorized() {
		return StringUtils.isNotBlank(endpointAddress);
	}

	@Override
	public void deauthorize() {
		usernameHint = null;
		publicKey = null;
		address = null;
		endpointAddress = null;
		endpointPort = 0;
		mtu = 0;
		dns = null;
		peristentKeepalive = 0;
		allowedIps = null;
	}

}
