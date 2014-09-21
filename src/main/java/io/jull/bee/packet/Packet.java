package io.jull.bee.packet;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Arrays;

import java.io.ByteArrayOutputStream;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import io.jull.bee.common.AbstractClient;

public class Packet extends AbstractClient {
    public enum Type {
	RESERVED, CONNECT, CONNACK, PUBLISH, PUBACK,
        PUBREC, PUBREL, PUBCOMP, SUBSCRIBE,
        SUBACK, UNSUBSCRIBE, UNSUBACK, PINGREQ,
	PINGRESP, DISCONNECT
    }

    private static List<Type> TypeValues = Arrays.asList(Type.values());
    
    private final class Mask {
	public static final byte DUPLICATE = 0x01;
	public static final byte RETAIN = 0x01;
	public static final byte QOS = 0x03;
    }
    
    private class OrMask {
	public static final byte DUPLICATE = 0x08;
	public static final byte RETAIN = 0x01;
    }
    
    private class Shift {
	public static final byte TYPE = 0X04;
	public static final byte QOS = 0x01;
	public static final byte DUPLICATE = 0x03;
    }

    public class ReturnCode {
	public static final byte ACCEPTED = 0x00;
	public static final byte UNACCEPTABLE_VERSION = 0x01;
	public static final byte ID_REJECTED = 0x02;
	public static final byte SERVER_UNAVAILABLE = 0x03;
	public static final byte BAD_CREDENTIALS = 0x04;
	public static final byte NOT_AUTHORIZED = 0x05;
    }
    
    private int id;
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
    private ByteArrayOutputStream variable;
    private Collection<ByteBuffer> payload;
    
    private int returnCode;
    private boolean isnew = true;
    private boolean changed = false;
    private boolean complete;
    private boolean valid;
    
    private String topic;
    private List<Integer> topicsQos;

    
    public Packet(Type type, boolean duplicate, short qos, boolean retain, byte[] variable) {
	this.type = type;
	this.duplicate = duplicate;
	this.qos = qos;
	this.retain = retain;
	this.variable = new ByteArrayOutputStream();
	if (variable.length > 0) {
	    this.variable.write(variable, 0, variable.length);
	}
	
	isnew = false;
    }
    
    public Packet(Type type, boolean duplicate, short qos, boolean retain, byte[] variable, byte[] payload) {
	this(type, duplicate, qos, retain, variable);
	
	this.payload = new ArrayList<ByteBuffer>();
	this.payload.add(ByteBuffer.wrap(payload));
    }
    
    public Packet(Type type, boolean duplicate, short qos, boolean retain) {
	this(type, duplicate, qos, retain, new byte[0]);
    }
    
    public Packet() {
	payload = new ArrayList<ByteBuffer>();
	variable = new ByteArrayOutputStream();
    }
    
    private String getString(ByteBuffer buffer, int length, boolean copy) {
	byte[] bytes = getBytes(buffer, length);
	
	if (copy) {
	    variable.write(bytes, 0, bytes.length);
	}
	
	return new String(bytes, Charset.forName("UTF-8"));
    }
    
    private String getString(ByteBuffer buffer, boolean copy) {
	return getString(buffer, getShort(buffer, copy), copy);
    }
    
    private String getString(ByteBuffer buffer, int length) {
	return getString(buffer, length, false);
    }
    
    private String getString(ByteBuffer buffer) {
	return getString(buffer, getShort(buffer), false);
    }
    
    private byte getByte(ByteBuffer buffer) {
	remaining--;
	return buffer.get();
    }
    
    private byte[] getBytes(ByteBuffer buffer, int length) {
	byte[] bytes = new byte[length];
	
	buffer.get(bytes);
	remaining -= length;
	return bytes;
    }
    
    private byte[] getBytes(ByteBuffer buffer) {
	return getBytes(buffer, getShort(buffer));
    }
    
    private int getShort(ByteBuffer buffer) {
	return getShort(buffer, false);
    }
    
    private int getShort(ByteBuffer buffer, boolean copy) {
	byte[] bytes = getBytes(buffer, 2);
	
	if (copy) {
	    variable.write(bytes, 0, 2);
	}
	
	return ((bytes[0] << 8) & 0xff00) | (bytes[1] & 0xff);
    }
    
    public static byte[] getShortBytes(int n) {
	byte[] bytes = new byte[2];

	bytes[0] |= (byte)((n >> 8) & 0xff);
	bytes[1] |= (byte)(n & 0xff);

	return bytes;
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
		
		if (qos > 2) {
		    complete = true;
		    return;
		}
		
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
	case SUBSCRIBE:
	    parseSubscribe(buffer);
	    break;
	case UNSUBSCRIBE:
	    parseUnsubscribe(buffer);
	    break;
	case PUBLISH:
	    parsePublish(buffer);
	    break;
	case PUBACK:
	case PUBREC:
	case PUBREL:
	case PUBCOMP:
	    parseAck(buffer);
	    break;
	case PINGREQ:
	    parsePingReq(buffer);
	    break;
	default:
	    break;
	}
	
