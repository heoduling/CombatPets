package su.nightexpress.combatpets.shop;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.ChineseConfigMigration;
import su.nightexpress.combatpets.config.Perms;
import su.nightexpress.combatpets.shop.data.EggPrice;
import su.nightexpress.combatpets.shop.menu.EggConfirmMenu;
import su.nightexpress.combatpets.shop.menu.ShopAdminPetsMenu;
import su.nightexpress.combatpets.shop.menu.ShopAdminTiersMenu;
import su.nightexpress.combatpets.shop.menu.ShopEggsMenu;
import su.nightexpress.combatpets.shop.menu.ShopTiersMenu;
import su.nightexpress.nightcore.bridge.currency.Currency;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.integration.currency.CurrencyId;
import su.nightexpress.nightcore.integration.currency.EconomyBridge;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.menu.api.Menu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShopManager extends AbstractManager<PetsPlugin> {

    public static final String FILE_NAME = "shop.yml";
    public static final String PLAYER_SHOP_TIER_ID = "common";
    public static final int PLAYER_SHOP_TIER_SLOT = 13;

    private static final List<String> DEFAULT_DISABLED_PET_IDS = List.of(
        "warden",
        "wither",
        "iron_golem",
        "ravager",
        "illusioner",
        "ender_dragon"
    );
    private static final int DISABLED_PETS_CONFIG_VERSION = 2;

    private final Map<String, Map<String, EggPrice>> eggPriceMap;
    private final Map<String, EggPrice>              defaultEggPriceMap;
    private final Set<String>                        disabledEggPetIds;
    private final Object                             priceLock;

    private ShopTiersMenu  tiersMenu;
    private ShopEggsMenu   eggsMenu;
    private EggConfirmMenu eggConfirmMenu;
    private ShopAdminTiersMenu adminTiersMenu;
    private ShopAdminPetsMenu  adminPetsMenu;
    private FileConfig         priceConfig;
    private boolean             priceSaveScheduled;
    private long                priceRevision;

    public ShopManager(@NotNull PetsPlugin plugin) {
        super(plugin);
        this.eggPriceMap = new HashMap<>();
        this.defaultEggPriceMap = new HashMap<>();
        this.disabledEggPetIds = new HashSet<>();
        this.priceLock = new Object();
    }

    @Override
    protected void onLoad() {
        FileConfig config = this.getConfig();

        synchronized (this.priceLock) {
            this.priceConfig = config;
            this.priceRevision = 0L;
            this.priceSaveScheduled = false;
        }

        this.loadPrices(config);
        this.addPriceComments(config);
        this.loadUI();

        // The admin menus are generated during this manager setup, after the
        // global Chinese-comment migration has already run. Translate the
        // newly-created menu comments before exposing the menus.
        ChineseConfigMigration.translateComments(this.plugin);

        config.saveChanges();
    }

    @Override
    protected void onShutdown() {
        synchronized (this.priceLock) {
            if (this.priceConfig != null) this.priceConfig.saveChanges();
            this.priceConfig = null;
            this.priceSaveScheduled = false;
            this.priceRevision++;
        }

        if (this.eggConfirmMenu != null) this.eggConfirmMenu.clear();
        if (this.tiersMenu != null) this.tiersMenu.clear();
        if (this.eggsMenu != null) this.eggsMenu.clear();
        if (this.adminTiersMenu != null) this.adminTiersMenu.clear();
        if (this.adminPetsMenu != null) this.adminPetsMenu.clear();

        this.eggPriceMap.clear();
        this.defaultEggPriceMap.clear();
        this.disabledEggPetIds.clear();
    }

    private void loadPrices(@NotNull FileConfig config) {
        this.loadDefaultDisabledPets(config);
        //AtomicInteger modifier = new AtomicInteger(1);

        plugin.getPetManager().getTiers().stream().sorted(Comparator.comparingDouble(Tier::getWeight).reversed()).forEach(tier -> {
            double oldModifier = tier.getConfig().getDouble("Egg_Cost_Modifier", 1D);

            // ----------- UPDATE OLD DATA - START -----------
            for (Template petConfig : plugin.getPetManager().getTemplates()) {
                FileConfig petFile = petConfig.getConfig();
                if (!petFile.contains("Egg_Cost")) continue;

                String path = "EggPrice.Custom." + tier.getId() + "." + petConfig.getId();
                double oldPrice = petFile.getDouble("Egg_Cost", -1) * oldModifier;

                config.addMissing(path + ".Price", oldPrice);
                config.addMissing(path + ".Currency", CurrencyId.VAULT);
                petFile.remove("Egg_Cost");
            }
            // ----------- UPDATE OLD DATA - END -----------


            // Load default prices.
            String path = "EggPrice.Default." + tier.getId();
            double price = ConfigValue.create(path + ".Price", 1000D).read(config);
            if (price <= 0D) return;

            String currencyId = CurrencyId.reroute(ConfigValue.create(path + ".Currency", CurrencyId.VAULT).read(config));
            Currency currency = EconomyBridge.getCurrency(currencyId);
            if (currency == null) {
            this.plugin.warn("宠物品质 '" + tier.getId() + "' 的默认价格使用了无效货币 '" + currencyId + "'。");
                return;
            }

            this.defaultEggPriceMap.put(tier.getId(), new EggPrice(currency, price));
        });

        if (config.getSection("EggPrice.Custom").isEmpty()) {
            config.set("EggPrice.Custom.common.zombie.Price", 500D);
            config.set("EggPrice.Custom.common.zombie.Currency", CurrencyId.VAULT);
            config.set("EggPrice.Custom.rare.zombie.Price", 1500D);
            config.set("EggPrice.Custom.rare.zombie.Currency", CurrencyId.VAULT);
        }

        // Load custom per egg prices.
        config.getSection("EggPrice.Custom").forEach(tierId -> {
            Tier tier = this.plugin.getPetManager().getTier(tierId);
            if (tier == null) return;

            config.getSection("EggPrice.Custom." + tierId).forEach(petId -> {
                String path = "EggPrice.Custom." + tierId + "." + petId;
                this.migrateLegacyPrice(config, path);

                Template petConfig = this.plugin.getPetManager().getTemplate(petId);
                if (petConfig == null) return;

                path = "EggPrice.Custom." + tier.getId() + "." + petConfig.getId();

                double price = ConfigValue.create(path + ".Price", 0D).read(config);
                if (price <= 0D) return;

                String currencyId = CurrencyId.reroute(ConfigValue.create(path + ".Currency", CurrencyId.VAULT).read(config));
                Currency currency = EconomyBridge.getCurrency(currencyId);
                if (currency == null) {
            this.plugin.warn("宠物蛋价格 '" + tier.getId() + " -> " + petConfig.getId() + "' 使用了无效货币 '" + currencyId + "'。");
                    return;
                }

                this.eggPriceMap.computeIfAbsent(tier.getId(), k -> new HashMap<>()).put(petConfig.getId(), new EggPrice(currency, price));
            });
        });

//        if (Config.isWardrobeEnabled()) {
//            config.getSection("Price.Customizations").forEach(name -> {
//                EntityVariant<?> variant = VariantRegistry.getVariant(name);
//                if (variant == null) return;
//
//                config.getSection("Price.Customizations." + name).forEach(value -> {
//                    Object object = variant.getHandler().parse(value);
//                    if (object == null) return;
//
//                    String path = "Price.Customizations." + name + "." + value;
//
//                    double price = ConfigValue.create(path + ".Price", 0D).read(config);
//                    if (price <= 0D) return;
//
//                    String currencyId = ConfigValue.create(path + ".Currency", VaultEconomyHandler.ID).read(config);
//                    Currency currency = this.plugin.getCurrencyManager().getCurrency(currencyId);
//                    if (currency == null) {
//                        this.plugin.warn("Invalid currency '" + currencyId + "' for '" + name + " -> " + value + "' pet customization price.");
//                        return;
//                    }
//
//                    this.customsPriceMap.computeIfAbsent(variant.getName(), k -> new HashMap<>()).put(value.toLowerCase(), new EggPrice(currency, price));
//                });
//            });
//        }
    }

    private void loadDefaultDisabledPets(@NotNull FileConfig config) {
        String path = "EggPrice.Default_Disabled_Pets";
        String versionPath = "EggPrice.Default_Disabled_Pets_Version";
        int version = config.getInt(versionPath, 0);
        if (!config.contains(path)) {
            config.set(path, DEFAULT_DISABLED_PET_IDS);
        }
        else if (version < DISABLED_PETS_CONFIG_VERSION) {
            List<String> petIds = new ArrayList<>(config.getStringList(path));
            List<String> additions = version < 1 ? List.of("ender_dragon", "illusioner") : List.of("illusioner");
            for (String addition : additions) {
                if (petIds.stream().noneMatch(addition::equalsIgnoreCase)) petIds.add(addition);
            }
            config.set(path, petIds);
        }
        config.set(versionPath, DISABLED_PETS_CONFIG_VERSION);

        this.disabledEggPetIds.clear();
        for (String petId : config.getStringList(path)) {
            String normalized = petId.toLowerCase().trim();
            if (normalized.isEmpty()) continue;

            this.disabledEggPetIds.add(normalized);
        }
    }

    private void migrateLegacyPrice(@NotNull FileConfig config, @NotNull String path) {
        Object value = config.get(path);
        if (!(value instanceof Number number)) return;

        config.set(path, null);
        config.set(path + ".Price", number.doubleValue());
        config.set(path + ".Currency", CurrencyId.VAULT);
    }

    private void addPriceComments(@NotNull FileConfig config) {
        config.setComments("EggPrice", "宠物蛋商店价格设置。");
        config.setComments("EggPrice.Default_Disabled_Pets",
            "默认关闭商店购买的宠物 ID 列表。仅影响商店，不影响管理员发放或已有宠物。",
            "从列表移除宠物 ID 即可重新开放购买；这不是永久禁用。",
            "尚未实现的宠物 ID 也会保留，以免未来加入该宠物后意外自动上架。"
        );
        config.setComments("EggPrice.Default_Disabled_Pets_Version", "默认禁售名单的自动迁移版本，请勿手动修改。");
        config.setComments("EggPrice.Default", "各宠物品质的默认价格；未单独定价的宠物会使用这里的价格。");
        config.getSection("EggPrice.Default").forEach(tierId -> {
            String path = "EggPrice.Default." + tierId;
            config.setComments(path, "宠物品质 '" + tierId + "' 的默认价格。");
            config.setComments(path + ".Price", "售价；设为 0 或负数可禁用该品质的默认商品。");
            config.setComments(path + ".Currency", "付款货币 ID，例如 vault。");
        });

        config.setComments("EggPrice.Custom", "指定品质与宠物的单独价格，会覆盖对应品质的默认价格。");
        config.getSection("EggPrice.Custom").forEach(tierId -> {
            String tierPath = "EggPrice.Custom." + tierId;
            config.setComments(tierPath, "宠物品质 '" + tierId + "' 的单独定价。");
            config.getSection(tierPath).forEach(petId -> {
                String path = tierPath + "." + petId;
                config.setComments(path, "宠物 '" + petId + "' 的单独价格。");
                config.setComments(path + ".Price", "该宠物蛋的售价；输入 0 可移除单独价格并恢复品质默认价格。");
                config.setComments(path + ".Currency", "付款货币 ID，例如 vault。");
            });
        });
    }

    private void loadUI() {
        this.tiersMenu = new ShopTiersMenu(this.plugin, this);
        this.eggsMenu = new ShopEggsMenu(this.plugin, this);
        this.eggConfirmMenu = new EggConfirmMenu(this.plugin, this);
        this.adminTiersMenu = new ShopAdminTiersMenu(this.plugin, this);
        this.adminPetsMenu = new ShopAdminPetsMenu(this.plugin, this);
    }

    @NotNull
    public FileConfig getConfig() {
        return FileConfig.loadOrExtract(this.plugin, FILE_NAME);
    }

    /**
     * @param tier Pet tier
     * @param config Pet config
     * @return Price for the pet egg with specified tier. If no price set, null will be returned.
     */
    @Nullable
    public EggPrice getEggPrice(@NotNull Tier tier, @NotNull Template config) {
        synchronized (this.priceLock) {
            if (this.disabledEggPetIds.contains(config.getId().toLowerCase())) return null;

            return this.eggPriceMap.getOrDefault(tier.getId(), Collections.emptyMap()).getOrDefault(config.getId(), this.defaultEggPriceMap.get(tier.getId()));
        }
    }

    @Nullable
    public EggPrice getCustomEggPrice(@NotNull Tier tier, @NotNull Template template) {
        synchronized (this.priceLock) {
            return this.eggPriceMap.getOrDefault(tier.getId(), Collections.emptyMap()).get(template.getId());
        }
    }

    /**
     * Sets a single pet's custom price.  A value of 0 removes the override and
     * restores the tier default price.  The in-memory shop is changed first;
     * the YAML write is serialized on the Folia async scheduler.
     */
    public boolean setCustomEggPrice(@NotNull Tier tier, @NotNull Template template, double price) {
        if (!Double.isFinite(price) || price < 0D) return false;

        boolean changed = false;
        synchronized (this.priceLock) {
            FileConfig config = this.priceConfig;
            if (config == null) return false;

            String path = "EggPrice.Custom." + tier.getId() + "." + template.getId();
            if (price == 0D) {
                changed = config.remove(path);
                Map<String, EggPrice> prices = this.eggPriceMap.get(tier.getId());
                if (prices != null) {
                    prices.remove(template.getId());
                    if (prices.isEmpty()) this.eggPriceMap.remove(tier.getId());
                }
            }
            else {
                String currencyId = config.getString(path + ".Currency");
                if (currencyId == null) {
                    currencyId = config.getString("EggPrice.Default." + tier.getId() + ".Currency", CurrencyId.VAULT);
                }
                currencyId = CurrencyId.reroute(currencyId);

                Currency currency = EconomyBridge.getCurrency(currencyId);
                if (currency == null) return false;

                config.set(path + ".Price", price);
                config.set(path + ".Currency", currency.getInternalId());
                config.setComments(path, "宠物品质 '" + tier.getId() + "' 中宠物 '" + template.getId() + "' 的单独价格。");
                config.setComments(path + ".Price", "该宠物蛋的售价；输入 0 可恢复品质默认价格。");
                config.setComments(path + ".Currency", "付款货币 ID，例如 vault。");

                this.eggPriceMap.computeIfAbsent(tier.getId(), ignored -> new HashMap<>())
                    .put(template.getId(), new EggPrice(currency, price));
                changed = true;
            }
        }

        if (changed) this.schedulePriceConfigSave();
        return true;
    }

    private void schedulePriceConfigSave() {
        synchronized (this.priceLock) {
            if (this.priceConfig == null) return;
            this.priceRevision++;
            if (this.priceSaveScheduled) return;
            this.priceSaveScheduled = true;
        }

        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task -> {
            try {
                while (true) {
                    synchronized (this.priceLock) {
                        if (this.priceConfig == null) return;
                        long revision = this.priceRevision;
                        this.priceConfig.saveChanges();
                        if (revision == this.priceRevision) {
                            this.priceSaveScheduled = false;
                            return;
                        }
                    }
                }
            }
            finally {
                synchronized (this.priceLock) {
                    this.priceSaveScheduled = false;
                }
            }
        });
    }

