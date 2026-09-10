package su.nightexpress.combatpets.pet.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Aspect;
import su.nightexpress.combatpets.api.pet.Stat;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.pet.impl.PetTier;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.dialog.Dialog;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuSize;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.menu.api.AutoFill;
import su.nightexpress.nightcore.menu.api.AutoFilled;
import su.nightexpress.nightcore.menu.impl.ConfigMenu;
import su.nightexpress.nightcore.menu.item.ItemHandler;
import su.nightexpress.nightcore.menu.item.MenuItem;
import su.nightexpress.nightcore.menu.link.Linked;
import su.nightexpress.nightcore.menu.link.ViewLink;
import su.nightexpress.nightcore.util.ItemReplacer;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.NumberUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

/** Attribute target editor. The target is the value reached when all linked aspect points are spent. */
public class AttributeAdminValuesMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Stat>, Linked<AttributeAdminValuesMenu.Context> {

    private static final String FILE_NAME = "pet_admin_attributes_values.yml";
    private static final String BASE = "%base%";
    private static final String TARGET = "%target%";
    private static final String PER_POINT = "%per_point%";
    private static final String BUDGET = "%budget%";
    private static final String ASPECTS = "%aspects%";
    private static final String SOURCE = "%source%";

    private final PetManager petManager;
    private final ViewLink<Context> link;
    private final ItemHandler returnHandler;

    private String attributeName;
    private List<String> attributeLore;
    private int[] attributeSlots;

