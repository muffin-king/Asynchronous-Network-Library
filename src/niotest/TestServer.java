package niotest;

import niotest.listeners.PacketEvent;
import niotest.listeners.PacketListener;

public class TestServer {
    Server server;
    public TestServer() {
        server = new Server(8081, System.out, 2);
        server.addPacketListener(new PacketEchoer());
    }

    public static void main(String[] args) {
        new TestServer();
    }

    private class PacketEchoer implements PacketListener {
        @Override
        public void onPacketReceive(PacketEvent e) {
            server.send(e.getPacket().getData(), (ConnectedClient) e.getSource());
            System.out.println(e.getPacket().getData());
        }
    }
}
