package su.nightexpress.combatpets;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.capture.CaptureManager;
import su.nightexpress.combatpets.capture.command.CaptureCommands;
import su.nightexpress.combatpets.command.impl.AspectPointsCommands;
import su.nightexpress.combatpets.command.impl.BaseCommands;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.ChineseConfigMigration;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.config.NightcoreChineseLocale;
import su.nightexpress.combatpets.config.Perms;
import su.nightexpress.combatpets.config.PluginConfigMigration;
import su.nightexpress.combatpets.data.DataHandler;
import su.nightexpress.combatpets.data.UserManager;
import su.nightexpress.combatpets.hook.HookId;
import su.nightexpress.combatpets.hook.impl.LandsHook;
import su.nightexpress.combatpets.hook.impl.PlaceholderHook;
import su.nightexpress.combatpets.item.ItemManager;
import su.nightexpress.combatpets.level.LevelingManager;
import su.nightexpress.combatpets.level.command.LevelingCommands;
import su.nightexpress.combatpets.nms.PetNMS;
import su.nightexpress.combatpets.nms.mc_1_21_10.MC_1_21_10;
import su.nightexpress.combatpets.nms.mc_26_2.MC_26_2;
import su.nightexpress.combatpets.nms.mc_1_21_3.MC_1_21_4;
import su.nightexpress.combatpets.nms.mc_1_21_5.MC_1_21_5;
import su.nightexpress.combatpets.nms.mc_1_21_8.MC_1_21_8;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.shop.ShopManager;
import su.nightexpress.combatpets.shop.command.ShopCommands;
import su.nightexpress.combatpets.wardrobe.WardrobeManager;
import su.nightexpress.combatpets.wardrobe.command.WardrobeCommands;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.NightCore;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.commands.command.NightCommand;
import su.nightexpress.nightcore.config.PluginDetails;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.Version;

import java.io.File;

public class PetsPlugin extends NightPlugin {

    private DataHandler dataHandler;
    private UserManager userManager;

    private ItemManager itemManager;
    private PetManager      petManager;
    private LevelingManager levelingManager;
    private CaptureManager  captureManager;
    private WardrobeManager wardrobeManager;
    private ShopManager     shopManager;
    private LandsHook       landsHook;

    private PetNMS petNMS;

