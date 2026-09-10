package su.nightexpress.combatpets.util;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.FoodItem;
import su.nightexpress.combatpets.api.pet.Stat;
import su.nightexpress.combatpets.api.pet.type.ExhaustReason;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.nms.PetNMS;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.combatpets.pet.impl.*;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.bukkit.NightItem;
import su.nightexpress.nightcore.util.bukkit.NightSound;
import su.nightexpress.nightcore.util.wrapper.UniParticle;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static su.nightexpress.nightcore.util.text.tag.Tags.*;
import static su.nightexpress.combatpets.Placeholders.*;

@SuppressWarnings("UnstableApiUsage")
public class PetCreator {

    public static final String SULFUR_CUBE_EGG_SKIN = "f0d9056ec6db388af12304ef96ffdc8228dcf368ab255323258b716f990b4ab";
    public static final String SULFUR_CUBE_LEGACY_EGG_SKIN = "38957d5023c937c4c41aa2412d43410bda23cf79a9f6ab36b76fef2d7c429";

    private static final String ASPECT_STRENGTH  = "strength";
    private static final String ASPECT_VITALITY  = "vitality";
    private static final String ASPECT_DEFENSE   = "defense";
    private static final String ASPECT_DEXTERITY = "dexterity";

    private static final String FOOD_FLOWERS       = "flowers";
    private static final String FOOD_RAW_MEAT      = "raw_meat";
    private static final String FOOD_RAW_FISH      = "raw_fish";
    private static final String FOOD_MEAT          = "cooked_meat";
    private static final String FOOD_FISH          = "cooked_fish";
    private static final String FOOD_WHEAT_SEEDS   = "wheat_seeds";
    private static final String FOOD_WHEAT         = "wheat";
    private static final String FOOD_APPLES        = "apples";
    private static final String FOOD_CARROTS       = "carrots";
    private static final String FOOD_BREAD         = "bread";
    private static final String FOOD_MUSHROOMS     = "mushrooms";
    private static final String FOOD_BEETROOT      = "beetroot";
    private static final String FOOD_BAMBOO        = "bamboo";
    private static final String FOOD_EGGS          = "eggs";
    private static final String FOOD_SPIDER_EYES   = "spider_eyes";
    private static final String FOOD_GUNPOWDER     = "gunpowder";
    private static final String FOOD_ROTTEN_FLESH  = "rotten_flesh";
    private static final String FOOD_CHORUS_FRUITS = "chorus_fruits";
    private static final String FOOD_BONE_MEAL     = "bone_meal";
    private static final String FOOD_WITHER_ROSES  = "wither_roses";
    private static final String FOOD_CREAKING      = "sticks";
    private static final String FOOD_CACTUS        = "cactus";
    private static final String FOOD_WARPED_FUNGUS = "warped_fungus";
    private static final String FOOD_COPPER_INGOTS = "copper_ingots";
    private static final String FOOD_SNOWBALLS      = "snowballs";
    private static final String FOOD_TROPICAL_FISH  = "tropical_fish";
    private static final String FOOD_TORCHFLOWER_SEEDS = "torchflower_seeds";
    private static final String FOOD_IRON_INGOTS    = "iron_ingots";
    private static final String FOOD_CAKES          = "cakes";
    private static final String FOOD_BLAZE_POWDER   = "blaze_powder";
    private static final String FOOD_FEATHERS       = "feathers";

    private static final double VILLAGER_MOVEMENT_SPEED = 0.25D;

    private static final Set<EntityType> SATURATION_MIGRATION_TYPES = EnumSet.of(
        EntityType.ALLAY,
        EntityType.ARMADILLO,
        EntityType.AXOLOTL,
        EntityType.BLAZE,
        EntityType.BREEZE,
        EntityType.IRON_GOLEM,
        EntityType.SNIFFER,
        EntityType.SNOW_GOLEM
    );

    public static void createTiers(@NotNull PetsPlugin plugin) {
        createTier(plugin, "common", tier -> {
            tier.setName(WHITE.wrap("普通"));
            tier.setIcon(ItemUtil.getSkinHead("6cedec04d2380cd77027f9d44455c98b7edc5f664ca0dd30a6106903915391db"));
            tier.setWeight(75);
            tier.setInventorySize(9);
            tier.setAutoRespawnTime(3600);
            tier.setReviveCost(500);
            tier.setMaxLevel(30);
        });

        createTier(plugin, "rare", tier -> {
            tier.setName(LIGHT_GREEN.wrap("稀有"));
            tier.setIcon(ItemUtil.getSkinHead("f3a58bec65668b682abab361300a9c4133bb6c06db8847210a62b884ee6fbc7d"));
            tier.setWeight(15);
            tier.setInventorySize(18);
            tier.setAutoRespawnTime(7200);
            tier.setReviveCost(1500);
            tier.setMaxLevel(35);
        });

        createTier(plugin, "unique", tier -> {
            tier.setName(LIGHT_BLUE.wrap("珍奇"));
            tier.setIcon(ItemUtil.getSkinHead("bd37285793131ed755f1b0098f29dd413d67656b6208728935492b49b1d0d4ba"));
            tier.setWeight(7.5);
            tier.setInventorySize(27);
            tier.setAutoRespawnTime(21600);
            tier.setReviveCost(3000);
            tier.setMaxLevel(40);
        });

        createTier(plugin, "mythic", tier -> {
            tier.setName(LIGHT_PURPLE.wrap("神话"));
            tier.setIcon(ItemUtil.getSkinHead("74649c6c22cb2480a1400fb7e845bddf36ffcd3011dd51a8e9aabebeae918bb6"));
            tier.setWeight(2.5);
            tier.setInventorySize(36);
            tier.setAutoRespawnTime(43200);
            tier.setReviveCost(5000);
            tier.setMaxLevel(50);
        });
    }

    private static void createTier(@NotNull PetsPlugin plugin, @NotNull String id, @NotNull Consumer<PetTier> consumer) {
        File file = new File(plugin.getDataFolder() + Config.DIR_TIERS, id + ".yml");
        PetTier tier = new PetTier(plugin, file);

        tier.setNameFormat(GRAY.wrap(PET_NAME) + " " + LIGHT_YELLOW.wrap("等级 " + LIGHT_ORANGE.wrap(PET_LEVEL)));
        tier.setHasInventory(true);
        tier.setHasEquipment(true);
        tier.setInventoryDropChance(0D);
        tier.setEquipmentDropChance(0D);
        tier.setCapturable(true);
        tier.setInitialXP(500);
        tier.setXPModifier(1.093);
        tier.setAspectPointsPerLevel(1);
        tier.setStartAspectPoints(0);
        tier.getAspectsMax().put(ASPECT_DEFENSE, 30);
        tier.getAspectsMax().put(ASPECT_DEXTERITY, 30);
        tier.getAspectsMax().put(ASPECT_STRENGTH, 30);
        tier.getAspectsMax().put(ASPECT_VITALITY, 30);

        consumer.accept(tier);
        tier.save();
    }

