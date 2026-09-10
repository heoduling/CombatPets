package su.nightexpress.combatpets.wardrobe;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.wardrobe.config.WardrobeConfig;
import su.nightexpress.combatpets.wardrobe.handler.*;
import su.nightexpress.combatpets.wardrobe.listener.WardrobeListener;
import su.nightexpress.combatpets.wardrobe.util.EntityVariant;
import su.nightexpress.combatpets.wardrobe.util.VariantHandler;
import su.nightexpress.combatpets.wardrobe.util.VariantRegistry;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.ItemReplacer;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.PDCUtil;
import su.nightexpress.nightcore.util.StringUtil;

public class WardrobeManager extends AbstractManager<PetsPlugin> {

    public static final String FILE_NAME = "accessories.yml";

    private static final String[] CLIMATE_VARIANTS = {
        "cold", "寒带", "temperate", "温带", "warm", "热带"
    };
    private static final String[] VILLAGER_TYPES = {
        "desert", "沙漠", "jungle", "丛林", "plains", "平原", "savanna", "热带草原",
        "snow", "雪原", "swamp", "沼泽", "taiga", "针叶林"
    };
    private static final String[] VILLAGER_PROFESSIONS = {
        "armorer", "盔甲匠", "butcher", "屠夫", "cartographer", "制图师", "cleric", "牧师",
        "farmer", "农民", "fisherman", "渔夫", "fletcher", "制箭师", "leatherworker", "皮匠",
        "librarian", "图书管理员", "mason", "石匠", "nitwit", "傻子", "none", "无职业",
        "shepherd", "牧羊人", "toolsmith", "工具匠", "weaponsmith", "武器匠"
    };

