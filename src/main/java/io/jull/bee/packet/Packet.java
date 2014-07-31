package io.jull.bee.packet;

import java.nio.ByteBuffer;

public class Packet implements PacketInterface {
    private TYPES type = TYPES.CONNECT;
    
    public void parse(ByteBuffer buffer) {
	
    }
    
    public TYPES getType() {
	return type;
    }
    
    public boolean isComplete() {
	return true;
    }
    
    public boolean isValid() {
	return true;
    }
}