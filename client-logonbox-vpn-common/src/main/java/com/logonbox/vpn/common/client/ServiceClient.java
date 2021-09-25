package com.logonbox.vpn.common.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypersocket.json.AuthenticationRequiredResult;
import com.hypersocket.json.AuthenticationResult;
import com.hypersocket.json.JsonLogonResult;
import com.hypersocket.json.JsonSession;
import com.hypersocket.json.input.InputField;
import com.logonbox.vpn.common.client.api.PeerResponse;
import com.logonbox.vpn.common.client.dbus.VPNConnection;

public class ServiceClient {
	
	public interface Authenticator {

		String getUUID();

		HostnameVerifier getHostnameVerifier();

		void authorized() throws IOException;

		void collect(JsonNode i18n, AuthenticationRequiredResult result, Map<InputField, BasicNameValuePair> results) throws IOException;

		void error(JsonNode i18n, AuthenticationResult logonResult);
		
	}

	static Logger log = LoggerFactory.getLogger(ServiceClient.class);

	private ObjectMapper mapper;
	private CookieStore cookieStore;
	private Authenticator authenticator;

	public ServiceClient(Authenticator authenticator) {
		mapper = new ObjectMapper();
		this.authenticator = authenticator;
	}


	protected String doGet(VPNConnection connection, String url, NameValuePair... headers)
			throws ClientProtocolException, IOException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		HttpGet httpget = new HttpGet(connection.getUri(false) + url);
		for (NameValuePair header : headers)
			httpget.addHeader(header.getName(), header.getValue());

		log.info("Executing request " + httpget.getRequestLine());

		CloseableHttpClient httpClient = (CloseableHttpClient) getHttpClient(connection);
		CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(httpget);

		log.info("Response: " + response.getStatusLine().toString());