//    @Nullable
//    public EggPrice getCustomizationPrice(@NotNull EntityVariant<?> variant, @NotNull String value) {
//        return this.customsPriceMap.getOrDefault(variant.getName(), Collections.emptyMap()).get(value.toLowerCase());
//    }

    @Nullable
    public EggPrice getDefaultEggPrice(@NotNull Tier tier) {
        synchronized (this.priceLock) {
            return this.defaultEggPriceMap.get(tier.getId());
        }
    }

    public boolean isEggBuyable(@NotNull Tier tier) {
        synchronized (this.priceLock) {
            return this.eggPriceMap.containsKey(tier.getId()) || this.defaultEggPriceMap.containsKey(tier.getId());
        }
    }

    public boolean isEggBuyable(@NotNull Tier tier, @NotNull Template config) {
        EggPrice price = this.getEggPrice(tier, config);
        return price != null && price.getPrice() >= 0D;
    }

//    public boolean isCustomizationBuyable(@NotNull EntityVariant<?> variant, @NotNull String value) {
//        EggPrice price = this.getCustomizationPrice(variant, value);
//        return price != null && price.getPrice() >= 0D;
//    }

    public void openTiersMenu(@NotNull Player player) {
        this.openTiersMenu(player, false);
    }

    public void openProShop(@NotNull Player player) {
        this.openTiersMenu(player, true);
    }

    public void openTiersMenu(@NotNull Player player, boolean proShop) {
        if (this.plugin.getPetManager().isShuttingDown()) return;
        if (proShop && !this.hasProShopAccess(player)) return;

        this.tiersMenu.open(player, proShop);
    }

    public void openEggsMenu(@NotNull Player player, @NotNull Tier tier) {
        this.openEggsMenu(player, tier, false);
    }

    public void openEggsMenu(@NotNull Player player, @NotNull Tier tier, boolean proShop) {
        if (this.plugin.getPetManager().isShuttingDown()) return;
        if (!this.canPurchaseTier(player, tier, proShop)) return;

        this.eggsMenu.open(player, new ShopEggsMenu.View(tier, proShop));
    }

    public void openEggPurchaseConfirm(@NotNull Player player, @NotNull Template petConfig, @NotNull Tier tier) {
        this.openEggPurchaseConfirm(player, petConfig, tier, false);
    }

    public void openEggPurchaseConfirm(@NotNull Player player, @NotNull Template petConfig, @NotNull Tier tier, boolean proShop) {
        if (this.plugin.getPetManager().isShuttingDown()) return;
        if (!this.canPurchaseEgg(player, tier, petConfig, proShop)) return;

        this.eggConfirmMenu.open(player, new EggConfirmMenu.BuyInfo(petConfig, tier, proShop));
    }

    public boolean canPurchaseEgg(@NotNull Player player, @NotNull Tier tier, @NotNull Template template, boolean proShop) {
        return this.canPurchaseTier(player, tier, proShop) && this.isEggBuyable(tier, template);
    }

    public boolean isPlayerShopTier(@NotNull Tier tier) {
        return PLAYER_SHOP_TIER_ID.equalsIgnoreCase(tier.getId());
    }

    private boolean canPurchaseTier(@NotNull Player player, @NotNull Tier tier, boolean proShop) {
        return canAccessTier(tier.getId(), proShop,
            player.hasPermission(Perms.COMMAND_ADMIN),
            player.hasPermission(Perms.COMMAND_ADMIN_PRO_SHOP));
    }

    private boolean hasProShopAccess(@NotNull Player player) {
        return player.hasPermission(Perms.COMMAND_ADMIN) && player.hasPermission(Perms.COMMAND_ADMIN_PRO_SHOP);
    }

    static boolean canAccessTier(@NotNull String tierId, boolean proShop, boolean admin, boolean proShopPermission) {
        return proShop ? admin && proShopPermission : PLAYER_SHOP_TIER_ID.equalsIgnoreCase(tierId);
    }

    public void openAdminShop(@NotNull Player player) {
        if (this.plugin.getPetManager().isShuttingDown()) return;
        this.adminTiersMenu.open(player);
    }

    public void openAdminShopPets(@NotNull Player player, @NotNull Tier tier) {
        if (this.plugin.getPetManager().isShuttingDown()) return;
        this.adminPetsMenu.open(player, tier);
    }

    public boolean ownsMenu(@NotNull Menu menu) {
        return menu == this.tiersMenu || menu == this.eggsMenu || menu == this.eggConfirmMenu
            || menu == this.adminTiersMenu || menu == this.adminPetsMenu;
    }
}
