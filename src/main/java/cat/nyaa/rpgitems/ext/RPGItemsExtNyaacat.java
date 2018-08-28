package cat.nyaa.rpgitems.ext;


import com.comphenix.packetwrapper.WrapperPlayServerSpawnEntity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.plugin.java.JavaPlugin;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.power.PowerManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.comphenix.protocol.PacketType.Play.Server.*;

public final class RPGItemsExtNyaacat extends JavaPlugin {

    public static boolean hijackEntitySpawn;

    public static ProtocolManager protocolManager;

    public static RPGItemsExtNyaacat plugin;

    public static Set<Integer> hiddenEntities = new HashSet<>();

    public static Map<Integer, Consumer<PacketEvent>> entitySpawnHandler = new HashMap<>();

    public static Map<Integer, Consumer<PacketEvent>> entityMetadataHandler = new HashMap<>();

    public static Cache<Integer, PacketContainer> entitySpawnCache = CacheBuilder.newBuilder()
                                                                                 .concurrencyLevel(2)
                                                                                 .expireAfterWrite(1, TimeUnit.SECONDS)
                                                                                 .build();

    private static final PacketType[] ENTITY_PACKETS = {
            ENTITY_EQUIPMENT, BED, ANIMATION, NAMED_ENTITY_SPAWN,
            COLLECT, SPAWN_ENTITY, SPAWN_ENTITY_LIVING, SPAWN_ENTITY_PAINTING, SPAWN_ENTITY_EXPERIENCE_ORB,
            ENTITY_VELOCITY, REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_TELEPORT, ENTITY_HEAD_ROTATION, ENTITY_STATUS,
            ATTACH_ENTITY, ENTITY_METADATA, ENTITY_EFFECT, REMOVE_ENTITY_EFFECT, BLOCK_BREAK_ANIMATION
    };

    @Override
    public void onLoad() {
        plugin = this;
        super.onLoad();
        protocolManager = ProtocolLibrary.getProtocolManager();
        PowerManager.registerPowers(this, "cat.nyaa.rpgitems.ext.power");
        PowerManager.addDescriptionResolver(this, (power, property) -> {
            if (property == null) {
                @LangKey(skipCheck = true) String powerKey = "power.properties." + power.getKey() + ".main_description";
                return I18n.format(powerKey);
            }
            @LangKey(skipCheck = true) String key = "power.properties." + power.getKey() + "." + property;
            if (I18n.instance.hasKey(key)) {
                return I18n.format(key);
            }
            @LangKey(skipCheck = true) String baseKey = "power.properties.base." + property;
            if (I18n.instance.hasKey(baseKey)) {
                return I18n.format(baseKey);
            }
            return null;
        });

        protocolManager.addPacketListener(
                new PacketAdapter(RPGItemsExtNyaacat.plugin, ListenerPriority.NORMAL, ENTITY_PACKETS) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        int entityID = event.getPacket().getIntegers().read(0);
                        if (hijackEntitySpawn && event.getPacketType() == SPAWN_ENTITY && entitySpawnCache.getIfPresent(entityID) == null) {
                            WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity(event.getPacket());
                            entitySpawnCache.put(entityID, spawnEntity.getHandle().deepClone());
                            event.setCancelled(true);
                            return;
                        }
                        if (hiddenEntities.contains(entityID) || entitySpawnCache.getIfPresent(entityID) != null) {
                            event.setCancelled(true);
                        }
                        if (entitySpawnHandler.containsKey(entityID) && event.getPacketType() == SPAWN_ENTITY) {
                            entitySpawnHandler.get(entityID).accept(event);
                        }
                        if (entityMetadataHandler.containsKey(entityID) && event.getPacketType() == ENTITY_METADATA) {
                            entityMetadataHandler.get(entityID).accept(event);
                        }
                    }
                });
    }

    @Override
    public void onEnable() {
        plugin = this;
        new I18n(this, "en_US");
        getServer().getPluginManager().registerEvents(new EventListener(), this);
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(plugin);
        plugin = null;
    }
}
