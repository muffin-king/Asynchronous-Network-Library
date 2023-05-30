package niotest.listeners;

import niotest.Connection;
import niotest.Packet;

import java.time.Instant;
import java.util.EventObject;

public class PacketEvent extends EventObject {
    private final Instant instant;
    private final Packet packet;
    public PacketEvent(Connection source, Instant instant, Packet packet) {
        super(source);
        this.instant = instant;
        this.packet = packet;
    }

    public Instant getInstant() {
        return instant;
    }

    public Packet getPacket() {
        return packet;
    }

    @Override
    public Connection getSource() {
        return (Connection) super.getSource();
    }
}
