package su.nightexpress.combatpets.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.Placeholders;
import su.nightexpress.combatpets.api.item.ItemType;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Keys;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.combatpets.wardrobe.util.EntityVariant;
import su.nightexpress.combatpets.wardrobe.util.VariantRegistry;
import su.nightexpress.nightcore.manager.AbstractManager;
import su.nightexpress.nightcore.util.*;
import su.nightexpress.nightcore.util.random.Rnd;
import su.nightexpress.nightcore.util.wrapper.UniParticle;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class ItemManager extends AbstractManager<PetsPlugin> {

    private static final String RECOVERY_PROPERTY = "combatpets_egg";

    public ItemManager(@NotNull PetsPlugin plugin) {
        super(plugin);
    }

    @Override
    protected void onLoad() {
        this.addListener(new ItemListener(this.plugin, this));
    }

    @Override
    protected void onShutdown() {

    }

    private void setItemType(@NotNull ItemStack itemStack, @NotNull ItemType type) {
        ItemUtil.editMeta(itemStack, meta -> setItemType(meta, type));
    }

    private void setItemType(@NotNull ItemMeta meta, @NotNull ItemType type) {
        PDCUtil.set(meta, Keys.itemType, type.name());
    }

    @Nullable
    public ItemType getItemType(@NotNull ItemStack itemStack) {
        String name = PDCUtil.getString(itemStack, Keys.itemType).orElse(null);
        ItemType type = name == null ? null : StringUtil.getEnum(name, ItemType.class).orElse(null);
        if (type == ItemType.EGG || type == ItemType.MYSTERY_EGG) {
            this.sealEggRecovery(itemStack, type);
            return type;
        }

        return type == null ? this.restoreEgg(itemStack) : type;
    }

    public boolean isItemOfType(@NotNull ItemStack itemStack, @NotNull ItemType itemType) {
        return this.getItemType(itemStack) == itemType;
    }

    @NotNull
    public ItemStack createEgg(@NotNull Template template, @NotNull Tier tier) {
        ItemStack item = PetUtils.getRawEggItem(template);
        ItemUtil.editMeta(item, meta -> {
            PDCUtil.set(meta, Keys.eggPetId, template.getId());
            PDCUtil.set(meta, Keys.eggTierId, tier.getId());
            setItemType(meta, ItemType.EGG);
        });

        ItemReplacer.create(item).readMeta()
            .replace(template.getPlaceholders())
            .replace(tier.getPlaceholders())
            .writeMeta();

        this.sealEggRecovery(item, ItemType.EGG);

        return item;
    }

    @NotNull
    public ItemStack createMysteryEgg(@NotNull Template template) {
        ItemStack item = PetUtils.getRawMysteryEgg();
        ItemUtil.editMeta(item, meta -> {
            PDCUtil.set(meta, Keys.eggPetId, template.getId());
            setItemType(meta, ItemType.MYSTERY_EGG);
        });
        ItemReplacer.create(item).readMeta().replace(template.getPlaceholders()).writeMeta();

        this.sealEggRecovery(item, ItemType.MYSTERY_EGG);

        return item;
    }

    @NotNull
    public ItemStack createRandomMysteryEgg() {
        ItemStack item = PetUtils.getRawMysteryEgg();
        this.setItemType(item, ItemType.MYSTERY_EGG);
        ItemReplacer.create(item).readMeta()
            .replace(Placeholders.TEMPLATE_ID, "random")
            .replace(Placeholders.TEMPLATE_NAME, "随机宠物")
            .replace(Placeholders.TEMPLATE_DEFAULT_NAME, "随机宠物")
            .writeMeta();
        this.sealEggRecovery(item, ItemType.MYSTERY_EGG);
        return item;
    }

    public void refreshEggRecovery(@NotNull ItemStack itemStack) {
        String name = PDCUtil.getString(itemStack, Keys.itemType).orElse(null);
        if (name == null) return;

        ItemType type = StringUtil.getEnum(name, ItemType.class).orElse(null);
        if (type == ItemType.EGG || type == ItemType.MYSTERY_EGG) {
            this.sealEggRecovery(itemStack, type);
        }
    }

    private void sealEggRecovery(@NotNull ItemStack itemStack, @NotNull ItemType type) {
        String templateId = PDCUtil.getString(itemStack, Keys.eggPetId).orElse("");
        String tierId = PDCUtil.getString(itemStack, Keys.eggTierId).orElse("");
        if (type == ItemType.EGG && (templateId.isBlank() || tierId.isBlank())) return;

        Map<String, String> accessories = new TreeMap<>();
        VariantRegistry.getVariants().forEach(variant -> PDCUtil.getString(itemStack, variant.getKey())
            .ifPresent(value -> accessories.put(variant.getName(), value)));

        if (!(itemStack.getItemMeta() instanceof SkullMeta skullMeta)) return;

        String encoded = new EggRecoveryData(type, templateId, tierId, accessories).encode();
        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) {
            UUID profileId = UUID.nameUUIDFromBytes((RECOVERY_PROPERTY + ':' + encoded).getBytes(StandardCharsets.UTF_8));
            profile = Bukkit.createProfile(profileId);
        }

        long matches = profile.getProperties().stream()
            .filter(property -> property.getName().equals(RECOVERY_PROPERTY))
            .filter(property -> property.getValue().equals(encoded))
            .count();
        if (matches == 1 && profile.getProperties().stream()
            .filter(property -> property.getName().equals(RECOVERY_PROPERTY)).count() == 1) return;

        profile.removeProperty(RECOVERY_PROPERTY);
        profile.setProperty(new ProfileProperty(RECOVERY_PROPERTY, encoded));
        skullMeta.setPlayerProfile(profile);
        itemStack.setItemMeta(skullMeta);
    }

    public void migrateOnlineEggs() {
        Bukkit.getOnlinePlayers().forEach(player -> PetScheduler.runAtEntity(this.plugin, player,
            () -> this.migrateEggs(player.getInventory(), player.getEnderChest())));
    }

    public void migrateEggs(@NotNull Inventory... inventories) {
        for (Inventory inventory : inventories) {
            for (ItemStack itemStack : inventory.getContents()) {
                if (itemStack != null && !itemStack.getType().isAir()) this.getItemType(itemStack);
            }
        }
    }

    @Nullable
    private ItemType restoreEgg(@NotNull ItemStack itemStack) {
        EggRecoveryData recoveryData = this.readRecoveryData(itemStack);
        if (recoveryData == null) return null;

        ItemStack restored = this.createRestoredEgg(recoveryData);
        if (restored != null) {
            itemStack.setType(restored.getType());
            itemStack.setItemMeta(restored.getItemMeta());
        }
        return recoveryData.itemType();
    }

    @Nullable
    private EggRecoveryData readRecoveryData(@NotNull ItemStack itemStack) {
        if (!(itemStack.getItemMeta() instanceof SkullMeta skullMeta)) return null;

        PlayerProfile profile = skullMeta.getPlayerProfile();
        if (profile == null) return null;

        String encoded = null;
        for (ProfileProperty property : profile.getProperties()) {
            if (!property.getName().equals(RECOVERY_PROPERTY)) continue;
            if (encoded != null) return null;
            encoded = property.getValue();
        }
        return encoded == null ? null : EggRecoveryData.decode(encoded).orElse(null);
    }

    @Nullable
    private ItemStack createRestoredEgg(@NotNull EggRecoveryData recoveryData) {
        Template template = recoveryData.templateId().isEmpty()
            ? null
            : this.plugin.getPetManager().getTemplate(recoveryData.templateId());

        ItemStack restored;
        if (recoveryData.itemType() == ItemType.EGG) {
            Tier tier = this.plugin.getPetManager().getTier(recoveryData.tierId());
            if (template == null || tier == null) return null;
            restored = this.createEgg(template, tier);
        }
        else {
            if (!recoveryData.templateId().isEmpty() && template == null) return null;
            restored = template == null ? this.createRandomMysteryEgg() : this.createMysteryEgg(template);
        }

        ItemUtil.editMeta(restored, meta -> recoveryData.accessories().forEach((name, value) -> {
            EntityVariant<?> variant = VariantRegistry.getVariant(name);
            if (variant == null || variant.getHandler().parse(value) == null) return;
            PDCUtil.set(meta, variant.getKey(), value);
        }));
        this.sealEggRecovery(restored, recoveryData.itemType());
        return restored;
    }

    public boolean isEgg(@NotNull ItemStack itemStack) {
        return this.isItemOfType(itemStack, ItemType.EGG);
    }

    public boolean isMysteryEgg(@NotNull ItemStack itemStack) {
        return this.isItemOfType(itemStack, ItemType.MYSTERY_EGG);
    }

    @Nullable
    public Template getEggTemplate(@NotNull ItemStack item) {
        String id = PDCUtil.getString(item, Keys.eggPetId).orElse(null);
        return id == null ? null : this.plugin.getPetManager().getTemplate(id);
    }

    @Nullable
    public Tier getEggTier(@NotNull ItemStack item) {
        String tierId = PDCUtil.getString(item, Keys.eggTierId).orElse(null);
        return tierId == null ? null : this.plugin.getPetManager().getTier(tierId);
    }

    public void onItemUse(@NotNull Player player, @NotNull ItemStack itemStack, @NotNull ItemType type) {
        switch (type) {
            case EGG -> this.handleEggUse(player, itemStack);
            case MYSTERY_EGG -> this.handleMysteryEggUse(player, itemStack);
            case XP_ORB -> {}
            case REBIRTH -> {}
            default -> {}
        }
    }

    private void handleEggUse(@NotNull Player player, @NotNull ItemStack itemStack) {
        Template template = this.getEggTemplate(itemStack);
        if (template == null) return;

        Tier tier = this.getEggTier(itemStack);
        if (tier == null) return;

        PetData petData = plugin.getPetManager().tryClaimPet(player, tier, template);
        if (petData == null) return;

        if (Config.isWardrobeEnabled()) {
            petData.setWardrobe(plugin.getWardrobeManager().readAccessoryData(itemStack));
        }

        itemStack.setAmount(itemStack.getAmount() - 1);
    }

    private void handleMysteryEggUse(@NotNull Player player, @NotNull ItemStack itemStack) {
        Tier tier = this.plugin.getPetManager().getTierByWeight();

        Template template = this.getEggTemplate(itemStack);
        if (template == null && PDCUtil.getString(itemStack, Keys.eggPetId).isEmpty()) {
            var templates = this.plugin.getPetManager().getTemplates();
            if (templates.isEmpty()) return;

            template = Rnd.get(templates);
        }
        if (template == null) return;
        Template resultTemplate = template;

        itemStack.setAmount(itemStack.getAmount() - 1);

        Players.addItem(player, this.createEgg(resultTemplate, tier));

        UniParticle.of(Particle.WITCH).play(player.getLocation().add(0, 0.5, 0), 0.1, 0.5, 50);

        Lang.PET_MYSTERY_EGG_HATCH.message().send(player, replacer -> replacer
            .replace(tier.replacePlaceholders())
            .replace(resultTemplate.replacePlaceholders())
        );
    }
}
