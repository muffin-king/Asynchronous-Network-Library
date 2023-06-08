package asyncnetworklibrary;

import java.io.Serializable;
import java.net.InetSocketAddress;

public class Packet implements Serializable {
    private final Serializable data;
    private transient final InetSocketAddress address;
    private final int ID;
    Packet(Serializable data, Connection connection, int ID) {
        this.data = data;
        if(connection != null)
            address = connection.getAddress();
        else
            address = null;
        this.ID = ID;
    }

    public Serializable getData() {
        return data;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public int getID() {
        return ID;
    }
}
