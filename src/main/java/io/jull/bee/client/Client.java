package io.jull.bee.client;

import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import io.jull.bee.server.Server.Worker;
import io.jull.bee.server.ServerListener;
import io.jull.bee.packet.PacketFactory;
import io.jull.bee.packet.Packet;

public class Client implements ClientInterface {
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
	    handleConnect(packet);
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
    
    private void handleConnect(Packet packet) {
	if (status != STATUSES.NOT_CONNECTED) {
	    return;
	}
	/*
	write(PacketFactory.createConnack());
	status = STATUSES.CONNECTED;
	try {
	    listener.onClientConnected(this, packet);
	} catch(RuntimeException e) {
	    listener.onClientError(this, e);
	}
	*/
    }
    
    private void handleDisconnect(Packet packet) {
	if (status != STATUSES.CONNECTED) {
	    return;
	}
    }
    
    private void write(ByteBuffer buffer) {
	outQueue.add(buffer);
	listener.onClientWriteDemand(this);
    }
    
    private void write(List<ByteBuffer> buffers) {
	for (ByteBuffer buffer: buffers) {
	    write(buffer);
	}
    }
}