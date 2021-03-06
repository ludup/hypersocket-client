package com.hypersocket.client.rmi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hypersocket.replace.ReplacementUtils;
import com.hypersocket.utils.CommandExecutor;

public class ScriptLauncher implements ResourceLauncher, Serializable {

	static Logger log = LoggerFactory.getLogger(ScriptLauncher.class);
	
	private static final long serialVersionUID = 4922604914995232181L;

	String script;
	Map<String,String> properties;
	File workingDirectory;
	List<String> args = new ArrayList<String>();
	
	public ScriptLauncher(String script, File workingDirectory, Map<String,String> properties) {
		this.script = script;
		this.workingDirectory = workingDirectory;
		this.properties = properties;
	}

	public void addArg(String arg) {
		args.add(arg);
	}
	
	@Override
	public int launch() {
		
		script = ReplacementUtils.processTokenReplacements(script, System.getenv());
		script = ReplacementUtils.processTokenReplacements(script, properties);
		
		File scriptFile = null; 
		FileOutputStream out = null;
		try {
			scriptFile = File.createTempFile("tmp", getScriptSuffix());
			out = new FileOutputStream(scriptFile);
			IOUtils.write(script, out);
		} catch(IOException ex) { 
			log.error("Failed to create temporary script file", ex);
			scriptFile.delete();
			return Integer.MIN_VALUE;
		} finally {
			IOUtils.closeQuietly(out);
		}
		
		scriptFile.setExecutable(true);
		
		final CommandExecutor cmd;
		if(System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			cmd = createWindowsScript(scriptFile);
		} else {
			cmd = createBashScript(scriptFile);
		}
		
		cmd.setWorkingDirectory(workingDirectory);
		cmd.addArgs(args.toArray(new String[0]));
		
		try {
			int exitCode = cmd.execute();
			
			if(log.isInfoEnabled()) {
				log.info("Script exited with exit code " + exitCode);
			}
			if(log.isDebugEnabled()) {
				log.debug("---BEGIN CMD OUTPUT----");
				log.debug(cmd.getCommandOutput());
				log.debug("---END CMD OUTPUT----");
			}
			
			return exitCode;
		} catch (IOException e) {
			log.error("Failed to execute script", e);
			return Integer.MIN_VALUE;
		} finally {
			try {
				Files.delete(scriptFile.toPath());
			} catch (IOException e) {
				log.error("Script file could not be deleted " + scriptFile.getAbsolutePath(), e);
			}
			
			
		}
				
	}

	private CommandExecutor createBashScript(File scriptFile) {
		return new CommandExecutor("/bin/sh", scriptFile.getAbsolutePath());
	}

	private CommandExecutor createWindowsScript(File scriptFile) {
		return new CommandExecutor("cmd.exe", "/C", scriptFile.getAbsolutePath());
	}

	private String getScriptSuffix() {
		return System.getProperty("os.name").toLowerCase().startsWith("windows") ? ".bat" : ".sh";
	}

	@Override
	public String toString() {
		return "ScriptLauncher [script=" + script + ", properties="
				+ properties + "]";
	}

}
