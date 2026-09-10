package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuSize;
import su.nightexpress.nightcore.util.*;

import java.util.Map;
import java.util.Set;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class Config {

    public static final String DIR_MENU  = "/menu/";
    public static final String DIR_TIERS = "/pets/tiers/";
    public static final String DIR_PETS  = "/pets/configs/";

    @Deprecated
    public static final ConfigValue<Boolean> GENERAL_PLACEHOLDER_API_GUI = ConfigValue.create("General.PlaceholderAPI_In_GUI",
        false,
        "启用后，在 GUI 标题和物品中解析 PlaceholderAPI 占位符。"
    );

    public static final ConfigValue<Boolean> GENERAL_COLLECTION_DEFAULT_COMMAND = ConfigValue.create("General.Collection_As_Default_Command",
        false,
        "设置执行无参数宠物命令时是否打开宠物收藏菜单。");

    public static final ConfigValue<Integer> GENERAL_GUI_AUTO_REFRESH_SECONDS = ConfigValue.create("General.GUI_Auto_Refresh_Seconds",
        1,
        "设置所有 CombatPets GUI 的自动刷新间隔（秒）。",
        "刷新会同步更新经验、饱食度、状态和倒计时。",
        "设为 0 可关闭自动刷新。"
    );

    public static final ConfigValue<Boolean> FEATURES_LEVELING_ENABLED = ConfigValue.create("Features.Leveling",
        true,
        "是否启用宠物升级功能。"
    );

    public static final ConfigValue<Boolean> FEATURES_CAPTURE = ConfigValue.create("Features.Capture",
        true,
        "是否启用宠物捕捉功能。"
    );

    public static final ConfigValue<Boolean> FEATURES_WARDROBE = ConfigValue.create("Features.Accessories",
        true,
        "是否启用宠物配饰功能。"
    );

    public static final ConfigValue<Boolean> FEATURES_SHOP = ConfigValue.create("Features.Shop",
        true,
        "是否启用宠物商店功能。"
    );



    public static final ConfigValue<ItemStack> ITEM_MYSTERY_EGG = ConfigValue.create("Items.Mystery_Egg",
        PetCreator.getDefaultMysteryEgg(),
        "神秘宠物蛋的物品格式。"
    );



    public static final ConfigValue<ItemStack> PET_EGG_ITEM = ConfigValue.create("Pets.Egg_Item",
        PetCreator.getDefaultEgg(),
        "宠物蛋的物品格式。",
        "[*] 如需应用宠物蛋头颅纹理，物品材质应使用 '" + BukkitThing.toString(Material.PLAYER_HEAD) + "'。"
    );

    public static final ConfigValue<Set<String>> PET_DISABLED_WORLDS = ConfigValue.create("Pets.Disabled_Worlds",
        Lists.newSet("custom_world"),
        "完全禁用宠物功能的世界列表。"
    );

    public static final ConfigValue<Boolean> PET_RELEASE_ALLOWED = ConfigValue.create("Pets.Release.Allowed",
        true,
        "是否允许玩家放生宠物。"
    );

    public static final ConfigValue<Boolean> PET_RELEASE_NATURAL = ConfigValue.create("Pets.Release.Natural",
        true,
        "启用后，放生宠物时会生成同类原版生物。"
    );

    public static final ConfigValue<Set<String>> PET_RELEASE_DISABLED_WORLDS = ConfigValue.create("Pets.Release.BadWorlds",
        Lists.newSet("spawn", "other_world"),
        "禁止玩家放生宠物的世界列表。"
    );

    public static final ConfigValue<Boolean> PET_PVP_ALLOWED = ConfigValue.create("Pets.PVP_Allowed",
        true,
        "是否允许不同玩家的宠物互相攻击。"
    );

    public static final ConfigValue<Boolean> PET_SNEAK_TO_OPEN_MENU = ConfigValue.create("Pets.Sneak_To_Open_Menu",
        false,
        "玩家是否必须潜行并右键宠物才能打开宠物菜单。"
    );

    public static final ConfigValue<Boolean> PET_FOOD_BLOCK_MENU_WHEN_INCOMPATIBLE = ConfigValue.create("Pets.Food_Interaction.Block_Menu_When_Incompatible",
        false,
        "手持已配置但宠物不接受的食物右键时，是否阻止打开宠物管理菜单。",
        "关闭时，不兼容食物会继续执行普通右键菜单逻辑。",
        "兼容食物始终阻止菜单，以避免喂食时误开菜单。"
    );

    public static final ConfigValue<Boolean> PET_ATTACK_DAMAGE_FOR_PROJECTILES = ConfigValue.create("Pets.Attack_Damage_For_Projectiles",
        true,
        "远程攻击（非近战）是否使用宠物的攻击伤害属性覆盖原始伤害。",
        "原版箭、火球、三叉戟等远程攻击不会使用生物的攻击伤害属性。",
        "启用此项后将改变该行为。"
    );

    public static final ConfigValue<Double> PET_ORIGINAL_DAMAGE_FROM_PROJECTILES = ConfigValue.create("Pets.Original_Damage_From_Projectiles",
        0.25D,
        "[仅在 Attack_Damage_For_Projectiles 启用时生效]",
        "将宠物远程攻击的原始伤害按此倍率加到攻击伤害属性上。",
        "可用于平衡附魔远程武器等额外伤害。"
    );

    public static final ConfigValue<Integer> PET_SATURATION_PERCENT_TO_REGEN = ConfigValue.create("Pets.Saturation_Percent_To_Regen",
        70,
        "宠物开始自然恢复生命所需的最低饱食度百分比。");

    public static final ConfigValue<Double> PET_DAMAGE_REGEN_COOLDOWN = ConfigValue.create("Pets.Damage_Regen_Cooldown",
        5D,
        "宠物受伤后开始自然恢复生命的冷却时间（秒）。");

    public static final ConfigValue<Boolean> PET_PERMANENT_DEATH = ConfigValue.create("Pets.Permanent_Deaths",
        false,
        "宠物死亡时是否从玩家收藏中永久移除。");

    public static final ConfigValue<Boolean> PET_DROP_INVENTORY = ConfigValue.create("Pets.Drop_Inventory",
        true,
        "宠物死亡时是否掉落背包内物品。");

    public static final ConfigValue<Boolean> PET_DROP_EQUIPMENT = ConfigValue.create("Pets.Drop_Equipment",
        true,
        "宠物死亡时是否掉落所穿装备。");

    public static final ConfigValue<Boolean> PET_REALLOCATE_ASPECTS = ConfigValue.create("Pets.Allow_Reallocate_Aspect_Points",
        false,
        "是否允许玩家通过 GUI 中的重置属性点按钮重新分配宠物属性。");

    //public static final ConfigValue<Boolean> PET_DAMAGE_EQUIPMENT = ConfigValue.create("Pets.Damage_Equipment", true,
    //    "Sets whether or not pet equipment can be damaged.");

    public static final ConfigValue<Boolean> PET_AUTO_FOOD_ENABLED = ConfigValue.create("Pets.AutoFoodUsage.Enabled",
        false,
        "宠物是否自动食用其背包中的食物。");

    public static final ConfigValue<Double> PET_AUTO_FOOD_PERCENT = ConfigValue.create("Pets.AutoFoodUsage.At_Saturation",
        75D,
        "宠物开始自动进食的饱食度百分比。",
        "默认值为 75，即饱食度降至 75% 或更低时自动进食。");

    public static final ConfigValue<Boolean> PET_POP_DEFAULT_EQUIPMENT = ConfigValue.create("Pets.Populate_Default_Equipment",
        true,
        "捕捉宠物时是否保留该生物的原版装备。",
        "例如可让骷髅宠物保留弓、猪灵宠物保留剑。"
    );



    public static final ConfigValue<Integer> PET_NAME_LENGTH_MAX = ConfigValue.create("Pets.Name.Max_Length",
        16,
        "宠物名称的最大字符数。");

    public static final ConfigValue<Integer> PET_NAME_LENGTH_MIN = ConfigValue.create("Pets.Name.Min_Length",
        3,
        "宠物名称的最小字符数。");

    public static final ConfigValue<Boolean> PET_NAME_RENAME_ALLOW_NAMETAGS = ConfigValue.create("Pets.Name.Rename.Allow_NameTags",
        true,
        "是否允许使用命名牌重命名宠物。");

    public static final ConfigValue<Boolean> PET_NAME_RENAME_MENU_REQUIRES_NAMETAG = ConfigValue.create("Pets.Name.Rename.Menu_Requires_NameTag",
        true,
        "使用宠物菜单中的重命名按钮时，玩家背包中是否必须有命名牌。");



    public static final ConfigValue<Boolean>  PET_HEALTHBAR_ENABLED = ConfigValue.create("Pets.Healthbar.Enabled",
        true,
        "是否使用 Boss 栏显示宠物生命值。");

    public static final ConfigValue<String> PET_HEALTHBAR_TITLE = ConfigValue.create("Pets.Healthbar.Title",
        LIGHT_YELLOW.wrap(PET_NAME) + "   " + GRAY.wrap("等级 ") + WHITE.wrap(PET_LEVEL) + "   " + LIGHT_RED.wrap(PET_HEALTH) + GRAY.wrap("/") + LIGHT_RED.wrap(PET_MAX_HEALTH + " ❤"),
        "宠物生命值 Boss 栏标题。",
        "可使用宠物占位符：" + WIKI_PLACEHOLDERS_URL
    );

    public static final ConfigValue<BarStyle> PET_HEALTHBAR_STYLE = ConfigValue.create("Pets.Healthbar.Style",
        BarStyle.class, BarStyle.SOLID,
        "宠物生命值 Boss 栏样式。",
        "可用值：" + StringUtil.inlineEnum(BarStyle.class, ", ")
    );

    public static final ConfigValue<BarColor> PET_HEALTHBAR_COLOR = ConfigValue.create("Pets.Healthbar.Color",
        BarColor.class, BarColor.GREEN,
        "宠物生命值 Boss 栏颜色。",
        "可用值：" + StringUtil.inlineEnum(BarColor.class, ", ")
    );

    public static final ConfigValue<RankMap<Integer>> PET_AMOUNT_PER_RANK = ConfigValue.create("Pets.Amount_Per_Rank",
        (cfg, path, def) -> RankMap.readInt(cfg, path, 10),
        (cfg, path, map) -> map.write(cfg, path),
        () -> new RankMap<>(
            RankMap.Mode.RANK,
            "pets.amount.",
            10,
            PluginConfigMigration.createDefaultRankLimits()
        ),
        "按权限组设置玩家每个品质可拥有的宠物数量。",
        "未单独列出的权限组使用 '" + DEFAULT + "'。",
        "使用 '-1' 表示不限制数量。"
    );

    @Deprecated
    public static boolean isGUIPlaceholdersEnabled() {
        return GENERAL_PLACEHOLDER_API_GUI.get();
    }

    public static boolean isLevelingEnabled() {
        return FEATURES_LEVELING_ENABLED.get();
    }

    public static boolean isCapturingEnabled() {
        return FEATURES_CAPTURE.get();
    }

    public static boolean isShopEnabled() {
        return FEATURES_SHOP.get();
    }

    public static boolean isWardrobeEnabled() {
        return FEATURES_WARDROBE.get();
    }

    public static int getGuiAutoRefreshMillis() {
        int seconds = GENERAL_GUI_AUTO_REFRESH_SECONDS.get();
        if (seconds <= 0) return 0;

        return seconds >= Integer.MAX_VALUE / 1000 ? Integer.MAX_VALUE : seconds * 1000;
    }

    @NotNull
    public static MenuOptions createMenuOptions(@NotNull String title, @NotNull MenuSize size) {
        return new MenuOptions(title, size.getSize(), InventoryType.CHEST, getGuiAutoRefreshMillis());
    }
}
