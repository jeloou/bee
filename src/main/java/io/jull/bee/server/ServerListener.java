package io.jull.bee.server;

import java.nio.ByteBuffer;

import io.jull.bee.client.Client;
import io.jull.bee.packet.Packet;

public interface ServerListener {
    public void onClientConnect(Client client, Packet packet);
    public void onClientDisconnect(Client client);
    public void onClientSubscribe(Client client, String topic);
    public void onClientPublish(Client client, String topic, ByteBuffer[] payload);
    public void onClientWriteDemand(Client client);
}
