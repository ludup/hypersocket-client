package com.logonbox.vpn.common.client;

import java.net.URI;
import java.util.List;

public interface ConnectionRepository {

	Connection getConfigurationForPublicKey(String publicKey);

	Connection getConnection(Long id);

	Connection getConnectionByName(String owner, String name);

	Connection save(Connection connection);

	Connection createNew();

	Connection createNew(URI uriObj);

	Connection update(URI uriObj, Connection connection);

	List<Connection> getConnections(String owner);

	void delete(Connection con);

	Connection getConnectionByURI(String owner, String uri);
}
