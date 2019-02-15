package cat.nyaa.rpgitems.ext;


import cat.nyaa.rpgitems.ext.power.PowerCastPoint;
import com.comphenix.packetwrapper.*;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.item.ItemManager;
import think.rpgitems.item.RPGItem;
import think.rpgitems.power.PowerManager;
import think.rpgitems.power.Trigger;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static cat.nyaa.rpgitems.ext.power.PacketTriggers.NYAAEXT_PACKET_LEFT_CLICK;
import static cat.nyaa.rpgitems.ext.power.PacketTriggers.NYAAEXT_PACKET_RIGHT_CLICK;
import static com.comphenix.protocol.PacketType.Play.Client.*;
import static com.comphenix.protocol.PacketType.Play.Server.*;
import static org.bukkit.GameMode.CREATIVE;

public final class RPGItemsExtNyaacat extends JavaPlugin {

    public static Set<PacketType> hijacked = new HashSet<>();

    public static void hijack(PacketType packetType) {
        hijacked.add(packetType);
    }

    public static void stopHijack(PacketType packetType) {
        hijacked.remove(packetType);
    }

    public static ProtocolManager protocolManager;

    public static RPGItemsExtNyaacat plugin;

    public static Set<Integer> hiddenEntities = new HashSet<>();

    public static Map<Integer, Consumer<PacketEvent>> entitySpawnHandler = new HashMap<>();

    public static Map<Integer, Consumer<PacketEvent>> entityMetadataHandler = new HashMap<>();

    public static Cache<Integer, PacketContainer> entitySpawnCache = CacheBuilder.newBuilder()
                                                                                 .concurrencyLevel(2)
                                                                                 .expireAfterWrite(1, TimeUnit.SECONDS)
                                                                                 .build();
    public static ConcurrentMap<UUID, PlayerState> frozenPlayerState = new ConcurrentHashMap<>();


    private static final PacketType[] ENTITY_PACKETS = {
            ENTITY_EQUIPMENT, BED, ANIMATION, NAMED_ENTITY_SPAWN,
            COLLECT, SPAWN_ENTITY, SPAWN_ENTITY_LIVING, SPAWN_ENTITY_PAINTING, SPAWN_ENTITY_EXPERIENCE_ORB,
            ENTITY_VELOCITY, REL_ENTITY_MOVE, ENTITY_LOOK, ENTITY_TELEPORT, ENTITY_HEAD_ROTATION, ENTITY_STATUS,
            ATTACH_ENTITY, ENTITY_METADATA, ENTITY_EFFECT, REMOVE_ENTITY_EFFECT, BLOCK_BREAK_ANIMATION
    };

    private static final PacketType[] PLAYER_ACTION_PACKETS = {
            USE_ENTITY, FLYING, PacketType.Play.Client.POSITION, POSITION_LOOK, BOAT_MOVE,
            PacketType.Play.Client.ABILITIES, BLOCK_DIG, ENTITY_ACTION, STEER_VEHICLE, PacketType.Play.Client.HELD_ITEM_SLOT,
            ARM_ANIMATION, USE_ITEM, BLOCK_PLACE,
    };

    private static final PacketType[] PLAYER_CLICK_PACKETS = {
            BLOCK_DIG, ARM_ANIMATION, BLOCK_PLACE, USE_ITEM, USE_ENTITY
    };

    private PacketAdapter entityPacketAdapter;

    private PacketAdapter playerActionPacketAdapter;

    private PacketAdapter playerClickAdapter;

    public static boolean unfreeze(Player player) {
        PlayerState frozenState = frozenPlayerState.remove(player.getUniqueId());
        if (frozenState == null) {
            return false;
        }
        Runnable runnable = () -> {
            player.setWalkSpeed(frozenState.walkSpeed);
            player.setFlySpeed(frozenState.flySpeed);
            long current = System.nanoTime() / 1000000;
            long deltaMillis = current - frozenState.startTime;
            long jumpLeft = frozenState.jumpDuration - deltaMillis / 50;
            if (jumpLeft > 0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, (int) jumpLeft, frozenState.jumpAmplifier), true);
            } else {
                player.removePotionEffect(PotionEffectType.JUMP);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
        return true;
    }

