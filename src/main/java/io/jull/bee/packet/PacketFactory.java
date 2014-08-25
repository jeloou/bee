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
	int id = packet.getId();
	return new Packet(Packet.Type.PUBACK, false, (short)0, false, Packet.getShortBytes(id));
    }
    
    public static Packet createPubrec(Packet packet) {
	int id = packet.getId();
	return new Packet(Packet.Type.PUBREC, false, (short)0, false, Packet.getShortBytes(id));
    }
    
    public static Packet createPubrel(Packet packet) {
	int id = packet.getId();
	return new Packet(Packet.Type.PUBREL, false, (short)0, false, Packet.getShortBytes(id));
    }
    
    public static Packet createPubcomp(Packet packet) {
	int id = packet.getId();
	return new Packet(Packet.Type.PUBCOMP, false, (short)0, false, Packet.getShortBytes(id));
    }
    
    public static Packet createSuback(Packet packet) {
	int id = packet.getId();
	
	List<Integer> topicsQos = packet.getTopicsQos();
	byte[] payload = new byte[topicsQos.size()];
	
	int i = 0;
	for (int qos : topicsQos) {
	    payload[i++] |= (byte)(qos & 0xff);
	}
	
	return new Packet(Packet.Type.SUBACK, false, (short)0, false, Packet.getShortBytes(id), payload);
    }
    
    public static Packet createUnsuback(Packet packet) {
	int id = packet.getId();
	return new Packet(Packet.Type.UNSUBACK, false, (short)0, false, Packet.getShortBytes(id));
    }
    
    public static Packet createPingResp() {
	return new Packet(Packet.Type.PINGRESP, false, (short)0, false);
    }
}