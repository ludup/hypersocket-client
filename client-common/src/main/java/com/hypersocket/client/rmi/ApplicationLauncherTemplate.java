package com.hypersocket.client.rmi;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

public class ApplicationLauncherTemplate implements Serializable {

	private static final long serialVersionUID = -1187629371260022723L;

	String name;
	String exe;
	String[] args = { };
	String startupScript;
	String shutdownScript;
	Map<String,String> variables;
	Long id;
	String logo;
	
	public ApplicationLauncherTemplate(Long id, String name, String exe, String startupScript, String shutdownScript, String logo, Map<String,String> variables, String... args) {
		this.name = name;
		this.id = id;
		this.exe = exe;
		this.args = args;
		this.startupScript = startupScript;
		this.shutdownScript = shutdownScript;
		this.variables = variables;
		this.logo = logo;
	}
	
	}
	
	public ApplicationLauncherTemplate(Long id, String name, String exe, String startupScript, String shutdownScript, Map<String,String> variables, String args) {
		this.name = name;
		this.exe = exe;
		this.id = id;
		this.startupScript = startupScript;
		this.shutdownScript = shutdownScript;
		this.variables = variables;
		if(!StringUtils.isEmpty(args)) {
			this.args = args.split("\\]\\|\\[");
		}
	}
	
	public String getLogo() {
		return logo;
	}

	public void setLogo(String logo) {
		this.logo = logo;
	}
	
	public Long getId() {
		return id;
	}
	
	public String getName() {
		return name;
	}
	
	public String getExe() {
		return exe;
	}
	
	public String[] getArgs() {
		return args;
	}
	
	public Map<String,String> getVariables() {
		return variables;
	}
	
	public String getStartupScript() {
		return startupScript;
	}
	
	public String getShutdownScript() {
		return shutdownScript;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(args);
		result = prime * result + ((exe == null) ? 0 : exe.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result
				+ ((shutdownScript == null) ? 0 : shutdownScript.hashCode());
		result = prime * result
				+ ((startupScript == null) ? 0 : startupScript.hashCode());
		result = prime * result
				+ ((variables == null) ? 0 : variables.hashCode());
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
		ApplicationLauncherTemplate other = (ApplicationLauncherTemplate) obj;
		if (!Arrays.equals(args, other.args))
			return false;
		if (exe == null) {
			if (other.exe != null)
				return false;
		} else if (!exe.equals(other.exe))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (shutdownScript == null) {
			if (other.shutdownScript != null)
				return false;
		} else if (!shutdownScript.equals(other.shutdownScript))
			return false;
		if (startupScript == null) {
			if (other.startupScript != null)
				return false;
		} else if (!startupScript.equals(other.startupScript))
			return false;
		if (variables == null) {
			if (other.variables != null)
				return false;
		} else if (!variables.equals(other.variables))
			return false;
		return true;
	}
	
}