    public WardrobeManager(@NotNull PetsPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onLoad() {
        FileConfig config = this.getConfig();

        this.loadConfig(config);
        this.loadVariants(config);

        config.saveChanges();

        this.addListener(new WardrobeListener(this.plugin, this));
    }

    @Override
    protected void onShutdown() {
        VariantRegistry.clear();
    }

    @NotNull
    public FileConfig getConfig() {
        return FileConfig.loadOrExtract(this.plugin, FILE_NAME);
    }

    private void loadConfig(@NotNull FileConfig config) {
        config.initializeOptions(WardrobeConfig.class);
    }

    private void loadVariants(@NotNull FileConfig config) {
        config.setComments("Variants",
            "可用的宠物外观配饰类型。这里只控制配饰类型是否启用及其显示方式。"
        );
        this.register("age", new AgeVariantHandler(), config);
        this.register("size", new SizeVariantHandler(), config);
        this.register("axolotl_variant", new AxolotlVariantHandler(), config);
        this.register("fox_type", new FoxTypeVariantHandler(), config);
        this.register("llama_color", new LlamaColorVariantHandler(), config);
        this.register("sheep_color", new SheepColorVariantHandler(), config);
        this.register("horse_style", new HorseStyleVariantHandler(), config);
        this.register("horse_color", new HorseColorVariantHandler(), config);
        this.register("shear_style", new SheepShearVariantHandler(), config);
        this.register("rabbit_type", new RabbitVariantHandler(), config);

        this.registerReflective("cat_type", "org.bukkit.entity.Cat", "getCatType", "setCatType", config,
            "all_black", "全黑猫", "black", "黑白猫", "british_shorthair", "英国短毛猫",
            "calico", "三花猫", "jellie", "杰利猫", "persian", "波斯猫", "ragdoll", "布偶猫",
            "red", "橘猫", "siamese", "暹罗猫", "tabby", "虎斑猫", "white", "白猫");
        this.registerReflective("parrot_variant", "org.bukkit.entity.Parrot", "getVariant", "setVariant", config,
            "red", "红色", "blue", "蓝色", "green", "绿色", "cyan", "青色", "gray", "灰色");
        this.registerReflective("frog_variant", "org.bukkit.entity.Frog", "getVariant", "setVariant", config, CLIMATE_VARIANTS);
        this.registerReflective("wolf_variant", "org.bukkit.entity.Wolf", "getVariant", "setVariant", config,
            "ashen", "灰狼", "black", "黑狼", "chestnut", "栗色狼", "pale", "苍狼",
            "rusty", "锈色狼", "snowy", "雪狼", "spotted", "斑点狼", "striped", "条纹狼", "woods", "森林狼");
        this.registerReflective("mooshroom_variant", "org.bukkit.entity.MushroomCow", "getVariant", "setVariant", config,
            "red", "红色", "brown", "棕色");
        this.registerReflective("cow_variant", "org.bukkit.entity.Cow", "getVariant", "setVariant", config, CLIMATE_VARIANTS);
        this.registerReflective("pig_variant", "org.bukkit.entity.Pig", "getVariant", "setVariant", config, CLIMATE_VARIANTS);
        this.registerReflective("chicken_variant", "org.bukkit.entity.Chicken", "getVariant", "setVariant", config, CLIMATE_VARIANTS);
        this.registerReflective("zombie_nautilus_variant", "org.bukkit.entity.ZombieNautilus", "getVariant", "setVariant", config,
            "temperate", "温带", "warm", "热带");
        this.registerReflective("villager_type", "org.bukkit.entity.Villager", "getVillagerType", "setVillagerType", config, VILLAGER_TYPES);
        this.registerReflective("villager_profession", "org.bukkit.entity.Villager", "getProfession", "setProfession", config, VILLAGER_PROFESSIONS);
        this.registerReflective("zombie_villager_type", "org.bukkit.entity.ZombieVillager", "getVillagerType", "setVillagerType", config, VILLAGER_TYPES);
        this.registerReflective("zombie_villager_profession", "org.bukkit.entity.ZombieVillager", "getVillagerProfession", "setVillagerProfession", config, VILLAGER_PROFESSIONS);
        this.registerReflective("snow_golem_pumpkin", "org.bukkit.entity.Snowman", "isDerp", "setDerp", config,
            "false", "佩戴南瓜", "true", "未佩戴南瓜");
        this.registerReflective("bogged_sheared", "org.bukkit.entity.Bogged", "isSheared", "setSheared", config,
            "false", "长有蘑菇", "true", "已剪除蘑菇");
    }

    private void registerReflective(@NotNull String name,
                                    @NotNull String entityClassName,
                                    @NotNull String getterName,
                                    @NotNull String setterName,
                                    @NotNull FileConfig config,
                                    @NotNull String... valueLabels) {
        ReflectiveVariantHandler handler = new ReflectiveVariantHandler(entityClassName, getterName, setterName, valueLabels);
        if (handler.isAvailable()) this.register(name, handler, config);
    }

    public <T> void register(@NotNull String name, @NotNull VariantHandler<T> handler, @NotNull FileConfig config) {
        EntityVariant<T> variant = new EntityVariant<>(name, handler);

        String path = "Variants." + variant.getName();

        config.setComments(path, "配饰类型：" + PetCreator.getVariantName(variant.getName()) + "。");
        config.setComments(path + ".Enabled", "是否启用此配饰类型。");
        config.setComments(path + ".DisplayName", "此配饰类型在物品和消息中的显示名称。");
        config.setComments(path + ".Icon", "此配饰类型在界面中使用的图标物品。");

        boolean enabled = ConfigValue.create(path + ".Enabled", true).read(config);
        if (!enabled) return;

        String displayName = ConfigValue.create(path + ".DisplayName", PetCreator.getVariantName(variant.getName())).read(config);
        variant.setDisplayName(displayName);

        ItemStack icon = ConfigValue.create(path + ".Icon", new ItemStack(Material.MAP)).read(config);
        variant.setIcon(icon);

        VariantRegistry.register(variant);
        this.plugin.info("已注册宠物配饰类型 '" + variant.getName() + "'。");
    }

    public boolean applyAccessory(@NotNull Player player, @NotNull LivingEntity entity, @NotNull ItemStack item, @NotNull EntityVariant<?> variant, @NotNull String value) {
        ActivePet petHolder = this.plugin.getPetManager().getPetByMob(entity);
        if (petHolder == null) return false;

        if (!petHolder.isOwner(player)) {
            Lang.ACCESSORY_APPLY_ERROR_NOT_YOURS.message().send(player);
            return false;
        }

        if (variant.getHandler().alreadyHas(entity, value)) {
            Lang.ACCESSORY_APPLY_ERROR_ALREADY_HAS.message().send(player);
            return false;
        }

        if (!variant.getHandler().apply(entity, value)) {
            Lang.ACCESSORY_APPLY_ERROR_WRONG_TYPE.message().send(player);
            return false;
        }

        item.setAmount(item.getAmount() - 1);

        WardrobeConfig.WARDROBE_ACCESSORY_APPLY_EFFECT.get().play(entity.getEyeLocation(), 0.35, 0.05, 40);
        WardrobeConfig.WARDROBE_ACCESSORY_APPLY_SOUND.get().play(entity.getEyeLocation());

        return true;
    }

    // Used to store entity accessories in pet egg obtained from capturing.
    public void storeAccessoryData(@NotNull LivingEntity entity, @NotNull ItemStack itemStack) {
        ItemUtil.editMeta(itemStack, meta -> {
            PetWardrobe.of(entity).getAccessories().forEach((key, data) -> {

                EntityVariant<?> variant = VariantRegistry.getVariant(key);
                if (variant == null) return;

                PDCUtil.set(meta, variant.getKey(), data);
            });
        });
    }

    // Used to restore entity accessories to PetData when player claims pet egg.
    @NotNull
    public PetWardrobe readAccessoryData(@NotNull ItemStack itemStack) {
        PetWardrobe wardrobe = new PetWardrobe();

        VariantRegistry.getVariants().forEach(variant -> {
            String data = PDCUtil.getString(itemStack, variant.getKey()).orElse(null);
            if (data == null) return;

            wardrobe.addAccessory(variant.getName(), data);
        });

        return wardrobe;
    }

    @NotNull
    public ItemStack getItem(@NotNull EntityVariant<?> variant, @NotNull String value) {
        ItemStack item = new ItemStack(WardrobeConfig.WARDROBE_ACCESSORY_ITEM.get());
        ItemReplacer.replace(item, str -> str
            .replace(Placeholders.GENERIC_TYPE, variant.getDisplayName())
            .replace(Placeholders.GENERIC_NAME, variant.getHandler().getLocalized(value)));

        PDCUtil.set(item, Keys.accessoryType, variant.getName());
        PDCUtil.set(item, Keys.accessoryValue, value.toLowerCase());

        return item;
    }

    @Nullable
    public EntityVariant<?> getType(@NotNull ItemStack item) {
        String type = PDCUtil.getString(item, Keys.accessoryType).orElse(null);
        return type == null ? null : VariantRegistry.getVariant(type);
    }

    @Nullable
    public String getValue(@NotNull ItemStack item) {
        return PDCUtil.getString(item, Keys.accessoryValue).orElse(null);
    }
}
