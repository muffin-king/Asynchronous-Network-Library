package niotest;

import niotest.listeners.PacketEvent;
import niotest.listeners.PacketListener;

public class TestClient {
    public static void main(String[] args) throws InterruptedException {
        Client client = new Client(0, System.out);

        client.addPacketListener(new PacketPrinter());

        client.connect("localhost", 8081);
        client.send("Cookie Monster");

        Thread.sleep(1000);
        client.disconnect();
    }

    private static class PacketPrinter implements PacketListener {
        @Override
        public void onPacketReceive(PacketEvent e) {
            System.out.println(e.getPacket().getData());
        }
    }
}
