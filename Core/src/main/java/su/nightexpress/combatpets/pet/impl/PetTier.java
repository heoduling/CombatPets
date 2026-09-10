package su.nightexpress.combatpets.pet.impl;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.combatpets.api.pet.Aspect;
import su.nightexpress.combatpets.api.pet.Stat;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.integration.currency.CurrencyId;
import su.nightexpress.nightcore.integration.currency.EconomyBridge;
import su.nightexpress.nightcore.manager.AbstractFileData;
import su.nightexpress.nightcore.util.StringUtil;
import su.nightexpress.nightcore.util.placeholder.PlaceholderMap;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class PetTier extends AbstractFileData<PetsPlugin> implements Tier {

    private String    name;
    private ItemStack icon;
    private String    nameFormat;
    private double    weight;
    private boolean   capturable;
    private boolean   hasInventory;
    private int       inventorySize;
    private boolean   hasEquipment;
    private int       autoRespawnTime;
    private String    reviveCurrency;
    private double    reviveCost;
    private double    inventoryDropChance;
    private double    equipmentDropChance;
    private int       initialXP;
    private double    xpFactor;
    private int       maxLevel;
    private int       startAspectPoints;
    private int       aspectPointsPerLevel;

    private final Map<String, Integer>      aspectsMax;
    private final Map<String, Map<String, Double>> attributeTargets;
    private final Object                    attributeTargetLock;
    private boolean                          attributeTargetsDirty;
    private final TreeMap<Integer, Integer> xpMap;
    private final PlaceholderMap            placeholderMap;

    public PetTier(@NotNull PetsPlugin plugin, @NotNull File file) {
        super(plugin, file);
        this.aspectsMax = new HashMap<>();
        this.attributeTargets = new HashMap<>();
        this.attributeTargetLock = new Object();
        this.attributeTargetsDirty = false;
        this.xpMap = new TreeMap<>();
        this.placeholderMap = Placeholders.forTier(this);
    }

    @Override
    protected boolean onLoad(@NotNull FileConfig config) {
        this.name = ConfigValue.create("Name", this.plugin.getDetails().getLanguage().equalsIgnoreCase("zh") ? PetCreator.getTierName(this.getId()) : StringUtil.capitalizeUnderscored(this.getId()),
            "宠物品质的显示名称。"
        ).read(config);

        this.icon = ConfigValue.create("Icon", new ItemStack(Material.SILVERFISH_SPAWN_EGG),
            "宠物品质的图标。",
            "可用选项：" + WIKI_ITEMS_URL
        ).read(config);

        this.setNameFormat(ConfigValue.create("Name_Format",
            WHITE.enclose(PET_NAME) + " " + GRAY.enclose(this.plugin.getDetails().getLanguage().equalsIgnoreCase("zh") ? "等级 " : "Lv. ") + LIGHT_GREEN.enclose(PET_LEVEL),
            "该品质宠物的实体名称格式。",
            "可使用宠物和品质占位符：" + WIKI_PLACEHOLDERS_URL
        ).read(config));

        this.setWeight(ConfigValue.create("Weight",
            25D,
            "宠物品质的权重。",
            "权重决定捕捉和神秘宠物蛋选中该品质的概率，以及品质在 GUI 中的显示顺序。",
            "权重越高，抽中概率越高；实际概率为该品质权重除以全部品质权重总和。"
        ).read(config));

        this.hasInventory = ConfigValue.create("Inventory.Enabled",
            true,
            "启用后，该品质的宠物可以拥有背包。"
        ).read(config);

        this.inventorySize = ConfigValue.create("Inventory.Size",
            9,
            "宠物背包大小。可用值：[9, 18, 27, 36, 45, 54]"
        ).read(config);

        this.hasEquipment = ConfigValue.create("Equipment.Enabled",
            false,
            "启用后，该品质的宠物可以穿戴护甲和武器。"
        ).read(config);

        this.autoRespawnTime = ConfigValue.create("Death.Auto_Respawn_Time",
            600,
            "该品质宠物死亡后的自动复活时间（秒）。",
            "设为 -1 可禁用自动复活。"
        ).read(config);

        this.setReviveCurrency(ConfigValue.create("Death.Revive_Currency",
            CurrencyId.VAULT,
            "复活宠物时使用的货币。",
            "可用货币：[" + String.join(", ", EconomyBridge.getCurrencyIds()) + "]"
        ).read(config));

        this.setReviveCost(ConfigValue.create("Death.Revive_Cost",
            2000D,
            "玩家立即复活该品质宠物所需的价格。"
        ).read(config));

        this.inventoryDropChance = ConfigValue.create("Death.Inventory_Drop_Chance",
            50D,
            "宠物死亡时整个背包掉落的概率。",
            "请确保已在 config.yml 中启用宠物背包掉落。"
        ).read(config);

        this.equipmentDropChance = ConfigValue.create("Death.Equipment_Drop_Chance",
            25D,
            "宠物死亡时全部装备掉落的概率。",
            "请确保已在 config.yml 中启用宠物装备掉落。"
        ).read(config);

        this.capturable = ConfigValue.create("Catching.Enabled",
            false,
            "启用后，捕捉时可以获得该品质的宠物。"
        ).read(config);

        this.initialXP = ConfigValue.create("Leveling.Start_Exp",
            500,
            "升至下一级所需经验的初始值。"
        ).read(config);

        this.setXPModifier(ConfigValue.create("Leveling.XPModifier",
            1.093,
            "每级所需经验的增长倍率。"
        ).read(config));

        this.maxLevel = ConfigValue.create("Leveling.Max_Level",
            30,
            "该品质宠物的最高等级。"
        ).read(config);

        this.aspectPointsPerLevel = ConfigValue.create("Leveling.Aspect_Points_Per_Level",
            1,
            "宠物每次升级获得的属性点数量。"
        ).read(config);

        this.setStartAspectPoints(ConfigValue.create("Leveling.Start_Aspect_Points",
            0,
            "获得该品质宠物时附带的初始属性点数量。"
        ).read(config));

        if (this.getMaxLevel() > 0) {
            for (int level = 1; level < (this.getMaxLevel() + 1); level++) {
                double previousXP = this.xpMap.getOrDefault(level - 1, this.getInitialXP());
                int xp = (int) (previousXP * this.xpFactor);
                this.xpMap.put(level, xp);
            }
        }


        for (String aspectId : config.getSection("Aspects.Max")) {
            Aspect aspect = this.plugin.getPetManager().getAspect(aspectId);
            if (aspect == null) continue;

            this.aspectsMax.put(aspect.getId(), config.getInt("Aspects.Max." + aspectId));
        }

        synchronized (this.attributeTargetLock) {
            this.attributeTargets.clear();
            this.attributeTargetsDirty = false;
            for (String petId : config.getSection("Attributes.Target")) {
                String petPath = "Attributes.Target." + petId;
                Map<String, Double> targets = new HashMap<>();
                for (String attributeId : config.getSection(petPath)) {
                    double target = config.getDouble(petPath + "." + attributeId, Double.NaN);
                    if (!Double.isFinite(target) || target < 0D) continue;

                    targets.put(attributeId.toLowerCase(), target);
                }
                if (!targets.isEmpty()) this.attributeTargets.put(petId.toLowerCase(), targets);
            }
        }
        config.setComments("Attributes.Target", "管理员设置的属性满点最终值；留空时使用宠物配置中的 Per_Aspect。\n目标值按品质配置的全部关联属性点计算，玩家仍需手动分配属性点。\n路径格式：Attributes.Target.<宠物ID>.<属性ID>。");

        return true;
    }

    @Override
    protected void onSave(@NotNull FileConfig config) {
        config.setComments("Attributes.Target", "管理员设置的属性满点最终值；留空时使用宠物配置中的 Per_Aspect。\n目标值按品质配置的全部关联属性点计算，玩家仍需手动分配属性点。\n路径格式：Attributes.Target.<宠物ID>.<属性ID>。");
        config.set("Name", this.getName());
        config.setItem("Icon", this.getIcon());
        config.set("Name_Format", this.getNameFormat());
        config.set("Weight", this.getWeight());
        config.set("Inventory.Enabled", this.hasInventory());
        config.set("Inventory.Size", this.getInventorySize());
        config.set("Equipment.Enabled", this.hasEquipment());
        config.set("Death.Auto_Respawn_Time", this.getAutoRespawnTime());
        config.set("Death.Revive_Cost", this.getReviveCost());
        config.set("Death.Inventory_Drop_Chance", this.getInventoryDropChance());
        config.set("Death.Equipment_Drop_Chance", this.getEquipmentDropChance());
        config.set("Catching.Enabled", this.isCapturable());
        config.set("Leveling.Start_Exp", this.getInitialXP());
        config.set("Leveling.XPModifier", this.getXPModifier());
        config.set("Leveling.Max_Level", this.getMaxLevel());
        config.set("Leveling.Aspect_Points_Per_Level", this.aspectPointsPerLevel);
        config.set("Leveling.Start_Aspect_Points", this.startAspectPoints);
        config.remove("Aspects.Max");
        this.getAspectsMax().forEach((aspect, value) -> {
            config.set("Aspects.Max." + aspect, value);
        });

        synchronized (this.attributeTargetLock) {
            config.remove("Attributes.Target");
            this.attributeTargets.forEach((petId, targets) -> targets.forEach((attributeId, target) ->
                config.set("Attributes.Target." + petId + "." + attributeId, target)));
            this.attributeTargetsDirty = false;
        }
    }

    @Override
    @NotNull
    public PlaceholderMap getPlaceholders() {
        return this.placeholderMap;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(@NotNull String name) {
        this.name = name;
    }

    @NotNull
    @Override
    public ItemStack getIcon() {
        return new ItemStack(this.icon);
    }

    @Override
    public void setIcon(@NotNull ItemStack icon) {
        this.icon = new ItemStack(icon);
    }

    @NotNull
    @Override
    public String getNameFormat() {
        return nameFormat;
    }

    @Override
    public void setNameFormat(@NotNull String nameFormat) {
        this.nameFormat = nameFormat;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public boolean isCapturable() {
        return this.capturable;
    }

    @Override
    public void setCapturable(boolean capturable) {
        this.capturable = capturable;
    }

    @Override
    public boolean hasInventory() {
        return this.hasInventory && this.getInventorySize() > 0 && this.getInventorySize() % 9 == 0;
    }

    @Override
    public void setHasInventory(boolean hasInventory) {
        this.hasInventory = hasInventory;
    }

    @Override
    public int getInventorySize() {
        return inventorySize;
    }

    @Override
    public void setInventorySize(int inventorySize) {
        this.inventorySize = inventorySize;
    }

    @Override
    public boolean hasEquipment() {
        return this.hasEquipment;
    }

    @Override
    public void setHasEquipment(boolean hasEquipment) {
        this.hasEquipment = hasEquipment;
    }

    @Override
    public int getAutoRespawnTime() {
        return this.autoRespawnTime;
    }

    @Override
    public void setAutoRespawnTime(int autoRespawnTime) {
        this.autoRespawnTime = autoRespawnTime;
    }

    @NotNull
    @Override
    public String getReviveCurrency() {
        return reviveCurrency;
    }

    @Override
    public void setReviveCurrency(@NotNull String reviveCurrency) {
        this.reviveCurrency = reviveCurrency;
    }

    @Override
    public double getReviveCost() {
        return this.reviveCost;
    }

    @Override
    public void setReviveCost(double reviveCost) {
        this.reviveCost = Math.max(0, reviveCost);
    }

    @Override
    public double getInventoryDropChance() {
        return inventoryDropChance;
    }

    @Override
    public void setInventoryDropChance(double inventoryDropChance) {
        this.inventoryDropChance = Math.max(0, inventoryDropChance);
    }

    @Override
    public double getEquipmentDropChance() {
        return equipmentDropChance;
    }

    @Override
    public void setEquipmentDropChance(double equipmentDropChance) {
        this.equipmentDropChance = Math.max(0, equipmentDropChance);
    }

    @Override
    public int getInitialXP() {
        return this.initialXP;
    }

    @Override
    public void setInitialXP(int initialXP) {
        this.initialXP = Math.max(1, initialXP);
    }

    @Override
    public double getXPModifier() {
        return this.xpFactor;
    }

    @Override
    public void setXPModifier(double xpModifier) {
        this.xpFactor = Math.max(1, xpModifier);
    }

    @Override
    public int getMaxLevel() {
        return this.maxLevel;
    }

    @Override
    public void setMaxLevel(int maxLevel) {
        this.maxLevel = Math.max(1, maxLevel);
    }

    @Override
    public int getRequiredXP(int level) {
        Map.Entry<Integer, Integer> entry = this.xpMap.floorEntry(level);
        return entry != null ? entry.getValue() : this.getInitialXP();
    }

    @Override
    public int getAspectPointsPerLevel() {
        return this.aspectPointsPerLevel;
    }

    @Override
    public void setAspectPointsPerLevel(int aspectPointsPerLevel) {
        this.aspectPointsPerLevel = Math.max(0, aspectPointsPerLevel);
    }

    @Override
    public int getStartAspectPoints() {
        return startAspectPoints;
    }

    @Override
    public void setStartAspectPoints(int startAspectPoints) {
        this.startAspectPoints = Math.max(0, startAspectPoints);
    }

    @Override
    @NotNull
    public Map<String, Integer> getAspectsMax() {
        return this.aspectsMax;
    }

    /**
     * Returns the administrator-defined final value for an attribute after
     * all linked aspect points are spent, or {@code null} when the pet still
     * uses its legacy {@code Attributes.Per_Aspect} value.
     */
    @Nullable
    public Double getAttributeTarget(@NotNull Template template, @NotNull Stat attribute) {
        synchronized (this.attributeTargetLock) {
            Map<String, Double> targets = this.attributeTargets.get(template.getId().toLowerCase());
            return targets == null ? null : targets.get(attribute.getId().toLowerCase());
        }
    }

    /**
     * Calculates the per-point increment for the target-value model. The
     * target is divided across all aspect points that are linked to the
     * attribute, so shared attributes still keep their existing additive
     * behaviour while reaching exactly the configured final value at full
     * points.
     */
    public double getAttributePerAspect(@NotNull Template template, @NotNull Stat attribute) {
        Double target = this.getAttributeTarget(template, attribute);
        if (target == null) return template.getAttributePerAspect(attribute);

        int pointBudget = this.getAttributePointBudget(attribute);
        if (pointBudget <= 0) return 0D;

        return (target - template.getAttributeStart(attribute)) / (double) pointBudget;
    }

    public int getAttributePointBudget(@NotNull Stat attribute) {
        int total = 0;
        for (Aspect aspect : this.plugin.getPetManager().getAspects()) {
            if (aspect.getAttributes().contains(attribute.getId())) {
                total += Math.max(0, this.getAspectMax(aspect));
            }
        }
        return total;
    }

    /** Returns the target model's full-point value, or the legacy projected value. */
    public double getAttributeMaximum(@NotNull Template template, @NotNull Stat attribute) {
        Double target = this.getAttributeTarget(template, attribute);
        if (target != null) return target;

        return template.getAttributeStart(attribute)
            + template.getAttributePerAspect(attribute) * this.getAttributePointBudget(attribute);
    }

    public boolean setAttributeTarget(@NotNull Template template, @NotNull Stat attribute, double target) {
        if (!Double.isFinite(target) || target < 0D) return false;

        synchronized (this.attributeTargetLock) {
            String petId = template.getId().toLowerCase();
            String attributeId = attribute.getId().toLowerCase();
            Double previous = this.attributeTargets.computeIfAbsent(petId, ignored -> new HashMap<>())
                .put(attributeId, target);
            if (previous == null || Double.compare(previous, target) != 0) {
                this.attributeTargetsDirty = true;
            }
        }
        return true;
    }

    public boolean resetAttributeTarget(@NotNull Template template, @NotNull Stat attribute) {
        synchronized (this.attributeTargetLock) {
            Map<String, Double> targets = this.attributeTargets.get(template.getId().toLowerCase());
            if (targets == null) return false;

            boolean removed = targets.remove(attribute.getId().toLowerCase()) != null;
            if (targets.isEmpty()) this.attributeTargets.remove(template.getId().toLowerCase());
            if (removed) this.attributeTargetsDirty = true;
            return removed;
        }
    }

    /** Saves the tier file after an async admin edit or during shutdown. */
    public void saveAttributeTargets() {
        synchronized (this.attributeTargetLock) {
            if (this.attributeTargetsDirty) this.save();
        }
    }
}
