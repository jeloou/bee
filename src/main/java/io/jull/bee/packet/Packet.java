package io.jull.bee.packet;

import java.util.Collection;
import java.util.ArrayList;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class Packet implements PacketInterface {
    private Type type;
    
    private boolean duplicate;
    private boolean retain;
    private int qos;

    private String protocol;
    private short version;

    private String clientId;
    private String username;
    private String password;
    
    private boolean will;
    private boolean willRetain;
    private int willQos;
    private boolean clean;
    private short keepAlive;
    
    private String willTopic;
    private byte[] willMessage;
    
    private int remaining;
    private Collection<ByteBuffer> buffers;
    
    private boolean complete;
    private boolean valid;
    
    public Packet() {
	buffers = new ArrayList<ByteBuffer>();
    }

    private String getString(ByteBuffer buffer, short length) {
	byte[] bytes = new byte[length];
	
	buffer.get(bytes);
	return new String(bytes, Charset.forName("UTF-8"));
    }

    private String getString(ByteBuffer buffer) {
	return getString(buffer, buffer.getShort());
    }
    
    private byte[] getBytes(ByteBuffer buffer, short length) {
	byte[] bytes = new byte[length];
	
	buffer.get(bytes);
	return bytes;
    }
    
    private byte[] getBytes(ByteBuffer buffer) {
	return getBytes(buffer, buffer.getShort());
    }
    
    private short getShort(ByteBuffer buffer) {
	byte[] bytes = new byte[2];
	
	buffer.get(bytes);
	return ByteBuffer.wrap(bytes).getShort();
    }
    
    public void parse(ByteBuffer buffer) {
	if (buffers.isEmpty()) {
	    byte fixed = buffer.get();
	    
	    int type = ((int)fixed >> 4)-1;
	    if (type < 0 || type > TypeValues.length - 1) {
		complete = true;
		return;
	    }
	    this.type = TypeValues[type];
	    
	    if ((int)((fixed >> 3) & 0x1) > 0) {
		duplicate = true;
	    }

	    qos = (int)((fixed >> 1) & 0xf0);
	    
	    if ((int)(fixed & 0x1) > 0) {
		retain = true;
	    }

	    int m = 1;
	    do {
		fixed = buffer.get();
		remaining += ((int)(fixed & 0x7f) * m);
		m *= 0x80;
	    } while((fixed & 0x80) > 0);
	}
	
	switch(getType()) {
	case CONNECT:
	    parseConnect(buffer);
	    break;
	default:
	    break;
	}
    }
    
    private void parseConnect(ByteBuffer buffer) {
	short length;
	
	protocol = getString(buffer);
	if (!protocol.equals("MQIsdp")) {
            complete = true;
            return;
	}
	
	version = (short)buffer.get();
	
	byte flags = buffer.get();
	will = ((flags >> 2) & 0x01) > 0;
	if (will) {
	    willRetain = ((flags & 0x40) >> 6) > 0;
	    willQos = (flags >> 3) & 0x03;
	}
	
	clean = ((flags >> 1) & 0x01 ) > 0;
	keepAlive = buffer.getShort();

	length = buffer.getShort();
	if (length < 1 || length > 23) {
	    complete = true;
	    return;
	}
	clientId = getString(buffer, length);
	
	if (will) {
	    willTopic = getString(buffer);
	    willMessage = getBytes(buffer);
	}
	
	if (((flags >> 7) & 0x01) > 0) {
	    username = getString(buffer);
	}
	
	if (((flags & 0x40) >> 6) > 0) {
	    password = getString(buffer);
	}
    }
    
    public Type getType() {
	return type;
    }
    
    public boolean isComplete() {
	return complete;
    }
    
    public boolean isValid() {
	return valid;
    }
}