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
	    PUBLISH, SUBSCRIBE, UNSUBSCRIBE, CLEAN
        }
	
	private Type type;
	
	private Client client;
	private Packet packet;
	
	public Job(Type type, Client client, Packet packet) {
	    this.type = type;
	    this.client = client;
	    this.packet = packet;
	}

	public Job(Type type, Client client) {
	    this.type = type;
	    this.client = client;
	}
	
	public Type getType() {
	    return type;
	}
    }

    private class Topic {
	private String topic;
	private Map<Client, Integer> clients;
	
	public Topic(String topic) {
	    clients = new HashMap<Client, Integer>();
	    this.topic = topic;
	}
    }

    private BlockingQueue<Job> jobsQueue;
    private Map<String, Topic> topics;
    
    public PubSub() {
	jobsQueue = new LinkedBlockingQueue<Job>();
	topics = new HashMap<String, Topic>();
    }
    
    private void addJob(Job job) {
	try {
	    jobsQueue.put(job);
	} catch (InterruptedException e) {
	    System.err.println(e.getMessage());
	    System.err.flush();
	    System.exit(1);
	}
    }
    
    public void subscribe(Client client, Packet packet) {
	Job job = new Job(Job.Type.SUBSCRIBE, client, packet);
	addJob(job);
    }
    
    public void unsubscribe(Client client, Packet packet) {
	Job job = new Job(Job.Type.UNSUBSCRIBE, client, packet);
	addJob(job);
    }
    
    public void clean(Client client) {	
	Job job = new Job(Job.Type.CLEAN, client);
	addJob(job);
    }
    
    public void publish(Client client, Packet packet) {
	Job job = new Job(Job.Type.PUBLISH, client, packet);
	addJob(job);
    }
    
    public void ack(Client client, Packet packet) {
	
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
		case UNSUBSCRIBE:
		    handleUnsubscribe(job);
		    break;
		case CLEAN:
		    handleClean(job);
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
	if (!topics.containsKey(job.packet.getTopic())) {
	    return;
	}
	
	Topic topic = topics.get(job.packet.getTopic());
	Packet packet = job.packet;
	int qos;
	
	for (Client client : topic.clients.keySet()) {
	    qos = topic.clients.get(client);
	    packet.setQoS(qos);
	    client.send(packet);
	}
    }
    
    private void handleSubscribe(Job job) {
	Topic topic;
	
	for (String k : job.packet.getTopics().keySet()) {
	    int qos = job.packet.getTopics().get(k);
	    if (!topics.containsKey(k)) {
		topic = new Topic(k);
		topics.put(k, topic);
	    } else {
		topic = topics.get(k);
	    }
	    
	    topic.clients.put(job.client, qos);
	}
	
	return;
    }

    private void handleUnsubscribe(Job job) {
	Topic topic;

	for (String k : job.packet.getTopics().keySet()) {
	    if (topics.containsKey(k)) {
		topic = topics.get(k);
		
		if (!topic.clients.containsKey(job.client)) {
		    return;
		}
		
		topic.clients.remove(job.client);
	    }
	}
    }

    private void handleClean(Job job) {
	Topic topic;
	
	for (String k: job.client.getSubscriptions()) {
	    if (topics.containsKey(k)) {
		topic = topics.get(k);
		topic.clients.remove(job.client);
	    }
	}
    }
}

