package io.jull.bee.server;

import io.jull.bee.client.Client;

public interface ServerListener {
    public void onClientConnect(Client client);
    public void onClientDisconnect(Client client);
    public void onClientWriteDemand(Client client);
}
