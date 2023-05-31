package niotest;

import betterthreadpool.ThreadPool;
import niotest.listeners.ConnectionEvent;
import niotest.listeners.ConnectionListener;
import niotest.listeners.PacketEvent;
import niotest.listeners.PacketListener;
import org.apache.commons.lang3.SerializationUtils;

import javax.swing.event.EventListenerList;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Server {
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final ThreadPool threadPool;
    private final Map<Integer, ConnectedClient> clients;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private static final Random RNG = new Random();
    private PrintStream logStream;
    private final EventListenerList events;

    public Server(int port, PrintStream logStream, int threads) {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));

            serverChannel.configureBlocking(false);

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            clients = new ConcurrentHashMap<>();

            threadPool = new ThreadPool(threads);

            this.logStream = logStream;

            readBuffer = ByteBuffer.allocate(1024);
            writeBuffer = ByteBuffer.allocate(1024);

            events = new EventListenerList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        threadPool.scheduleTask(new ServerTask(), 1, 0, TimeUnit.MILLISECONDS);
    }

    public void send(Serializable data, ConnectedClient client) {
        client.getPacketQueue().add(new Packet(data, client));
    }

    public void send(Serializable data, int ID) {
        clients.get(ID).getPacketQueue().add(new Packet(data, clients.get(ID)));
    }

    public ConnectedClient getClientByAddress(InetSocketAddress address) {
        for(ConnectedClient client : clients.values()) {
            if(client.getAddress().equals(address))
                return client;
        }
        throw new NoSuchElementException("No such client with address "+address);
    }

    public void addPacketListener(PacketListener listener) {
        events.add(PacketListener.class, listener);
    }

    public void addConnectionListener(ConnectionListener listener) {
        events.add(ConnectionListener.class, listener);
    }

    private void firePacketListeners(ConnectedClient client, Packet packet) {
        for(PacketListener listener : events.getListeners(PacketListener.class))
            listener.onPacketReceive(new PacketEvent(client, Instant.now(), packet));
    }

    private void fireConnectionListeners(ConnectedClient client) {
        for(ConnectionListener listener : events.getListeners(ConnectionListener.class))
            listener.onConnection(new ConnectionEvent(client, Instant.now()));
    }

    private ConnectedClient getClientByChannel(SocketChannel channel) {
        for(ConnectedClient client : clients.values()) {
            if(client.getChannel() == channel)
                return client;
        }
        throw new NoSuchElementException("No such client with channel "+channel);
    }

    private void resetReadBuffer() {
        Arrays.fill(readBuffer.array(), (byte) 0);
        readBuffer.position(0);
    }

    private void resetWriteBuffer() {
        Arrays.fill(writeBuffer.array(), (byte) 0);
        writeBuffer.position(0);
    }

    public void disconnect(ConnectedClient client) {
        try {
            send(Packet.InternalPacketSignal.DISCONNECT, client);
            client.getKey().cancel();
            clients.values().remove(client);
            client.getChannel().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log(client.getAddress() + " was disconnected");
    }

    private void remove(ConnectedClient client) {
        try {
            clients.values().remove(client);
            client.getKey().cancel();
            client.getChannel().close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class ServerTask implements Runnable {
        @Override
        public void run() {
            try {
                selector.select();
                Set<SelectionKey> keySet = selector.selectedKeys();
                for (SelectionKey key : keySet) {
                    if(key.isAcceptable()) {
                        SocketChannel channel = serverChannel.accept();
                        channel.configureBlocking(false);

                        int id = RNG.nextInt();
                        clients.put(id, new ConnectedClient(id, channel,
                                channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)));
                        log("Client from "+clients.get(id).getAddress()+" connected, assigned ID "+id);

                        fireConnectionListeners(clients.get(id));
                    }
                    if(key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ConnectedClient client = getClientByChannel(channel);
                        int byteCount = channel.read(readBuffer);

                        Serializable data = SerializationUtils.deserialize(readBuffer.array());

                        if(data.equals(Packet.InternalPacketSignal.DISCONNECT)) {
                            remove(client);
                            log(client.getAddress() + " disconnected");
                            break;
                        } else
                            firePacketListeners(client, new Packet(data, client));

                        resetReadBuffer();
                    }
                    if(key.isWritable()) {
                        ConnectedClient client = getClientByChannel((SocketChannel) key.channel());
                        for (Packet packet : client.getPacketQueue()) {
                            client.getPacketQueue().remove();
                            byte[] data = SerializationUtils.serialize(packet.getData());
                            writeBuffer.put(data);
                            writeBuffer.position(0);
                            client.getChannel().write(writeBuffer);
                            resetWriteBuffer();
                        }
                    }
                    keySet.remove(key);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public PrintStream getLogStream() {
        return logStream;
    }

    public void setLogStream(PrintStream logStream) {
        this.logStream = logStream;
    }

    private void log(String message) {
        logStream.println("[Server INFO] " + message);
    }
}