	isnew = false;
    }
    
    private void parseConnect(ByteBuffer buffer) {
	int length;
	
	protocol = getString(buffer);
	if (!protocol.equals("MQIsdp")) {
            complete = true;
            return;
	}
	
	version = getByte(buffer) & 0xff;
	if (version != 3) {
	    returnCode = ReturnCode.UNACCEPTABLE_VERSION;
	    complete = true;
	    return;
	}
	
	byte flags = getByte(buffer);
	will = ((flags >> 2) & 0x01) > 0;
	if (will) {
	    willRetain = ((flags & 0x40) >> 6) > 0;
	    willQos = (flags >> 3) & 0x03;
	}
	
	clean = (int)((flags >> 1) & 0x01 ) > 0;
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
    
    private void parseSubscribe(ByteBuffer buffer) {
	String topic;
	int qos;
	
	topics = new HashMap<String, Integer>();
	topicsQos = new ArrayList<Integer>();
	id = getShort(buffer);
	
	int length;
	do {
	    length = getShort(buffer);
	    topic = getString(buffer, length);
	    if (topic.length() < length) {
		break;
	    }
	    
	    qos = getByte(buffer) & 0xff;
	    if (qos > 2) {
		complete = true;
		return;
	    }
	    
	    topics.put(topic, qos);
	    topicsQos.add(qos);
	} while(remaining > 0);
	
	complete = true;
	valid = true;
    }
    
    private void parseUnsubscribe(ByteBuffer buffer) {
	String topic;
	
	topics = new HashMap<String, Integer>();
	id = getShort(buffer);
	
	int length;
	do {
	    length = getShort(buffer);
	    topic = getString(buffer, length);
	    if (topic.length() < length) {
		break;
	    }
	    
	    topics.put(topic, 0);
	} while(remaining > 0);
	
	complete = true;
	valid = true;
    }
    
    private void parsePublish(ByteBuffer buffer) {
	if (variable.size() < 1) {
	    topic = getString(buffer, true);
	    if (qos > 0) {
		id = getShort(buffer, true);
	    }
	}
	
	int left = buffer.limit() - buffer.position();
	int limit = buffer.position() + remaining;
	
	if (limit > buffer.capacity()) {
	    limit = buffer.capacity();
	}
	
	if (left > remaining) {
	    payload.add((ByteBuffer)buffer.asReadOnlyBuffer().limit(limit));
	    buffer.position(limit);
	    remaining = 0;

	    complete = true;
	    valid = true;
	    return;
	}
	
	payload.add((ByteBuffer)buffer.asReadOnlyBuffer());
	buffer.position(limit);
	remaining -= left;
	
	complete = remaining > 0? false : true;
	valid = true;
    }
    
    private void parseAck(ByteBuffer buffer) {
	id = getShort(buffer);
	
	complete = true;
	valid = true;
    }
    
    private void parsePingReq(ByteBuffer buffer) {
	complete = true;
	valid = true;
    }
    
    private void generate() {
	switch(getType()) {
	case PUBLISH:
	    generatePublish();
	    break;
	default:
	    break;
	}

	changed = false;
    }
    
    private void generatePublish() {
	byte[] length = new byte[2];
	byte[] topic;
	
	variable.reset();
	
	topic = this.topic.getBytes(Charset.forName("UTF-8"));
	length = getShortBytes(topic.length);
	
	variable.write(length, 0, 2);
	variable.write(topic, 0, topic.length);
	
	if (qos > 0) {
	    variable.write(getShortBytes(id), 0, 2);
	}
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
    
    public int getId() {
	return id;
    }
    
    public void setId(int id) {
	this.id = id;
	changed = true;
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
    
    public void setQoS(int qos) {
	this.qos = qos;
	changed = true;
    }
    
    public int getQoS() {
	return qos;
    }
    
    public String getTopic() {
	return topic;
    }
    
    public Map<String, Integer> getTopics() {
	return topics;
    }
    
    public List<Integer> getTopicsQos() {
	return topicsQos;
    }
    
    public ByteBuffer toBuffer() {
	byte fixed = 0x00;
	int length;
	
	fixed |= (TypeValues.indexOf(type) << Shift.TYPE);
	
	if (duplicate) {
	    fixed |= OrMask.DUPLICATE;
	}
	
	if (qos > 0) {
	    fixed |= (qos << Shift.QOS);
	}
	
	if (retain) {
	    fixed |= OrMask.RETAIN;
	}
	
	if (changed) {
	    generate();
	}
	
	length = variable.size();
	if (hasPayload()) {
	    for (ByteBuffer buffer : getPayload()) {
		length += (buffer.limit() - buffer.position());
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
	if (length > 0) {
	    buffer.put(variable.toByteArray());
	}
	
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