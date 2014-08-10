package io.jull.bee.server;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class PubSub {
    private class Listener extends JedisPubSub {
	@Override
	public void onMessage(String topic, String message) {
	    System.out.println(topic + ": " + message);
	}
	
	@Override
	public void onSubscribe(String topic, int n) {
	    System.out.println(topic + ": " + n);
	}
	
	@Override
	public void onUnsubscribe(String topic, int n) {
	    
	}
	
	@Override
	public void onPSubscribe(String pattern, int n) {
	    
	}
	
	@Override
	public void onPUnsubscribe(String pattern, int n) {
	    
	}
	
	@Override
	public void onPMessage(String pattern, String topic, String message) {
	    
	}
    }
    
    private Listener listener;
    private Jedis conn;
    
    public PubSub() {
	conn = new Jedis("localhost");
	listener = new Listener();
    }
    
    public void subscribe(final String topic) {
	Thread thread = new Thread() {
	    @Override
	    public void run() {
		conn.subscribe(listener, topic);
	    }
        };

	thread.start();
    }
}