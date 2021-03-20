# Hypersocket Client

* WORK IN PROGRESS *

## Developer Instructions

### Obtaining Source

As from branch_2.4.x, you only need to clone the modules you will actually be working on. So if you are just working on the VPN client, you can clone `hypersocket-client` only. 

### Building From Maven

#### Building

Use the standard Maven phases. All modules share the following behaviour :-

 * `compile` will compile source and generate translations (so translations may be used in development environment).
 * All other Maven phases act as normal.
 
*Enterprise Builds*
 
### Running The Client Service, GUI and CLI tool

Running the VPN client from either inside your IDE, or from the command line on your development machine is best done using a `_run` project.
 
As from version 2.4.x, it is now possible to run the server without `app-bootstrap`, instead you should use `logonbox-forker-wrapper-plugin`.   This method allows you to either run a wrapped service with privilege escalation, full restart and monitoring features as a deployed client would. Or, to use the `no-fork` mode which runs the client in a similar manner to earlier versions, but with a rudimentary restart mechanism. Both methods also support actually installing and using extensions from a store (with some caveats), and can be configured using fewer files.

At a minimum, you will need need 2 `_run` projects, one for the service and one for the GUI. 

#### The _run_vpnclient_service Project

*Your _run project should never be checked into version control*

##### _run_vpnclient_service POM

The `_run_vpnclient_service` project's `pom.xml` defines what modules are included. The transitive depdendencies needed will be automatically calculated.

```xml
<!-- Copyright (c) 2021 LogonBox Limited. All rights reserved. -->
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<artifactId>_run_vpnclient_gui_jfx</artifactId>
	<name>LogonBox - Run VPN Client GUI (JavaFX)</name>

	<groupId>com.hypersocket</groupId>
	<version>2.3.0-LOCAL</version>

	<dependencies>
	
		<dependency>
			<groupId>com.logonbox</groupId>
			<artifactId>logonbox-bootstrap-wrapper-plugin</artifactId>
			<version>2.4.0-SNAPSHOT</version>
		</dependency>
	
		<!-- Allows the existing running application to be notified if the user
		attempts to start another instance. Instead, the client will open its
		own window -->
		<dependency>
			<groupId>com.sshtools</groupId>
			<artifactId>forker-wrapped</artifactId>
			<version>1.7-SNAPSHOT</version>
		</dependency>
		
		<dependency>
			<groupId>com.logonbox</groupId>
			<artifactId>x-client-logonbox-vpn-gui-jfx</artifactId>
			<version>${project.version}</version>
		</dependency>
	</dependencies>
	<build>
		<plugins>
			<plugin>
				<groupId>org.apache.maven.plugins</groupId>
				<artifactId>maven-compiler-plugin</artifactId>
				<configuration>
					<source>11</source>
					<target>11</target>
				</configuration>
			</plugin>
		</plugins>
	</build>

	<repositories>
		<repository>
			<id>libs-snapshots</id>
			<name>libs-snapshots</name>
			<url>http://artifactory.javassh.com/libs-snapshots-local</url>
			<snapshots/>
			<releases>
				<enabled>false</enabled>
			</releases>
		</repository>
	</repositories>
</project>

```

##### _run_vpnclient_service Forker Configuration

The only other required file you need apart from the POM is the *Forker Configuration File*. This instructs forker how to launch the application. 

*It is recommended you do not alter this default configuration, instead save it exactly as below and create a `conf.d` directory to place any customisation there.*

Copy and paste the following into `_run/conf/service-forker.cfg`

