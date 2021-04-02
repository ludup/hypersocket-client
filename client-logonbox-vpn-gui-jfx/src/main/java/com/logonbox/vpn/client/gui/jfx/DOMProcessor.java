package com.logonbox.vpn.client.gui.jfx;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
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
import com.logonbox.vpn.common.client.api.Branding;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public class DOMProcessor {
	final static Logger log = LoggerFactory.getLogger(UI.class);
	
	private Map<String, String> replacements = new HashMap<>();
	private Map<Node, Collection<Node>> newNodes = new HashMap<>();
	private Set<Node> removeNodes = new HashSet<>();
	private Element documentElement;
	private ResourceBundle pageBundle;
	private ResourceBundle bundle;
	private Map<String, Collection<String>> collections;

	public DOMProcessor(VPNConnection connection, Map<String, Collection<String>> collections, String lastErrorMessage, Throwable lastException, Branding branding, ResourceBundle pageBundle, ResourceBundle resources, Element documentElement, String disconnectionReason) {
		String errorText = "";
		String exceptionText = "";

		if (lastException != null) {
			StringWriter s = new StringWriter();
			lastException.printStackTrace(new PrintWriter(s, true));
			exceptionText = s.toString();
			if (lastErrorMessage == null) {
				errorText = lastException.getMessage();
			} else {
				errorText = lastErrorMessage + " " + lastException.getMessage();
			}
		} else if (lastErrorMessage != null) {
			errorText = lastErrorMessage;
		}

		replacements.put("errorMessage", errorText);
		replacements.put("exception", exceptionText);
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
		replacements.put("clientVersion",  HypersocketVersion.getVersion("client-logonbox-vpn-gui-jfx"));
		replacements.put("brand", MessageFormat.format(resources.getString("brand"),
			(branding == null || branding.getResource() == null
						|| StringUtils.isBlank(branding.getResource().getName()) ? "LogonBox"
								: branding.getResource().getName())));
		replacements.put("allowedIps", connection == null ? "" : String.join(", ", connection.getAllowedIps()));
		replacements.put("dns", connection == null ? "" : String.join(", ", connection.getDns()));
		replacements.put("persistentKeepalive", connection == null ? "" : String.valueOf(connection.getPersistentKeepalive()));
		replacements.put("disconnectionReason", disconnectionReason == null ? "" : disconnectionReason);

		this.documentElement = documentElement;
		this.pageBundle = pageBundle;
		this.bundle = resources;
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
						attrVal = bundle.getString(val);
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
