package cat.nyaa.rpgitems.ext;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import static cat.nyaa.rpgitems.ext.RgiExpansion.entityMetadataHandler;
import static cat.nyaa.rpgitems.ext.RgiExpansion.entitySpawnHandler;
import static cat.nyaa.rpgitems.ext.RgiExpansion.hiddenEntities;

public class EventListener implements Listener {

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        int entityId = event.getEntity().getEntityId();
        hiddenEntities.remove(entityId);
        entitySpawnHandler.remove(entityId);
        entityMetadataHandler.remove(entityId);
    }
}