    public static void createConfigs(@NotNull PetsPlugin plugin) {
        PetNMS nms = plugin.getPetNMS();

		for (EntityType entityType : nms.getSupportedEntities()) {
            String name = entityType.name().toLowerCase();//.getKey().getKey();
            File file = new File(plugin.getDataFolder() + Config.DIR_PETS, name + ".yml");
            if (file.exists()) {
                if (SATURATION_MIGRATION_TYPES.contains(entityType)) {
                    FileConfig config = FileConfig.load(file.toPath());
                    if (migrateSaturationDefaults(config, getFoodCategories(entityType))) {
                        config.saveChanges();
                        plugin.info("已补齐宠物配置 '" + file.getName() + "' 的默认食物与饱食度。");
                    }
                }
                if (migrateLegacyVillagerMovementSpeed(file, entityType)) {
                    plugin.info("已修正宠物配置 '" + file.getName() + "' 中过高的旧版移动速度默认值。");
                }
                if (migrateLegacyCamelMovementSpeed(file, entityType)) {
                    plugin.info("已恢复宠物配置 '" + file.getName() + "' 的原版骆驼基础移动速度。");
                }
                continue;
            }

            PetTemplate petTemplate = new PetTemplate(plugin, file);

            petTemplate.setEntityType(entityType);
            petTemplate.setDefaultName(getEntityName(entityType));
            petTemplate.setEggTexture(getSkinURL(entityType));
            petTemplate.setInventory(true);
            petTemplate.setEquipment(true);
            petTemplate.setCapturable(true);
            petTemplate.setCaptureChance(30D);
            petTemplate.setCaptureEscapeChance(0.5D);
            petTemplate.setSpawnParticle(UniParticle.of(Particle.CLOUD));
            petTemplate.setDespawnParticle(UniParticle.of(Particle.ASH));
            petTemplate.setEatSound(NightSound.of(Sound.ENTITY_GENERIC_EAT));
            petTemplate.setExhaustModifier(ExhaustReason.IDLE, 0.01);
            petTemplate.setExhaustModifier(ExhaustReason.WALK, 0.02);
            petTemplate.setExhaustModifier(ExhaustReason.COMBAT, 0.04);
            petTemplate.setFoodCategories(getFoodCategories(entityType));
            setExtras(petTemplate, nms);
            petTemplate.save();
		}
    }

    @NotNull
    public static List<PetAspect> getDefaultAspects() {
        return Lists.newList(
            new PetAspect(ASPECT_VITALITY, "体质",
                ItemUtil.getSkinHead("1c3cec68769fe9c971291edb7ef96a4e3b60462cfd5fb5baa1cbb3a71513e7b"),
                Lists.newSet(AttributeRegistry.MAX_HEALTH, AttributeRegistry.MAX_SATURATION, AttributeRegistry.HEALTH_REGENEATION_SPEED)
            ),
            new PetAspect(ASPECT_DEFENSE, "防御",
                ItemUtil.getSkinHead("2328f378f28a9872226f5ce04d6e1dfa111618587f48dfa1fe82d043216a5cf"),
                Lists.newSet(AttributeRegistry.ARMOR)
            ),
            new PetAspect(ASPECT_STRENGTH, "力量",
                ItemUtil.getSkinHead("41a2c088637fee9ae3a36dd496e876e657f509de55972dd17c18767eae1f3e9"),
                Lists.newSet(AttributeRegistry.ATTACK_DAMAGE, AttributeRegistry.HEALTH_REGENEATION_FORCE)
            ),
            new PetAspect(ASPECT_DEXTERITY, "敏捷",
                ItemUtil.getSkinHead("a4efb34417d95faa94f25769a21676a022d263346c8553eb5525658b34269"),
                Lists.newSet(AttributeRegistry.MOVEMENT_SPEED, AttributeRegistry.FLYING_SPEED, AttributeRegistry.ATTACK_SPEED)
            )
        );
    }

