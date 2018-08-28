package cat.nyaa.rpgitems.ext.power;

import cat.nyaa.nyaacore.utils.TridentUtils;
import cat.nyaa.rpgitems.ext.I18n;
import cat.nyaa.rpgitems.ext.RgiExpansion;
import com.comphenix.packetwrapper.WrapperPlayServerEntityMetadata;
import com.comphenix.packetwrapper.WrapperPlayServerSpawnEntity;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import think.rpgitems.Events;
import think.rpgitems.power.PowerMeta;
import think.rpgitems.power.PowerProjectileHit;
import think.rpgitems.power.PowerResult;
import think.rpgitems.power.PowerRightClick;

import java.util.*;

import static cat.nyaa.rpgitems.ext.RgiExpansion.*;
import static com.comphenix.packetwrapper.WrapperPlayServerSpawnEntity.ObjectTypes.ITEM_STACK;

@PowerMeta(immutableTrigger = true)
public class PowerThrowable  extends BasePower implements PowerRightClick, PowerProjectileHit {
    @Override
    public PowerResult<Void> rightClick(Player player, ItemStack itemStack, Block block, PlayerInteractEvent playerInteractEvent) {
        hijackEntitySpawn = true;
        Trident entity = player.launchProjectile(Trident.class);
        hijackEntitySpawn = false;

        int entityId = entity.getEntityId();
        PacketContainer packetContainer = entitySpawnCache.getIfPresent(entityId);
        entitySpawnCache.invalidate(entityId);

        entitySpawnHandler.put(entityId, packetEvent -> packetEvent.setPacket(getFakeItemStack(entityId, packetEvent.getPacket()).getHandle()));
        entityMetadataHandler.put(entityId, packetEvent -> packetEvent.setPacket(getFakeMetadata(entityId, itemStack).getHandle()));

        entity.setSilent(true);
        entity.setPersistent(false);

        WrapperPlayServerSpawnEntity spawnEntity = getFakeItemStack(entityId, packetContainer);
        WrapperPlayServerEntityMetadata metadata = getFakeMetadata(entityId, itemStack);

        protocolManager.broadcastServerPacket(spawnEntity.getHandle());
        protocolManager.broadcastServerPacket(metadata.getHandle());

        Events.rpgProjectiles.put(entityId, getItem().getUID());

        UUID uuid = entity.getUniqueId();
        Events.tridentCache.put(uuid, itemStack.clone());
        ItemStack fakeItem = new ItemStack(Material.TRIDENT);
        fakeItem.addEnchantment(Enchantment.LOYALTY, 3);
        List<String> fakeLore = new ArrayList<>(1);
        fakeLore.add(uuid.toString());
        ItemMeta fakeItemItemMeta = fakeItem.getItemMeta();
        fakeItemItemMeta.setLore(fakeLore);
        fakeItem.setItemMeta(fakeItemItemMeta);
        TridentUtils.setTridentItemStack(entity, fakeItem);

        ItemStack stack = player.getInventory().getItemInMainHand();
        int count = stack.getAmount() - 1;
        if (count == 0) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(RgiExpansion.plugin, () -> player.getInventory().setItemInMainHand(new ItemStack(Material.AIR)), 1L);
        } else {
            stack.setAmount(count);
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
    public PowerResult<Void> projectileHit(Player player, ItemStack stack, Projectile arrow, ProjectileHitEvent event) {
        return PowerResult.ok();
    }
}
