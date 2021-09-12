package com.logonbox.vpn.common.client.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hypersocket.json.JsonSession;
import com.hypersocket.json.ResourceStatus;

@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PeerResponse extends ResourceStatus<Peer> {

	private String content;
	private String hostname;
	private JsonSession session;
	private String remoteHostname;
	private String deviceUUID;

	public PeerResponse(Peer peer, String content) {
		super(peer);
		this.content = content;
	}

	public PeerResponse() {
		super(true, "VPN Not available.");
	}

	public PeerResponse(String message) {
		super(false, message);
	}

	public String getDeviceUUID() {
		return deviceUUID;
	}

	public void setDeviceUUID(String deviceUUID) {
		this.deviceUUID = deviceUUID;
	}

	public String getContent() {
		return content;
	}

	public String getHostname() {
		return hostname;
	}

	public JsonSession getSession() {
		return session;
	}

	public void setSession(JsonSession session) {
		this.session = session;
	}

	public String getRemoteHostname() {
		return remoteHostname;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public void setRemoteHostname(String remoteHostname) {
		this.remoteHostname = remoteHostname;
	}

}
