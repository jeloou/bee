package io.jull.bee.common;

import java.util.Map;

public abstract class AbstractClient {
    protected String protocol;
    protected int version;
    
    protected String clientId;
    protected String username;
    protected String password;
    
    protected int keepAlive;
    protected boolean clean;

    protected Map<String, Integer> topics;
    
    public String getProtocol() {
        return protocol;
    }
    
    public int getVersion() {
        return version;
    }
    
    public String getClientId() {
	return clientId;
    }
    
    public String getUsername() {
	return username;
    }
    
    public String getPassword() {
	return password;
    }

    public int getKeepAlive() {
	return keepAlive;
    }
    
    public boolean getClean() {
	return clean;
    }

    public Map<String, Integer> getTopics() {
	return topics;
    }
}