```properties
configuration-directory conf.d

# Temporary file locations
pidfile tmp/service.pid
argfile tmp/service.args

# Class and library path
classpath tmp/x-logonbox-vpn-client-service/*.jar
jvmarg -Djava.library.path=tmp/lib

# Launching
timeout 300
main com.logonbox.vpn.client.Main

# Open up all modules
jvmarg --add-opens
jvmarg java.base/jdk.internal.loader=ALL-UNNAMED

# Configure security manager for RMI
jvmarg -Djava.security.policy=conf/server.policy

# Other JVM configuration
jvmarg -Djna.nosys=true

# Logging configuration
log logs/service.log
jvmarg -Dhypersocket.logConfiguration=conf/log4j-service.properties

# RMI
jvmarg -Dhypersocket.rmi=conf/rmi.properties
jvmarg -Djava.rmi.server.hostname=127.0.0.1

# No need for Forker Daemon in the GUI
no-forker-daemon

# Application Properties

app-property app.id=client-logonbox-vpn-service
app-property app.name=LogonBox VPN Client
app-property app.archive=x-client-logonbox-vpn-service
app-property app.dist=service
```

#### _run_vpnclient_service Policy

Create a file named `conf/server.policy` and put the following content inside.

```
grant {
    permission java.security.AllPermission;
};
```

#### Running via Maven

How you have `_run_vpnclient_service` project, starting a client service is as simple as ..

```bash
cd _run_vpnclient_service
mvn exec:exec
```

Press Ctrl+C to stop the server.

#### Debugging via Maven

Debugging via requires that you activate remote debugging, and activate forking from the wrapper.

```bash
mvn exec:exec -Ddebug=true -DnoFork=false
```

You will then need to create a **Debug Configuration** of type **Remote Java Application**  in Eclipse (or other IDE), and connect to default port 1099. 

By default, the remote debugging is set to **Suspend** until a debugger client connects. You can change this behaviour using the same debug property.

```bash
mvn exec:exec -Ddebug=suspend=n -DnoFork=false
```

#### Running via Eclipse

To run the server directly from Eclipse, create a new **Run Configuration** of type **Java Application**.  This is likely the preferred method for most development, as it will give you direct debugging with hot code replacement.

Project: **_run**
Main class: **com.sshtools.forker.wrapper.ForkerWrapper**
Arguments: **-c conf/service-forker.cfg --no-fork**
Use @argfile: **ON** (optional but recommended)

The `--no-fork` option is there to prevent the wrapper from creating a disassociated JVM. Without this, you could not get direct debugging with hot-code replacement.

Note that this option requires Maven integration for Eclipse to be installed.

#### Running As Administrator

It may be useful to service as an administrator. To do so, create a forker configuration file in `conf.d` such as `conf.d/administrator.cfg` and add a single line with the word `administrator`. Alternatively, add the `--administrator` argument to your Eclipse launcher.

You will be prompted for your password or the administrator password depending on the OS, or you can automate this as described below.

*Note that only Remote Debugging can be used if you choose to run the service as an administrator.*

#### Automatic Administrator Password

To automatically provide the password for privilege escalation (including running the service as administrator), create a forker configuration file in `conf.d` such as `conf.d/sudo.cfg`

```
jvmarg -Dforker.administrator.password=your_secret
```

You probably want to log to console in a development environment. Add the following in a file named `conf/log4j-service.properties`.

```
# Set root category priority to INFO
log4j.rootCategory=INFO,LOGFILE,CONSOLE

# LOGFILE is set to be a File appender using a PatternLayout.
log4j.appender.LOGFILE=org.apache.log4j.RollingFileAppender
log4j.appender.LOGFILE.File=logs/service-app.log
log4j.appender.LOGFILE.Append=true
log4j.appender.LOGFILE.MaxFileSize=20MB
log4j.appender.LOGFILE.MaxBackupIndex=10
log4j.appender.LOGFILE.layout=org.apache.log4j.PatternLayout
log4j.appender.LOGFILE.layout.ConversionPattern=%d{dd MMM yyyy HH:mm:ss,SSS} [%t] %-5p %c{1} %x - %m%n

# CONSOLE is set to be a ConsoleAppender using a PatternLayout.
log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender
log4j.appender.CONSOLE.layout=org.apache.log4j.PatternLayout
log4j.appender.CONSOLE.layout.ConversionPattern=%d{dd-MM-yyyy HH:mm:ss} [%t] %-5p %c{1} - %m%n

```
