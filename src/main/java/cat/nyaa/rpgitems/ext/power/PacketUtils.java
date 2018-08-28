package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.utils.ReflectionUtils;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public class PacketUtils {
    public static WrappedDataWatcher watcher(Map<Integer, Object> data) {
        WrappedDataWatcher watcher = new WrappedDataWatcher();
        for (Map.Entry<Integer, Object> e : data.entrySet()) {
            if (e.getValue() instanceof org.bukkit.inventory.ItemStack) {
                try {
                    Class<?> craftItemStack = ReflectionUtils.getOBCClass("inventory.CraftItemStack");
                    Method asNMSCopy = ReflectionUtils.getMethod(craftItemStack, "asNMSCopy", ItemStack.class);
                    watcher.setObject(e.getKey(), WrappedDataWatcher.Registry.getItemStackSerializer(false), asNMSCopy.invoke(null, e.getValue()));
                } catch (IllegalAccessException | InvocationTargetException e1) {
                    e1.printStackTrace();
                }
                continue;
            }
            watcher.setObject(e.getKey(), WrappedDataWatcher.Registry.get(e.getValue().getClass()), e.getValue());
        }
        return watcher;
    }
}
