package com.logonbox.vpn.client.gui.jfx;


import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.hypersocket.json.version.HypersocketVersion;
import com.logonbox.vpn.common.client.ConnectionStatus;
import com.logonbox.vpn.common.client.Util;
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.VPN;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public class DOMProcessor {
	final static Logger log = LoggerFactory.getLogger(UI.class);
	
	private Map<String, String> replacements = new HashMap<>();
	private Map<Node, Collection<Node>> newNodes = new HashMap<>();
	private Set<Node> removeNodes = new HashSet<>();
	private Element documentElement;
	private ResourceBundle pageBundle;
	private ResourceBundle resources;
	private Map<String, Collection<String>> collections;

	public DOMProcessor(VPN vpn, VPNConnection connection, Map<String, Collection<String>> collections, String lastErrorMessage, String lastErrorCause, String lastException, Branding branding, ResourceBundle pageBundle, ResourceBundle resources, Element documentElement, String disconnectionReason) {
		String errorText = "";
		String exceptionText = "";
		String errorCauseText = lastErrorCause == null ? "" : lastErrorCause;

		if (lastException != null) {
			exceptionText = lastException;
		} 
		if (lastErrorMessage != null) {
			errorText = lastErrorMessage;
		}

		/* VPN service */
		replacements.put("updatesEnabled", String.valueOf(vpn != null && vpn.isUpdatesEnabled()));
		replacements.put("needsUpdating", String.valueOf(vpn != null && vpn.isNeedsUpdating()));
		long vpnFreeMemory = vpn == null ? 0 : vpn.getFreeMemory();
		long vpnMaxMemory = vpn == null ? 0 : vpn.getMaxMemory();
		replacements.put("serviceFreeMemory",  Util.toHumanSize(vpnFreeMemory));
		replacements.put("serviceMaxMemory",  Util.toHumanSize(vpnMaxMemory));
		replacements.put("serviceUsedMemory",  Util.toHumanSize(vpnMaxMemory - vpnFreeMemory));
		
		/* General */
		long freeMemory = Runtime.getRuntime().freeMemory();
		replacements.put("freeMemory",  Util.toHumanSize(freeMemory));
		long maxMemory = Runtime.getRuntime().maxMemory();
		replacements.put("maxMemory",  Util.toHumanSize(maxMemory));
		replacements.put("usedMemory",  Util.toHumanSize(maxMemory - freeMemory));
		replacements.put("errorMessage", errorText);
		replacements.put("errorCauseMessage", errorCauseText);
		replacements.put("exception", exceptionText);
		String version = HypersocketVersion.getVersion("com.hypersocket/client-logonbox-vpn-gui-jfx");
		replacements.put("clientVersion",  version);
		replacements.put("snapshot",  String.valueOf(version.indexOf("-SNAPSHOT") != -1));
		replacements.put("brand", MessageFormat.format(resources.getString("brand"),
			(branding == null || branding.getResource() == null
						|| StringUtils.isBlank(branding.getResource().getName()) ? "LogonBox"
								: branding.getResource().getName())));
		replacements.put("tracksServerVersion", vpn == null ? "true" : String.valueOf(vpn.isTrackServerVersion()));
//		replacements.put("trayConfigurable", String.valueOf(Client.get().isTrayConfigurable()));
		replacements.put("trayConfigurable", String.valueOf(false));
		
		/* Connection */
		replacements.put("displayName", connection == null ? "" : connection.getDisplayName());
		replacements.put("name", connection == null || connection.getName() == null ? "" : connection.getName());
		replacements.put("server", connection == null ? "" : connection.getHostname());
		replacements.put("serverUrl", connection == null ? "" : connection.getUri(false));
		replacements.put("port", connection == null ? "" : String.valueOf(connection.getPort()));
		replacements.put("endpoint", connection == null ? ""
				: connection.getEndpointAddress() + ":" + connection.getEndpointPort());
		replacements.put("publicKey",  connection == null ? "" : connection.getPublicKey());
		replacements.put("userPublicKey", connection == null ? "" : connection.getUserPublicKey());
		replacements.put("address", connection == null ? "" : connection.getAddress());
		replacements.put("usernameHint", connection == null ? "" : connection.getUsernameHint());
		replacements.put("connectAtStartup", connection == null ? "false" : String.valueOf(connection.isConnectAtStartup()));
		replacements.put("stayConnected", connection == null ? "false" : String.valueOf(connection.isStayConnected()));
		replacements.put("allowedIps", connection == null ? "" : String.join(", ", connection.getAllowedIps()));
		replacements.put("dns", connection == null ? "" : String.join(", ", connection.getDns()));
		replacements.put("persistentKeepalive", connection == null ? "" : String.valueOf(connection.getPersistentKeepalive()));
		replacements.put("disconnectionReason", disconnectionReason == null ? "" : disconnectionReason);
		String statusType = connection == null ? "" : connection.getStatus();
		replacements.put("status", statusType);
		replacements.put("connected", String.valueOf(statusType.equals(ConnectionStatus.Type.CONNECTED.name())));
		if(connection == null || !statusType.equals(ConnectionStatus.Type.CONNECTED.name())) {
			replacements.put("lastHandshake",  "");			
			replacements.put("usage",  "");
		}
		else {
			replacements.put("lastHandshake",  DateFormat.getDateTimeInstance().format(new Date(connection.getLastHandshake())));			
			replacements.put("usage",  MessageFormat.format(resources.getString("usageDetail"), Util.toHumanSize(connection.getRx()), Util.toHumanSize(connection.getTx())));
		}

		this.documentElement = documentElement;
		this.pageBundle = pageBundle;
		this.resources = resources;
		this.collections = collections; 
	}
	
	public void process() {
		dataAttributes(documentElement,  newNodes, removeNodes);
		for (Map.Entry<Node, Collection<Node>> en : newNodes.entrySet()) {
			for (Node n : en.getValue())
				en.getKey().appendChild(n);
		}
		for (Node n : removeNodes)
			n.getParentNode().removeChild(n);
	}

	protected void dataAttributes(Element node, Map<Node, Collection<Node>> newNodes,
			Set<Node> removeNodes) {

		NamedNodeMap attrs = node.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			String val = attr.getNodeValue();
			if (attr.getNodeName().equals("data-conditional")) {
				String valVal = replacements.get(val);
				if(valVal != null && valVal.length() > 0 && !valVal.equals("false") && !valVal.equals("0")) {
					// Include
				}
				else {
					node.getParentNode().removeChild(node);
					return;
				}
			}
			else if (attr.getNodeName().startsWith("data-attr-i18n-")) {
				String attrVal = pageBundle == null ? "?" : pageBundle.getString(val);
				String attrName = attr.getNodeName().substring(15);
				node.setAttribute(attrName, attrVal);
			} else {
				if (attr.getNodeName().equals("data-attr-value")) {
					node.setAttribute(node.getAttribute("data-attr-name"), replacements.get(val));
				} else if (attr.getNodeName().equals("data-i18n")) {
					List<Object> args = new ArrayList<>();
					for (int ai = 0; ai < 9; ai++) {
						String i18nArg = node.getAttribute("data-i18n-" + ai);
						if (i18nArg != null) {
							args.add(replacements.get(i18nArg));
						}
					}
					String attrVal;
					try {
						attrVal = pageBundle.getString(val);
					} catch (MissingResourceException mre) {
						attrVal = resources.getString(val);
					}
					if (!args.isEmpty()) {
						attrVal = MessageFormat.format(attrVal, args.toArray(new Object[0]));
					}
					node.setTextContent(attrVal);
				} else if (attr.getNodeName().equals("data-collection")) {
					Collection<String> collection = collections.get(val);
					if (collection == null)
						log.warn(String.format("No collection named %s", val));
					else {
						List<Node> newCollectionNodes = new ArrayList<>();
						for (String elVal : collection) {
							Node template = node.cloneNode(false);
							template.setTextContent(elVal);
							newCollectionNodes.add(template);
						}
						newNodes.put(node.getParentNode(), newCollectionNodes);
						removeNodes.add(node);
					}
				} else if (attr.getNodeName().equals("data-content")) {
					node.setTextContent(replacements.get(val));
				}
			}
		}

		NodeList n = node.getChildNodes();
		for (int i = 0; i < n.getLength(); i++) {
			Node c = n.item(i);
			if (c instanceof Element)
				dataAttributes((Element) c, newNodes, removeNodes);
		}
	}
}
