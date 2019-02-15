package cat.nyaa.rpgitems.ext;

import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class WrappedPacketEvent extends Event {
    private static final HandlerList handlers = null;

    WrappedPacketEvent(PacketEvent event){
        this.event = event;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    public final PacketEvent event;
}
