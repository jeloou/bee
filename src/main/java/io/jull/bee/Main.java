package io.jull.bee;

import java.io.IOException;
import java.net.UnknownHostException;
    
import io.jull.bee.server.DefaultServer;

public class Main {
    public static void main(String[] args) throws UnknownHostException {
	int port = 1883;
	int i = 0;
	while (i < args.length) {
	    System.out.println(args[i++]);
	}

	DefaultServer server = new DefaultServer(port, 3);
	server.start();
    }
}