package niotest;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class Packet {
    private final Serializable data;
    private final InetSocketAddress address;
    Packet(Serializable data, Connection connection) {
        this.data = data;
        if(connection != null)
            address = connection.getAddress();
        else
            address = null;
    }

    public Serializable getData() {
        return data;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    enum InternalPacketSignal implements Serializable {
        DISCONNECT
    }
}
