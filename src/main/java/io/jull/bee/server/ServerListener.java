package io.jull.bee.server;

import io.jull.bee.client.Client;
import io.jull.bee.packet.Packet;

public interface ServerListener {
    public void onClientConnect(Client client, Packet packet);
    public void onClientDisconnect(Client client);
    public void onClientWriteDemand(Client client);
}