		try {
			if (response.getStatusLine().getStatusCode() != 200) {
				throw new ClientProtocolException(
						"Expected status code 200 for doGet [" + url + ", " + response.getStatusLine().getStatusCode() + "]");
			}

			return IOUtils.toString(response.getEntity().getContent(), "UTF-8");
		} finally {
			response.close();
			httpClient.close();
		}
	}

	protected HttpClient getHttpClient(VPNConnection connection) {
		if(cookieStore == null) {
			cookieStore = new BasicCookieStore();
			BasicClientCookie cookie = new BasicClientCookie(AbstractDBusClient.DEVICE_IDENTIFIER, authenticator.getUUID());
			cookie.setSecure(true);
			cookie.setPath("/");
			cookie.setDomain(connection.getHostname());
			cookie.setAttribute(ClientCookie.DOMAIN_ATTR, "true");
			cookieStore.addCookie(cookie);
		}
		return HttpClients.custom().setDefaultCookieStore(cookieStore).setSSLHostnameVerifier(authenticator.getHostnameVerifier())
				.build();

	}

	protected void debugJSON(String json) throws JsonParseException, JsonMappingException, IOException {
		if (log.isDebugEnabled()) {
			Object obj = mapper.readValue(json, Object.class);
			String ret = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
			log.debug(ret);
		}

	}

	public void register(VPNConnection connection)
			throws IOException, URISyntaxException {
		JsonSession session = auth(connection);
		String deviceId = authenticator.getUUID();
		try {
			StringBuilder keyParameters = new StringBuilder();
			keyParameters.append("os=" + Util.getOS());
			keyParameters.append('&');
			keyParameters.append("mode=" + connection.getMode());
			keyParameters.append('&');
			keyParameters.append("deviceName=" + URLEncoder.encode(Util.getOS(), "UTF-8"));
			if (StringUtils.isNotBlank(connection.getUserPublicKey())) {
				keyParameters.append('&');
				keyParameters.append("publicKey=" + URLEncoder.encode(connection.getUserPublicKey(), "UTF-8"));
			}
			keyParameters.append('&');
			keyParameters.append("token=" + URLEncoder.encode(session.getCsrfToken(), "UTF-8"));
			log.info(String.format("Retrieving peers for %s", deviceId));
			String json = doGet(connection, "/api/peers/get?" + keyParameters.toString());
			debugJSON(json);
			PeerResponse result = mapper.readValue(json, PeerResponse.class);
			boolean success = result.isSuccess();

			if (success) {
				log.info("Retrieved peers");
				String deviceUUID = result.getDeviceUUID();
				if (StringUtils.isNotBlank(deviceUUID) && result.getResource() == null) {
					json = doGet(connection, "/api/peers/register?" + keyParameters.toString());
					debugJSON(json);
					result = mapper.readValue(json, PeerResponse.class);
					if (result.isSuccess()) {
						log.info(String.format("Device UUID registered. %s", deviceUUID));
						configure(connection, result.getContent(),
								session.getCurrentPrincipal().getPrincipalName());
					} else {
						throw new IOException("Failed to register. " + result.getMessage());
					}
				} else {
					log.info("Already have UUID, passing on configuration");
					configure(connection, result.getContent(),
							session.getCurrentPrincipal().getPrincipalName());
				}
			} else
				throw new IOException("Failed to query for existing peers. " + result.getMessage());
		} finally {
			doGet(connection, "/api/logoff?token=" + URLEncoder.encode(session.getCsrfToken(), "UTF-8"));
		}
	}

	protected void configure(VPNConnection config, String configIniFile,
			String usernameHint) throws IOException {
		log.info(String.format("Configuration for %s", usernameHint));
		config.setUsernameHint(usernameHint);
		String error = config.parse(configIniFile);
		if (StringUtils.isNotBlank(error))
			throw new IOException(error);

		config.save();
		authenticator.authorized();
		config.authorized();
	}

	protected JsonSession auth(VPNConnection connection)
			throws ClientProtocolException, IOException, URISyntaxException {

		JsonNode i18n;
		try {
			/* 2.4 server */
			String i18njson = doGet(connection, "/api/i18n/group/_default_i18n_group");
			i18n = mapper.readTree(i18njson);
		}
		catch(ClientProtocolException cpe) {
			/* 2.3 server */
			String i18njson = doGet(connection, "/api/i18n");
			i18n = mapper.readTree(i18njson);
		}

		// main.getServer().
		String json = doGet(connection, "/api/logon");
		debugJSON(json);

		AuthenticationRequiredResult result;
		String logonJson;
		Map<InputField, BasicNameValuePair> results = new HashMap<>();
		while (true) {
			result = mapper.readValue(json, AuthenticationRequiredResult.class);

			if (result.getSuccess())
				throw new IllegalStateException("Didn't expect to be already logged in.");

			authenticator.collect(i18n, result, results);

			logonJson = doPost(connection, "/api/logon", results.values().toArray(new BasicNameValuePair[0]));
			debugJSON(logonJson);

			AuthenticationResult logonResult = mapper.readValue(logonJson, AuthenticationResult.class);
			if (logonResult.getSuccess()) {
				JsonLogonResult logon = mapper.readValue(logonJson, JsonLogonResult.class);
				return logon.getSession();
			} else {
				authenticator.error(i18n, logonResult);
			}

			if (result.isLast())
				break;

			json = doGet(connection, "/api/logon");
			debugJSON(json);
		}

		throw new IOException("Authentication failed.");
	}

	protected String doPost(VPNConnection connection, String url, NameValuePair... postVariables)
			throws URISyntaxException, ClientProtocolException, IOException {

		if (!url.startsWith("/")) {
			url = "/" + url;
		}

		HttpUriRequest login = RequestBuilder.post().setUri(new URI(connection.getUri(false) + url))
				.addParameters(postVariables).build();

		log.info("Executing request " + login.getRequestLine());
		try (CloseableHttpClient httpClient = (CloseableHttpClient) getHttpClient(connection)) {
			try (CloseableHttpResponse response = (CloseableHttpResponse) httpClient.execute(login)) {
				log.info("Response: " + response.getStatusLine().toString());
				if (response.getStatusLine().getStatusCode() != 200) {
					throw new ClientProtocolException("Expected status code 200 for doPost");
				}
				return IOUtils.toString(response.getEntity().getContent(), "UTF-8");
			}
		}

	}
}
