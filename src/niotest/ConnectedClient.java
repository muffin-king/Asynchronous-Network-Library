package niotest;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConnectedClient implements Connection {
    private final int ID;
    private final SocketChannel channel;
    private final Deque<Packet> packetQueue;
    private final SelectionKey key;
    ConnectedClient(int ID, SocketChannel channel, SelectionKey key) {
        this.ID = ID;
        this.channel = channel;
        packetQueue = new ConcurrentLinkedDeque<>();
        this.key = key;
    }

    public SocketChannel getChannel() {
        return channel;
    }

    @Override
    public InetSocketAddress getAddress() {
        return new InetSocketAddress(channel.socket().getLocalAddress().getHostName(), channel.socket().getPort());
    }

    @Override
    public int getID() {
        return ID;
    }

    Deque<Packet> getPacketQueue() {
        return packetQueue;
    }

    SelectionKey getKey() {
        return key;
    }
}
