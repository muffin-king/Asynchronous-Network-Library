package niotest;

import java.net.InetSocketAddress;

public interface Connection {
    InetSocketAddress getAddress();

    default String getHostname() {
        return getAddress().getHostName();
    }
    default int getPort() {
        return getAddress().getPort();
    }

    int getID();

    default boolean same(Connection connection) {
        return connection.getPort() == getPort() && connection.getHostname().equals(getHostname());
    }
}
