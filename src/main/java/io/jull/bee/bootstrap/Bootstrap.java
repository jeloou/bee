package io.jull.bee.bootstrap;

import java.util.Properties;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;;

import java.io.File;
import java.io.FileOutputStream;

import java.lang.management.*;

import io.jull.bee.server.Server;

public class Bootstrap {
    private static Bootstrap bootstrap;
    private Server server;
    
    public static long pid() {
	RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
	long pid;

	String name = runtimeMXBean.getName();
	try {
	    name = name.split("@")[0];
	    pid = Long.parseLong(name);
	} catch (Exception e) {
	    pid = -1;
	}

	return pid;
    }
    
    public void setup(boolean addShutdownHook, Map options) throws Exception {
	server = new Server(1883);
	if (addShutdownHook) {
	    Runtime.getRuntime().addShutdownHook(new Thread() {
	        @Override
		public void run() {
		    server.close();
		}
	    });
	}
    }
    
    public void start() {
	server.start();
    }
    
    public void stop() {
	server.stop();
    }
    
    public void destroy() {
	server.close();
    }
    
    public static void main(String[] args) {
	bootstrap = new Bootstrap();
	
	Map<String, String> options = new HashMap<>();
	Properties properties = System.getProperties();
	Enumeration n = properties.propertyNames();
	String k;
	
	while (n.hasMoreElements()) {
	    k = (String) n.nextElement();
	    
	    if (k.startsWith("bee.")) {
		options.put(k.split("^bee.")[1], (String) properties.get(k));
	    }
	}
	
	if (options.containsKey("pid")) {
	    try {
		File pidFile = new File(options.get("pid"));
		if (pidFile.getParentFile() != null) {
		    pidFile.getParentFile().mkdirs();
		}
		
		FileOutputStream output = new FileOutputStream(pidFile);
		output.write(Long.toString(pid()).getBytes("UTF-8"));
		output.close();
		
		pidFile.deleteOnExit();
	    } catch (Exception e) {
		System.err.println(e.getMessage());
		System.err.flush();
		System.exit(1);
	    }
	}
	
	boolean daemon = false;
	if (options.containsKey("daemon")) {
	    daemon = true;
	}
	
	try {
	    if (daemon) {
		System.out.close();
	    }
	    
	    bootstrap.setup(true, options);
	    bootstrap.start();

	    if (daemon) {
		System.err.close();
	    }
	    
	} catch (Throwable e) {
	    System.err.println(e.getMessage());
	    System.err.flush();
	    System.exit(1);
	}
    }
}