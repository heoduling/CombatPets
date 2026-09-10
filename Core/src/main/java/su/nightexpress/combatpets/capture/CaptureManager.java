package su.nightexpress.combatpets.capture;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.api.pet.event.capture.PetCaptureStartEvent;
import su.nightexpress.combatpets.capture.config.CaptureConfig;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.config.Perms;
import su.nightexpress.combatpets.hook.HookId;
import su.nightexpress.combatpets.hook.impl.MythicMobsHook;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.EntityUtil;
import su.nightexpress.nightcore.util.PDCUtil;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.nightcore.util.random.Rnd;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CaptureManager extends AbstractManager<PetsPlugin> {

    private static final String FILE_NAME = "capturing.yml";
    private static final int DISABLED_ENTITY_TYPES_CONFIG_VERSION = 1;

    private final Map<UUID, CaptureTask> captureMap;

    public CaptureManager(@NotNull PetsPlugin plugin) {
        super(plugin);
        this.captureMap = new ConcurrentHashMap<>();
    }

    @Override
    protected void onLoad() {
        this.loadConfig();

        this.addListener(new CaptureListener(this.plugin, this));
    }

    @Override
    protected void onShutdown() {
        this.captureMap.values().forEach(CaptureTask::stop);
        this.captureMap.clear();
    }

    public void prepareShutdown(@NotNull Runnable completion) {
        List<CaptureTask> tasks = List.copyOf(this.captureMap.values());
        if (tasks.isEmpty()) {
            completion.run();
            return;
        }

        AtomicInteger remaining = new AtomicInteger(tasks.size());
        tasks.forEach(task -> task.stop(() -> {
            if (remaining.decrementAndGet() == 0) completion.run();
        }));
    }

    @NotNull
    public FileConfig getConfig() {
        return FileConfig.loadOrExtract(this.plugin, FILE_NAME);
    }

    private void loadConfig() {
        FileConfig config = this.getConfig();
        String path = "Settings.Disabled_Entity_Types";
        String versionPath = "Settings.Disabled_Entity_Types_Version";
        int version = config.getInt(versionPath, 0);
        boolean hadDisabledTypes = config.contains(path);

        config.initializeOptions(CaptureConfig.class);
        if (hadDisabledTypes && version < DISABLED_ENTITY_TYPES_CONFIG_VERSION) {
            List<String> entityTypes = new ArrayList<>(config.getStringList(path));
            if (entityTypes.stream().noneMatch("illusioner"::equalsIgnoreCase)) {
                entityTypes.add("illusioner");
                config.set(path, entityTypes);
            }
        }
        config.set(versionPath, DISABLED_ENTITY_TYPES_CONFIG_VERSION);
        config.setComments(versionPath, "默认禁捕名单的自动迁移版本，请勿手动修改。");
        config.saveChanges();
    }

    public void tickCaptures() {
        // Kept for API compatibility with older integrations. CaptureTask now self-schedules
        // on the target entity's scheduler instead of touching entities from a global task.
    }

    public boolean isCapturing(@NotNull Player player) {
        return this.getCaptureProcess(player.getUniqueId()) != null;
    }

    public boolean isBeingCaptured(@NotNull LivingEntity entity) {
        return this.captureMap.values().stream().anyMatch(captureTask -> captureTask.getEntity() == entity);
    }

    @Nullable
    public CaptureTask getCaptureProcess(@NotNull UUID playerId) {
        return this.captureMap.get(playerId);
    }

    public boolean tryCapture(@NotNull Player player, @NotNull LivingEntity entity, @NotNull ItemStack itemStack) {
        if (this.plugin.getPetManager().isShuttingDown()) return false;

        if (!PetUtils.isAllowedPetZone(entity.getLocation())) {
            Lang.PET_ERROR_BAD_WORLD.message().send(player);
            return false;
        }

        String name = entity.getCustomName();
        String localized = name == null ? PetCreator.getEntityName(entity.getType()) : name;

        if (!this.canBeCaptured(entity)) {
            Lang.CAPTURE_ERROR_NOT_CAPTURABLE.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_NAME, localized));
            return false;
        }

        Template template = this.plugin.getPetManager().getTemplate(entity);
        if (template == null || !template.isCapturable()) {
            Lang.CAPTURE_ERROR_NOT_CAPTURABLE.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_NAME, localized));
            return false;
        }

        if (!player.hasPermission(Perms.CAPTURE) && !player.hasPermission(template.getCapturePermission())) {
            Lang.CAPTURE_ERROR_PERMISSION.message().send(player, replacer -> replacer.replace(Placeholders.GENERIC_NAME, localized));
            return false;
        }

        if (!this.isReadyToCapture(entity)) {
            Lang.CAPTURE_ERROR_NOT_READY.message().send(player, replacer -> replacer
                .replace(Placeholders.GENERIC_NAME, localized)
                .replace(Placeholders.GENERIC_AMOUNT, CaptureConfig.CAPTURE_HEALTH_PERCENT.get()));
            return false;
        }

        if (this.isCapturing(player)) {
            Lang.CAPTURE_ERROR_ALREADY.message().send(player);
            return false;
        }

        if (this.plugin.getPetManager().getTiers().stream().noneMatch(Tier::isCapturable)) {
            Lang.CAPTURE_ERROR_NO_TIER.message().send(player);
            return false;
        }

        if (this.startCapture(player, entity, template)) {
            if (CaptureConfig.CAPTURE_CONSUME_ITEM.get()) {
                itemStack.setAmount(itemStack.getAmount() - 1);
            }
            return true;
        }

        Lang.CAPTURE_ERROR_START.message().send(player);
        return false;
    }

    public boolean startCapture(@NotNull Player player, @NotNull LivingEntity entity, @NotNull Template petConfig) {
        if (this.plugin.getPetManager().isShuttingDown()) return false;
        if (this.isCapturing(player)) return false;

        Tier tier = this.getTierByWeight();
        if (tier == null) return false;

        PetCaptureStartEvent event = new PetCaptureStartEvent(player, entity, petConfig, tier);
        plugin.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        CaptureTask process = new CaptureTask(plugin, this, player, entity, petConfig, tier);
        if (this.captureMap.putIfAbsent(player.getUniqueId(), process) != null) return false;

        if (!process.start()) {
            this.captureMap.remove(player.getUniqueId(), process);
            return false;
        }
        return true;
    }

    public void stopCapture(@NotNull UUID playerId) {
        CaptureTask process = this.getCaptureProcess(playerId);
        if (process == null) return;

        process.stop();
        this.captureMap.remove(playerId, process);
    }

    void removeTask(@NotNull CaptureTask task) {
        this.captureMap.remove(task.getPlayerId(), task);
    }

    @Nullable
    public Tier getTierByWeight() {
        Map<Tier, Double> map = new HashMap<>();
        this.plugin.getPetManager().getTiers().forEach(tier -> {
            if (tier.isCapturable()) {
                map.put(tier, tier.getWeight());
            }
        });
        return map.isEmpty() ? null : Rnd.getByWeight(map);
    }

    @NotNull
    public ItemStack createCaptureItem() {
        ItemStack itemStack = CaptureConfig.CAPTURE_ITEM.get().getItemStack();
        PDCUtil.set(itemStack, Keys.captureItem, true);
        return itemStack;
    }

    public boolean isCaptureItem(@NotNull ItemStack itemStack) {
        return PDCUtil.getBoolean(itemStack, Keys.captureItem).orElse(false);
    }

    public boolean isReadyToCapture(@NotNull LivingEntity entity) {
        double maxPercent = CaptureConfig.CAPTURE_HEALTH_PERCENT.get();
        if (maxPercent <= 0D || maxPercent >= 100D) return true;

        double maxHealth = EntityUtil.getAttribute(entity, Attribute.MAX_HEALTH);
        double currentHealth = entity.getHealth();
        double healthPercent = currentHealth / maxHealth * 100D;

        return healthPercent <= maxPercent;
    }

    public boolean canBeCaptured(@NotNull LivingEntity entity) {
        String entityId = entity.getType().getKey().getKey();
        if (CaptureConfig.CAPTURE_DISABLED_ENTITY_TYPES.get().stream()
            .map(String::trim)
            .anyMatch(entityId::equalsIgnoreCase)) return false;

        if (hasForbiddenCaptureState(entity)) return false;
        if (this.plugin.getPetManager().isPetEntity(entity)) return false;
        if (entity instanceof Player) return false;
        if (entity instanceof Tameable tameable && tameable.isTamed()) return false;
        if (Plugins.isLoaded(HookId.MYTHIC_MOBS) && MythicMobsHook.isMythicMob(entity)) return false;

        return this.plugin.getPetManager().getTemplate(entity) != null;
    }

    static boolean hasForbiddenCaptureState(@NotNull LivingEntity entity) {
        return entity instanceof Creeper creeper && creeper.isPowered();
    }

    public static void saveCaptureProgress(@NotNull LivingEntity entity, int value) {
        PDCUtil.set(entity, Keys.captureProgress, value);
    }

    public static int getSavedCaptureProgress(@NotNull LivingEntity entity) {
        return PDCUtil.getInt(entity, Keys.captureProgress).orElse(0);
    }
}
