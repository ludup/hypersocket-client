package com.hypersocket.client.rmi;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class ResourceRealmImpl implements ResourceRealm, Serializable {

	private static final long serialVersionUID = -2321878064950104362L;

	String name;
	List<Resource> resources;

	public ResourceRealmImpl() {
	}

	public ResourceRealmImpl(String name, List<Resource> resources) {
		this.name = name;
		this.resources = resources;

		for (Resource r : resources) {
			r.setResourceRealm(this);
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public List<Resource> getResources() {
		return Collections.unmodifiableList(resources);
	}

	@Override
	public void addResource(Resource res) {
		res.setResourceRealm(this);
		resources.add(res);
	}

	@Override
	public void removeResource(Resource res) {
		if(this.equals(res.getRealm())) {
			res.setResourceRealm(null);
		}
		resources.remove(res);
	}

	@Override
	public String toString() {
		return "ResourceRealmImpl [name=" + name + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
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
		ResourceRealmImpl other = (ResourceRealmImpl) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		return true;
	}

}
