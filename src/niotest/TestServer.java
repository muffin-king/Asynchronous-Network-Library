package niotest;

import niotest.listeners.*;

public class TestServer {
    Server server;
    public TestServer() {
        server = new Server(System.out, 2);
        server.open(8081);
        server.addPacketListener(new PacketEchoer());
        server.addConnectionListener(new PacketEchoer());
    }

    public static void main(String[] args) {
        new TestServer();
    }

    private class PacketEchoer implements PacketListener, ConnectionListener {
        @Override
        public void onPacketReceive(PacketEvent e) {
            server.send(e.getPacket().getData(), (ConnectedClient) e.getSource(), 1);
            System.out.println(e.getPacket().getData());
        }

        @Override
        public void onConnection(ConnectionEvent e) {
            server.close();
            server.open(8081);
        }

        @Override
        public void onDisconnection(DisconnectionEvent e) {

        }
    }
}
