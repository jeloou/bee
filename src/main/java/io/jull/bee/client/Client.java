package io.jull.bee.client;

import java.io.IOException;
import java.nio.channels.ByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;

import java.util.Map;
import java.util.Collection;
import java.util.HashMap;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
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
    
    private Map<Integer, Packet> inInflight;
    private Map<Integer, Packet> outInflight;
    private Map<Integer, Packet> packets;
    private List<String> subscriptions;
    
    private Packet packet;
    private int currentId;
    
    private boolean endAfter = false;
    
    public Client(ServerListener listener) {
	this.listener = listener;
	
	inQueue = new LinkedBlockingQueue<ByteBuffer>();
	outQueue = new LinkedBlockingQueue<ByteBuffer>();
	
	inInflight = new HashMap<Integer, Packet>();
	outInflight = new HashMap<Integer, Packet>();
	packets = new HashMap<Integer, Packet>();
	subscriptions = new ArrayList<String>();
	
	Random rand = new Random();
	currentId = (rand.nextInt(65535) + 1);
    }
    
    @Override
    public int hashCode() {
	return clientId.hashCode();
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
	case SUBSCRIBE:
	    handleSubscribe();
	    break;
	case UNSUBSCRIBE:
	    handleUnsubscribe();
	    break;
	case PUBLISH:
	    handlePublish();
	    break;
	case PUBACK:
	case PUBREC:
	case PUBREL:
	case PUBCOMP:
	    handleAck();
	    break;
	case PINGREQ:
	    handlePingReq();
	    break;
	default:
	    break;
	}
    }
    
    private void handleConnect() {
	if (status != Status.NOT_CONNECTED) {
	    end(false);
	    return;
	}
	
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
    
    private void handleSubscribe() {
	subscriptions.addAll((Collection<String>)packet.getTopics().keySet());
	listener.onClientSubscribe(this, packet);
	
	if (packet.getQoS() > 0) {
	    send(PacketFactory.createSuback(packet));
	}
	packet = null;
    }
    
    private void handleUnsubscribe() {
	listener.onClientUnsubscribe(this, packet);
	
	if (packet.getQoS() > 0) {
	    send(PacketFactory.createUnsuback(packet));
	}
	packet = null;
    }
    
    private void handlePublish() {
	int id = packet.getId();
	int qos = packet.getQoS();
	
	if (qos > 1) {
	    if (packets.containsKey(id)) {
		return;
	    }
	    
	    packets.put(id, packet);
	}
	
	Packet ack = null;
	
	switch (qos) {
	case 1:
	    ack = PacketFactory.createPuback(packet);
	    break;
	case 2:
	    ack = PacketFactory.createPubrec(packet);
	    inInflight.put(id, ack);
	    break;
	default:
	    break;
	}
	
	if (ack != null) {
	    send(ack);
	}
	
	if (qos < 2) {
	    listener.onClientPublish(this, packet);
	}
	
	packet = null;
    }
    
    private void handleAck() {
	int id = packet.getId();
	
	Packet inflight = null;
	Packet ack = null;
	
	switch (packet.getType()) {
	case PUBACK:
	    if (!outInflight.containsKey(id)) {
		return;
	    }

	    inflight = outInflight.get(id);
	    if (inflight.getType() != Packet.Type.PUBLISH) {
		return;
	    }
	    
	    outInflight.remove(id);
	    break;
	case PUBREL:
	    if (!inInflight.containsKey(id)) {
		return;
	    }
	    
	    inflight = inInflight.get(id);
	    if (inflight.getType() != Packet.Type.PUBREC) {
		return;
	    }
	    
	    listener.onClientPublish(this, packets.get(id));
	    packets.remove(id);

	    ack = PacketFactory.createPubcomp(packet);
	    inInflight.remove(id);
	    break;
	case PUBREC:
	    if (!outInflight.containsKey(id)) {
		return;
	    }

	    inflight = outInflight.get(id);
	    if (inflight.getType() != Packet.Type.PUBLISH) {
		return;
	    }
	    
	    ack = PacketFactory.createPubrel(packet);
	    outInflight.put(id, ack);
	    break;
	case PUBCOMP:
	    if (!outInflight.containsKey(id)) {
		return;
	    }

	    inflight = outInflight.get(id);
	    if (inflight.getType() != Packet.Type.PUBREL) {
		return;
	    }
	    
	    outInflight.remove(id);
	    break;
	default:
	    break;
	}

	if (ack != null) {
	    send(ack);
	}
	
	listener.onClientAck(this, packet);
	packet = null;
    }
    
    private void handlePingReq() {
	send(PacketFactory.createPingResp());
	packet = null;
    }
    
    private void handleInvalidPacket() {
	if (packet.getType() == Packet.Type.CONNECT) {
	    endAfter = true;
	    send(PacketFactory.createConnack(packet.getReturnCode()));
	    return;
	}
	
	end();
    }
    
    private int nextId() {
	currentId++;
	if (currentId > 65535) {
	    currentId = 1;
	}
	
	return currentId;
    }
    
    public void send(Packet packet, boolean callback) {
	synchronized(this) {
	    if (packet.getQoS() > 0) {
		if (packet.getType() == Packet.Type.PUBLISH) {
		    packet.setId(nextId());
		    outInflight.put(packet.getId(), packet);
		}
	    }
	    
	    outQueue.add(packet.toBuffer());
	    if (packet.hasPayload()) {
		for (ByteBuffer buffer : packet.getPayload()) {
		    outQueue.add((ByteBuffer)buffer.asReadOnlyBuffer());
		}
	    }
	}
	
	if (callback) {
	    listener.onClientWriteDemand(this);
	}
    }
    
    public void send(Packet packet) {
	send(packet, true);
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

    public List<String> getSubscriptions() {
	return subscriptions;
    }
}