    @Override
    @NotNull
    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Pets", new String[]{"pets", "pet", "combatpets"})
            .setConfigClass(Config.class)
            .setPermissionsClass(Perms.class);
    }

    @Override
    protected void addRegistries() {
        super.addRegistries();

        NightcoreChineseLocale.install(this);

        if (this.getDetails().getLanguage().equalsIgnoreCase("zh")) {
            File localeFile = new File(this.getDataFolder(), "lang/lang_zh.yml");
            File messagesFile = new File(this.getDataFolder(), "lang/messages_zh.yml");

            if (!localeFile.isFile() && !messagesFile.isFile()) {
                this.saveResource("lang/messages_zh.yml", false);
            }

            ChineseConfigMigration.migrateLocale(this);
        }

        this.registerLang(Lang.class);
    }

    @Override
    protected boolean disableCommandManager() {
        return true;
    }

    @Override
    protected void loadManagers() {
        super.loadManagers();
        ChineseConfigMigration.translateComments(this);
    }

    @Override
    public void enable() {
        PetScheduler.initialize();
        PetAPI.setup(this);

        PluginConfigMigration.migrate(this);
        ChineseConfigMigration.migrate(this);

        if (!this.setupNMS()) {
            this.error("不支持当前服务端版本！");
            this.getPluginManager().disablePlugin(this);
            return;
        }

        Keys.load(this);



        this.dataHandler = new DataHandler(this);
        this.dataHandler.setup();

        this.userManager = new UserManager(this, this.dataHandler);
        this.userManager.setup();

        this.itemManager = new ItemManager(this);
        this.itemManager.setup();

        this.petManager = new PetManager(this);
        this.petManager.setup();

        if (Config.isLevelingEnabled()) {
            this.levelingManager = new LevelingManager(this);
            this.levelingManager.setup();
        }

        if (Config.isCapturingEnabled()) {
            this.captureManager = new CaptureManager(this);
            this.captureManager.setup();
        }

        if (Config.isWardrobeEnabled()) {
            this.wardrobeManager = new WardrobeManager(this);
            this.wardrobeManager.setup();
        }

        if (Config.isShopEnabled()) {
            this.shopManager = new ShopManager(this);
            this.shopManager.setup();
        }

        this.itemManager.migrateOnlineEggs();

        this.loadHooks();
        this.loadCommands();
    }

    private void loadHooks() {
        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.setup(this);
        }
        if (Plugins.isLoaded(HookId.LANDS)) {
            this.landsHook = LandsHook.create(this);
            if (this.landsHook != null) this.info("已启用 Lands 领地兼容。");
        }
    }

    @Override
    public void disable() {
        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.shutdown();
        }
        this.landsHook = null;

        if (this.levelingManager != null) this.levelingManager.shutdown();
        if (this.shopManager != null) this.shopManager.shutdown();
        if (this.captureManager != null) this.captureManager.shutdown();
        if (this.petManager != null) this.petManager.shutdown();
        if (this.wardrobeManager != null) this.wardrobeManager.shutdown();
        if (this.itemManager != null) this.itemManager.shutdown();
        if (this.userManager != null) this.userManager.shutdown();
        if (this.dataHandler != null) this.dataHandler.shutdown();

        PetAPI.shutdown();
    }

    @Override
    protected void onShutdown() {
        NightCore.CHILDRENS.remove(this);

        synchronized (Bukkit.getHelpMap()) {
            Bukkit.getHelpMap().getHelpTopics().removeIf(topic -> {
                String name = topic.getName();
                return name.equalsIgnoreCase("CombatPets")
                    || name.equalsIgnoreCase("/pets")
                    || name.equalsIgnoreCase("/pet")
                    || name.equalsIgnoreCase("/combatpets")
                    || name.equalsIgnoreCase("/combatpets:pets")
                    || name.equalsIgnoreCase("/combatpets:pet")
                    || name.equalsIgnoreCase("/combatpets:combatpets");
            });
        }
    }

    private void loadCommands() {
        this.rootCommand = NightCommand.forPlugin(this, builder -> {
            BaseCommands.load(this, builder);
            AspectPointsCommands.load(this, builder);

            if (this.captureManager != null) {
                CaptureCommands.load(this, this.captureManager, builder);
            }
            if (this.levelingManager != null) {
                LevelingCommands.load(this, builder);
            }
            if (this.shopManager != null) {
                ShopCommands.load(this, this.shopManager, builder);
            }
            if (this.wardrobeManager != null) {
                WardrobeCommands.load(this, this.wardrobeManager, builder);
            }
        });
    }

    private boolean setupNMS() {
        switch (Version.getCurrent()) {
            case MC_1_21_4 -> this.petNMS = new MC_1_21_4();
            case MC_1_21_5 -> this.petNMS = new MC_1_21_5();
            case MC_1_21_8 -> this.petNMS = new MC_1_21_8();
            case MC_1_21_10 -> this.petNMS = new MC_1_21_10();
            case MC_26_2 -> this.petNMS = new MC_26_2();
        }
        return this.petNMS != null;
    }

    @NotNull
    public DataHandler getDataHandler() {
        return this.dataHandler;
    }

    @NotNull
    public UserManager getUserManager() {
        return userManager;
    }

    @NotNull
    public PetNMS getPetNMS() {
        return this.petNMS;
    }

    @NotNull
    public ItemManager getItemManager() {
        return itemManager;
    }

    @NotNull
    public PetManager getPetManager() {
        return this.petManager;
    }

    public LevelingManager getLevelingManager() {
        return this.levelingManager;
    }

    public CaptureManager getCaptureManager() {
        return this.captureManager;
    }

    public WardrobeManager getWardrobeManager() {
        return this.wardrobeManager;
    }

    public ShopManager getShopManager() {
        return this.shopManager;
    }

    @Nullable
    public LandsHook getLandsHook() {
        return this.landsHook;
    }
}
