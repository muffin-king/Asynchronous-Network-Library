package niotest;

import betterthreadpool.ThreadPool;
import niotest.listeners.ConnectionEvent;
import niotest.listeners.ConnectionListener;
import niotest.listeners.PacketEvent;
import niotest.listeners.PacketListener;
import org.apache.commons.lang3.SerializationUtils;

import javax.swing.event.EventListenerList;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class Client {
    private SocketChannel channel;
    private final ThreadPool threadPool;
    private final Selector selector;
    private final ByteBuffer readBuffer;
    private final ByteBuffer writeBuffer;
    private final EventListenerList listeners;
    private ConnectedServer server;

    public Client(int port) {
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
    }

    public void connect(String hostname, int port) {
        try {
            channel.connect(new InetSocketAddress(hostname, port));
            channel.finishConnect();
            server = new ConnectedServer(channel,
                    channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void disconnect() {
        try {
            send(Packet.InternalPacketSignal.DISCONNECT);
            server.getKey().cancel();
            server = null;
            channel.close();
            channel = SocketChannel.open();
            channel.bind(null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void finishServersideDisconnection() {
        try {
            server.getKey().cancel();
            server = null;
            channel.close();
            channel = SocketChannel.open();
            channel.bind(null);
        } catch(IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println("Server disconnected");
    }

    public void send(Serializable data) {
        server.getPacketQueue().add(new Packet(data, server));
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

    private class ChannelReader implements Runnable {
        @Override
        public void run() {
            try {
                if(server != null) {
                    selector.select();
                    Set<SelectionKey> keys = selector.selectedKeys();

                    for (SelectionKey key : keys) {
                        if(key.isReadable()) {
                            channel.read(readBuffer);

                            Serializable data = SerializationUtils.deserialize(readBuffer.array());

                            if (data.equals(Packet.InternalPacketSignal.DISCONNECT))
                                finishServersideDisconnection();
                            else
                                firePacketListeners(server, new Packet(data, server));

                            resetReadBuffer();
                        }
                        if(key.isWritable()) {
                            for (Packet packet : server.getPacketQueue()) {
                                server.getPacketQueue().remove();
                                writeBuffer.put(SerializationUtils.serialize(packet.getData()));
                                writeBuffer.position(0);
                                try {
                                    channel.write(writeBuffer);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                resetWriteBuffer();
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
