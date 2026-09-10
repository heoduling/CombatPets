package su.nightexpress.combatpets.level;

import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.hook.HookId;
import su.nightexpress.combatpets.hook.impl.MythicMobsHook;
import su.nightexpress.combatpets.level.data.XPSource;
import su.nightexpress.combatpets.level.listener.LevelingListener;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.BukkitThing;
import su.nightexpress.nightcore.util.NumberUtil;
import su.nightexpress.nightcore.util.PDCUtil;
import su.nightexpress.nightcore.util.Plugins;

import java.util.*;

public class LevelingManager extends AbstractManager<PetsPlugin> {

    private static final String FILE_NAME = "leveling.yml";

    private Map<String, XPSource> xpSourceMap;

    public LevelingManager(@NotNull PetsPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onLoad() {
        FileConfig config = this.getConfig();
        this.loadConfig(config);
        this.loadXPSources(config);
        config.saveChanges();

        this.addListener(new LevelingListener(this.plugin, this));

        this.plugin.info("宠物升级模块已加载。");
    }

    @Override
    protected void onShutdown() {
        if (this.xpSourceMap != null) {
            this.xpSourceMap.clear();
            this.xpSourceMap = null;
        }
    }

    private void loadConfig(@NotNull FileConfig config) {
        config.initializeOptions(LevelingConfig.class);
    }

    private void loadXPSources(@NotNull FileConfig config) {
        if (!this.useCustomXPTable()) return;

        this.xpSourceMap = new HashMap<>();

        Map<String, XPSource> defaults = XPSource.getDefaults();
        if (config.getSection("XPSources").isEmpty()) {
            defaults.forEach((id, source) -> source.write(config, "XPSources." + id));
        }
        else if (LevelingConfig.AUTO_UPDATE_XP_SOURCES.get()) {
            mergeMissingDefaultXPSources(config, defaults, LevelingConfig.AUTO_UPDATE_XP_EXCLUDED_MOBS.get());
        }
        this.addXPSourcesComments(config);
        config.getSection("XPSources").forEach(id -> {
            XPSource source = XPSource.read(config, "XPSources." + id);
            this.xpSourceMap.put(id.toLowerCase(), source);
        });
        this.plugin.info("已加载 " + this.xpSourceMap.size() + " 个经验来源。");
    }

    static boolean mergeMissingDefaultXPSources(@NotNull FileConfig config,
                                                 @NotNull Map<String, XPSource> defaults,
                                                 @NotNull Set<String> excludedMobs) {
        Set<String> configuredMobs = new HashSet<>();
        for (String id : config.getSection("XPSources")) {
            configuredMobs.addAll(config.getStringSet("XPSources." + id + ".Mobs").stream()
                .map(String::toLowerCase)
                .toList());
        }

        Set<String> exclusions = new HashSet<>();
        excludedMobs.stream().map(String::toLowerCase).forEach(exclusions::add);
        boolean changed = false;

        for (Map.Entry<String, XPSource> entry : defaults.entrySet()) {
            LinkedHashSet<String> missing = new LinkedHashSet<>(entry.getValue().getMobs());
            missing.removeIf(mob -> configuredMobs.contains(mob) || exclusions.contains(mob));
            if (missing.isEmpty()) continue;

            String path = "XPSources." + entry.getKey();
            if (config.contains(path)) {
                LinkedHashSet<String> merged = new LinkedHashSet<>(config.getStringSet(path + ".Mobs"));
                merged.addAll(missing);
                config.set(path + ".Mobs", merged);
            }
            else {
                XPSource source = entry.getValue();
                new XPSource(source.getAmount(), source.getChance(), missing).write(config, path);
            }
            configuredMobs.addAll(missing);
            changed = true;
        }
        return changed;
    }

    private void addXPSourcesComments(@NotNull FileConfig config) {
        config.setComments("XPSources",
            "自定义宠物经验来源分组。生物只会使用首个匹配到的分组。",
            "分组名称可以自行修改；每个分组都包含经验范围、触发概率和生物列表。"
        );
        config.getSection("XPSources").forEach(id -> {
            String path = "XPSources." + id;
            config.setComments(path, "经验来源分组：" + id + "。");
            config.setComments(path + ".Amount", "该分组成功触发时，宠物获得的随机经验范围。");
            config.setComments(path + ".Amount.Min", "随机经验的最小值。");
            config.setComments(path + ".Amount.Max", "随机经验的最大值。");
            config.setComments(path + ".Chance", "击杀该分组生物时获得经验的概率，范围为 0 到 100。");
            config.setComments(path + ".Mobs", "属于该经验来源的生物 ID 列表；可使用 '*' 匹配任意生物。");
        });
    }

    @NotNull
    public FileConfig getConfig() {
        return FileConfig.loadOrExtract(this.plugin, FILE_NAME);
    }

    @NotNull
    public Set<XPSource> getXPSources() {
        return this.useCustomXPTable() ? new HashSet<>(this.xpSourceMap.values()) : Collections.emptySet();
    }

    public boolean shouldDropXP(@NotNull LivingEntity entity) {
        return PDCUtil.getBoolean(entity, Keys.levelingNoXP).isEmpty();
    }

    public void setDropXP(@NotNull LivingEntity entity, boolean value) {
        if (value) {
            PDCUtil.set(entity, Keys.levelingNoXP, true);
        }
        else PDCUtil.remove(entity, Keys.levelingNoXP);
    }

    public boolean useCustomXPTable() {
        return LevelingConfig.USE_CUSTOM_XP_TABLE.get();
    }

//    public boolean useDamageDealtCheck() {
//        return LevelingConfig.XP_BASED_ON_DAMAGE_DEALT.get();
//    }

    public boolean isDisabledWorld(@NotNull World world) {
        return this.isDisabledWorld(world.getName());
    }

    public boolean isDisabledWorld(@NotNull String name) {
        return LevelingConfig.DISABLED_WORLDS.get().contains(name);
    }

    @Nullable
    public XPSource getXPSource(@NotNull LivingEntity entity) {
        String name;
        if (Plugins.isInstalled(HookId.MYTHIC_MOBS) && MythicMobsHook.isMythicMob(entity)) {
            name = MythicMobsHook.getMobInternalName(entity);
        }
        else name = BukkitThing.toString(entity.getType());

        return this.getXPSources().stream().filter(xpSource -> xpSource.isMob(name)).findFirst().orElse(null);
    }

    public int rewardXP(@NotNull ActivePet activePet, @NotNull LivingEntity victim, double damagePercent, int naturalXP) {
        if (this.plugin.getPetManager().isShuttingDown()) return 0;
        if (!activePet.canGainXP()) return 0;

        int xpReward = 0;

        if (this.useCustomXPTable()) {
            XPSource source = this.getXPSource(victim);
            if (source != null && source.checkChance()) {
                xpReward = source.getAmount().roll();
            }
        }
        else xpReward = naturalXP;

        xpReward = (int) ((double) xpReward * damagePercent);
        if (xpReward <= 0) return 0;

        int finalXpReward = xpReward;
        boolean scheduled = PetScheduler.runAtEntity(this.plugin, activePet.getEntity(), () -> {
            if (!this.plugin.isEnabled()) return;

            activePet.addXP(finalXpReward);
            String petName = activePet.getName();
            org.bukkit.entity.Player owner = activePet.getOwner();
            PetScheduler.runAtEntity(this.plugin, owner, () -> {
                if (!this.plugin.isEnabled()) return;

                this.plugin.getUserManager().save(owner);
                if (!owner.isOnline()) return;

                Lang.LEVELING_XP_GAIN.message().send(owner, replacer -> replacer
                    .replace(Placeholders.GENERIC_AMOUNT, NumberUtil.format(finalXpReward))
                    .replace(Placeholders.PET_NAME, petName)
                );
            });
        }, null);

        return scheduled ? xpReward : 0;
    }
}
