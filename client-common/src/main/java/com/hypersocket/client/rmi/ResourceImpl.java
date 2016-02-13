package com.hypersocket.client.rmi;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public final class ResourceImpl implements Resource, Serializable {

	private static final long serialVersionUID = 6947909274209893794L;

	String name;
	String colour;
	List<ResourceProtocol> protocols;
	ResourceRealm realm;
	boolean launchable;
	ResourceLauncher launcher;
	String icon;
	Type type;	
	String uid;
	Calendar modified;
	String group;
	String groupIcon;
	
	public ResourceImpl() {
	}

	public ResourceImpl(String uid, String name, List<ResourceProtocol> resources) {
		this.name = name;
		this.uid = uid;
		this.protocols = resources;
		for(ResourceProtocol r : resources) {
			r.setResource(this);
		}
	}
	
	public ResourceImpl(String uid, String name) {
		this.name = name;
		this.uid = uid;
		this.protocols = new ArrayList<ResourceProtocol>();
	}
	

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public void setGroupIcon(String groupIcon) {
		this.groupIcon = groupIcon;
	}

	public String getGroupIcon() {
		return groupIcon;
	}

	public Calendar getModified() {
		return modified;
	}

	public void setModified(Calendar modified) {
		this.modified = modified;
	}

	public void addProtocol(ResourceProtocol proto) {
		proto.setResource(this);
		protocols.add(proto);
	}
	
	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public String getColour() {
		return colour;
	}

	public void setColour(String colour) {
		this.colour = colour;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	@Override
	public String getHostname() {
		return name;
	}
	@Override
	public List<ResourceProtocol> getProtocols() {
		return protocols;
	}

	@Override
	public void setResourceRealm(ResourceRealm realm) {
		this.realm = realm;
	}

	@Override
	public ResourceRealm getRealm() {
		return realm;
	}

	@Override
	public boolean isLaunchable() {
		return launchable;
	}
	
	@Override
	public void setLaunchable(boolean launchable) {
		this.launchable = launchable;
	}

	@Override
	public ResourceLauncher getResourceLauncher() {
		return launcher;
	}

	@Override
	public void setResourceLauncher(ResourceLauncher launcher) {
		this.launcher = launcher;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((uid == null) ? 0 : uid.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		Resource other = (Resource) obj;
		if (type != other.getType())
			return false;
		if (uid == null) {
			if (other.getUid() != null)
				return false;
		} else if (!uid.equals(other.getUid()))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "ResourceImpl [name=" + name + ", colour=" + colour
				+ ", protocols=" + protocols + ", realm=" + realm
				+ ", launchable=" + launchable + ", launcher=" + launcher
				+ ", icon=" + icon + ", type=" + type + ", uid=" + uid
				+ ", modified=" + modified + ", group=" + group
				+ ", groupIcon=" + groupIcon + "]";
	}

	
}
