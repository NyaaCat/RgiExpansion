package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.rpgitems.ext.I18n;
import com.comphenix.packetwrapper.WrapperPlayClientArmAnimation;
import com.comphenix.packetwrapper.WrapperPlayClientUseEntity;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.RPGItems;
import think.rpgitems.power.PowerMeta;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.Property;

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static cat.nyaa.rpgitems.ext.RPGItemsExtNyaacat.*;
import static com.comphenix.protocol.PacketType.Play.Client.*;

@PowerMeta(defaultTrigger = "NYAAEXT_PACKET_LEFT_CLICK")
public class PowerCastPoint extends BasePower implements PowerPacketLeftClick, PowerPacketRightClick {

    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;

    @Property
    public int duration = 20;

    private static AtomicInteger rc = new AtomicInteger(0);

    private static ConcurrentMap<UUID, PacketContainer> packetCache = new ConcurrentHashMap<>();

    private static Listener listener;

    @Override
    public PowerResult<Void> packetLeftClick(Player player, ItemStack stack, PacketEvent event) {
        event.setCancelled(true);
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        return packetReceive(player, event);
    }

    @Override
    public PowerResult<Void> packetRightClick(Player player, ItemStack stack, PacketEvent event) {
        event.setCancelled(true);
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        return packetReceive(player, event);
    }

    private PowerResult<Void> packetReceive(Player player, PacketEvent event) {
        float walkSpeed = player.getWalkSpeed();
        float flySpeed = player.getFlySpeed();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.setWalkSpeed(walkSpeed);
            player.setFlySpeed(flySpeed);
        }, duration + 1);
        freeze(player, false);
        final UUID uuid = player.getUniqueId();
        PacketContainer raw = event.getPacket();
        packetCache.put(uuid, raw);
        new BukkitRunnable() {
            private int left = duration;

            @Override
            public void run() {
                if (--left <= 0) {
                    PacketContainer packet = packetCache.remove(uuid);
                    if (packet != null) {
                        try {
                            unfreeze(player);
                            new Message(I18n.format("user.castpoint.success")).send(player, Message.MessageType.ACTION_BAR);
                            PacketType packetType = packet.getType();
                            if (packetType == USE_ENTITY || packetType == BLOCK_DIG || packetType == ARM_ANIMATION) {
                                RayTraceResult rayTraceResult = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 4, (e) -> !player.equals(e));
                                Entity hitEntity = rayTraceResult == null ? null : rayTraceResult.getHitEntity();
                                if (hitEntity != null) {
                                    WrapperPlayClientUseEntity useEntity = new WrapperPlayClientUseEntity();
                                    useEntity.setType(EnumWrappers.EntityUseAction.ATTACK);
                                    useEntity.setTargetID(hitEntity.getEntityId());
                                    protocolManager.recieveClientPacket(player, useEntity.getHandle(), false);
                                }
                                protocolManager.recieveClientPacket(player, new WrapperPlayClientArmAnimation().getHandle(), false);
                            } else {
                                protocolManager.recieveClientPacket(player, packet, false);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                    cancel();
                    return;
                }
                if (!packetCache.containsKey(uuid)) {
                    cancel();
                    return;
                }
                if (!player.isOnGround()) {
                    cancel();
                    fail(player);
                    return;
                }
                new Message(I18n.format("user.castpoint.left", left / 20.0)).send(player, Message.MessageType.ACTION_BAR);
            }
        }.runTaskTimer(plugin, 0, 0);
        return PowerResult.ok();
    }

    @Override
    public @LangKey(skipCheck = true) String getName() {
        return "castpoint";
    }

    @Override
    public String displayText() {
        return I18n.format("power.castpoint", duration / 20.0);
    }

    @Override
    public void init(ConfigurationSection s) {
        int orc = rc.getAndIncrement();
        super.init(s);
        if (orc == 0) {
            listener = new Listener() {
                @EventHandler(ignoreCancelled = true)
                void onPlayerTeleport(PlayerTeleportEvent e) {
                    Player player = e.getPlayer();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerDropItem(PlayerDropItemEvent e) {
                    Player player = e.getPlayer();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerOpenInv(InventoryOpenEvent e) {
                    if (!(e.getPlayer() instanceof Player)) return;
                    Player player = (Player) e.getPlayer();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerOpenInv(InventoryClickEvent e) {
                    if (!(e.getWhoClicked() instanceof Player)) return;
                    Player player = (Player) e.getWhoClicked();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerQuit(PlayerQuitEvent e) {
                    Player player = e.getPlayer();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
                    Player player = e.getPlayer();
                    fail(player);
                }

                @EventHandler(ignoreCancelled = true)
                void onPlayerDamaged(EntityDamageEvent e) {
                    if (!(e.getEntity() instanceof Player)) return;
                    Player player = (Player) e.getEntity();
                    UUID uuid = player.getUniqueId();
                    PlayerState state = frozenPlayerState.get(uuid);
                    if (state != null && !state.allowUpdatePosition) {
                        fail(player);
                    }
                }
            };
            Bukkit.getPluginManager().registerEvents(listener, RPGItems.plugin);
        }
    }

    private static void fail(Player player) {
        boolean unfreeze = unfreeze(player);
        packetCache.remove(player.getUniqueId());
        if (unfreeze) {
            unfreeze(player);
            player.sendMessage(I18n.format("user.castpoint.fail"));
        }
    }

    @Override
    public void deinit() {
        int nrc = rc.decrementAndGet();
        if (nrc == 0) {
            HandlerList.unregisterAll(listener);
            for (Player p : packetCache.keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList())) {
                fail(p);
            }
        }
    }
}
