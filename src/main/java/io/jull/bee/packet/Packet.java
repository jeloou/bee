package io.jull.bee.packet;

import java.util.Collection;
import java.util.ArrayList;

import java.nio.ByteBuffer;

public class Packet implements PacketInterface {
    private Type type;
    
    private boolean duplicate = false;
    private boolean retain = false;
    private int qos = 0;
    
    private int remaining = 0;
    private Collection<ByteBuffer> buffers;
    
    private boolean complete = false;
    private boolean valid = false;
    
    public Packet() {
	buffers = new ArrayList<ByteBuffer>();
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