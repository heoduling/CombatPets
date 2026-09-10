package su.nightexpress.combatpets.capture.config;

import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.bukkit.NightItem;

import java.util.Set;

public class CaptureConfig {

    public static final ConfigValue<Set<String>> CAPTURE_DISABLED_ENTITY_TYPES = ConfigValue.create("Settings.Disabled_Entity_Types",
        Lists.newSet("warden", "wither", "iron_golem", "ravager", "illusioner", "ender_dragon"),
        "默认禁止玩家捕捉的生物类型 ID。",
        "从列表移除 ID 即可重新允许捕捉；对应宠物配置中的 Catchable 也必须为 true。"
    );

    public static final ConfigValue<NightItem> CAPTURE_ITEM = ConfigValue.create("Settings.Item",
        PetCreator.getCaptureItem(),
        "宠物捕捉道具的物品格式。"
    );

    public static final ConfigValue<Boolean> CAPTURE_CONSUME_ITEM = ConfigValue.create("Settings.Consume_Item",
        true,
        "使用捕捉道具后是否消耗一个。"
    );

    public static final ConfigValue<Boolean> CAPTURE_ESCAPE_ALLOWED = ConfigValue.create("Settings.Escape_Allowed",
        true,
        "捕捉时生物是否可以逃脱；逃脱只会结束本次捕捉，之后仍可再次尝试。"
    );

    public static final ConfigValue<Double> CAPTURE_HEALTH_PERCENT = ConfigValue.create("Settings.Health_Percent",
        40D,
        "允许捕捉时生物生命值占最大生命值的上限百分比。",
        "生物当前生命值百分比高于此值时无法捕捉。"
    );

    public static final ConfigValue<Boolean> CAPTURE_SAVE_PROGRESS = ConfigValue.create("Settings.Save_Progress",
        true,
        "启用后，捕捉失败时会将成功进度保存在生物上。",
        "下次捕捉该生物时会继承已保存的成功进度。"
    );

    public static final ConfigValue<Integer> CAPTURE_MAX_DISTANCE = ConfigValue.create("Settings.Max_Distance",
        5,
        "捕捉过程中玩家与生物之间允许的最大距离。",
        "距离超过此值时将取消捕捉。"
    );
}
