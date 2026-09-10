package su.nightexpress.combatpets.pet;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Difficulty;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.pet.*;
import su.nightexpress.combatpets.api.pet.event.generic.PetReleaseEvent;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.ChineseConfigMigration;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.config.Perms;
import su.nightexpress.combatpets.config.PetNameFilter;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.data.impl.PetUser;
import su.nightexpress.combatpets.hook.HookId;
import su.nightexpress.combatpets.hook.impl.LandsHook;
import su.nightexpress.combatpets.pet.impl.*;
import su.nightexpress.combatpets.pet.listener.CombatListener;
import su.nightexpress.combatpets.pet.listener.LevelledMobsListener;
import su.nightexpress.combatpets.pet.listener.PetGenericListener;
import su.nightexpress.combatpets.pet.listener.PluginLifecycleListener;
import su.nightexpress.combatpets.pet.listener.PlayerGenericListener;
import su.nightexpress.combatpets.pet.menu.*;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.nightcore.bridge.currency.Currency;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.dialog.Dialog;
import su.nightexpress.nightcore.integration.currency.EconomyBridge;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.menu.api.Menu;
import su.nightexpress.nightcore.menu.impl.AbstractMenu;
import su.nightexpress.nightcore.util.FileUtil;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.PDCUtil;
import su.nightexpress.nightcore.util.Players;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.bukkit.NightSound;
import su.nightexpress.nightcore.util.random.Rnd;
import su.nightexpress.nightcore.util.text.NightMessage;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetManager extends AbstractManager<PetsPlugin> {

    private final Map<String, Tier>     tierMap;
    private final Map<String, Template> templateMap;
    private final Map<String, Aspect>   aspectMap;
    private final Map<String, FoodCategory> foodMap;
    private final AtomicBoolean shuttingDown;
    private final Queue<ActivePet> retiredPetCleanup;
    private final PetNameFilter nameFilter;
    private PluginLifecycleListener lifecycleListener;

    private TiersMenu      tiersMenu;
    private CollectionMenu collectionMenu;
    private PetMenu        petMenu;
    private AspectsMenu    aspectsMenu;
    private ReleaseMenu    releaseMenu;
    private ReviveMenu     reviveMenu;
    private AttributeAdminTiersMenu  attributeAdminTiersMenu;
    private AttributeAdminPetsMenu   attributeAdminPetsMenu;
    private AttributeAdminValuesMenu attributeAdminValuesMenu;
    private AdminTestTiersMenu       adminTestTiersMenu;
    private AdminTestPetsMenu        adminTestPetsMenu;

    public PetManager(@NotNull PetsPlugin plugin) {
        super(plugin);
        this.tierMap = new HashMap<>();
        this.templateMap = new HashMap<>();
        this.aspectMap = new HashMap<>();
        this.foodMap = new HashMap<>();
        this.shuttingDown = new AtomicBoolean();
        this.retiredPetCleanup = new ConcurrentLinkedQueue<>();
        this.nameFilter = new PetNameFilter(plugin);
    }

    @Override
    protected void onLoad() {
        this.shuttingDown.set(false);
        this.nameFilter.load();
        this.loadAttributes();
        this.loadAspects();
        this.loadFood();
        this.loadTiers();
        this.loadPets();
        this.loadUI();
        ChineseConfigMigration.translateComments(this.plugin);

        this.addListener(new PetGenericListener(this.plugin, this));
        this.addListener(new CombatListener(this.plugin, this));
        this.addListener(new PlayerGenericListener(this.plugin, this));
        this.lifecycleListener = new PluginLifecycleListener(this.plugin, this);
        this.addListener(this.lifecycleListener);
        if (Plugins.isInstalled(HookId.LEVELLED_MOBS)) {
            this.addListener(new LevelledMobsListener(this.plugin));
        }

        this.addTask(this.plugin.createTask(this::tickPets).setSecondsInterval(1));
        this.addTask(this.plugin.createTask(this::regeneratePets).setTicksInterval(5L));
    }

    @Override
    protected void onShutdown() {
        this.shuttingDown.set(true);
        if (this.lifecycleListener != null) {
            this.lifecycleListener.unregisterPlugManGuard();
            this.lifecycleListener = null;
        }

        if (this.plugin.getServer().isStopping()) {
            // Folia has already halted region ticking when plugins are disabled during
            // a normal server stop. Snapshot the pet, prevent vanilla chunk persistence,
            // and let world teardown discard the live entity instead of removing it here.
            PetManager.getActivePets().forEach(holder -> {
                LivingEntity entity = holder.getEntity();
                if (!entity.isDead()) entity.setPersistent(false);
                holder.saveData();
                this.plugin.getDataHandler().saveUser(this.plugin.getUserManager().getOrFetch(holder.getOwner()));
                PetEntityBridge.removeRetiredHolder(holder);
            });
        }
        else {
            // PlugManX lifecycle commands already remove live pets while the plugin is enabled.
            // If a runtime disable reaches this point unexpectedly, only detach stale mappings:
            // Folia rejects newly scheduled entity work after the plugin has been disabled.
            PetManager.getActivePets().forEach(PetEntityBridge::removeRetiredHolder);
        }

        if (this.collectionMenu != null) this.collectionMenu.clear();
        if (this.petMenu != null) this.petMenu.clear();
        if (this.aspectsMenu != null) this.aspectsMenu.clear();
        if (this.releaseMenu != null) this.releaseMenu.clear();
        if (this.reviveMenu != null) this.reviveMenu.clear();
        if (this.tiersMenu != null) this.tiersMenu.clear();
        if (this.attributeAdminTiersMenu != null) this.attributeAdminTiersMenu.clear();
        if (this.attributeAdminPetsMenu != null) this.attributeAdminPetsMenu.clear();
        if (this.attributeAdminValuesMenu != null) this.attributeAdminValuesMenu.clear();
        if (this.adminTestTiersMenu != null) this.adminTestTiersMenu.clear();
        if (this.adminTestPetsMenu != null) this.adminTestPetsMenu.clear();

        // Attribute targets are kept in memory while an admin edits them.
        // Flush them before the tier map is released so PlugMan/reload cannot
        // discard a value whose async save task has not started yet.
        this.tierMap.values().forEach(tier -> {
            if (tier instanceof PetTier petTier) petTier.saveAttributeTargets();
        });

        this.tierMap.clear();
        this.templateMap.clear();
        this.aspectMap.clear();
        this.foodMap.clear();
        this.retiredPetCleanup.clear();
        this.nameFilter.clear();

        AttributeRegistry.clear();
    }

    public boolean prepareLifecycleShutdown(@NotNull Runnable ready) {
        if (!this.shuttingDown.compareAndSet(false, true)) return false;

        AtomicInteger pending = new AtomicInteger(1);
        if (this.plugin.getCaptureManager() != null) {
            pending.incrementAndGet();
            this.plugin.getCaptureManager().prepareShutdown(pending::decrementAndGet);
        }

        List<ActivePet> pets = collectLifecyclePets(getActivePets(), this.retiredPetCleanup);
        pets.forEach(holder -> {
            this.schedulePetShutdown(holder, pending);
            this.scheduleOwnerCleanup(holder, pending);
        });

        List<Player> players = List.copyOf(this.plugin.getServer().getOnlinePlayers());
        players.forEach(player -> this.schedulePlayerCleanup(player, pending));

        this.plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(this.plugin, task -> {
            if (!this.plugin.isEnabled()) {
                task.cancel();
                return;
            }
            if (pending.get() != 0) return;

            task.cancel();
            this.savePetDataAndContinue(ready);
        }, 1L, 1L);

        pending.decrementAndGet();
        return true;
    }

    private void savePetDataAndContinue(@NotNull Runnable ready) {
        Set<PetUser> users = this.plugin.getUserManager().getLoaded();
        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task -> {
            try {
                this.plugin.getDataHandler().saveUsersFully(users);
            }
            catch (RuntimeException exception) {
                this.plugin.getLogger().log(Level.SEVERE, "卸载前完整保存宠物数据失败。", exception);
            }
            finally {
                this.plugin.getServer().getGlobalRegionScheduler().run(this.plugin, scheduledTask -> {
                    if (this.plugin.isEnabled()) ready.run();
                });
            }
        });
    }

    private void schedulePetShutdown(@NotNull ActivePet holder, @NotNull AtomicInteger pending) {
        pending.incrementAndGet();
        Runnable finish = once(pending::decrementAndGet);

        try {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, holder.getEntity(), () -> {
                try {
                    holder.removeForShutdown();
                }
                finally {
                    finish.run();
                }
            }, () -> {
                PetEntityBridge.removeRetiredHolder(holder);
                finish.run();
            });
            if (!scheduled) finish.run();
        }
        catch (RuntimeException exception) {
            finish.run();
            throw exception;
        }
    }

    private void scheduleOwnerCleanup(@NotNull ActivePet holder, @NotNull AtomicInteger pending) {
        pending.incrementAndGet();
        Runnable finish = once(pending::decrementAndGet);

        try {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, holder.getOwner(), () -> {
                try {
                    holder.removeHealthBar();
                }
                finally {
                    finish.run();
                }
            }, finish);
            if (!scheduled) finish.run();
        }
        catch (RuntimeException exception) {
            finish.run();
            throw exception;
        }
    }

    private void schedulePlayerCleanup(@NotNull Player player, @NotNull AtomicInteger pending) {
        pending.incrementAndGet();
        Runnable finish = once(pending::decrementAndGet);

        try {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, player, () -> {
                try {
                    Menu menu = AbstractMenu.getMenu(player);
                    if (this.ownsMenu(menu)) {
                        player.getOpenInventory().getTopInventory().clear();
                        menu.close(player);
                    }
                }
                finally {
                    finish.run();
                }
            }, finish);
            if (!scheduled) finish.run();
        }
        catch (RuntimeException exception) {
            finish.run();
            throw exception;
        }
    }

    private boolean ownsMenu(@Nullable Menu menu) {
        if (menu == null) return false;
        if (menu == this.collectionMenu || menu == this.tiersMenu || menu == this.petMenu ||
            menu == this.aspectsMenu || menu == this.releaseMenu || menu == this.reviveMenu ||
            menu == this.attributeAdminTiersMenu || menu == this.attributeAdminPetsMenu ||
            menu == this.attributeAdminValuesMenu || menu == this.adminTestTiersMenu ||
            menu == this.adminTestPetsMenu) {
            return true;
        }
        return this.plugin.getShopManager() != null && this.plugin.getShopManager().ownsMenu(menu);
    }

    @NotNull
    private static Runnable once(@NotNull Runnable action) {
        AtomicBoolean completed = new AtomicBoolean();
        return () -> {
            if (completed.compareAndSet(false, true)) action.run();
        };
    }

    public boolean isShuttingDown() {
        return this.shuttingDown.get();
    }

    private void loadTiers() {
        File dir = new File(plugin.getDataFolder() + Config.DIR_TIERS);
        if (!dir.exists() && dir.mkdirs()) {
            PetCreator.createTiers(this.plugin);
        }

        for (File file : FileUtil.getConfigFiles(plugin.getDataFolder() + Config.DIR_TIERS)) {
            Tier tier = new PetTier(this.plugin, file);
            if (tier.load()) {
                this.tierMap.put(tier.getId(), tier);
            }
            else this.plugin.warn("宠物品质配置加载失败：'" + file.getName() + "'！");
        }

        this.plugin.info("已加载 " + this.tierMap.size() + " 个宠物品质。");
    }

    private void loadPets() {
        File dir = new File(plugin.getDataFolder() + Config.DIR_PETS);
        if (!dir.exists()) dir.mkdirs();

        // Fill only templates introduced by a newer server/plugin version.
        // PetCreator skips existing files, so administrator changes are preserved.
        PetCreator.createConfigs(this.plugin);

        for (File config : FileUtil.getConfigFiles(plugin.getDataFolder() + Config.DIR_PETS)) {
            Template petConfig = new PetTemplate(this.plugin, config);
            if (petConfig.load()) {
                this.templateMap.put(petConfig.getId(), petConfig);
            }
            else this.plugin.warn("宠物配置加载失败：'" + config.getName() + "'！");
        }

        this.plugin.info("已加载 " + this.templateMap.size() + " 个宠物配置。");
    }

    private void loadFood() {
        FileConfig config = this.plugin.getConfig();
        List<PetFoodCategory> defaults = PetCreator.getDefaultFoods();
        boolean changed = false;
        for (PetFoodCategory category : defaults) {
            String path = "Food." + category.getId();
            if (config.contains(path)) continue;

            category.write(config, path);
            changed = true;
        }
        if (changed) config.saveChanges();

        config.getSection("Food").forEach(sId -> {
            PetFoodCategory category = PetFoodCategory.read(config, "Food." + sId, sId);
            this.foodMap.put(category.getId(), category);
        });

        this.plugin.info("已加载 " + this.foodMap.size() + " 个食物类别。");
    }

    private void loadAspects() {
        FileConfig config = this.plugin.getConfig();
        if (!config.contains("Aspects")) {
            PetCreator.getDefaultAspects().forEach(aspect -> aspect.write(config, "Aspects." + aspect.getId()));
        }

        config.getSection("Aspects").forEach(sId -> {
            Aspect aspect = PetAspect.read(config, "Aspects." + sId, sId);
            this.aspectMap.put(aspect.getId(), aspect);
        });

        this.plugin.info("已加载 " + this.aspectMap.size() + " 个宠物属性。");
    }

    private void loadAttributes() {
        this.loadAttribute(AttributeRegistry.ARMOR, Attribute.ARMOR);
        this.loadAttribute(AttributeRegistry.ATTACK_DAMAGE, Attribute.ATTACK_DAMAGE);
        this.loadAttribute(AttributeRegistry.ATTACK_KNOCKBACK, Attribute.ATTACK_KNOCKBACK);
        this.loadAttribute(AttributeRegistry.ATTACK_SPEED, Attribute.ATTACK_SPEED);
        this.loadAttribute(AttributeRegistry.FLYING_SPEED, Attribute.FLYING_SPEED);
        this.loadAttribute(AttributeRegistry.HEALTH_REGENEATION_FORCE);
        this.loadAttribute(AttributeRegistry.HEALTH_REGENEATION_SPEED);
        this.loadAttribute(AttributeRegistry.HORSE_JUMP_STRENGTH, Attribute.JUMP_STRENGTH);
        this.loadAttribute(AttributeRegistry.KNOCKBACK_RESISTANCE, Attribute.KNOCKBACK_RESISTANCE);
        this.loadAttribute(AttributeRegistry.MAX_HEALTH, Attribute.MAX_HEALTH);
        this.loadAttribute(AttributeRegistry.MAX_SATURATION);
        this.loadAttribute(AttributeRegistry.MOVEMENT_SPEED, Attribute.MOVEMENT_SPEED);
            //this.loadAttribute(AttributeRegistry.ATTACK_REACH, Attribute.valueOf("GENERIC_ATTACK_REACH"));
        this.loadAttribute(AttributeRegistry.BURNING_TIME, Attribute.BURNING_TIME);
        this.loadAttribute(AttributeRegistry.EXPLOSION_KNOCKBACK_RESISTANCE, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE);
        this.loadAttribute(AttributeRegistry.FALL_DAMAGE_MULTIPLIER, Attribute.FALL_DAMAGE_MULTIPLIER);
        this.loadAttribute(AttributeRegistry.GRAVITY, Attribute.GRAVITY);
        this.loadAttribute(AttributeRegistry.MOVEMENT_EFFICIENCY, Attribute.MOVEMENT_EFFICIENCY);
        this.loadAttribute(AttributeRegistry.SAFE_FALL_DISTANCE, Attribute.SAFE_FALL_DISTANCE);
        this.loadAttribute(AttributeRegistry.SCALE, Attribute.SCALE);
        this.loadAttribute(AttributeRegistry.STEP_HEIGHT, Attribute.STEP_HEIGHT);
        this.loadAttribute(AttributeRegistry.WATER_MOVEMENT_EFFICIENCY, Attribute.WATER_MOVEMENT_EFFICIENCY);
    }

    private void loadAttribute(@NotNull String name) {
        NamespacedKey key = new NamespacedKey(this.plugin, "attribute." + name.toLowerCase());
        this.loadAttribute(new PetAttribute(name, key));
    }

    private void loadAttribute(@NotNull String name, @NotNull Attribute vanillaMirror) {
        this.loadAttribute(new PetAttribute(name, vanillaMirror));
    }

    private void loadAttribute(@NotNull PetAttribute attribute) {
        FileConfig config = this.plugin.getConfig();

        attribute.read(config, "Attributes." + attribute.getId());

        AttributeRegistry.register(attribute);
        this.plugin.info("已注册宠物属性 '" + attribute.getId() + "'。");
    }

    private void loadUI() {
        this.collectionMenu = new CollectionMenu(this.plugin);
        this.tiersMenu = new TiersMenu(this.plugin);
        this.petMenu = new PetMenu(this.plugin);
        this.aspectsMenu = new AspectsMenu(this.plugin);
        this.releaseMenu = new ReleaseMenu(this.plugin);
        this.reviveMenu = new ReviveMenu(this.plugin);
        this.attributeAdminTiersMenu = new AttributeAdminTiersMenu(this.plugin, this);
        this.attributeAdminPetsMenu = new AttributeAdminPetsMenu(this.plugin, this);
        this.attributeAdminValuesMenu = new AttributeAdminValuesMenu(this.plugin, this);
        this.adminTestTiersMenu = new AdminTestTiersMenu(this.plugin, this);
        this.adminTestPetsMenu = new AdminTestPetsMenu(this.plugin, this);
    }

    public void regeneratePets() {
        this.runForActivePets(holder -> holder.doRegenerate(EntityRegainHealthEvent.RegainReason.REGEN));
    }

    public void tickPets() {
        this.runForActivePets(ActivePet::tickPet);
    }

    private void runForActivePets(@NotNull java.util.function.Consumer<ActivePet> action) {
        if (this.isShuttingDown()) return;

        ActivePet retired;
        while ((retired = this.retiredPetCleanup.poll()) != null) {
            PetScheduler.runAtEntity(this.plugin, retired.getOwner(), retired::removeHealthBar);
        }

        getActivePets().forEach(holder -> {
            boolean scheduled = PetScheduler.runAtEntity(this.plugin, holder.getEntity(), () -> {
                if (this.plugin.isEnabled()) {
                    action.accept(holder);
                }
            }, () -> this.handleRetiredPet(holder));

            // Folia returns false when the entity scheduler is already retired
            // and does not invoke the retired callback in that case.
            if (!scheduled) {
                this.handleRetiredPet(holder);
            }
        });
    }

    private void handleRetiredPet(@NotNull ActivePet holder) {
        // Publish the retired holder before removing it from the active registry.
        // Together with collectLifecyclePets() reading active pets before this
        // queue, this guarantees that a concurrent shutdown sees it in one place.
        this.retiredPetCleanup.offer(holder);
        PetEntityBridge.removeRetiredHolder(holder);
    }

    @NotNull
    static List<ActivePet> collectLifecyclePets(@NotNull Collection<ActivePet> activePets,
                                                @NotNull Queue<ActivePet> retiredPets) {
        Set<ActivePet> pets = new LinkedHashSet<>(activePets);

        ActivePet retired;
        while ((retired = retiredPets.poll()) != null) {
            pets.add(retired);
        }
        return List.copyOf(pets);
    }

    @NotNull
    public Map<String, Tier> getTierMap() {
        return this.tierMap;
    }

    @NotNull
    public Set<Tier> getTiers() {
        return new HashSet<>(this.tierMap.values());
    }

    @NotNull
    public List<String> getTierIds() {
        return new ArrayList<>(this.tierMap.keySet());
    }

    @Nullable
    public Tier getTier(@NotNull String id) {
        return this.tierMap.get(id.toLowerCase());
    }

    public void scheduleTierAttributeSave(@NotNull Tier tier) {
        if (!(tier instanceof PetTier petTier) || !this.plugin.isEnabled()) return;

        this.plugin.getServer().getAsyncScheduler().runNow(this.plugin, task -> {
            try {
                petTier.saveAttributeTargets();
            }
            catch (RuntimeException exception) {
                this.plugin.getLogger().log(Level.SEVERE, "保存宠物属性目标值失败。", exception);
            }
        });
    }

    /** Recalculates already summoned pets affected by an administrator attribute edit. */
    public void refreshAttributeTarget(@NotNull Tier tier, @NotNull Template template) {
        if (this.isShuttingDown()) return;

        getActivePets().stream()
            .filter(holder -> holder.getTier() == tier && holder.getTemplate() == template)
            .forEach(holder -> PetScheduler.runAtEntity(this.plugin, holder.getEntity(), holder::update));
    }

    @NotNull
    public Tier getTierByWeight() {
        Map<Tier, Double> map = new HashMap<>();
        this.getTiers().forEach(tier -> {
            map.put(tier, tier.getWeight());
        });
        return Rnd.getByWeight(map);
    }

    @NotNull
    public Map<String, Template> getTemplateMap() {
        return this.templateMap;
    }

    @NotNull
    public Set<Template> getTemplates() {
        return new HashSet<>(this.templateMap.values());
    }

    @NotNull
    public List<String> getTemplateIds() {
        return new ArrayList<>(this.templateMap.keySet());
    }

    @Nullable
    public Template getTemplate(@NotNull String id) {
        return this.templateMap.get(id.toLowerCase());
    }

    @Nullable
    public Template getTemplate(@NotNull LivingEntity entity) {
        return this.getTemplates().stream()
            .filter(config -> config.getEntityType() == entity.getType()).findAny().orElse(null);
    }

    @NotNull
    public Set<Aspect> getAspects() {
        return new HashSet<>(this.aspectMap.values());
    }

    @Nullable
    public Aspect getAspect(@NotNull String id) {
        return this.aspectMap.get(id.toLowerCase());
    }

    @Nullable
    public FoodItem getFoodItem(@NotNull String categoryName, @NotNull String itemName) {
        FoodCategory category = this.getFoodCategory(categoryName);
        if (category == null) return null;

        return category.getItem(itemName.toLowerCase());
    }

    @Nullable
    public FoodItem getFoodItem(@NotNull ItemStack itemStack) {
        return this.getFoodCategories().stream().map(category -> category.getItem(itemStack)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    @Nullable
    public FoodCategory getFoodCategory(@NotNull String id) {
        return this.foodMap.get(id.toLowerCase());
    }

    @NotNull
    public Set<FoodCategory> getFoodCategories() {
        return new HashSet<>(this.foodMap.values());
    }

    @NotNull
    public Set<FoodItem> getFoodItems() {
        return this.getFoodCategories().stream().flatMap(category -> category.getItems().stream()).collect(Collectors.toSet());
    }

    @NotNull
    public List<String> getFoodCategoryNames() {
        return new ArrayList<>(this.foodMap.keySet());
    }

    @NotNull
    public static Collection<ActivePet> getActivePets() {
        return PetEntityBridge.getAll();
    }

    @Nullable
    public ActivePet getPlayerPet(@NotNull Player player) {
        return PetEntityBridge.getByPlayer(player);
    }

    @Nullable
    public ActivePet getPetByMob(@NotNull LivingEntity entity) {
        return PetEntityBridge.getByMob(entity);
    }

    public boolean isPetEntity(@NotNull LivingEntity entity) {
        return PetEntityBridge.isPet(entity) || PDCUtil.getBoolean(entity, Keys.petEntity).orElse(false);
    }

    public boolean hasActivePet(@NotNull Player player) {
        return this.getPlayerPet(player) != null;
    }


    public void openTierCollection(@NotNull Player player) {
        if (this.isShuttingDown()) return;
        if (plugin.getUserManager().getLoaded(player) == null) {
            Lang.PET_ERROR_DATA_LOADING.message().send(player);
            return;
        }

        this.tiersMenu.open(player);
    }

    public void openPetsCollection(@NotNull Player player, @NotNull Tier tier) {
        if (this.isShuttingDown()) return;
        if (plugin.getUserManager().getLoaded(player) == null) {
            Lang.PET_ERROR_DATA_LOADING.message().send(player);
            return;
        }

        this.collectionMenu.open(player, tier);
    }

    public void openOverviewMenu(@NotNull Player player) {
        if (this.isShuttingDown()) return;
        this.petMenu.open(player);
    }

    public void openAspectsMenu(@NotNull Player player) {
        if (this.isShuttingDown()) return;
        this.aspectsMenu.open(player);
    }

    public void openAdminAttributes(@NotNull Player player) {
        if (this.isShuttingDown()) return;
        this.attributeAdminTiersMenu.open(player);
    }

    public void openAdminAttributePets(@NotNull Player player, @NotNull Tier tier) {
        if (this.isShuttingDown()) return;
        this.attributeAdminPetsMenu.open(player, tier);
    }

    public void openAdminAttributeValues(@NotNull Player player, @NotNull Tier tier, @NotNull Template template) {
        if (this.isShuttingDown()) return;
        this.attributeAdminValuesMenu.open(player, new AttributeAdminValuesMenu.Context(tier, template));
    }

    public void openAdminTestMenu(@NotNull Player player) {
        if (this.isShuttingDown() || !player.hasPermission(Perms.COMMAND_ADMIN) ||
            !player.hasPermission(Perms.COMMAND_ADMIN_MENU)) return;
        this.adminTestTiersMenu.open(player);
    }

    public void openAdminTestPets(@NotNull Player player, @NotNull Tier tier) {
        if (this.isShuttingDown() || !player.hasPermission(Perms.COMMAND_ADMIN) ||
            !player.hasPermission(Perms.COMMAND_ADMIN_MENU)) return;
        this.adminTestPetsMenu.open(player, tier);
    }

    public void openReleaseMenu(@NotNull Player player, @NotNull PetData data) {
        if (this.isShuttingDown()) return;
        this.releaseMenu.open(player, data);
    }

    public void openReviveMenu(@NotNull Player player, @NotNull PetData data) {
        if (this.isShuttingDown()) return;
        this.reviveMenu.open(player, data);
    }

    public boolean openPetMenu(@NotNull Player player) {
        ActivePet petHolder = getPlayerPet(player);
        if (petHolder == null) {
            Lang.PET_ERROR_NO_ACTIVE_PET.message().send(player);
            return false;
        }

        petHolder.openMenu();
        return true;
    }

    public static int getPetsLimit(@NotNull Player player) {
        return Config.PET_AMOUNT_PER_RANK.get().getGreatestOrNegative(player);
    }

    public boolean spawnPet(@NotNull Player player, @NotNull Tier tier, @NotNull Template config) {
        PetUser user = plugin.getUserManager().getOrFetch(player);
        PetData petData = user.getPet(config, tier);
        if (petData == null) return false;

        return this.spawnPet(player, petData);
    }

    public boolean spawnPet(@NotNull Player player, @NotNull PetData petData) {
        if (this.isShuttingDown()) return false;

        if (this.hasActivePet(player)) {
            Lang.PET_SPAWN_ERROR_ALREADY.message().send(player);
            return false;
        }
        if (petData.isDead()) {
            Lang.PET_SPAWN_ERROR_DEAD.message().send(player);
            return false;
        }
        if (!PetUtils.isAllowedPetZone(player.getLocation())) {
            Lang.PET_ERROR_BAD_WORLD.message().send(player);
            return false;
        }

        Template template = petData.getTemplate();
        if (player.getWorld().getDifficulty() == Difficulty.PEACEFUL &&
            !this.plugin.getPetNMS().isAllowedInPeaceful(template.getEntityType())) {
            Lang.PET_SPAWN_ERROR_PEACEFUL.message().send(player);
            return false;
        }

        Location location = player.getLocation().clone();
        Vector direction = location.getDirection();
        location.add(0, 1, 0).add(direction.multiply(1.5));
        Block block = location.getBlock();
        if (!block.isEmpty() && block.getType().isSolid()) {
            Lang.PET_SPAWN_ERROR_BAD_PLACE.message().send(player);
            return false;
        }

        LandsHook landsHook = this.plugin.getLandsHook();
        if (landsHook != null && !landsHook.canSummon(player, location)) {
            Lang.PET_SPAWN_ERROR_PROTECTED_AREA.message().send(player);
            return false;
        }

        location.getChunk(); // Force load chunk before pet spawn.

        ActivePet pet = plugin.getPetNMS().spawnPet(template, location, entity -> new PetInstance(this.plugin, player, entity, petData));
        pet.handleSpawn();

        return true;
    }

    public boolean spawnTestPet(@NotNull Player player, @NotNull Tier tier, @NotNull Template template) {
        if (!player.hasPermission(Perms.COMMAND_ADMIN) || !player.hasPermission(Perms.COMMAND_ADMIN_MENU)) return false;
        return this.spawnPet(player, PetData.create(template, tier));
    }

    public void handleDeath(@NotNull ActivePet holder) {
        Player player = holder.getOwner();
        PetUser user = this.plugin.getUserManager().getOrFetch(player);

        holder.handleDeath();

        this.plugin.getUserManager().save(user);
    }

    public void despawnPet(@NotNull Player player) {
        ActivePet holder = getPlayerPet(player);
        if (holder == null) return;

        PetScheduler.runAtEntity(this.plugin, holder.getEntity(), () -> this.despawnPet(holder));
    }

    /**
     * Despawns a player's pet and runs the continuation only after the entity
     * scheduler has finished removing it. This keeps GUI transitions ordered
     * on Folia instead of racing a global next-tick refresh.
     */
    public void despawnPet(@NotNull Player player, @NotNull Runnable after) {
        ActivePet holder = getPlayerPet(player);
        if (holder == null) {
            PetScheduler.runAtEntity(this.plugin, player, after);
            return;
        }

        AtomicBoolean completed = new AtomicBoolean();
        Runnable complete = () -> {
            if (!completed.compareAndSet(false, true)) return;
            PetScheduler.runAtEntity(this.plugin, player, after);
        };

        boolean scheduled = PetScheduler.runAtEntity(this.plugin, holder.getEntity(), () -> {
            this.despawnPet(holder);
            complete.run();
        }, complete);
        if (!scheduled) complete.run();
    }

    public void despawnPet(@NotNull ActivePet holder) {
        this.removePetAndSave(holder);
        Player owner = holder.getOwner();
        PetScheduler.runAtEntity(this.plugin, owner, () -> Lang.PET_DESPAWN_DEFAULT.message().send(owner));
    }

    public void removePet(@NotNull ActivePet holder) {
        holder.remove();
    }

    public void removePetAndSave(@NotNull ActivePet holder) {
        this.removePet(holder);
        PetUser user = this.plugin.getUserManager().getOrFetch(holder.getOwner());
        this.plugin.getUserManager().save(user);
    }

    @NotNull
    public PetData addToCollection(@NotNull PetUser user, @NotNull Tier tier, @NotNull Template template) {
        PetData petData = PetData.create(template, tier);
        user.addPet(petData);
        this.plugin.getUserManager().save(user);

        return petData;
    }

    public void removeFromCollection(@NotNull PetUser user, @NotNull Tier tier, @NotNull Template template) {
        Player player = user.getPlayer();
        ActivePet activePet = player == null ? null : this.getPlayerPet(player);
        if (activePet != null && activePet.getTier() == tier && activePet.getTemplate() == template) {
            this.removePet(activePet);
        }
        user.removePet(template, tier);
        this.plugin.getUserManager().save(user);
    }

    @Nullable
    public PetData tryClaimPet(@NotNull Player player, @NotNull Tier tier, @NotNull Template config) {
        PetUser user = this.plugin.getUserManager().getLoaded(player);
        if (user == null) {
            Lang.PET_ERROR_DATA_LOADING.message().send(player);
            return null;
        }

        if (user.hasPet(config, tier)) {
            Lang.PET_CLAIM_ERROR_ALREADY_HAVE.message().send(player);
            return null;
        }

        int petLimit = PetManager.getPetsLimit(player);
        int petCollected = user.getPets(tier).size();
        if (petLimit > 0 && petCollected >= petLimit) {
            Lang.PET_CLAIM_ERROR_REACHED_LIMIT.message().send(player, replacer -> replacer
                .replace(tier.replacePlaceholders())
                .replace(Placeholders.GENERIC_AMOUNT, petLimit)
            );
            return null;
        }

        PetData petData = this.addToCollection(user, tier, config);
        Lang.PET_CLAIM_SUCCESS.message().send(player);
        return petData;
    }

    public boolean revivePet(@NotNull Player player, @NotNull PetData petData) {
        Tier tier = petData.getTier();

        Currency currency = EconomyBridge.getCurrency(tier.getReviveCurrency());
        if (currency == null) return false;

        double cost = tier.getReviveCost();

        if (cost > 0) {
            if (currency.getBalance(player) < cost) {
                Lang.PET_REVIVE_ERROR_NOT_ENOUGH_FUNDS.message().send(player, replacer -> replacer
                    .replace(Placeholders.PET_NAME, petData.getName())
                    .replace(Placeholders.GENERIC_AMOUNT, currency.format(cost))
                );
                return false;
            }
            currency.take(player, cost);
        }

        petData.revive();

        Lang.PET_REVIVE_SUCCESS.message().send(player, replacer -> replacer
            .replace(Placeholders.PET_NAME, petData.getName())
            .replace(Placeholders.GENERIC_AMOUNT, currency.format(cost))
        );
        return true;
    }

    public boolean releasePet(@NotNull Player player, @NotNull PetData petData) {
        if (!Config.PET_RELEASE_ALLOWED.get() && !player.hasPermission(Perms.BYPASS_RELEASE_DISABLED)) {
            Lang.ERROR_NO_PERMISSION.withPrefix(this.plugin).send(player);
            return false;
        }

        if (Config.PET_RELEASE_DISABLED_WORLDS.get().contains(player.getWorld().getName())) {
            Lang.PET_RELEASE_ERROR_BAD_WORLD.message().send(player);
            return false;
        }

        Template template = petData.getTemplate();
        Tier tier = petData.getTier();

        PetReleaseEvent event = new PetReleaseEvent(player, tier, template);
        this.plugin.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        if (Config.PET_RELEASE_NATURAL.get()) {
            Location location = player.getLocation();
            Block against = location.getBlock();
            Block placed = against.getRelative(BlockFace.UP);
            ItemStack item = new ItemStack(Material.STONE);
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(placed, placed.getState(), against, item, player, true, EquipmentSlot.HAND);
            plugin.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) {
                Lang.PET_RELEASE_ERROR_PROTECTED_AREA.message().send(player);
                return false;
            }

            if (PetUtils.isAllowedPetZone(location) && petData.isAlive()) {
                Entity entity = player.getWorld().spawnEntity(location, template.getEntityType());
                if (entity instanceof LivingEntity livingEntity) {
                    petData.getWardrobe().dressUp(livingEntity);
                }
                entity.setVelocity(location.getDirection().multiply(3));

            }
        }

        this.removeFromCollection(plugin.getUserManager().getOrFetch(player), tier, template);
        Lang.PET_RELEASE_SUCCESS.message().send(player, replacer -> replacer.replace(petData.replacePlaceholders()));
        return true;
    }

    public boolean tryInteract(@NotNull Player player, @NotNull LivingEntity entity) {
        ActivePet petHolder = this.getPetByMob(entity);
        if (petHolder == null) return false;

        if (!petHolder.isOwner(player)) {
            if (entity.getType().name().equals("HAPPY_GHAST") && !player.isSneaking()) {
                entity.addPassenger(player);
                return true;
            }
            Lang.PET_ERROR_NOT_YOUR.message().send(player);
            return true;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        FoodItem foodItem = this.getFoodItem(handItem);
        if (foodItem != null) {
            boolean compatibleFood = petHolder.getTemplate().isFood(foodItem);
            if (compatibleFood && petHolder.doFeed(foodItem)) {
                handItem.setAmount(handItem.getAmount() - 1);
            }
            if (compatibleFood || Config.PET_FOOD_BLOCK_MENU_WHEN_INCOMPATIBLE.get()) return true;
        }

        if (handItem.getType() == Material.NAME_TAG && Config.PET_NAME_RENAME_ALLOW_NAMETAGS.get()) {
            ItemMeta meta = handItem.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                if (this.tryRename(player, meta.getDisplayName())) {
                    handItem.setAmount(handItem.getAmount() - 1);
                }
            }
            return true;
        }

        if (petHolder.isEquipmentUnlocked() && !handItem.getType().isAir()) {
            EntityEquipment equipment = entity.getEquipment();
            if (equipment != null) {
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (ItemUtil.isHelmet(handItem)) slot = EquipmentSlot.HEAD;
                else if (ItemUtil.isChestplate(handItem)) slot = EquipmentSlot.CHEST;
                else if (ItemUtil.isLeggings(handItem)) slot = EquipmentSlot.LEGS;
                else if (ItemUtil.isBoots(handItem)) slot = EquipmentSlot.FEET;
                else if (handItem.getType() == Material.SHIELD) slot = EquipmentSlot.OFF_HAND;

                if (equipment.getItem(slot).getType().isAir()) {
                    equipment.setItem(slot, new ItemStack(handItem));
                    handItem.setAmount(0);

                    petHolder.saveData();
                    PetUser user = this.plugin.getUserManager().getOrFetch(player);
                    this.plugin.getUserManager().save(user);
                    NightSound.of(Sound.ITEM_ARMOR_EQUIP_LEATHER).play(entity.getLocation());
                }
                return true;
            }
        }

        String entityType = entity.getType().name();
        boolean isRideable = entity instanceof AbstractHorse || entityType.equals("STRIDER") ||
            entityType.equals("NAUTILUS") || entityType.equals("ZOMBIE_NAUTILUS") ||
            entityType.equals("HAPPY_GHAST");
        boolean needSneak = Config.PET_SNEAK_TO_OPEN_MENU.get();
        if (isRideable) needSneak = !needSneak;

        boolean isMenu = needSneak == player.isSneaking();

        if (isMenu) {
            petHolder.openMenu();
        }
        else if (isRideable) {
            entity.addPassenger(player);
        }
        return true;
    }

    public boolean startRename(@NotNull Player player) {
        if (!this.hasActivePet(player)) {
            Lang.PET_ERROR_NO_ACTIVE_PET.message().send(player);
            return false;
        }

        boolean nametagRequired = Config.PET_NAME_RENAME_MENU_REQUIRES_NAMETAG.get();
        if (nametagRequired) {
            if (Players.countItem(player, Material.NAME_TAG) < 1) {
                Lang.PET_RENAME_ERROR_NO_NAMETAG.message().send(player);
                return false;
            }
            Players.takeItem(player, Material.NAME_TAG, 1);
        }

        Lang.PET_RENAME_PROMPT.message().send(player);
        Dialog.create(player, (dialog, input) -> {
            String name = input.getText();
            PetScheduler.runAtEntity(this.plugin, player, () -> {
                if (!this.tryRename(player, name, () -> this.refreshOverviewMenu(player)) && nametagRequired) {
                    Players.addItem(player, new ItemStack(Material.NAME_TAG));
                }
            });
            return true;
        });

        return true;
    }

    public boolean tryRename(@NotNull Player player, @NotNull String name) {
        return this.tryRename(player, name, () -> {});
    }

    private boolean tryRename(@NotNull Player player, @NotNull String name, @NotNull Runnable onRenamed) {
        ActivePet holder = this.getPlayerPet(player);
        if (holder == null) {
            Lang.PET_ERROR_NO_ACTIVE_PET.message().send(player);
            return false;
        }

        String rawName = NightMessage.stripAll(name);
        if (rawName.isEmpty()) return false;

        if (!player.hasPermission(Perms.BYPASS_NAME_WORDS)) {
            if (this.nameFilter.isForbidden(rawName)) {
                Lang.PET_RENAME_ERROR_FORBIDDEN.message().send(player);
                return false;
            }
        }

        if (!player.hasPermission(Perms.BYPASS_NAME_LENGTH)) {
            int max = Config.PET_NAME_LENGTH_MAX.get();
            if (rawName.length() > max) {
                Lang.PET_RENAME_ERROR_TOO_LONG.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_AMOUNT, max));
                return false;
            }

            int min = Config.PET_NAME_LENGTH_MIN.get();
            if (rawName.length() < min) {
                Lang.PET_RENAME_ERROR_TOO_SHORT.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_AMOUNT, min));
                return false;
            }
        }

        return PetScheduler.runAtEntity(this.plugin, holder.getEntity(), () -> {
            holder.setName(name);
            PetScheduler.runAtEntity(this.plugin, player, () -> {
                if (!this.plugin.isEnabled() || !player.isOnline()) return;

                Lang.PET_RENAME_SUCCESS.message().send(player, replacer -> replacer.replace(Placeholders.PET_NAME, name));
                onRenamed.run();
            });
        }, null);
    }

    private void refreshOverviewMenu(@NotNull Player player) {
        if (AbstractMenu.getMenu(player) == this.petMenu) {
            this.petMenu.flush(player);
        }
    }

    public boolean canDamage(@NotNull LivingEntity damager, @NotNull LivingEntity victim) {
        ActivePet damagerPet = this.getPetByMob(damager);
        if (damagerPet != null) {
            return this.canBeDamaged(damagerPet, victim);
        }

        ActivePet victimPet = this.getPetByMob(victim);
        if (victimPet != null) {
            return this.canBeDamaged(victimPet, damager);
        }

        return true;
    }

    public boolean canBeDamaged(@NotNull ActivePet pet, @NotNull LivingEntity entity) {
        if (entity == pet.getOwner()) return false;
        if (entity == pet.getEntity()) return true;
        if (Config.PET_PVP_ALLOWED.get()) return true;

        return (!(entity instanceof Player) && !this.isPetEntity(entity));
    }
}
