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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.Set;
import java.util.List;

import io.jull.bee.client.Client;

public abstract class Server extends ServerAdapter implements Runnable {
    
    public static int WORKERS = Runtime.getRuntime().availableProcessors();
    
    private Thread thread;
    private final InetSocketAddress address;
    private ServerSocketChannel server;
    private Selector selector;
    private List<Worker> workers;
    private int current = 0;
    private Collection<Client> clients;

    public class Worker extends Thread {
	private BlockingQueue<Client> queue;
	
	public Worker() {
	    clientsQueue = new LinkedBlockingQueue<Client>();
	}
	
	public void run() {
	    System.out.println("worker running");
	    
	    ByteBuffer buffer = null;
	    Client client = null;
	    
	    while (true) {
		try {
		    client = queue.take();
		    buffer = client.queue.poll();
		    if (buffer == null) {
			continue;
		    }
		    
		    client.talk(buffer);
		} catch (InterruptedException e) {
		} catch (RuntimeException e ) {
		    System.out.println("worker exception");
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
	    System.out.println("exception:0");
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
			    System.out.println("isAcceptable"); 
			    
			    SocketChannel channel = server.accept();
			    channel.configureBlocking(false);
			    
			    client = new Client();
			    key = channel.register(selector, SelectionKey.OP_READ, client);
			    
			    client.channel = channel;
			    client.key = key;
			    
			    i.remove();
			    continue;
			}
			
			if (key.isReadable()) {
			    System.out.println("isReadable");
			    
			    ByteBuffer buffer = ByteBuffer.allocate(16384);
			    client = (Client) key.attachment();
			    
			    int read = client.channel.read(buffer);
			    buffer.flip();
			    if (read > -1) {
				client.queue.put(buffer);

				Worker worker = null;
				if (client.worker == null) {
				    worker = workers.get(current);
				    
				    if (current == workers.size()-1) {
					current = 0;
				    } else {
					current++;
				    }
				}
				
				worker.clientsQueue.put(client);
			    } else {
				System.out.println("EOT");
			    }
			    
			    i.remove();
			    continue;
			}
			
			if (key.isWritable()) {
			    System.out.println("isWritable");
			    
			    client = (Client) key.attachment();
			    ByteBuffer buffer = client.outQueue.peek();
			    do {
				client.channel.write(buffer);
				if (buffer.remaining() > 0) {
				    continue;
				}
				
				ws.outQueue.poll();
				buffer = client.outQueue.peek();
			    } while (buffer != null);
			    
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
	    System.out.println("exception:2");
	    return;
	}
    }
    
    protected boolean addClient(Client client) {
	synchronized(clients) {
	    return this.clients.add(client);
	}
    }

    protected boolean removeClient(Client client) {
	synchronized(clients) {
	    return this.clients.remove(client);
	}
    }

    @Override
    public final void onClientConnect(Client client) {
	if (addClient(client)) {
	    onConnect(client);
	}
    }

    @Override
    public final void onClientDisconnect(Client client) {
	
    }

    @Override
    public final void onClientWriteDemand(Client client) {
	try {
	    client.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
	} catch (CancelledKeyException e) {
	    client.outQueue.clear();	    
	}
	selector.wakeUp();
    }
}