package io.jull.bee.client;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.jull.bee.server.Server.Worker;
import io.jull.bee.server.ServerListener;
import io.jull.bee.packet.PacketFactory;
import io.jull.bee.packet.Packet;

public class Client implements ClientInterface {
    private String clientId;
    
    private String username;
    private String password;
    
    public ByteChannel channel;
    public SelectionKey key;
    public Worker worker;
    
    public BlockingQueue<ByteBuffer> inQueue;
    public BlockingQueue<ByteBuffer> outQueue;
    
    private ServerListener listener;
    private STATUSES status = STATUSES.NOT_CONNECTED;
    private Packet packet;
    
    public Client(ServerListener listener) {
	this.listener = listener;
	inQueue = new LinkedBlockingQueue<ByteBuffer>();
	outQueue = new LinkedBlockingQueue<ByteBuffer>();
    }
    
    public synchronized void close() {
	if (status == STATUSES.CLOSED) {
	    return;
	}

	if (key != null) {
	    key.cancel();
	}

	if (channel != null) {
	    try {
		channel.close();
	    } catch (IOException e) {
		return;
	    }
	}
	
	listener.onClientDisconnect(this);
	status = STATUSES.CLOSED;
    }
    
    public void parse(ByteBuffer buffer) {
	if (packet == null) {
	    packet = new Packet();
	}

	packet.parse(buffer);
	
	if (!packet.isComplete() || !packet.isValid()) {
	    if (!packet.isValid()) {
		// handle an invalid packet
	    }
	    return;
	}
	
	switch (packet.getType()) {
	case CONNECT:
	    handleConnect();
	    break;
	/*
	case DISCONNECT:
           handleDisconnect(packet);
           break;
        case Packet.TYPES.SUBSCRIBE:
           handleSubcribe(packet);
           break;
        case Packet.TYPES.UNSUBSCRIBE:
           handleUnsubcribe(packet);
           break;
	case Packet.TYPES.PUBLISH:
	   handlePublish(packet);
           break;
	*/
	default:
	    break;
	}
    }
    
    private void handleConnect() {
	if (status != STATUSES.NOT_CONNECTED) {
	    return;
	}

	status = STATUSES.CONNECTED;
	clientId = packet.getClientId();
	
	if (packet.hasUsername() && packet.hasPassword()) {
	    username = packet.getUsername();
	    password = packet.getPassword();
	}
	
	send(PacketFactory.createConnack());
	try {
	    listener.onClientConnect(this, packet);
	} catch(RuntimeException e) {
	    
	}
	
	packet = null;
    }
    
    private void handleDisconnect(Packet packet) {
	if (status != STATUSES.CONNECTED) {
	    return;
	}
    }
    
    private void send(Packet packet) {
	outQueue.add(packet.toBuffer());
	if (packet.hasPayload()) {
	    for (ByteBuffer buffer : packet.getPayload()) {
		outQueue.add(packet.toBuffer());
	    }
	}
	
	listener.onClientWriteDemand(this);
    }
}