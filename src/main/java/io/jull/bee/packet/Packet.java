package io.jull.bee.packet;

import java.util.Collection;
import java.util.ArrayList;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import io.jull.bee.common.AbstractClient;

public class Packet extends AbstractClient implements PacketInterface {
    private Type type;
    
    private boolean duplicate;
    private boolean retain;
    private int qos;
    
    private boolean will;
    private boolean willRetain;
    private int willQos;
    
    private String willTopic;
    private byte[] willMessage;
    
    private int remaining;
    private byte[] variable;
    private Collection<ByteBuffer> payload;

    private int returnCode;
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
	return getBytes(buffer, getShort(buffer));
    }
    
    private int getShort(ByteBuffer buffer) {
	byte[] bytes = getBytes(buffer, 2);
	return ((bytes[0] << 8) & 0xff00) | (bytes[1] & 0xff);
    }
    
    private int calcRemaining(ByteBuffer buffer) {
	int m = 1, r = 0;
	byte b;
	
	do {
	    b = buffer.get();
	    r += (b & 0x7f) * m;
	    m *= 0x80;
	} while ((b & 0x80) > 0);

	return r;
    }
    
    private void skip(ByteBuffer buffer, int n) {
	buffer.position(buffer.position() + n);
    }
    
    public void parse(ByteBuffer buffer) {
	if (isNew()) {
	    byte fixed = buffer.get();
	    
	    int type = (fixed & 0xff) >>> Shift.TYPE;
	    if (type < 1 || type > TypeValues.size() - 1) {
		complete = true;
		return;
	    }
	    this.type = TypeValues.get(type);
	    
	    if (needsQoS()) {
		qos = (fixed >> Shift.QOS) & Mask.QOS;
		
		if (qos > 0) {
		    if (((fixed >> Shift.DUPLICATE) & Mask.DUPLICATE) > 0) {
			duplicate = true;
		    }
		}
	    }
	    
	    if (this.type == Type.PUBLISH) {
		if ((fixed & Mask.RETAIN) > 0) {
		    retain = true;
		}
	    }
	    
	    if (needsRemaining()) {
		remaining = calcRemaining(buffer);
	    }
	}
	
	switch(getType()) {
	case CONNECT:
	    parseConnect(buffer);
	    break;
	case DISCONNECT:
	    parseDisconnect(buffer);
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
	
	version = getShort(buffer);
	if (version != 3) {
	    returnCode = ReturnCode.UNACCEPTABLE_VERSION;
	    complete = true;
	    return;
	}
	
	byte flags = buffer.get();
	will = ((flags >> 2) & 0x01) > 0;
	if (will) {
	    willRetain = ((flags & 0x40) >> 6) > 0;
	    willQos = (flags >> 3) & 0x03;
	}
	
	clean = ((flags >> 1) & 0x01 ) > 0;
	keepAlive = getShort(buffer);

	length = getShort(buffer);
	if (length < 1 || length > 23) {
	    returnCode = ReturnCode.ID_REJECTED;
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
	
	returnCode = ReturnCode.ACCEPTED;
	complete = true;
	valid = true;
    }
    
    private void parseDisconnect(ByteBuffer buffer) {
	skip(buffer, 1);
	complete = true;
	valid = true;
    }
    
    private boolean needsQoS() {
	if (type == Type.PUBLISH || type == Type.PUBREL || type == Type.SUBSCRIBE || type == Type.UNSUBSCRIBE) {
	    return true;
	}
	return false;
    }
    
    private boolean needsRemaining() {
	if (type == Type.PINGREQ || type == Type.PINGRESP || type == Type.DISCONNECT) {
	    return false;
	}
	return true;
    }
    
    private boolean isNew() {
	return isnew;
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
    
    public int getReturnCode() {
	return returnCode;
    }
    
    public ByteBuffer toBuffer() {
	byte fixed = 0x00;
	int length;
	
	fixed |= (TypeValues.indexOf(type) << Shift.TYPE);
	
	if (duplicate)
	    fixed |= OrMask.DUPLICATE;
	
	if (qos > 0)
	    fixed |= (qos << Shift.QOS);
	
	if (retain)
	    fixed |= OrMask.RETAIN;
	
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