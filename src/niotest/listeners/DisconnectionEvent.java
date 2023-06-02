package niotest.listeners;

import niotest.Connection;

import java.time.Instant;
import java.util.EventObject;

public class DisconnectionEvent extends EventObject {
    private final Instant instant;
    public DisconnectionEvent(Connection source, Instant instant) {
        super(source);
        this.instant = instant;
    }

    public Instant getInstant() {
        return instant;
    }

    @Override
    public Connection getSource() {
        return (Connection) super.getSource();
    }
}
