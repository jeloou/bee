package io.jull.bee.server;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import io.jull.bee.client.Client;
import io.jull.bee.packet.Packet;

public class PubSub extends Thread {
    static private class Job {
	public enum Type {
	    PUBLISH, SUBSCRIBE
	}
	
	private Type type;
	
	private Client client;
	private Packet packet;
	private String topic;
	
	public Job(Type type, Client client, String topic) {
	    this.type = type;
	    this.client = client;
	    this.topic = topic;
	}
	
	public Job(Type type, Packet packet, String topic) {
	    this.type = type;
	    this.packet = packet;
	    this.topic = topic;
	}

	public Type getType() {
	    return type;
	}
    }

    private class Topic {
	private String topic;
	private Map<Client, Integer> clients;
	
	public Topic(String topic) {
	    this.topic = topic;
	}
    }

    private BlockingQueue<Job> jobsQueue;
    private Map<String, Topic> topics;
    
    public PubSub() {
	
    }
    
    public void subscribe(Client client, String topic) {
	Job job = new Job(Job.Type.SUBSCRIBE, client, topic);
	jobsQueue.add(job);
    }
    
    public void publish(Packet packet, String topic) {
	Job job = new Job(Job.Type.PUBLISH, packet, topic);
	jobsQueue.add(job);
    }
    
    public void run() {
	Job job;
	
	while (true) {
	    try {
		job = jobsQueue.take();
		
		switch (job.getType()) {
		case PUBLISH:
		    handlePublish(job);
		    break;
		case SUBSCRIBE:
		    handleSubscribe(job);
		    break;
		default:
		    break;
		}
		
	    } catch (InterruptedException e) {
		System.err.println(e.getMessage());
		System.err.flush();
		System.exit(1);
	    }
	}
	
    }
    
    private void handlePublish(Job job) {
	if (!topics.containsKey(job.topic)) {
	    return;
	}

	Topic topic = topics.get(job.topic);
	Packet packet = job.packet;
	int qos;
	
	for (Client client : topic.clients.keySet()) {
	    qos = topic.clients.get(client);
	    packet.setQoS(qos);
	    client.send(packet, false);
	}
    }
    
    private void handleSubscribe(Job job) {
	Topic topic;

	if (!topics.containsKey(job.topic)) {
	    topic = new Topic(job.topic);
	    topics.put(job.topic, topic);
	} else  {
	    topic = topics.get(job.topic);
	}
	
	topic.clients.put(job.client, job.packet.getQoS());
	return;
    }
}

