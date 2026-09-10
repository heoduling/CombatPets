package su.nightexpress.combatpets.level;

import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.StringUtil;

import java.util.Set;
import static org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

public class LevelingConfig {

    public static final ConfigValue<Boolean> ALLOW_DOWNGRADE = ConfigValue.create("Settings.Downgrade_Allowed",
        true,
        "是否允许宠物降级。",
        "启用后宠物经验可以变为负数，并因此降低等级。"
    );

    public static final ConfigValue<Set<String>> DISABLED_WORLDS = ConfigValue.create("Settings.Disabled_Worlds",
        Lists.newSet("world_name", "another_world"),
        "宠物不会获得或损失经验的世界列表。"
    );

    public static final ConfigValue<Double> DEATH_XP_LOSS = ConfigValue.create("Settings.Death_XP_Loss",
        5D,
        "宠物死亡时损失的经验百分比（相对于该级最大经验）。"
    );

//    public static final ConfigValue<Boolean> XP_BASED_ON_DAMAGE_DEALT = ConfigValue.create("Settings.XP_Based_On_Damage_Dealt",
//        true,
//        "When enabled, adjusts XP amount based on damage dealt by a pet.",
//        "Example: If pet dealt 10% damage = 10% XP of total XP amount."
//    );

    public static final ConfigValue<Boolean> USE_CUSTOM_XP_TABLE = ConfigValue.create("Settings.Use_Custom_XP_Table",
        true,
        "是否使用自定义经验来源表 'XPSources'。",
        "禁用后使用生物死亡时产生的原版经验值。"
    );

    public static final ConfigValue<Boolean> AUTO_UPDATE_XP_SOURCES = ConfigValue.create("Settings.Auto_Update_XP_Sources",
        true,
        "是否在插件更新后自动向 XPSources 补齐新增生物。",
        "只添加尚未出现在任何组的生物，不会覆盖现有分组、经验范围或触发概率。"
    );

    public static final ConfigValue<Set<String>> AUTO_UPDATE_XP_EXCLUDED_MOBS = ConfigValue.create("Settings.Auto_Update_XP_Excluded_Mobs",
        Lists.newSet("mannequin"),
        "自动补齐时始终排除的生物 ID。",
        "若管理员不想让某个新生物产生宠物经验，将其 ID 加入此处。"
    );

    public static final ConfigValue<Set<SpawnReason>> DISABLE_XP_BY_SPAWN_REASON = ConfigValue.forSet("Settings.Prevent_XP_From",
        id -> StringUtil.getEnum(id, SpawnReason.class).orElse(null),
        (cfg, path, set) -> cfg.set(path, set.stream().map(Enum::name).toList()),
        () -> Lists.newSet(
            SpawnReason.SPAWNER,
            SpawnReason.SPAWNER_EGG,
            SpawnReason.BUILD_IRONGOLEM,
            SpawnReason.BUILD_SNOWMAN,
            SpawnReason.DISPENSE_EGG,
            SpawnReason.EGG
        ),
        "以下列原因生成的生物不会为宠物提供经验。",
        "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/event/entity/CreatureSpawnEvent.SpawnReason.html"
    );
}
