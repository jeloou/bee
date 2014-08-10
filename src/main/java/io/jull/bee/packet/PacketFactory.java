package io.jull.bee.packet;

public class PacketFactory {
    public static Packet createConnack(int returnCode) {
	byte[] bytes = new byte[2];
	
	bytes[1] |= returnCode;
	return new Packet(Packet.Type.CONNACK, false, (short)0, false, bytes);
    }
    
    public static Packet createConnack() {
	return createConnack(Packet.ReturnCode.ACCEPTED);
    }
    
}