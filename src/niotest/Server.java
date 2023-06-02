package niotest;

import betterthreadpool.ThreadPool;
import niotest.listeners.*;
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
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final ThreadPool threadPool;
    private final Map<Integer, ConnectedClient> clients;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private static final Random RNG = new Random();
    private PrintStream debugOutput;
    private final EventListenerList listeners;

    public Server(PrintStream debugOutput, int threads) {
        clients = new ConcurrentHashMap<>();

        threadPool = new ThreadPool(threads);

        this.debugOutput = debugOutput;

        readBuffer = ByteBuffer.allocate(1024);
        writeBuffer = ByteBuffer.allocate(1024);

        listeners = new EventListenerList();
    }

    public void open(int port) {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));

            serverChannel.configureBlocking(false);

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        threadPool.scheduleTask(new ServerTask(), 1, 0, TimeUnit.MILLISECONDS);
    }

    public void close() {

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
        listeners.add(PacketListener.class, listener);
    }

    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(ConnectionListener.class, listener);
    }

    private void firePacketListeners(ConnectedClient client, Packet packet) {
        for(PacketListener listener : listeners.getListeners(PacketListener.class))
            listener.onPacketReceive(new PacketEvent(client, Instant.now(), packet));
    }

    private void fireConnectionListeners(ConnectedClient client) {
        for(ConnectionListener listener : listeners.getListeners(ConnectionListener.class))
            listener.onConnection(new ConnectionEvent(client, Instant.now()));
    }

    private void fireDisconnectionListener(ConnectedClient client) {
        for(ConnectionListener listener : listeners.getListeners(ConnectionListener.class))
            listener.onDisconnection(new DisconnectionEvent(client, Instant.now()));
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
        log("Disconnecting "+client);
        try {
            send(Packet.InternalPacketSignal.DISCONNECT, client);
            client.getKey().cancel();
            clients.values().remove(client);
            client.getChannel().close();
            fireDisconnectionListener(client);
            log(client + " was disconnected");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void remove(ConnectedClient client) {
        log(client + " disconnected");
        try {
            clients.values().remove(client);
            client.getKey().cancel();
            client.getChannel().close();
            fireDisconnectionListener(client);
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
                    if(key.isValid() && key.isAcceptable()) {
                        SocketChannel channel = serverChannel.accept();
                        channel.configureBlocking(false);

                        int id = RNG.nextInt();
                        clients.put(id, new ConnectedClient(id, channel,
                                channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE)));
                        log("Client from "+clients.get(id)+" connected, assigned ID "+id);

                        fireConnectionListeners(clients.get(id));
                    } else if(key.isValid() && key.isReadable()) {
                        SocketChannel channel = (SocketChannel) key.channel();
                        ConnectedClient client = getClientByChannel(channel);
                        int byteCount = channel.read(readBuffer);

                        if(byteCount == -1)
                            remove(client);
                        else {

                            Serializable data = SerializationUtils.deserialize(readBuffer.array());

                            firePacketListeners(client, new Packet(data, client));
                        }
                        resetReadBuffer();
                    }
                    if(key.isValid() && key.isWritable()) {
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

    public PrintStream getDebugOutput() {
        return debugOutput;
    }

    public void setDebugOutput(PrintStream debugOutput) {
        this.debugOutput = debugOutput;
    }

    private void log(String message) {
        debugOutput.println("[Server INFO] " + message);
    }
}
