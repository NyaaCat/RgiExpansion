package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.rpgitems.ext.RPGItemsExtNyaacat;
import org.bukkit.NamespacedKey;

/**
 * Base class containing common methods and fields.
 */
public abstract class BasePower extends think.rpgitems.power.impl.BasePower {
    @Override
    public NamespacedKey getNamespacedKey() {
        return new NamespacedKey(RPGItemsExtNyaacat.plugin, getName());
    }
}
