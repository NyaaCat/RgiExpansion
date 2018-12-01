package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.utils.ItemStackUtils;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class PacketUtils {
    public static WrappedDataWatcher watcher(Map<Integer, Object> data) {
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        for (Map.Entry<Integer, Object> e : data.entrySet()) {
            if (e.getValue() instanceof ItemStack) {
                watcher.setObject(e.getKey(), WrappedDataWatcher.Registry.getItemStackSerializer(false), ItemStackUtils.asNMSCopy((ItemStack) e.getValue()));
                continue;
            }
            watcher.setObject(e.getKey(), WrappedDataWatcher.Registry.get(e.getValue().getClass()), e.getValue());
        }
        return watcher;
    }
}
