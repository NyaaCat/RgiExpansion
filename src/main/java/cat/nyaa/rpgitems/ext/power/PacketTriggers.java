package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.rpgitems.ext.WrappedPacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.Trigger;

public class PacketTriggers {
    public static final Trigger<WrappedPacketEvent, PowerPacketLeftClick, Void, Void> NYAAEXT_PACKET_LEFT_CLICK = new Trigger<WrappedPacketEvent, PowerPacketLeftClick, Void, Void>("NYAAEXT_PACKET_LEFT_CLICK", WrappedPacketEvent.class, PowerPacketLeftClick.class, Void.class, Void.class) {
        @Override
        public PowerResult<Void> run(PowerPacketLeftClick power, Player player, ItemStack i, WrappedPacketEvent wrap) {
            return power.packetLeftClick(player, i, wrap.event);
        }
    };

    public static final Trigger<WrappedPacketEvent, PowerPacketRightClick, Void, Void> NYAAEXT_PACKET_RIGHT_CLICK = new Trigger<WrappedPacketEvent, PowerPacketRightClick, Void, Void>("NYAAEXT_PACKET_RIGHT_CLICK", WrappedPacketEvent.class, PowerPacketRightClick.class, Void.class, Void.class) {
        @Override
        public PowerResult<Void> run(PowerPacketRightClick power, Player player, ItemStack i, WrappedPacketEvent wrap) {
            return power.packetRightClick(player, i, wrap.event);
        }
    };
}
