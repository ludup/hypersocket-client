package com.logonbox.vpn.common.client;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.OneToOne;
import javax.persistence.Table;

import com.hypersocket.client.rmi.Connection;
import com.hypersocket.client.rmi.ConnectionImpl;

@Entity
@Table(name = "peer_configurations")
public class PeerConfigurationImpl implements PeerConfiguration, Serializable {

	private static final long serialVersionUID = 1007856764641094257L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@OneToOne(targetEntity = ConnectionImpl.class)
	@JoinColumn(name = "connection_id")
	private Connection connection;

	@Column(nullable = false)
	private String usernameHint;

	@Column(nullable = false)
	private String userPrivateKey;

	@Column(nullable = false)
	private String userPublicKey;

	@Column(nullable = false)
	private String publicKey;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false)
	private String endpointAddress;

	@Column(nullable = false)
	private int endpointPort;

	@Column(nullable = false)
	private int mtu;

	@Lob
	private String dns;

	@Column
	private int peristentKeepalive;

	@Lob
	private String allowedIps;

	public PeerConfigurationImpl() {
	}

	public String getUsernameHint() {
		return usernameHint;
	}

	public void setUsernameHint(String usernameHint) {
		this.usernameHint = usernameHint;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public Long getId() {
		return id;
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
		return "PeerConfigurationImpl [id=" + id + ", userPrivateKey=" + userPrivateKey + ", userPublicKey="
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

}
