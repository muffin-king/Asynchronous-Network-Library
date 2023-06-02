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
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Client {
    private SocketChannel channel;
    private final ThreadPool threadPool;
    private final Selector selector;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private final EventListenerList listeners;
    private PrintStream debugOutput;
    private ConnectedServer server;
    private boolean isConnected;

    public Client(int port, PrintStream debugOutput) {
        this.debugOutput = debugOutput;

        try {
            channel = SocketChannel.open();
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));

            selector = Selector.open();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        threadPool = new ThreadPool(1);
        threadPool.scheduleTask(new ChannelReader(), 1, 0, TimeUnit.MILLISECONDS);
        readBuffer = ByteBuffer.allocate(1024);
        writeBuffer = ByteBuffer.allocate(1024);
        listeners = new EventListenerList();

        server = null;

        isConnected = false;
    }

    public void connect(String hostname, int port) {
        log("Attempting to connect to " + new InetSocketAddress(hostname, port));
        try {
            channel.connect(new InetSocketAddress(hostname, port));
            channel.finishConnect();
            server = new ConnectedServer(channel,
                    channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE));
            log("Connected to " + server);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        isConnected = true;
    }

    public void disconnect() {
        isConnected = false;

        ConnectedServer server = this.server;
        log("Disconnecting from "+server);
        try {
            this.server.getKey().cancel();
            this.server = null;
            channel.close();
            channel = SocketChannel.open();
            channel.bind(null);
            fireDisconnectionListeners(server);
            log("Disconnected from "+server);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InetSocketAddress getAddress() {
        return new InetSocketAddress(channel.socket().getInetAddress().getHostName(), channel.socket().getLocalPort());
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void finishServersideDisconnection() {
        isConnected = false;

        ConnectedServer server = this.server;
        log("Server " + server + " disconnected");
        try {
            this.server.getKey().cancel();
            this.server = null;
            channel.close();
            channel = SocketChannel.open();
            channel.bind(null);
            fireDisconnectionListeners(server);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(Serializable data, int ID) {
        server.getPacketQueue().add(new Packet(data, server, ID));
    }

    private void clearPacketQueue() {
        for(Packet packet : server.getPacketQueue())
            server.getPacketQueue().remove(packet);
    }

    private void resetReadBuffer() {
        Arrays.fill(readBuffer.array(), (byte) 0);
        readBuffer.position(0);
    }

    private void resetWriteBuffer() {
        Arrays.fill(writeBuffer.array(), (byte) 0);
        writeBuffer.position(0);
    }

    public void addPacketListener(PacketListener listener) {
        listeners.add(PacketListener.class, listener);
    }

    public void addConnectionListener(ConnectionListener listener) {
        listeners.add(ConnectionListener.class, listener);
    }

    private void firePacketListeners(ConnectedServer server, Packet packet) {
        for(PacketListener listener : listeners.getListeners(PacketListener.class))
            listener.onPacketReceive(new PacketEvent(server, Instant.now(), packet));
    }

    private void fireConnectionListeners(ConnectedServer server) {
        for(ConnectionListener listener : listeners.getListeners(ConnectionListener.class))
            listener.onConnection(new ConnectionEvent(server, Instant.now()));
    }

    private void fireDisconnectionListeners(ConnectedServer server) {
        for(ConnectionListener listener : listeners.getListeners(ConnectionListener.class))
            listener.onDisconnection(new DisconnectionEvent(server, Instant.now()));
    }

    private class ChannelReader implements Runnable {
        @Override
        public void run() {
            try {
                if(server != null) {
                    selector.select();
                    Set<SelectionKey> keySet = selector.selectedKeys();

                    for (SelectionKey key : keySet) {
                        if(key.isValid() && key.isReadable()) {
                            int bytes = channel.read(readBuffer);

                            if(bytes == -1)
                                finishServersideDisconnection();
                            else {

                                Packet packet = SerializationUtils.deserialize(readBuffer.array());

                                firePacketListeners(server, packet);
                            }
                            resetReadBuffer();
                        }
                        if(key.isValid() && key.isWritable()) {
                            for (Packet packet : server.getPacketQueue()) {
                                server.getPacketQueue().remove();
                                writeBuffer.put(SerializationUtils.serialize(packet));
                                writeBuffer.position(0);
                                try {
                                    channel.write(writeBuffer);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                resetWriteBuffer();
                            }
                        }
                        keySet.remove(key);
                    }
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
            debugOutput.println("[Client INFO] " + message);
    }
}
