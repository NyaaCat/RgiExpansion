package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.utils.TridentUtils;
import cat.nyaa.rpgitems.ext.I18n;
import cat.nyaa.rpgitems.ext.RPGItemsExtNyaacat;
import com.comphenix.packetwrapper.WrapperPlayServerEntityMetadata;
import com.comphenix.packetwrapper.WrapperPlayServerSpawnEntity;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import think.rpgitems.Events;
import think.rpgitems.power.*;

import java.util.*;

import static cat.nyaa.rpgitems.ext.RPGItemsExtNyaacat.*;
import static com.comphenix.packetwrapper.WrapperPlayServerSpawnEntity.ObjectTypes.ITEM_STACK;
import static think.rpgitems.power.Utils.checkCooldown;

@PowerMeta(defaultTrigger = "RIGHT_CLICK", generalInterface = PowerPlain.class)
public class PowerThrowable extends BasePower implements PowerRightClick, PowerLeftClick, PowerProjectileHit, PowerPlain {

    /**
     * Cooldown time of this power
     */
    @Property
    public long cooldown = 20;

    /**
     * Cost of this power
     */
    @Property
    public int cost = 0;

    /**
     * When to return back to the player who threw it after it comes in contact with any block or entity, in tick. 0 to disable.
     */
    @Property
    public int autoReturn = 0;

    /**
     * Reduce autoReturn with level * 20
     */
    @Property
    public boolean loyaltyEnchant = false;

    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack stack, PlayerInteractEvent playerInteractEvent) {
        return fire(player, stack);
    }

    @Override
    public PowerResult<Void> leftClick(Player player, ItemStack stack, PlayerInteractEvent playerInteractEvent) {
        return fire(player, stack);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PowerResult<Void> fire(Player player, ItemStack stack) {
        if (!checkCooldown(this, player, cooldown, true)) return PowerResult.cd();
        ItemStack orig = stack.clone();
        if (!getItem().consumeDurability(stack, cost)) return PowerResult.cost();
        hijackEntitySpawn = true;
        Trident entity = player.launchProjectile(Trident.class);
        hijackEntitySpawn = false;

        int entityId = entity.getEntityId();
        PacketContainer packetContainer = entitySpawnCache.getIfPresent(entityId);
        entitySpawnCache.invalidate(entityId);

        entitySpawnHandler.put(entityId, packetEvent -> packetEvent.setPacket(getFakeItemStack(entityId, packetEvent.getPacket()).getHandle()));
        entityMetadataHandler.put(entityId, packetEvent -> packetEvent.setPacket(getFakeMetadata(entityId, orig).getHandle()));

        entity.setSilent(true);
        entity.setPersistent(false);

        WrapperPlayServerSpawnEntity spawnEntity = getFakeItemStack(entityId, packetContainer);
        WrapperPlayServerEntityMetadata metadata = getFakeMetadata(entityId, orig);

        protocolManager.broadcastServerPacket(spawnEntity.getHandle());
        protocolManager.broadcastServerPacket(metadata.getHandle());

        Events.registerProjectile(entityId, getItem().getUID());

        UUID uuid = entity.getUniqueId();
        Events.registerLocalItemStack(uuid, stack.clone());
        ItemStack fakeItem = new ItemStack(Material.TRIDENT);
        List<String> fakeLore = new ArrayList<>(1);
        fakeLore.add(uuid.toString());
        ItemMeta fakeItemItemMeta = fakeItem.getItemMeta();
        fakeItemItemMeta.setLore(fakeLore);
        fakeItem.setItemMeta(fakeItemItemMeta);
        TridentUtils.setTridentItemStack(entity, fakeItem);

        ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
        int count = itemInMainHand.getAmount() - 1;
        if (count == 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(RPGItemsExtNyaacat.plugin, () -> player.getInventory().setItemInMainHand(new ItemStack(Material.AIR)), 1L);
        } else {
            itemInMainHand.setAmount(count);
        }
        return PowerResult.ok();
    }

    private WrapperPlayServerEntityMetadata getFakeMetadata(int entityId, ItemStack itemStack) {
        WrapperPlayServerEntityMetadata metadata = new WrapperPlayServerEntityMetadata();
        metadata.setEntityID(entityId);
        HashMap<Integer, Object> watcher = new HashMap<>();
        watcher.put(6, itemStack.clone());
        WrappedDataWatcher dataWatcher = PacketUtils.watcher(watcher);
        metadata.setMetadata(dataWatcher.getWatchableObjects());
        return metadata;
    }

    private WrapperPlayServerSpawnEntity getFakeItemStack(int entityId, PacketContainer packetContainer) {
        WrapperPlayServerSpawnEntity spawnArrow = new WrapperPlayServerSpawnEntity(Objects.requireNonNull(packetContainer));
        WrapperPlayServerSpawnEntity spawnEntity = new WrapperPlayServerSpawnEntity();
        spawnEntity.setEntityID(entityId);
        spawnEntity.setType(ITEM_STACK);
        spawnEntity.setObjectData(2);
        spawnEntity.setX(spawnArrow.getX());
        spawnEntity.setY(spawnArrow.getY());
        spawnEntity.setZ(spawnArrow.getZ());
        spawnEntity.setYaw(spawnArrow.getYaw());
        spawnEntity.setPitch(spawnArrow.getPitch());
        spawnEntity.setOptionalSpeedX(spawnArrow.getOptionalSpeedX());
        spawnEntity.setOptionalSpeedY(spawnArrow.getOptionalSpeedY());
        spawnEntity.setOptionalSpeedZ(spawnArrow.getOptionalSpeedZ());
        spawnEntity.setUniqueId(UUID.randomUUID());
        return spawnEntity;
    }

    @Override
    public String getName() {
        return "throwable";
    }

    @Override
    public String displayText() {
        return I18n.format("power.throwable");
    }

    @Override
    public PowerResult<Void> projectileHit(Player player, ItemStack stack, ProjectileHitEvent event) {
        Projectile arrow = event.getEntity();
        if (autoReturn > 0) {
            if (item.getDurability(stack) <= 0) return PowerResult.fail();
            int returnTime = this.autoReturn;
            if (loyaltyEnchant) {
                returnTime = returnTime - 20 * stack.getEnchantmentLevel(Enchantment.LOYALTY);
                returnTime = Math.max(0, returnTime);
            }
            Bukkit.getScheduler().runTaskLater(RPGItemsExtNyaacat.plugin, () -> {
                if (!arrow.isDead() && arrow.isValid()) {
                    arrow.remove();
                    UUID uniqueId = arrow.getUniqueId();
                    ItemStack orig = Events.removeLocalItemStack(uniqueId);
                    HashMap<Integer, ItemStack> drop = player.getInventory().addItem(orig);
                    if (!drop.isEmpty()) {
                        drop.values().forEach(i -> player.getLocation().getWorld().dropItem(player.getLocation(), i)
                        );
                    }
                }
            }, returnTime);
            return PowerResult.ok();
        }
        return PowerResult.noop();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Set<Trigger> getTriggers() {
        Set<Trigger> triggers = super.getTriggers();
        triggers.add(Trigger.PROJECTILE_HIT);
        return triggers;
    }
}