    public AttributeAdminValuesMenu(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.petManager = petManager;
        this.link = new ViewLink<>();
        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer.getPlayer(), () -> {
                Context context = this.getLink(viewer);
                if (context != null) this.petManager.openAdminAttributePets(viewer.getPlayer(), context.tier());
            })));
        this.load();
    }

    @Override
    public ViewLink<Context> getLink() {
        return this.link;
    }

    @Override
    public void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        this.autoFill(viewer);
    }

    @Override
    protected void onReady(@NotNull MenuViewer viewer, @NotNull Inventory inventory) {

    }

    @Override
    public void onAutoFill(@NotNull MenuViewer viewer, @NotNull AutoFill<Stat> autoFill) {
        Context context = this.getLink(viewer);
        if (context == null || !(context.tier() instanceof PetTier petTier)) return;

        autoFill.setSlots(this.attributeSlots);
        autoFill.setItems(AttributeRegistry.stream()
            .filter(context.template()::hasAttribute)
            .sorted(Comparator.comparing(Stat::getDisplayName))
            .toList());
        autoFill.setItemCreator(attribute -> {
            Double customTarget = petTier.getAttributeTarget(context.template(), attribute);
            double target = petTier.getAttributeMaximum(context.template(), attribute);
            double perPoint = petTier.getAttributePerAspect(context.template(), attribute);
            int budget = petTier.getAttributePointBudget(attribute);
            String source = customTarget == null ? "使用宠物配置的 Per_Aspect" : "已设置满点目标值";
            String aspectNames = this.getLinkedAspects(petTier, attribute);

            ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
            List<String> lore = this.attributeLore.stream()
                .map(line -> line.replace(BASE, NumberUtil.format(context.template().getAttributeStart(attribute)))
                    .replace(TARGET, NumberUtil.format(target))
                    .replace(PER_POINT, NumberUtil.format(perPoint))
                    .replace(BUDGET, NumberUtil.format(budget))
                    .replace(ASPECTS, aspectNames)
                    .replace(SOURCE, source))
                .toList();
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.attributeName.replace("%attribute_name%", attribute.getDisplayName()))
                .setLore(lore)
                .writeMeta();
            return item;
        });
        autoFill.setClickAction(attribute -> (viewer1, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer1.getPlayer(), () -> this.editTarget(viewer1.getPlayer(), context, attribute)));
    }

    private String getLinkedAspects(@NotNull PetTier tier, @NotNull Stat attribute) {
        return this.petManager.getAspects().stream()
            .filter(aspect -> aspect.getAttributes().contains(attribute.getId()))
            .sorted(Comparator.comparing(Aspect::getName))
            .map(aspect -> aspect.getName() + "(" + tier.getAspectMax(aspect) + ")")
            .reduce((left, right) -> left + "、" + right)
            .orElse("无");
    }

    private void editTarget(@NotNull Player player, @NotNull Context context, @NotNull Stat attribute) {
        PetTier tier = (PetTier) context.tier();
        int budget = tier.getAttributePointBudget(attribute);
        if (budget <= 0) {
            Lang.PET_ATTRIBUTE_ADMIN_TARGET_ERROR.message().send(player);
            return;
        }

        Lang.PET_ATTRIBUTE_ADMIN_TARGET_PROMPT.message().send(player, replacer -> replacer
            .replace(su.nightexpress.combatpets.Placeholders.TIER_NAME, tier.getName())
            .replace(su.nightexpress.combatpets.Placeholders.TEMPLATE_DEFAULT_NAME, context.template().getDefaultName())
            .replace(su.nightexpress.combatpets.Placeholders.GENERIC_NAME, attribute.getDisplayName()));

        Dialog.create(player, (dialog, input) -> {
            String raw = input.getTextRaw().trim();
            if (raw.equalsIgnoreCase("reset") || raw.equals("重置") || raw.equals("恢复")) {
                PetScheduler.runAtEntity(this.plugin, player, () -> {
                    tier.resetAttributeTarget(context.template(), attribute);
                    this.petManager.refreshAttributeTarget(tier, context.template());
                    this.petManager.scheduleTierAttributeSave(tier);
                    Lang.PET_ATTRIBUTE_ADMIN_TARGET_RESET.message().send(player);
                    if (su.nightexpress.nightcore.menu.impl.AbstractMenu.getMenu(player) == this) this.flush(player);
                });
                return true;
            }

            double target;
            try {
                target = Double.parseDouble(raw);
            }
            catch (NumberFormatException exception) {
                PetScheduler.runAtEntity(this.plugin, player, () -> Lang.PET_ATTRIBUTE_ADMIN_TARGET_INVALID.message().send(player));
                return false;
            }
            if (!Double.isFinite(target) || target < 0D) {
                PetScheduler.runAtEntity(this.plugin, player, () -> Lang.PET_ATTRIBUTE_ADMIN_TARGET_INVALID.message().send(player));
                return false;
            }

            double finalTarget = target;
            PetScheduler.runAtEntity(this.plugin, player, () -> {
                tier.setAttributeTarget(context.template(), attribute, finalTarget);
                this.petManager.refreshAttributeTarget(tier, context.template());
                this.petManager.scheduleTierAttributeSave(tier);
                Lang.PET_ATTRIBUTE_ADMIN_TARGET_UPDATED.message().send(player, replacer ->
                    replacer.replace(su.nightexpress.combatpets.Placeholders.GENERIC_VALUE, NumberUtil.format(finalTarget)));
                if (su.nightexpress.nightcore.menu.impl.AbstractMenu.getMenu(player) == this) this.flush(player);
            });
            return true;
        });
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("满点属性管理 - 修改数值"), MenuSize.CHEST_45);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();
        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.editMeta(info, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("属性最终值")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("点击属性后输入满点时的最终值。"),
                LIGHT_GRAY.enclose("输入 reset 可恢复旧版 Per_Aspect 计算。"),
                LIGHT_GRAY.enclose("仅管理员设置目标值，玩家仍手动分配属性点。")
            ));
        });
        list.add(new MenuItem(info).setSlots(4).setPriority(10));
        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回宠物列表"))));
        list.add(new MenuItem(backItem).setSlots(40).setPriority(10).setHandler(this.returnHandler));
        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_LEFT))
            .setSlots(36).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));
        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_RIGHT))
            .setSlots(44).setPriority(10).setHandler(ItemHandler.forNextPage(this)));
        return list;
    }

    @Override
    protected void loadAdditional() {
        this.attributeName = ConfigValue.create("Attributes.Name", LIGHT_YELLOW.enclose(BOLD.enclose("%attribute_name%"))).read(cfg);
        this.attributeLore = ConfigValue.create("Attributes.Lore", Lists.newList(
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("初始值：") + BASE),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("满点最终值：") + TARGET),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("每点增加：") + PER_POINT),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("关联属性点上限：") + BUDGET),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("关联方向：") + ASPECTS),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("计算来源：") + SOURCE),
            "",
            LIGHT_GRAY.enclose(LIGHT_YELLOW.enclose("[▶]") + " 点击修改满点最终值。")
        )).read(cfg);
        this.attributeSlots = ConfigValue.create("Attributes.Slots", IntStream.range(0, 36).toArray()).read(cfg);
    }

    public record Context(@NotNull Tier tier, @NotNull Template template) {
    }
}
