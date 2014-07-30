package io.jull.bee.client;


public interface ClientInterface {
    public enum STATUSES {
	NOT_CONNECTED, CONNECTING, CONNECTED, CLOSING, CLOSED
    }
}