    @NotNull
    public static List<PetFoodCategory> getDefaultFoods() {
        List<PetFoodCategory> list = new ArrayList<>();

        list.add(createFoodCategory(FOOD_RAW_MEAT, category -> Lists.newList(
            createFoodItem(category, Material.CHICKEN, 2.5),
            createFoodItem(category, Material.RABBIT, 3),
            createFoodItem(category, Material.BEEF, 3.5),
            createFoodItem(category, Material.MUTTON, 3.5),
            createFoodItem(category, Material.PORKCHOP, 3.5)
        )));

        list.add(createFoodCategory(FOOD_RAW_FISH, category -> Lists.newList(
            createFoodItem(category, Material.COD, 2),
            createFoodItem(category, Material.SALMON, 2.5),
            createFoodItem(category, Material.TROPICAL_FISH, 2.5)
        )));

        list.add(createFoodCategory(FOOD_MEAT, category -> Lists.newList(
            createFoodItem(category, Material.COOKED_BEEF, 5),
            createFoodItem(category, Material.COOKED_CHICKEN, 3),
            createFoodItem(category, Material.COOKED_MUTTON, 3),
            createFoodItem(category, Material.COOKED_PORKCHOP, 3),
            createFoodItem(category, Material.COOKED_RABBIT, 4)
        )));

        list.add(createFoodCategory(FOOD_FISH, category -> Lists.newList(
            createFoodItem(category, Material.COOKED_COD, 3),
            createFoodItem(category, Material.COOKED_SALMON, 3)
        )));

        list.add(createFoodCategory(FOOD_FLOWERS, category -> Lists.newList(
            createFoodItem(category, Material.OXEYE_DAISY, 2)
        )));

        list.add(createFoodCategory(FOOD_WHEAT_SEEDS, category -> Lists.newList(
            createFoodItem(category, Material.WHEAT_SEEDS, 2)
        )));

        list.add(createFoodCategory(FOOD_WHEAT, category -> Lists.newList(
            createFoodItem(category, Material.WHEAT, 3)
        )));

        list.add(createFoodCategory(FOOD_APPLES, category -> Lists.newList(
            createFoodItem(category, Material.APPLE, 3)
        )));

        list.add(createFoodCategory(FOOD_CARROTS, category -> Lists.newList(
            createFoodItem(category, Material.CARROT, 3)
        )));

        list.add(createFoodCategory(FOOD_BREAD, category -> Lists.newList(
            createFoodItem(category, Material.BREAD, 2.25)
        )));

        list.add(createFoodCategory(FOOD_BEETROOT, category -> Lists.newList(
            createFoodItem(category, Material.BEETROOT, 2.25)
        )));

        list.add(createFoodCategory(FOOD_MUSHROOMS, category -> Lists.newList(
            createFoodItem(category, Material.RED_MUSHROOM, 2.5),
            createFoodItem(category, Material.BROWN_MUSHROOM, 2.5)
        )));

        list.add(createFoodCategory(FOOD_BAMBOO, category -> Lists.newList(
            createFoodItem(category, Material.BAMBOO, 3)
        )));

        list.add(createFoodCategory(FOOD_EGGS, category -> Lists.newList(
            createFoodItem(category, Material.EGG, 3)
        )));

        list.add(createFoodCategory(FOOD_SPIDER_EYES, category -> Lists.newList(
            createFoodItem(category, Material.SPIDER_EYE, 3)
        )));

        list.add(createFoodCategory(FOOD_GUNPOWDER, category -> Lists.newList(
            createFoodItem(category, Material.GUNPOWDER, 5)
        )));

        list.add(createFoodCategory(FOOD_ROTTEN_FLESH, category -> Lists.newList(
            createFoodItem(category, Material.ROTTEN_FLESH, 5)
        )));

        list.add(createFoodCategory(FOOD_CHORUS_FRUITS, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_CHORUS_FRUITS), 5)
        )));

        list.add(createFoodCategory(FOOD_BONE_MEAL, category -> Lists.newList(
            createFoodItem(category, Material.BONE_MEAL, 4)
        )));

        list.add(createFoodCategory(FOOD_WITHER_ROSES, category -> Lists.newList(
            createFoodItem(category, Material.WITHER_ROSE, 3)
        )));

        list.add(createFoodCategory(FOOD_CREAKING, category -> Lists.newList(
            createFoodItem(category, Material.STICK, 2)
        )));

        list.add(createFoodCategory(FOOD_CACTUS, category -> Lists.newList(
            createFoodItem(category, Material.CACTUS, 4)
        )));

        list.add(createFoodCategory(FOOD_WARPED_FUNGUS, category -> Lists.newList(
            createFoodItem(category, Material.WARPED_FUNGUS, 4)
        )));

        list.add(createFoodCategory(FOOD_COPPER_INGOTS, category -> Lists.newList(
            createFoodItem(category, Material.COPPER_INGOT, 5)
        )));

        list.add(createFoodCategory(FOOD_SNOWBALLS, category -> Lists.newList(
            createFoodItem(category, Material.SNOWBALL, 3)
        )));

        list.add(createFoodCategory(FOOD_TROPICAL_FISH, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_TROPICAL_FISH), 2.5)
        )));

        list.add(createFoodCategory(FOOD_TORCHFLOWER_SEEDS, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_TORCHFLOWER_SEEDS), 3)
        )));

        list.add(createFoodCategory(FOOD_IRON_INGOTS, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_IRON_INGOTS), 5)
        )));

        list.add(createFoodCategory(FOOD_CAKES, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_CAKES), 5)
        )));

        list.add(createFoodCategory(FOOD_BLAZE_POWDER, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_BLAZE_POWDER), 4)
        )));

        list.add(createFoodCategory(FOOD_FEATHERS, category -> Lists.newList(
            createFoodItem(category, getDefaultFoodMaterial(FOOD_FEATHERS), 2)
        )));

        return list;
    }

    @NotNull
    static Material getDefaultFoodMaterial(@NotNull String categoryId) {
        return switch (categoryId) {
            case FOOD_TROPICAL_FISH -> Material.TROPICAL_FISH;
            case FOOD_TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_SEEDS;
            case FOOD_IRON_INGOTS -> Material.IRON_INGOT;
            case FOOD_CAKES -> Material.CAKE;
            case FOOD_BLAZE_POWDER -> Material.BLAZE_POWDER;
            case FOOD_FEATHERS -> Material.FEATHER;
            case FOOD_CHORUS_FRUITS -> Material.CHORUS_FRUIT;
            default -> throw new IllegalArgumentException("Unsupported default food category: " + categoryId);
        };
    }

    @NotNull
    private static PetFoodCategory createFoodCategory(@NotNull String name, @NotNull Function<PetFoodCategory, List<FoodItem>> function) {
        PetFoodCategory category = new PetFoodCategory(name, getFoodCategoryName(name), new HashMap<>());
        Map<String, FoodItem> itemMap = category.getItemMap();
        
        function.apply(category).forEach(foodItem -> itemMap.put(foodItem.getId(), foodItem));

        return category;
    }

    @NotNull
    private static FoodItem createFoodItem(@NotNull PetFoodCategory category, @NotNull Material material, double saturation) {
        return new PetFoodItem(BukkitThing.toString(material), category, new ItemStack(material), saturation);
    }

    @NotNull
    public static NightItem getCaptureItem() {
        return NightItem.fromType(Material.LEAD)
            .setDisplayName(LIGHT_ORANGE.wrap(BOLD.wrap("宠物捕捉绳")))
            .setLore(Lists.newList(
                GRAY.wrap("用于捕捉生物并将其转化为宠物。"),
                "",
                LIGHT_GRAY.wrap(LIGHT_ORANGE.wrap("[▶]") + " 右键点击生物进行" + LIGHT_ORANGE.wrap("捕捉") + "。")
            ));
    }

    @NotNull
    public static ItemStack getDefaultEgg() {
        ItemStack itemStack = new ItemStack(Material.PLAYER_HEAD);

        ItemUtil.editMeta(itemStack, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.wrap(BOLD.wrap("宠物蛋")) + " " + GRAY.wrap("(" + WHITE.wrap(TEMPLATE_DEFAULT_NAME) + ")"));
            meta.setLore(Lists.newList(
                DARK_GRAY.wrap("品质：" + GRAY.wrap(TIER_NAME)),
                "",
                LIGHT_GRAY.wrap(LIGHT_YELLOW.wrap("[▶]") + " 右键点击" + LIGHT_YELLOW.wrap("领取") + "宠物。")
            ));
        });

        return itemStack;
    }

    @NotNull
    public static ItemStack getDefaultMysteryEgg() {
        ItemStack itemStack = ItemUtil.getSkinHead("e6ba9987f738e6d75d3b02c30d1480a360593ddb464bd1c81abb9d71d9e656c0");

        ItemUtil.editMeta(itemStack, meta -> {
            meta.setDisplayName(LIGHT_PURPLE.wrap(BOLD.wrap("神秘宠物蛋")) + " " + GRAY.wrap("(" + WHITE.wrap(TEMPLATE_DEFAULT_NAME) + ")"));
            meta.setLore(Lists.newList(
                DARK_GRAY.wrap("可孵化出随机品质的宠物蛋。"),
                "",
                LIGHT_GRAY.wrap(LIGHT_PURPLE.wrap("[▶]") + " 右键点击" + LIGHT_PURPLE.wrap("孵化") + "。")
            ));
        });

        return itemStack;
    }

    @NotNull
    public static ItemStack getDefaultAccessory() {
        ItemStack itemStack = new ItemStack(Material.FLOWER_BANNER_PATTERN);

        ItemUtil.editMeta(itemStack, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.wrap(BOLD.wrap("宠物配饰")) + " " + GRAY.wrap("(" + WHITE.wrap(GENERIC_TYPE) + ")"));
            meta.setLore(Lists.newList(
                GRAY.wrap("为你的宠物应用 " + LIGHT_YELLOW.wrap(GENERIC_NAME + " " + GENERIC_TYPE)),
                GRAY.wrap("配饰。"),
                "",
                LIGHT_GRAY.wrap(LIGHT_YELLOW.wrap("[▶]") + " 右键点击宠物进行" + LIGHT_YELLOW.wrap("应用") + "。")
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.values());
        });

        return itemStack;
    }

    @NotNull
    public static String getTierName(@NotNull String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "common" -> "普通";
            case "rare" -> "稀有";
            case "unique" -> "珍奇";
            case "mythic" -> "神话";
            default -> StringUtil.capitalizeUnderscored(id);
        };
    }

    @NotNull
    public static String getAspectName(@NotNull String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case ASPECT_VITALITY -> "体质";
            case ASPECT_DEFENSE -> "防御";
            case ASPECT_STRENGTH -> "力量";
            case ASPECT_DEXTERITY -> "敏捷";
            default -> StringUtil.capitalizeUnderscored(id);
        };
    }

    @NotNull
    public static String getFoodCategoryName(@NotNull String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case FOOD_FLOWERS -> "鲜花";
            case FOOD_RAW_MEAT -> "生肉";
            case FOOD_RAW_FISH -> "生鱼";
            case FOOD_MEAT -> "熟肉";
            case FOOD_FISH -> "熟鱼";
            case FOOD_WHEAT_SEEDS -> "小麦种子";
            case FOOD_WHEAT -> "小麦";
            case FOOD_APPLES -> "苹果";
            case FOOD_CARROTS -> "胡萝卜";
            case FOOD_BREAD -> "面包";
            case FOOD_MUSHROOMS -> "蘑菇";
            case FOOD_BEETROOT -> "甜菜根";
            case FOOD_BAMBOO -> "竹子";
            case FOOD_EGGS -> "鸡蛋";
            case FOOD_SPIDER_EYES -> "蜘蛛眼";
            case FOOD_GUNPOWDER -> "火药";
            case FOOD_ROTTEN_FLESH -> "腐肉";
            case FOOD_CHORUS_FRUITS -> "紫颂果";
            case FOOD_BONE_MEAL -> "骨粉";
            case FOOD_WITHER_ROSES -> "凋灵玫瑰";
            case FOOD_CREAKING -> "木棍";
            case FOOD_CACTUS -> "仙人掌";
            case FOOD_WARPED_FUNGUS -> "诡异菌";
            case FOOD_COPPER_INGOTS -> "铜锭";
            case FOOD_SNOWBALLS -> "雪球";
            case FOOD_TROPICAL_FISH -> "热带鱼";
            case FOOD_TORCHFLOWER_SEEDS -> "火把花种子";
            case FOOD_IRON_INGOTS -> "铁锭";
            case FOOD_CAKES -> "蛋糕";
            case FOOD_BLAZE_POWDER -> "烈焰粉";
            case FOOD_FEATHERS -> "羽毛";
            default -> StringUtil.capitalizeUnderscored(id);
        };
    }

    @NotNull
    public static String getAttributeName(@NotNull String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case AttributeRegistry.ARMOR -> "护甲";
            case AttributeRegistry.ATTACK_DAMAGE -> "攻击伤害";
            case AttributeRegistry.ATTACK_KNOCKBACK -> "攻击击退";
            case AttributeRegistry.ATTACK_SPEED -> "攻击速度";
            case AttributeRegistry.FLYING_SPEED -> "飞行速度";
            case AttributeRegistry.HEALTH_REGENEATION_FORCE -> "生命恢复量";
            case AttributeRegistry.HEALTH_REGENEATION_SPEED -> "生命恢复速度";
            case AttributeRegistry.HORSE_JUMP_STRENGTH -> "马匹跳跃强度";
            case AttributeRegistry.KNOCKBACK_RESISTANCE -> "击退抗性";
            case AttributeRegistry.MAX_HEALTH -> "最大生命值";
            case AttributeRegistry.MAX_SATURATION -> "最大饱食度";
            case AttributeRegistry.MOVEMENT_SPEED -> "移动速度";
            case AttributeRegistry.BURNING_TIME -> "燃烧时间";
            case AttributeRegistry.EXPLOSION_KNOCKBACK_RESISTANCE -> "爆炸击退抗性";
            case AttributeRegistry.FALL_DAMAGE_MULTIPLIER -> "摔落伤害倍率";
            case AttributeRegistry.GRAVITY -> "重力";
            case AttributeRegistry.MOVEMENT_EFFICIENCY -> "移动效率";
            case AttributeRegistry.SAFE_FALL_DISTANCE -> "安全摔落距离";
            case AttributeRegistry.SCALE -> "体型大小";
            case AttributeRegistry.STEP_HEIGHT -> "跨越高度";
            case AttributeRegistry.WATER_MOVEMENT_EFFICIENCY -> "水中移动效率";
            default -> StringUtil.capitalizeUnderscored(id);
        };
    }

    @NotNull
    public static String getVariantName(@NotNull String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "age" -> "年龄";
            case "size" -> "体型";
            case "axolotl_variant" -> "美西螈颜色";
            case "fox_type" -> "狐狸类型";
            case "llama_color" -> "羊驼颜色";
            case "sheep_color" -> "绵羊颜色";
            case "horse_style" -> "马匹斑纹";
            case "horse_color" -> "马匹颜色";
            case "shear_style" -> "绵羊剪毛状态";
            case "rabbit_type" -> "兔子类型";
            case "cat_type" -> "猫品种";
            case "parrot_variant" -> "鹦鹉羽色";
            case "frog_variant" -> "青蛙类型";
            case "wolf_variant" -> "狼品种";
            case "mooshroom_variant" -> "哞菇类型";
            case "cow_variant" -> "牛类型";
            case "pig_variant" -> "猪类型";
            case "chicken_variant" -> "鸡类型";
            case "zombie_nautilus_variant" -> "僵尸鹦鹉螺类型";
            case "villager_type" -> "村民生物群系外观";
            case "villager_profession" -> "村民职业";
            case "zombie_villager_type" -> "僵尸村民生物群系外观";
            case "zombie_villager_profession" -> "僵尸村民职业";
            case "snow_golem_pumpkin" -> "雪傀儡南瓜状态";
            case "bogged_sheared" -> "沼骸蘑菇状态";
            default -> StringUtil.capitalizeUnderscored(id);
        };
    }

    @NotNull
    public static String getEntityName(@NotNull EntityType type) {
        if (type.name().equals("CAMEL_HUSK")) return "骆驼尸壳";
        if (type.name().equals("COPPER_GOLEM")) return "铜傀儡";
        if (type.name().equals("PARCHED")) return "枯焦骷髅";
        if (type.name().equals("NAUTILUS")) return "鹦鹉螺";
        if (type.name().equals("ZOMBIE_NAUTILUS")) return "僵尸鹦鹉螺";
        if (type.name().equals("SULFUR_CUBE")) return "硫方怪";
        if (type.name().equals("HAPPY_GHAST")) return "快乐恶魂";

        return switch (type) {
            case BAT -> "蝙蝠";
            case ALLAY -> "悦灵";
            case ARMADILLO -> "犰狳";
            case AXOLOTL -> "美西螈";
            case BEE -> "蜜蜂";
            case BLAZE -> "烈焰人";
            case BOGGED -> "沼骸";
            case BREEZE -> "旋风人";
            case CAMEL -> "骆驼";
            case CAT -> "猫";
            case CAVE_SPIDER -> "洞穴蜘蛛";
            case CHICKEN -> "鸡";
            case COW -> "牛";
            case CREAKING -> "嘎枝";
            case CREEPER -> "苦力怕";
            case DONKEY -> "驴";
            case DROWNED -> "溺尸";
            case DOLPHIN -> "海豚";
            case ENDERMAN -> "末影人";
            case ENDERMITE -> "末影螨";
            case EVOKER -> "唤魔者";
            case FOX -> "狐狸";
            case FROG -> "青蛙";
            case GHAST -> "恶魂";
            case GUARDIAN -> "守卫者";
            case GIANT -> "巨人";
            case GOAT -> "山羊";
            case HORSE -> "马";
            case HOGLIN -> "疣猪兽";
            case HUSK -> "尸壳";
            case ILLUSIONER -> "幻术师";
            case IRON_GOLEM -> "铁傀儡";
            case LLAMA -> "羊驼";
            case MAGMA_CUBE -> "岩浆怪";
            case MULE -> "骡";
            case MOOSHROOM -> "哞菇";
            case OCELOT -> "豹猫";
            case PANDA -> "熊猫";
            case PARROT -> "鹦鹉";
            case PHANTOM -> "幻翼";
            case PIG -> "猪";
            case PIGLIN -> "猪灵";
            case PIGLIN_BRUTE -> "猪灵蛮兵";
            case PILLAGER -> "掠夺者";
            case POLAR_BEAR -> "北极熊";
            case RABBIT -> "兔子";
            case RAVAGER -> "劫掠兽";
            case SHEEP -> "绵羊";
            case SILVERFISH -> "蠹虫";
            case SKELETON -> "骷髅";
            case SKELETON_HORSE -> "骷髅马";
            case SLIME -> "史莱姆";
            case SNIFFER -> "嗅探兽";
            case SNOW_GOLEM -> "雪傀儡";
            case SPIDER -> "蜘蛛";
            case STRAY -> "流浪者";
            case STRIDER -> "炽足兽";
            case TRADER_LLAMA -> "行商羊驼";
            case TURTLE -> "海龟";
            case VEX -> "恼鬼";
            case VILLAGER -> "村民";
            case VINDICATOR -> "卫道士";
            case WANDERING_TRADER -> "流浪商人";
            case WARDEN -> "监守者";
            case WITCH -> "女巫";
            case WITHER -> "凋灵";
            case WITHER_SKELETON -> "凋灵骷髅";
            case WOLF -> "狼";
            case ZOGLIN -> "僵尸疣猪兽";
            case ZOMBIE -> "僵尸";
            case ZOMBIE_HORSE -> "僵尸马";
            case ZOMBIE_VILLAGER -> "僵尸村民";
            case ZOMBIFIED_PIGLIN -> "僵尸猪灵";
            default -> StringUtil.capitalizeUnderscored(type.name());
        };
    }

    @NotNull
    private static String getSkinURL(@NotNull EntityType type) {
//        if (Version.isAtLeast(Version.MC_1_21)) {
//            String raw = BukkitThing.toString(type);
//
//            //if (raw.equalsIgnoreCase("armadillo")) return "9852b33ba294f560090752d113fe728cbc7dd042029a38d5382d65a2146068b7";
//            //if (raw.equalsIgnoreCase("bogged")) return "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9";
//            //if (raw.equalsIgnoreCase("breeze")) return  "a275728af7e6a29c88125b675a39d88ae9919bb61fdc200337fed6ab0c49d65c";
//            //if (raw.equalsIgnoreCase("sniffer")) return "fe5a8341c478a134302981e6a7758ea4ecfd8d62a0df4067897e75502f9b25de";
//        }

        if (type.name().equals("CAMEL_HUSK")) return "750bfc9b2cc40f4d8d0224ccbabac26b338aa947d99dcde769f859b59b8d0b0e";
        if (type.name().equals("COPPER_GOLEM")) return "248e0f9eb24a10600729438dd99997839e1a99e276d76e32fd434bdb5f54296c";
        if (type.name().equals("PARCHED")) return "24aeceff5f26dd8413c5c03547c234ac03108d187af0b9cd834a8ce12598591c";
        if (type.name().equals("NAUTILUS")) return "3bb340dd3302615348de5162fe1670b9c5c9c616cd92d2de9d8398cb33e842ae";
        if (type.name().equals("ZOMBIE_NAUTILUS")) return "fd9a933376da44c3391307cb9f4cf03f16f3a54f495fd5a11bad8a373f9d5720";
        if (type.name().equals("SULFUR_CUBE")) return SULFUR_CUBE_EGG_SKIN;
        if (type.name().equals("HAPPY_GHAST")) return "82698aa7274ee241d31362e2faa48cfa733d25010516568d40f3f967247da11b";

        return switch (type) {
            case BAT -> "2399a68bf22650bf297719f8c528f1cf548a9f03aab9afdb0e65700fef84bec9";
            case ALLAY -> "f315a6a899b15a9810bc2bfdffd491a038c48016c189d43f327092c8f011599f";
            case ARMADILLO -> "9852b33ba294f560090752d113fe728cbc7dd042029a38d5382d65a2146068b7";
            case AXOLOTL -> "21c3aa0d539208b47972bf8e72f0505cdcfb8d7796b2fcf85911ce94fd0193d0";
            case BEE -> "cce9edbbc5fdc0d8487ac72eab239d2cacfe408d74288d6384b044111ba4de0f";
            case BLAZE -> "b78ef2e4cf2c41a2d14bfde9caff10219f5b1bf5b35a49eb51c6467882cb5f0";
            case BOGGED -> "a3b9003ba2d05562c75119b8a62185c67130e9282f7acbac4bc2824c21eb95d9";
            case BREEZE -> "a275728af7e6a29c88125b675a39d88ae9919bb61fdc200337fed6ab0c49d65c";
            case CAMEL -> "3642c9f71131b5df4a8c21c8c6f10684f22abafb8cd68a1d55ac4bf263a53a31";
            case CAT -> "3a12188258601bcb7f76e3e2489555a26c0d76e6efec2fd966ca372b6dde00";
            case CAVE_SPIDER -> "41645dfd77d09923107b3496e94eeb5c30329f97efc96ed76e226e98224";
            case CHICKEN -> "1638469a599ceef7207537603248a9ab11ff591fd378bea4735b346a7fae893";
            case COW -> "5d6c6eda942f7f5f71c3161c7306f4aed307d82895f9d2b07ab4525718edc5";
            case CREAKING -> "f93c9469797dd29ed877adefcb3d2e6da528d9203567ca2a4075e751db05c3e0";
            case CREEPER -> "f4254838c33ea227ffca223dddaabfe0b0215f70da649e944477f44370ca6952";
            case DONKEY -> "63a976c047f412ebc5cb197131ebef30c004c0faf49d8dd4105fca1207edaff3";
            case DROWNED -> "c84df79c49104b198cdad6d99fd0d0bcf1531c92d4ab6269e40b7d3cbbb8e98c";
            case DOLPHIN -> "8e9688b950d880b55b7aa2cfcd76e5a0fa94aac6d16f78e833f7443ea29fed3";
            case ENDERMAN -> "7a59bb0a7a32965b3d90d8eafa899d1835f424509eadd4e6b709ada50b9cf";
            case ENDERMITE -> "5a1a0831aa03afb4212adcbb24e5dfaa7f476a1173fce259ef75a85855";
            case EVOKER -> "d954135dc82213978db478778ae1213591b93d228d36dd54f1ea1da48e7cba6";
            case FOX -> "d8954a42e69e0881ae6d24d4281459c144a0d5a968aed35d6d3d73a3c65d26a";
            case FROG -> "ce62e8a048d040eb0533ba26a866cd9c2d0928c931c50b4482ac3a3261fab6f0";
            case GHAST -> "8b6a72138d69fbbd2fea3fa251cabd87152e4f1c97e5f986bf685571db3cc0";
            case GUARDIAN -> "b8e725779c234c590cce854db5c10485ed8d8a33fa9b2bdc3424b68bb1380bed";
            case GOAT -> "457a0d538fa08a7affe312903468861720f9fa34e86d44b89dcec5639265f03";
            case HORSE ->  "42eb967ab94fdd41a6325f1277d6dc019226e5cf34977eee69597fafcf5e";
            case HOGLIN -> "9bb9bc0f01dbd762a08d9e77c08069ed7c95364aa30ca1072208561b730e8d75";
            case HUSK -> "d674c63c8db5f4ca628d69a3b1f8a36e29d8fd775e1a6bdb6cabb4be4db121";
            case ILLUSIONER -> "512512e7d016a2343a7bff1a4cd15357ab851579f1389bd4e3a24cbeb88b";
            case IRON_GOLEM -> "89091d79ea0f59ef7ef94d7bba6e5f17f2f7d4572c44f90f76c4819a714";
            case LLAMA -> "cf24e56fd9ffd7133da6d1f3e2f455952b1da462686f753c597ee82299a";
            case MAGMA_CUBE -> "38957d5023c937c4c41aa2412d43410bda23cf79a9f6ab36b76fef2d7c429";
            case MULE -> "a0486a742e7dda0bae61ce2f55fa13527f1c3b334c57c034bb4cf132fb5f5f";
            case MOOSHROOM -> "a163bc416b8e6058f92b231e9a524b7fe118eb6e7eeab4ad16d1b52a3ec04fcd";
            case OCELOT -> "5657cd5c2989ff97570fec4ddcdc6926a68a3393250c1be1f0b114a1db1";
            case PANDA -> "ba6e3ad823f96d4a80a14556d8c9c7632163bbd2a876c0118b458925d87a5513";
            case PARROT -> "eb6086ab96e1f811621a44dff74a5e688f731828480e3c69a7a50e5efe4ac5c1";
            case PHANTOM -> "411d25bcdabafad5fd6e010c5b1cf7a00c9cca40c5a46747f706dc9cb3a";
            case PIG -> "621668ef7cb79dd9c22ce3d1f3f4cb6e2559893b6df4a469514e667c16aa4";
            case PIGLIN -> "11d18bbd0d795b9ac8efaad655e3d0c59fcbb9b964c2a9948ef537f4a3fbbf87";
            case PIGLIN_BRUTE -> "3e300e9027349c4907497438bac29e3a4c87a848c50b34c21242727b57f4e1cf";
            case PILLAGER -> "4aee6bb37cbfc92b0d86db5ada4790c64ff4468d68b84942fde04405e8ef5333";
            case POLAR_BEAR ->  "c4fe926922fbb406f343b34a10bb98992cee4410137d3f88099427b22de3ab90";
            case RABBIT ->  "ffecc6b5e6ea5ced74c46e7627be3f0826327fba26386c6cc7863372e9bc";
            case RAVAGER -> "3b62501cd1b87b37f628018210ec5400cb65a4d1aab74e6a3f7f62aa85db97ee";
            case SHEEP -> "f31f9ccc6b3e32ecf13b8a11ac29cd33d18c95fc73db8a66c5d657ccb8be70";
            case SILVERFISH -> "da91dab8391af5fda54acd2c0b18fbd819b865e1a8f1d623813fa761e924540";
            case SKELETON -> "301268e9c492da1f0d88271cb492a4b302395f515a7bbf77f4a20b95fc02eb2";
            case SKELETON_HORSE -> "72618a3c268ca388a38b893016aba26f6271519cde1a6bbd599cdd6472843b7f";
            case SLIME -> "895aeec6b842ada8669f846d65bc49762597824ab944f22f45bf3bbb941abe6c";
            case SNIFFER -> "fe5a8341c478a134302981e6a7758ea4ecfd8d62a0df4067897e75502f9b25de";
            case SNOW_GOLEM -> "98e334e4bee04264759a766bc1955cfaf3f56201428fafec8d4bf1bb36ae6";
            case SPIDER -> "cd541541daaff50896cd258bdbdd4cf80c3ba816735726078bfe393927e57f1";
            case STRAY -> "78ddf76e555dd5c4aa8a0a5fc584520cd63d489c253de969f7f22f85a9a2d56";
            case STRIDER -> "cb7ffdda656c68d88851a8e05b48cd2493773ffc4ab7d64e9302229fe3571059";
            case TRADER_LLAMA -> "15ad6b69cc6b4769d3516a0ce98b99b2a5d406fea4912dec570ea4a4f2bcc0ff";
            case TURTLE -> "99d6712582d60a0058e4fbee0e89dd089fbabb80f07e4a0874904c91bc48f08a";
            case VEX -> "c2ec5a516617ff1573cd2f9d5f3969f56d5575c4ff4efefabd2a18dc7ab98cd";
            case VILLAGER -> "4ca8ef2458a2b10260b8756558f7679bcb7ef691d41f534efea2ba75107315cc";
            case VINDICATOR -> "6deaec344ab095b48cead7527f7dee61b063ff791f76a8fa76642c8676e2173";
            case WANDERING_TRADER -> "499d585a9abf59fae277bb684d24070cef21e35609a3e18a9bd5dcf73a46ab93";
            case WARDEN -> "10b3c91b727d87d8c8aa96028f2275b8405debc7516a023d0f7748bab21f9c43";
            case WITCH -> "20e13d18474fc94ed55aeb7069566e4687d773dac16f4c3f8722fc95bf9f2dfa";
            case WITHER -> "ee280cefe946911ea90e87ded1b3e18330c63a23af5129dfcfe9a8e166588041";
            case WITHER_SKELETON -> "7953b6c68448e7e6b6bf8fb273d7203acd8e1be19e81481ead51f45de59a8";
            case WOLF -> "69d1d3113ec43ac2961dd59f28175fb4718873c6c448dfca8722317d67";
            case ZOGLIN -> "e67e18602e03035ad68967ce090235d8996663fb9ea47578d3a7ebbc42a5ccf9";
            case ZOMBIE -> "64528b3229660f3dfab42414f59ee8fd01e80081dd3df30869536ba9b414e089";
            case ZOMBIE_HORSE -> "171ce469cba4426c811f69be5d958a09bfb9b1b2bb649d3577a0c2161ad2f524";
            case ZOMBIE_VILLAGER -> "8c7505f224d5164a117d8c69f015f99eff434471c8a2df907096c4242c3524e8";
            case ZOMBIFIED_PIGLIN -> "7eabaecc5fae5a8a49c8863ff4831aaa284198f1a2398890c765e0a8de18da8c";
            default -> "c48d7d177f256ce10002ba9706068b93e337da24c85626c5af832021245f7a02"; // duck :D
        };
    }

    @NotNull
    static Set<String> getFoodCategories(@NotNull EntityType entityType) {
        //String raw = BukkitThing.toString(entityType);

        if (entityType.name().equals("CAMEL_HUSK")) return Lists.newSet(FOOD_ROTTEN_FLESH);
        if (entityType.name().equals("COPPER_GOLEM")) return Lists.newSet(FOOD_COPPER_INGOTS);
        if (entityType.name().equals("PARCHED")) return Lists.newSet(FOOD_BONE_MEAL);
        if (entityType.name().equals("NAUTILUS")) return Lists.newSet(FOOD_RAW_FISH, FOOD_FISH);
        if (entityType.name().equals("ZOMBIE_NAUTILUS")) return Lists.newSet(FOOD_ROTTEN_FLESH);
        if (entityType.name().equals("SULFUR_CUBE")) return Lists.newSet(FOOD_GUNPOWDER);
        if (entityType.name().equals("HAPPY_GHAST")) return Lists.newSet(FOOD_SNOWBALLS);

        return switch (entityType) {
            case ALLAY -> Lists.newSet(FOOD_CAKES);
            case ARMADILLO -> Lists.newSet(FOOD_SPIDER_EYES);
            case AXOLOTL -> Lists.newSet(FOOD_TROPICAL_FISH);
            case BAT -> Lists.newSet(FOOD_SPIDER_EYES);
            case BEE -> Lists.newSet(FOOD_FLOWERS);
            case BLAZE -> Lists.newSet(FOOD_BLAZE_POWDER);
            case BREEZE -> Lists.newSet(FOOD_FEATHERS);
            case CAT, OCELOT, FOX, WOLF, POLAR_BEAR, SPIDER, CAVE_SPIDER, RAVAGER, WARDEN -> Lists.newSet(FOOD_RAW_MEAT, FOOD_RAW_FISH);
            case CHICKEN -> Lists.newSet(FOOD_WHEAT_SEEDS);
            case CAMEL -> Lists.newSet(FOOD_CACTUS);
            case COW, LLAMA, TRADER_LLAMA, SHEEP, GOAT -> Lists.newSet(FOOD_WHEAT);
            case CREAKING -> Lists.newSet(FOOD_CREAKING);
            case DOLPHIN, GUARDIAN -> Lists.newSet(FOOD_RAW_FISH, FOOD_FISH);
            case DONKEY, HORSE, MULE -> Lists.newSet(FOOD_APPLES);
            case IRON_GOLEM -> Lists.newSet(FOOD_IRON_INGOTS);
            case MOOSHROOM -> Lists.newSet(FOOD_MUSHROOMS);
            case PANDA -> Lists.newSet(FOOD_RAW_MEAT, FOOD_BAMBOO);
            case PARROT -> Lists.newSet(FOOD_WHEAT_SEEDS);
            case PIG, PIGLIN, PIGLIN_BRUTE, HOGLIN -> Lists.newSet(FOOD_CARROTS, FOOD_APPLES, FOOD_BREAD, FOOD_BEETROOT);
            case RABBIT -> Lists.newSet(FOOD_CARROTS);
            case SNIFFER -> Lists.newSet(FOOD_TORCHFLOWER_SEEDS);
            case SNOW_GOLEM -> Lists.newSet(FOOD_CARROTS);
            case TURTLE, SLIME -> Lists.newSet(FOOD_EGGS);
            case STRIDER -> Lists.newSet(FOOD_WARPED_FUNGUS);
            case VILLAGER, WANDERING_TRADER, PILLAGER, ILLUSIONER, VINDICATOR, EVOKER, WITCH, VEX -> Lists.newSet(FOOD_MEAT, FOOD_FISH, FOOD_CARROTS, FOOD_BREAD, FOOD_BEETROOT, FOOD_APPLES);
            case FROG -> Lists.newSet(FOOD_SPIDER_EYES);
            case CREEPER, GHAST, MAGMA_CUBE -> Lists.newSet(FOOD_GUNPOWDER);
            case ZOGLIN, ZOMBIFIED_PIGLIN, ZOMBIE, ZOMBIE_VILLAGER, ZOMBIE_HORSE, HUSK, DROWNED, GIANT -> Lists.newSet(FOOD_ROTTEN_FLESH);
            case ENDERMAN, ENDERMITE, PHANTOM -> Lists.newSet(FOOD_CHORUS_FRUITS);
            case BOGGED, SKELETON, SKELETON_HORSE, STRAY, SILVERFISH -> Lists.newSet(FOOD_BONE_MEAL);
            case WITHER, WITHER_SKELETON -> Lists.newSet(FOOD_WITHER_ROSES);
            default -> Collections.emptySet();
        };
    }

    private static void setExtras(@NotNull PetTemplate petTemplate, @NotNull PetNMS nms) {
        EntityType entityType = petTemplate.getEntityType();
        World world = Bukkit.getWorlds().getFirst();
        LivingEntity livingEntity = nms.createUnspawnedEntity(entityType, world);
        if (livingEntity == null) return;

        double maxHealth = Math.max(10D, nms.getAttributeValue(livingEntity, Attribute.MAX_HEALTH));

        for (Stat petAttribute : AttributeRegistry.values()) {
            String name = petAttribute.getId();
            Attribute vanilla = petAttribute.getVanillaMirror();

            double startValue = -1D;
            double perAspectValue = 0D;

            if (vanilla != null) {
                startValue = nms.getAttributeValue(livingEntity, vanilla);
                if (name.equalsIgnoreCase(AttributeRegistry.MAX_HEALTH)) {
                    startValue = Math.max(10D, startValue);
                    perAspectValue = startValue * 0.05;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.ATTACK_DAMAGE)) {
                    startValue = Math.max(1, startValue);
                    perAspectValue = startValue * 0.05;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.ATTACK_SPEED)) {
                    startValue = 0.5D;
                    perAspectValue = startValue * 0.05;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.ARMOR)) {
                    startValue = Math.max(0.5, startValue);
                    perAspectValue = startValue * 0.1;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.MOVEMENT_SPEED)) {
                    if (isVillagerType(entityType)) startValue = VILLAGER_MOVEMENT_SPEED;
                    else if (!isCamelType(entityType)) startValue = Math.max(0.1, startValue);
                    perAspectValue = startValue * 0.01;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.FLYING_SPEED)) {
                    perAspectValue = startValue * 0.01;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.SCALE)) {
                    perAspectValue = startValue * 0.02;
                }
            }
            else {
                if (name.equalsIgnoreCase(AttributeRegistry.MAX_SATURATION)) {
                    if (!petTemplate.getFoodCategories().isEmpty()) {
                        startValue = maxHealth * 0.75D;
                        perAspectValue = maxHealth * 0.1;
                    }
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.HEALTH_REGENEATION_FORCE)) {
                    startValue = 0.5;
                    perAspectValue = 0.05;
                }
                else if (name.equalsIgnoreCase(AttributeRegistry.HEALTH_REGENEATION_SPEED)) {
                    startValue = 1D;
                    perAspectValue = 0.02;
                }
            }

            if (startValue >= 0D) {
                petTemplate.getAttributesStart().put(name, startValue);
            }

            petTemplate.getAttributesPerAspect().put(name, perAspectValue);
        }

        if (entityType == EntityType.BAT) {
            // Vanilla bats do not declare combat or flying attributes. Give
            // this combat pet conservative explicit values instead of relying
            // on registry defaults that players cannot improve with aspects.
            petTemplate.getAttributesStart().put(AttributeRegistry.ATTACK_DAMAGE, 1D);
            petTemplate.getAttributesStart().put(AttributeRegistry.ATTACK_SPEED, 0.5D);
            petTemplate.getAttributesStart().put(AttributeRegistry.MOVEMENT_SPEED, 0.25D);
            petTemplate.getAttributesStart().put(AttributeRegistry.FLYING_SPEED, 0.35D);
            petTemplate.getAttributesPerAspect().put(AttributeRegistry.ATTACK_DAMAGE, 0.05D);
            petTemplate.getAttributesPerAspect().put(AttributeRegistry.ATTACK_SPEED, 0.025D);
            petTemplate.getAttributesPerAspect().put(AttributeRegistry.MOVEMENT_SPEED, 0.0025D);
            petTemplate.getAttributesPerAspect().put(AttributeRegistry.FLYING_SPEED, 0.0035D);
        }
    }

    static boolean migrateSaturationDefaults(@NotNull FileConfig config, @NotNull Set<String> defaultFoodCategories) {
        if (defaultFoodCategories.isEmpty()) return false;

        String foodPath = "Saturation.FoodCategories";
        Set<String> foodCategories = new LinkedHashSet<>(config.getStringSet(foodPath));
        boolean changed = false;
        for (String defaultCategory : defaultFoodCategories) {
            if (foodCategories.stream().anyMatch(defaultCategory::equalsIgnoreCase)) continue;

            foodCategories.add(defaultCategory);
            changed = true;
        }
        if (changed) config.set(foodPath, foodCategories);

        double maxHealth = config.getDouble("Attributes.Default." + AttributeRegistry.MAX_HEALTH, 20D);
        if (!Double.isFinite(maxHealth) || maxHealth <= 0D) maxHealth = 20D;
        maxHealth = Math.max(10D, maxHealth);

        String maxPath = "Attributes.Default." + AttributeRegistry.MAX_SATURATION;
        if (!config.contains(maxPath)) {
            config.set(maxPath, maxHealth * 0.75D);
            changed = true;
        }

        String perAspectPath = "Attributes.Per_Aspect." + AttributeRegistry.MAX_SATURATION;
        double perAspect = config.getDouble(perAspectPath, Double.NaN);
        if (!config.contains(perAspectPath) || equalsGeneratedValue(perAspect, 0D)) {
            config.set(perAspectPath, maxHealth * 0.1D);
            changed = true;
        }

        return changed;
    }

    private static boolean migrateLegacyVillagerMovementSpeed(@NotNull File file, @NotNull EntityType entityType) {
        double legacySpeed = switch (entityType) {
            case VILLAGER -> 0.5D;
            case WANDERING_TRADER -> 0.7D;
            default -> -1D;
        };
        if (legacySpeed < 0D) return false;

        FileConfig config = FileConfig.load(file.toPath());
        double currentSpeed = config.getDouble("Attributes.Default." + AttributeRegistry.MOVEMENT_SPEED, Double.NaN);
        double currentPerAspect = config.getDouble("Attributes.Per_Aspect." + AttributeRegistry.MOVEMENT_SPEED, Double.NaN);
        if (!equalsGeneratedValue(currentSpeed, legacySpeed)
            || !equalsGeneratedValue(currentPerAspect, legacySpeed * 0.01D)) return false;

        config.set("Attributes.Default." + AttributeRegistry.MOVEMENT_SPEED, VILLAGER_MOVEMENT_SPEED);
        config.set("Attributes.Per_Aspect." + AttributeRegistry.MOVEMENT_SPEED, VILLAGER_MOVEMENT_SPEED * 0.01D);
        config.saveChanges();
        return true;
    }

    private static boolean migrateLegacyCamelMovementSpeed(@NotNull File file, @NotNull EntityType entityType) {
        if (!isCamelType(entityType)) return false;

        FileConfig config = FileConfig.load(file.toPath());
        String defaultPath = "Attributes.Default." + AttributeRegistry.MOVEMENT_SPEED;
        String perAspectPath = "Attributes.Per_Aspect." + AttributeRegistry.MOVEMENT_SPEED;
        double currentSpeed = config.getDouble(defaultPath, Double.NaN);
        double currentPerAspect = config.getDouble(perAspectPath, Double.NaN);
        if (!equalsGeneratedValue(currentSpeed, 0.1D)
            || !equalsGeneratedValue(currentPerAspect, 0.001D)) return false;

        config.set(defaultPath, 0.09D);
        config.set(perAspectPath, 0.0009D);
        config.saveChanges();
        return true;
    }

    private static boolean isVillagerType(@NotNull EntityType entityType) {
        return entityType == EntityType.VILLAGER || entityType == EntityType.WANDERING_TRADER;
    }

    private static boolean isCamelType(@NotNull EntityType entityType) {
        return entityType == EntityType.CAMEL || entityType.name().equals("CAMEL_HUSK");
    }

    private static boolean equalsGeneratedValue(double actual, double expected) {
        return Double.isFinite(actual) && Math.abs(actual - expected) < 1.0E-12D;
    }
}
