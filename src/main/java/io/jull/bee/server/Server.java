package io.jull.bee.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.CancelledKeyException;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.List;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.jull.bee.client.Client;
import io.jull.bee.packet.Packet;

public final class Server extends ServerAdapter implements Runnable {
    
    public static int WORKERS = Runtime.getRuntime().availableProcessors();
    
    private Thread thread;
    private final InetSocketAddress address;
    private ServerSocketChannel server;
    private Selector selector;
    private List<Worker> workers;
    private int current = 0;
    private List<Client> clients;
    private PubSub pubsub;

    public class Worker extends Thread {
	private BlockingQueue<Client> clientsQueue;
	
	public Worker() {
	    clientsQueue = new LinkedBlockingQueue<Client>();
	}
	
	public void run() {
	    ByteBuffer buffer = null;
	    Client client = null;
	    
	    while (true) {
		try {
		    client = clientsQueue.take();
		    buffer = client.inQueue.poll();
		    if (buffer == null) {
			continue;
		    }
		    
		    do {
			client.parse(buffer);
		    } while (buffer.remaining() > 0);
		} catch (InterruptedException e) {
		} catch (RuntimeException e ) {
		    System.err.println(e.getMessage());
		    System.err.flush();
		    System.exit(1);
		}
	    }
	}
    }

    public Server(InetSocketAddress address, int workers) {
	this.address = address;
	this.workers = new ArrayList<Worker>(workers);
	for (int i = 0; i < workers; i++) {
	    Worker worker = worker = new Worker();
	    this.workers.add(worker);
	    worker.start();
	}
	clients = new ArrayList<Client>();
	
	pubsub = new PubSub();
	pubsub.start();
    }

    public Server(int port, int workers) {
	this(new InetSocketAddress(port), workers);
    }
    
    public Server(InetSocketAddress address) {
	this(address, WORKERS);
    }
    
    public Server(int port) throws UnknownHostException {
	this(new InetSocketAddress(port), WORKERS);
    }
    
    public void start() {
	if (thread == null) {
	    new Thread(this).start();
	    return;
	}
	
	throw new IllegalStateException("start");
    }
    
    public void stop() {
	
    }
    
    public void close() {
	
    }
    
    public void run() {
	synchronized(this) {
	    if (thread != null) {
		throw new IllegalStateException("run");
	    }
	    
	    thread = Thread.currentThread();
	}

	try {
	    server = ServerSocketChannel.open();
	    server.configureBlocking(false);
	    
	    ServerSocket socket = server.socket();
	    socket.setReceiveBufferSize(16384);
	    socket.bind(address);

	    selector = Selector.open();
	    server.register(selector, server.validOps());
	} catch(IOException e) {
	    System.err.println(e);
	    System.err.flush();
	    System.exit(1);
	    return;
	}
	
	try {
	    while (!thread.isInterrupted()) {
		SelectionKey key = null;
		Client client = null;
		
		try {
		    selector.select();
		    Set<SelectionKey> keys = selector.selectedKeys();
		    Iterator<SelectionKey> i = keys.iterator();
		    
		    while (i.hasNext()) {
			key = i.next();
			
			if (!key.isValid()) {
			    return;
			}
			
			if (key.isAcceptable()) {
			    SocketChannel channel = server.accept();
			    channel.configureBlocking(false);
			    
			    client = new Client(this);
			    key = channel.register(selector, SelectionKey.OP_READ, client);
			    
			    client.channel = channel;
			    client.key = key;
			    
			    i.remove();
			    continue;
			}
			
			if (key.isReadable()) {
			    ByteBuffer buffer = ByteBuffer.allocate(16384);
			    client = (Client) key.attachment();
			    
			    int read = client.channel.read(buffer);
			    buffer.flip();
			    if (read > -1) {
				client.inQueue.put(buffer);

				Worker worker = null;
				if (client.worker == null) {
				    client.worker = workers.get(current);
				    if (current == workers.size()-1) {
					current = 0;
				    } else {
					current++;
				    }
				} 
				
				worker = client.worker;
				worker.clientsQueue.put(client);
			    } else {
				System.out.println("EOT");
				client.end();
			    }
			    
			    i.remove();
			    continue;
			}
			
			if (key.isWritable()) {
			    client = (Client) key.attachment();
			    ByteBuffer buffer = client.outQueue.peek();
			    do {
				client.channel.write(buffer);
				if (buffer.remaining() > 0) {
				    continue;
				}
				
				client.outQueue.poll();
				buffer = client.outQueue.peek();
			    } while (buffer != null);
			    
			    if (client.getEndAfter()) {
				client.end();
				i.remove();
				continue;
			    }
			    
			    if (key.isValid()) {
				key.interestOps(SelectionKey.OP_READ);
			    }
			    
			    i.remove();
			    continue;
			}
			
			i.remove();
		    }
		} catch (IOException e) {
		    System.out.println("exception:1");
		    return;
		} catch (InterruptedException e) {
		    return;
		}
	    }
	} catch (RuntimeException e) {
	    System.err.println(e);
	    System.err.flush();
	    System.exit(1);
	    return;
	}
    }
    
    private boolean authenticate(Client client, String username, String password) {
	return true;
    }
    
    private boolean authorizeSubscribe(Client client, String topic) {
	return true;
    }
    
    private boolean authorizePublish(Client client, String topic, Collection<ByteBuffer> payload) {
	return true;
    }
    
    protected boolean addClient(Client client) {
	synchronized(clients) {
	    if (!authenticate(client, client.getUsername(), client.getPassword())) {
		client.end();
		return false;
	    }
	    
	    if (!clients.contains(client)) {
		return clients.add(client);
	    }
	    
	    int i = clients.indexOf(client);
	    Client cl = clients.get(i);
	    if (cl.isConnected()) {
		cl.end(false);
	    }
	    
	    clients.remove(cl);
	    return clients.add(client);
	}
    }
    
    protected boolean removeClient(Client client) {
	synchronized(clients) {
	    int i = clients.indexOf(client);
	    if (client == clients.get(i)) {
		pubsub.clean(client);
		return clients.remove(client);
	    }
	    
	    return false;
	}
    }

    @Override
    public final void onClientConnect(Client client, Packet packet) {
	if (addClient(client)) {
	    //onConnect(client, packet);
	}
    }

    @Override
    public final void onClientDisconnect(Client client) {
	if (!client.getClean()) {
	    return;
	}
	
	if (removeClient(client)) {
	    //onDisconnect(client);
	}
    }

    @Override
    public final void onClientSubscribe(Client client, Packet packet) {
	if (!authorizeSubscribe(client, packet.getTopic())) {
	    return;
	}
	
	pubsub.subscribe(client, packet);
    }
    
    @Override
    public final void onClientUnsubscribe(Client client, Packet packet) {
	pubsub.unsubscribe(client, packet);
    }
    
    @Override
    public final void onClientPublish(Client client, Packet packet) {
	if (!authorizePublish(client, packet.getTopic(), packet.getPayload())) {
	    return;
	}
	
	pubsub.publish(client, packet);
    }
    
    @Override
    public final void onClientAck(Client client, Packet packet) {
	pubsub.ack(client, packet);
    }
    
    @Override
    public final void onClientWriteDemand(Client client) {
	try {
	    client.key.interestOps(client.key.interestOps() | SelectionKey.OP_WRITE);
	} catch (CancelledKeyException e) {
	    client.outQueue.clear();
	}
	selector.wakeup();
    }
}