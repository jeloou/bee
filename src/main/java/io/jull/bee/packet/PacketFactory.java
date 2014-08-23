package io.jull.bee.packet;

import java.util.List;

public class PacketFactory {
    public static Packet createConnack(int returnCode) {
	byte[] bytes = new byte[2];
	
	bytes[1] |= returnCode;
	return new Packet(Packet.Type.CONNACK, false, (short)0, false, bytes);
    }
    
    public static Packet createConnack() {
	return createConnack(Packet.ReturnCode.ACCEPTED);
    }
    
    public static Packet createPuback(Packet packet) {
	byte[] variable = new byte[2];
	int id = packet.getId();
	
	variable[0] |= (byte)((id >> 8) & 0xff);
	variable[1] |= (byte)(id & 0xff);
	
	return new Packet(Packet.Type.PUBACK, false, (short)0, false, variable);
    }
    
    public static Packet createSuback(Packet packet) {
	byte[] variable = new byte[2];
	int id = packet.getId();
	
	variable[0] |= (byte)((id >> 8) & 0xff);
	variable[1] |= (byte)(id & 0xff);
	
	List<Integer> topicsQos = packet.getTopicsQos();
	byte[] payload = new byte[topicsQos.size()];
	
	int i = 0;
	for (int qos : topicsQos) {
	    payload[i++] |= (byte)(qos & 0xff);
	}
	
	return new Packet(Packet.Type.SUBACK, false, (short)0, false, variable, payload);
    }
    
    public static Packet createUnsuback(Packet packet) {
	byte[] variable = new byte[2];
	int id = packet.getId();
	
	variable[0] |= (byte)((id >> 8) & 0xff);
	variable[1] |= (byte)(id & 0xff);
	
	return new Packet(Packet.Type.UNSUBACK, false, (short)0, false, variable);
    }
    
    public static Packet createPingResp() {
	return new Packet(Packet.Type.PINGRESP, false, (short)0, false);
    }
}