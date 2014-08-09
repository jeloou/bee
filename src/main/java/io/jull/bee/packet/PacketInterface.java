package io.jull.bee.packet;

public interface PacketInterface {
    public enum Type {
	CONNECT, CONNACK, PUBLISH, PUBACK,
        PUBREC, PUBREL, PUBCOMP, SUBSCRIBE,
        SUBACK, UNSUBSCRIBE, UNSUBACK, PINGREQ,
        PINRESP, DISCONNECT
    }
    
    public static Type[] TypeValues = Type.values();
}