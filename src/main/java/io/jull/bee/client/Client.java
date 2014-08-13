package io.jull.bee.client;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.jull.bee.common.AbstractClient;
import io.jull.bee.server.Server.Worker;
import io.jull.bee.server.ServerListener;
import io.jull.bee.packet.PacketFactory;
import io.jull.bee.packet.Packet;

public class Client extends AbstractClient implements ClientInterface {
    public ByteChannel channel;
    public SelectionKey key;
    public Worker worker;
    
    public BlockingQueue<ByteBuffer> inQueue;
    public BlockingQueue<ByteBuffer> outQueue;
    
    private ServerListener listener;
    private Status status = Status.NOT_CONNECTED;
    private Packet packet;
    
    private boolean endAfter = false;
    
    public Client(ServerListener listener) {
	this.listener = listener;
	inQueue = new LinkedBlockingQueue<ByteBuffer>();
	outQueue = new LinkedBlockingQueue<ByteBuffer>();
    }
    
    @Override 
    public boolean equals(Object o) {
	if (o == null) {
	    return false;
	}
	
	if (o == this) {
	    return true;
	}
	
	if (!(o instanceof Client)) {
	    return false;
	}

	Client client = (Client)o;
	return (client.clientId.equals(clientId));
    }
    
    public void parse(ByteBuffer buffer) {
	if (packet == null) {
	    packet = new Packet();
	}

	packet.parse(buffer);
	
	if (!packet.isComplete() || !packet.isValid()) {
	    if (!packet.isValid()) {
		handleInvalidPacket();
	    }
	    return;
	}
	
	switch (packet.getType()) {
	case CONNECT:
	    handleConnect();
	    break;
	case DISCONNECT:
	    handleDisconnect();
	    break;
	/*
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
	if (status != Status.NOT_CONNECTED)
	    return;
	
	protocol = packet.getProtocol();
	version = packet.getVersion();
	
	clientId = packet.getClientId();
	username = packet.getUsername();
	password = packet.getPassword();

	keepAlive = packet.getKeepAlive();
	clean = packet.getClean();
	
	try {
	    listener.onClientConnect(this, packet);
	} catch(RuntimeException e) {
	    
	}

	status = Status.CONNECTED;
	send(PacketFactory.createConnack());
	packet = null;
    }
    
    private void handleDisconnect() {
	if (status != Status.CONNECTED) {
	    return;
	}
	
	end();
    }
    
    private void handleInvalidPacket() {
	if (packet.getType() == Packet.Type.CONNECT) {
	    endAfter = true;
	    send(PacketFactory.createConnack(packet.getReturnCode()));
	    return;
	}
	
	end();
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
    
    public void end() {
	end(true);
    }
    
    public void end(boolean callback) {
	synchronized(this) {
	    if (status == Status.DISCONNECTED) {
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
	    
	    status = Status.DISCONNECTED;
	}
	
	if (callback) {
	    listener.onClientDisconnect(this);
	}
    }
    
    public synchronized boolean isConnected() {
	return (status == Status.CONNECTED);
    }

    public boolean getEndAfter() {
	return endAfter;
    }
}