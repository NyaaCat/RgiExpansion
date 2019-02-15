package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.Message;
import cat.nyaa.rpgitems.ext.I18n;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.RPGItems;
import think.rpgitems.power.*;

import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import static cat.nyaa.rpgitems.ext.RPGItemsExtNyaacat.*;

@PowerMeta(defaultTrigger = "LEFT_CLICK")
public class PowerCastBackswing extends BasePower implements PowerLeftClick, PowerRightClick, PowerPlain {

    @Property
    public int duration = 20;

    private static AtomicInteger rc = new AtomicInteger(0);

    private static Listener listener;

    private static ConcurrentSkipListSet<UUID> set = new ConcurrentSkipListSet<>();

    @Override
    public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent event) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> fire(Player player, ItemStack stack) {
        freeze(player, true);
        set.add(player.getUniqueId());
        new BukkitRunnable() {
            private int left = duration;

            @Override
            public void run() {
                if (--left > 0 && player.isOnline() ) {
                    freeze(player, true);
                    new Message(I18n.format("user.castpoint.left", left / 20.0)).send(player, Message.MessageType.ACTION_BAR);
                } else {
                    new Message(I18n.format("user.castpoint.left", left / 20.0)).send(player, Message.MessageType.ACTION_BAR);
                    set.remove(player.getUniqueId());
                    unfreeze(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0, 0);
        return PowerResult.ok();
    }

    @Override
    public @LangKey(skipCheck = true) String getName() {
        return "castbackswing";
    }

    @Override
    public String displayText() {
        return I18n.format("power.castbackswing", duration / 20.0);
    }

    @Override
    public void init(ConfigurationSection s) {
        int orc = rc.getAndIncrement();
        super.init(s);
        if (orc == 0) {
            listener = new Listener() {
                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerTeleport(PlayerTeleportEvent e) {
                    Player player = e.getPlayer();
                    if (set.contains(player.getUniqueId()) && e.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                        e.setCancelled(true);
                    }
                }

                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerDropItem(PlayerDropItemEvent e) {
                    Player player = e.getPlayer();
                    if (set.contains(player.getUniqueId())) {
                        e.setCancelled(true);
                    }
                }

                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerInventoryOpen(InventoryOpenEvent e) {
                    if (!(e.getPlayer() instanceof Player)) return;
                    Player player = (Player) e.getPlayer();
                    if (set.contains(player.getUniqueId())) {
                        e.setCancelled(true);
                    }
                }

                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerInventoryClick(InventoryClickEvent e) {
                    if (!(e.getWhoClicked() instanceof Player)) return;
                    Player player = (Player) e.getWhoClicked();
                    if (set.contains(player.getUniqueId())) {
                        e.setCancelled(true);
                    }
                }

                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerQuit(PlayerQuitEvent e) {
                    Player player = e.getPlayer();
                    unfreeze(player);
                }

                @EventHandler(priority = EventPriority.LOWEST)
                void onPlayerSwapHandItems(PlayerSwapHandItemsEvent e) {
                    Player player = e.getPlayer();
                    if (set.contains(player.getUniqueId())) {
                        e.setCancelled(true);
                    }
                }
            };
            Bukkit.getPluginManager().registerEvents(listener, RPGItems.plugin);
        }
    }

    @Override
    public void deinit() {
        int nrc = rc.decrementAndGet();
        if (nrc == 0) {
            HandlerList.unregisterAll(listener);
        }
    }
}
