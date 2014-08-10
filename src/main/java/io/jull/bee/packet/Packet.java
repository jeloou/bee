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
    private byte[] variable;
    private Collection<ByteBuffer> payload;

    private boolean isnew = true;
    private boolean complete;
    private boolean valid;

    public Packet(Type type, boolean duplicate, short qos, boolean retain, byte[] variable) {
	this.type = type;
	this.duplicate = duplicate;
	this.qos = qos;
	this.retain = retain;
	this.variable = variable;
	isnew = false;
    }
    
    public Packet() {
	payload = new ArrayList<ByteBuffer>();
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
    
    public void parse(ByteBuffer buffer) {
	if (isNew()) {
	    byte fixed = buffer.get();
	    
	    int type = ((int)fixed >> Shift.TYPE);
	    if (type < 1 || type > TypeValues.size() - 1) {
		complete = true;
		return;
	    }
	    this.type = TypeValues.get(type);
	    
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

	isnew = false;
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

	complete = true;
	valid = true;
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
    
    public boolean isNew() {
	return isnew;
    }
    
    public boolean hasUsername() {
	return username != null;
    }
    
    public String getUsername() {
	return username;
    }
    
    public boolean hasPassword() {
	return password != null;
    }

    public String getPassword() {
	return password;
    }
    
    public String getClientId() {
	return clientId;
    }
    
    public ByteBuffer toBuffer() {
	byte fixed = 0x00;
	int length;
	
	fixed |= (TypeValues.indexOf(type) << Shift.TYPE);
	
	if (duplicate)
	    fixed |= Mask.DUPLICATE;
	
	if (qos > 0)
	    fixed |= (qos << Shift.QOS);
	
	if (retain)
	    fixed |= Mask.RETAIN;
	
	length = variable.length;
	if (hasPayload()) {
	    for (ByteBuffer buffer : getPayload()) {
		length += buffer.limit();
	    }
	}
	
	int l = length, d;
	byte[] remaining = new byte[4];
	short i = 0;
	
	do {
	    d = l % 128;
	    l = l / 128;
	
	    if (l > 0) {
		d |= 0x80;
	    }
	    
	    remaining[i] = (byte)d;
	    i++;
	} while (l > 0);
	
	ByteBuffer buffer = ByteBuffer.allocate(1 + i + length);
	buffer.put(fixed);
	buffer.put(remaining, 0, i);
	buffer.put(variable);
	buffer.flip();

	return buffer;
    }
    
    public boolean hasPayload() {
	return payload != null && !payload.isEmpty();
    }

    public Collection<ByteBuffer> getPayload() {
	return payload;
    }
}