package niotest.listeners;

import java.util.EventListener;

public interface ConnectionListener extends EventListener {
    void onConnection(ConnectionEvent e);
    void onDisconnection(DisconnectionEvent e);
}
