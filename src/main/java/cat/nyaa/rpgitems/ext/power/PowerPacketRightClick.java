package cat.nyaa.rpgitems.ext.power;

import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.power.Power;
import think.rpgitems.power.PowerResult;

public interface PowerPacketRightClick extends Power {
    /**
     * Calls when {@code player} using {@code stack} knockbacked
     *
     * @param player Player
     * @param stack  ItemStack of this RPGItem
     * @param event  Event that triggered this power
     * @return PowerResult
     */
    PowerResult<Void> packetRightClick(Player player, ItemStack stack, PacketEvent event);
}
