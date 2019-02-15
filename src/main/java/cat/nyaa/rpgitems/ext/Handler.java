package cat.nyaa.rpgitems.ext;

import cat.nyaa.nyaacore.CommandReceiver;
import cat.nyaa.nyaacore.ILocalizer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Handler extends CommandReceiver {
    public Handler(JavaPlugin plugin, ILocalizer i18n) {
        super(plugin, i18n);
    }

    @SubCommand("debug")
    public void debug(CommandSender sender, Arguments args) {
        Player player = asPlayer(sender);
        if (RPGItemsExtNyaacat.frozenPlayerState.containsKey(player.getUniqueId())) {
            RPGItemsExtNyaacat.unfreeze(player);
        } else {
            RPGItemsExtNyaacat.freeze(player, false);
        }
    }

    @Override
    public String getHelpPrefix() {
        return "rpgitems-nyaa-ext";
    }
}
