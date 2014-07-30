package io.jull.bee.server;

import java.net.UnknownHostException;
import java.net.InetSocketAddress;

public class DefaultServer extends Server {
    public DefaultServer(InetSocketAddress address, int workers) {
	super(address, workers);
    }
    
    public DefaultServer(int port, int workers) {
	super(port, workers);
    }
    
    public DefaultServer(int port) throws UnknownHostException {
	super(new InetSocketAddress(port));
    }
    
    public DefaultServer(InetSocketAddress address) {
	super(address);
    }
}