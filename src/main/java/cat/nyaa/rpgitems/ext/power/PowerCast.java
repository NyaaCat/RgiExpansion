package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.rpgitems.ext.I18n;
import cat.nyaa.rpgitems.ext.RgiExpansion;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import think.rpgitems.power.PowerMeta;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.PowerRightClick;

@PowerMeta(immutableTrigger = true)
public class PowerCast extends BasePower implements PowerRightClick {
    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack itemStack, Block block, PlayerInteractEvent playerInteractEvent) {
        new Message("Hello world").send(player);
        return PowerResult.ok();
    }

    @Override
    public String getName() {
        return "cast";
    }

    @Override
    public NamespacedKey getNamespacedKey() {
        return new NamespacedKey(RgiExpansion.plugin, this.getName());
    }

    @Override
    public String displayText() {
        return I18n.format("power.cast");
    }
}
