package io.jull.bee.packet;

import java.util.List;
import java.util.Arrays;

public interface PacketInterface {
    public enum Type {
	RESERVED, CONNECT, CONNACK, PUBLISH, PUBACK,
        PUBREC, PUBREL, PUBCOMP, SUBSCRIBE,
        SUBACK, UNSUBSCRIBE, UNSUBACK, PINGREQ,
	PINRESP, DISCONNECT
    }
    
    public static List<Type> TypeValues = Arrays.asList(Type.values());
    
    public class Mask {
	public static byte DUPLICATE = 0x01;
	public static byte RETAIN = 0x01;
	public static byte QOS = 0x03;
    }
    
    public class OrMask {
	public static byte DUPLICATE = 0x08;
	public static byte RETAIN = 0x01;
    }
    
    public class Shift {
	public static byte TYPE = 0X04;
	public static byte QOS = 0x01;
	public static byte DUPLICATE = 0x03;
    }

    public class ReturnCode {
	public static byte ACCEPTED = 0x00;
	public static byte UNACCEPTABLE_VERSION = 0x01;
	public static byte ID_REJECTED = 0x02;
	public static byte SERVER_UNAVAILABLE = 0x03;
	public static byte BAD_CREDENTIALS = 0x04;
	public static byte NOT_AUTHORIZED = 0x05;
    }
}