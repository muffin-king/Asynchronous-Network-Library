package asyncnetworklibrary;

import betterthreadpool.ThreadPool;
import betterthreadpool.ThreadPoolTask;
import asyncnetworklibrary.listeners.*;
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
    private ThreadPoolTask serverTask;
    private boolean isOpen;

    public Server(PrintStream debugOutput, int threads) {
        clients = new ConcurrentHashMap<>();

        threadPool = new ThreadPool(threads);

        this.debugOutput = debugOutput;

        readBuffer = ByteBuffer.allocate(1024);
        writeBuffer = ByteBuffer.allocate(1024);

        listeners = new EventListenerList();

        serverChannel = null;
        selector = null;
        serverTask = null;

        isOpen = false;
    }

    public void open(int port) {
        log("Opening server on port "+port);
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));

            serverChannel.configureBlocking(false);

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        serverTask = threadPool.scheduleTask(new ServerTask(), 1, 0, TimeUnit.MILLISECONDS);

        log("Server successfully opened on port "+port);

        isOpen = true;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void close() {
        isOpen = false;

        log("Closing server");
        serverTask.cancel();
        serverTask = null;

        for(ConnectedClient client : clients.values()) {
            disconnect(client);
        }
        try {
            selector.close();
            selector = null;

            serverChannel.close();
            serverChannel = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        log("Server closed");
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(serverChannel.socket().getInetAddress().getHostName(), serverChannel.socket().getLocalPort());
    }

    public void send(Serializable data, ConnectedClient client, int ID) {
        client.getPacketQueue().add(new Packet(data, client, ID));
    }

    public void send(Serializable data, int clientID, int ID) {
        clients.get(clientID).getPacketQueue().add(new Packet(data, clients.get(clientID), ID));
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

    public ConnectedClient[] getClients() {
        return clients.values().toArray(new ConnectedClient[0]);
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

                            Packet packet = SerializationUtils.deserialize(readBuffer.array());

                            firePacketListeners(client, packet);
                        }
                        resetReadBuffer();
                    }
                    if(key.isValid() && key.isWritable()) {
                        ConnectedClient client = getClientByChannel((SocketChannel) key.channel());
                        for (Packet packet : client.getPacketQueue()) {
                            client.getPacketQueue().remove();
                            byte[] data = SerializationUtils.serialize(packet);
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
        if(debugOutput != null)
            debugOutput.println("[Server INFO] " + message);
    }
}
