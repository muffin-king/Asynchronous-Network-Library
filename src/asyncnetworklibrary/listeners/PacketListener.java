package asyncnetworklibrary.listeners;

import java.util.EventListener;

public interface PacketListener extends EventListener {
    void onPacketReceive(PacketEvent e);
}
