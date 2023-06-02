package niotest;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectedServer implements Connection {
    private final InetSocketAddress address;
    private final Deque<Packet> packetQueue;
    private final SelectionKey key;
    ConnectedServer(SocketChannel client, SelectionKey key) {
        this.address = new InetSocketAddress(client.socket().getInetAddress().getHostName(), client.socket().getPort());
        this.key = key;
        packetQueue = new ConcurrentLinkedDeque<>();
    }
    @Override
    public InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public int getID() {
        return 0;
    }

    Deque<Packet> getPacketQueue() {
        return packetQueue;
    }

    SelectionKey getKey() {
        return key;
    }

    public String toString() {
        return "ConnectedServer[address="+getAddress()+"]";
    }
}