    public static void freeze(Player player, boolean allowUpdatePosition) {
        if (!allowUpdatePosition) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setWalkSpeed(0);
                player.setFlySpeed(0);
                player.setSneaking(false);
                PotionEffect effect = new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 128, true, false);
                player.addPotionEffect(effect, true);
            });
        }
        if (frozenPlayerState.containsKey(player.getUniqueId())) {
            return;
        }
        PlayerState state = new PlayerState();
        state.startTime = System.nanoTime() / 1000000;
        state.walkSpeed = player.getWalkSpeed();
        state.allowUpdatePosition = allowUpdatePosition;
        PotionEffect potionEffect = player.getPotionEffect(PotionEffectType.JUMP);
        if (potionEffect != null) {
            state.jumpDuration = potionEffect.getDuration();
            state.jumpAmplifier = potionEffect.getAmplifier();
        }
        frozenPlayerState.put(player.getUniqueId(), state);
    }

    private void revertBlock(Player player, Location location) {
        WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange();
        BlockPosition blockPosition = new BlockPosition(location.toVector());
        blockChange.setLocation(blockPosition);
        blockChange.setBlockData(WrappedBlockData.createData(location.getBlock().getBlockData()));

        WrapperPlayServerBlockChange blockChangeU = getWrapperPlayServerBlockChange(location, 0, 1, 0);
        WrapperPlayServerBlockChange blockChangeD = getWrapperPlayServerBlockChange(location, 0, -1, 0);
        WrapperPlayServerBlockChange blockChangeE = getWrapperPlayServerBlockChange(location, 1, 0, 0);
        WrapperPlayServerBlockChange blockChangeW = getWrapperPlayServerBlockChange(location, -1, 0, 0);
        WrapperPlayServerBlockChange blockChangeS = getWrapperPlayServerBlockChange(location, 0, 0, 1);
        WrapperPlayServerBlockChange blockChangeN = getWrapperPlayServerBlockChange(location, 0, 0, -1);

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                player.updateInventory();
                protocolManager.sendServerPacket(player, blockChange.getHandle());
                protocolManager.sendServerPacket(player, blockChangeU.getHandle());
                protocolManager.sendServerPacket(player, blockChangeD.getHandle());
                protocolManager.sendServerPacket(player, blockChangeW.getHandle());
                protocolManager.sendServerPacket(player, blockChangeE.getHandle());
                protocolManager.sendServerPacket(player, blockChangeS.getHandle());
                protocolManager.sendServerPacket(player, blockChangeN.getHandle());
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        });
    }

    private WrapperPlayServerBlockChange getWrapperPlayServerBlockChange(Location location, int x, int y, int z) {
        WrapperPlayServerBlockChange blockChange = new WrapperPlayServerBlockChange();
        BlockPosition blockPosition = new BlockPosition(location.toVector().add(new Vector(x, y, z)));
        blockChange.setLocation(blockPosition);
        blockChange.setBlockData(WrappedBlockData.createData(blockPosition.toLocation(location.getWorld()).getBlock().getBlockData()));
        return blockChange;
    }

    @Override
    public void onLoad() {
        plugin = this;
        super.onLoad();
        new I18n(this, "en_US");
        Trigger.register(NYAAEXT_PACKET_LEFT_CLICK);
        Trigger.register(NYAAEXT_PACKET_RIGHT_CLICK);
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
    }

    @Override
    public void onEnable() {
        plugin = this;
        getServer().getPluginManager().registerEvents(new EventListener(), this);
        protocolManager = ProtocolLibrary.getProtocolManager();
        registPacketListener();
        Handler commandHandler = new Handler(this, I18n.instance);
        getCommand("rpgitems-ext-nyaacat").setExecutor(commandHandler);
        getCommand("rpgitems-ext-nyaacat").setTabCompleter(commandHandler);
    }


    private void registPacketListener() {
        entityPacketAdapter = new PacketAdapter(RPGItemsExtNyaacat.plugin, ListenerPriority.NORMAL, ENTITY_PACKETS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                int entityID = event.getPacket().getIntegers().read(0);
                if (isHijacked(SPAWN_ENTITY) && event.getPacketType() == SPAWN_ENTITY && entitySpawnCache.getIfPresent(entityID) == null) {
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
        };

        protocolManager.addPacketListener(entityPacketAdapter);

        playerActionPacketAdapter = new PacketAdapter(RPGItemsExtNyaacat.plugin, ListenerPriority.NORMAL, PLAYER_ACTION_PACKETS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                Player player = event.getPlayer();
                PlayerState playerState = frozenPlayerState.get(player.getUniqueId());
                if (playerState == null) return;
                if (!playerState.allowUpdatePosition) {
                    boolean updatePos = false;
                    if (event.getPacketType() == PacketType.Play.Client.POSITION) {
                        WrapperPlayClientPosition position = new WrapperPlayClientPosition(event.getPacket());
                        double squared = player.getLocation().toVector().distanceSquared(new Vector(position.getX(), position.getY(), position.getZ()));
                        if (squared > 1) {
                            updatePos = true;
                        }
                        if (squared > 9) {
                            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("Flying is not enabled in this server"));
                        }
                    } else if (event.getPacketType() == POSITION_LOOK) {
                        WrapperPlayClientPositionLook positionLook = new WrapperPlayClientPositionLook(event.getPacket());
                        double squared = player.getLocation().toVector().distanceSquared(new Vector(positionLook.getX(), positionLook.getY(), positionLook.getZ()));
                        if (squared > 1) {
                            updatePos = true;
                        }
                        if (squared > 9) {
                            Bukkit.getScheduler().runTask(plugin, () -> player.kickPlayer("Flying is not enabled in this server"));
                        }
                    }
                    if (updatePos) {
                        Location location = player.getLocation();
                        WrapperPlayServerPosition position = new WrapperPlayServerPosition();
                        position.setX(location.getX());
                        position.setY(location.getY());
                        position.setZ(location.getZ());
                        position.setPitch(location.getPitch());
                        position.setYaw(location.getYaw());
                        try {
                            protocolManager.sendServerPacket(player, position.getHandle());
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (event.getPacketType() == PacketType.Play.Client.POSITION || event.getPacketType() == POSITION_LOOK) {
                    if (playerState.allowUpdatePosition) {
                        return;
                    } else if (event.getPacketType() == POSITION_LOOK) {
                        WrapperPlayClientPositionLook positionLook = new WrapperPlayClientPositionLook(event.getPacket());
                        positionLook.setX(player.getLocation().getX());
                        positionLook.setY(player.getLocation().getY());
                        positionLook.setZ(player.getLocation().getZ());
                        return;
                    }
                }
                event.setCancelled(true);
                if (event.getPacketType() == BLOCK_DIG) {
                    WrapperPlayClientBlockDig blockDig = new WrapperPlayClientBlockDig(event.getPacket());
                    revertBlock(player, blockDig.getLocation().toLocation(player.getWorld()));
                } else if (event.getPacketType() == USE_ITEM) { // Actually BLOCK_PLACE here, blame spigot https://github.com/aadnk/ProtocolLib/issues/140
                    WrapperPlayClientUseItem useItem = new WrapperPlayClientUseItem(event.getPacket());
                    revertBlock(player, useItem.getLocation().toLocation(player.getWorld()));
                } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_SLOT) {
                    WrapperPlayServerHeldItemSlot heldItemSlot = new WrapperPlayServerHeldItemSlot();
                    heldItemSlot.setSlot(player.getInventory().getHeldItemSlot());
                    try {
                        protocolManager.sendServerPacket(player, heldItemSlot.getHandle());
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        };

        protocolManager.addPacketListener(playerActionPacketAdapter);

        playerClickAdapter = new PacketAdapter(RPGItemsExtNyaacat.plugin, ListenerPriority.HIGH, PLAYER_CLICK_PACKETS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                if (event.isCancelled()) {
                    return;
                }
                Player player = event.getPlayer();
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    return;
                }
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item == null) {
                    return;
                }
                Optional<RPGItem> optItem = ItemManager.toRPGItem(item);
                if (!optItem.isPresent()) {
                    return;
                }
                RPGItem rpgItem = optItem.get();
                if (rpgItem.getPower(PowerCastPoint.class).isEmpty()) {
                    return;
                }
                if (event.getPacketType() == BLOCK_DIG) {
                    WrapperPlayClientBlockDig blockDig = new WrapperPlayClientBlockDig(event.getPacket());
                    revertBlock(player, blockDig.getLocation().toLocation(player.getWorld()));
                    if (blockDig.getStatus() != EnumWrappers.PlayerDigType.START_DESTROY_BLOCK || blockDig.getStatus() != EnumWrappers.PlayerDigType.STOP_DESTROY_BLOCK) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (event.getPacketType() == USE_ENTITY) {
                    WrapperPlayClientUseEntity useEntity = new WrapperPlayClientUseEntity(event.getPacket());
                    if (useEntity.getType() != EnumWrappers.EntityUseAction.ATTACK) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (event.getPacketType() == ARM_ANIMATION) {
                    // see PlayerConnection.a(PacketPlayInArmAnimation)
                    double d3 = player.getGameMode() == CREATIVE ? 5.0D : 4.5D;
                    RayTraceResult rayTraceResult = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection(), d3, FluidCollisionMode.NEVER, false, 0, (e) -> !player.equals(e));
                    if (rayTraceResult != null && rayTraceResult.getHitBlock() != null) {
                        event.setCancelled(true);
                        return;
                    }
                } else if (event.getPacketType() == USE_ITEM) { // Actually BLOCK_PLACE here, blame spigot https://github.com/aadnk/ProtocolLib/issues/140
                    WrapperPlayClientUseItem useItem = new WrapperPlayClientUseItem(event.getPacket());
                    revertBlock(player, useItem.getLocation().toLocation(player.getWorld()));
                }
                if (event.getPacketType() == BLOCK_DIG || event.getPacketType() == ARM_ANIMATION || event.getPacketType() == USE_ENTITY) {
                    rpgItem.power(player, item, new WrappedPacketEvent(event), NYAAEXT_PACKET_LEFT_CLICK);
                } else {
                    rpgItem.power(player, item, new WrappedPacketEvent(event), NYAAEXT_PACKET_RIGHT_CLICK);
                }
            }
        };

        protocolManager.addPacketListener(playerClickAdapter);
    }

    private static boolean isHijacked(PacketType packetType) {
        return hijacked.contains(packetType);
    }

    @Override
    public void onDisable() {
        this.getServer().getScheduler().cancelTasks(plugin);
        protocolManager.removePacketListener(entityPacketAdapter);
        protocolManager.removePacketListener(playerActionPacketAdapter);
        protocolManager.removePacketListener(playerClickAdapter);
        getCommand("rpgitems-ext-nyaacat").setExecutor(null);
        getCommand("rpgitems-ext-nyaacat").setTabCompleter(null);
        plugin = null;
    }

    public static class PlayerState {
        public long startTime;
        public int jumpAmplifier;
        public int jumpDuration;
        public float walkSpeed;
        public float flySpeed;
        public boolean allowUpdatePosition;
    }
}
