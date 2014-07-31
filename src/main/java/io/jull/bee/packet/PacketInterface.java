package io.jull.bee.packet;

public interface PacketInterface {
    public enum TYPES {
	CONNECT, DISCONNECT, SUBSCRIBE, UNSUBSCRIBE, PUBLISH
    }
}