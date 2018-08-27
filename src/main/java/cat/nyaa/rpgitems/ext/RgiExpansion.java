package cat.nyaa.rpgitems.ext;


import org.bukkit.plugin.java.JavaPlugin;
import org.librazy.nclangchecker.LangKey;
import think.rpgitems.power.PowerManager;

public final class RgiExpansion extends JavaPlugin {

    public static RgiExpansion plugin;

    @Override
    public void onLoad() {
        plugin = this;
        super.onLoad();
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
        new I18n(this, "en_US");
    }

    @Override
    public void onDisable() {
        plugin = null;
    }
}
