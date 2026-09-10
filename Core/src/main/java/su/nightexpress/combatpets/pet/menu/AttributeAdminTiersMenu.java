package su.nightexpress.combatpets.pet.menu;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuSize;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.menu.api.AutoFill;
import su.nightexpress.nightcore.menu.api.AutoFilled;
import su.nightexpress.nightcore.menu.impl.ConfigMenu;
import su.nightexpress.nightcore.menu.item.ItemHandler;
import su.nightexpress.nightcore.menu.item.MenuItem;
import su.nightexpress.nightcore.util.ItemReplacer;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Lists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

/** Administrator entry menu for selecting the tier whose attributes should be edited. */
public class AttributeAdminTiersMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Tier> {

    private static final String FILE_NAME = "pet_admin_attributes_tiers.yml";

    private final PetManager petManager;
    private final ItemHandler closeHandler;

    private String tierName;
    private List<String> tierLore;
    private int[] tierSlots;

    public AttributeAdminTiersMenu(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.petManager = petManager;
        this.addHandler(this.closeHandler = ItemHandler.forClose(this));
        this.load();
    }

    @Override
    public void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        this.autoFill(viewer);
    }

    @Override
    protected void onReady(@NotNull MenuViewer viewer, @NotNull Inventory inventory) {

    }

    @Override
    public void onAutoFill(@NotNull MenuViewer viewer, @NotNull AutoFill<Tier> autoFill) {
        autoFill.setSlots(this.tierSlots);
        autoFill.setItems(this.petManager.getTiers().stream()
            .sorted(Comparator.comparingDouble(Tier::getWeight).reversed())
            .toList());
        autoFill.setItemCreator(tier -> {
            ItemStack item = tier.getIcon();
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.tierName)
                .setLore(this.tierLore)
                .replace(tier.getPlaceholders())
                .writeMeta();
            return item;
        });
        autoFill.setClickAction(tier -> (viewer1, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer1.getPlayer(), () ->
                this.petManager.openAdminAttributePets(viewer1.getPlayer(), tier)));
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("宠物属性管理 - 选择品质"), MenuSize.CHEST_36);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.editMeta(info, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("满点属性管理")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("选择品质后，再选择具体宠物。"),
                LIGHT_GRAY.enclose("管理员设置属性点全部分配后的最终值。"),
                "",
                LIGHT_GRAY.enclose(LIGHT_YELLOW.enclose("[▶]") + " 点击品质进入管理。")
            ));
        });
        list.add(new MenuItem(info).setSlots(4).setPriority(10));

        ItemStack closeItem = ItemUtil.getSkinHead(SKIN_WRONG_MARK);
        ItemUtil.editMeta(closeItem, meta -> meta.setDisplayName(LIGHT_RED.enclose(BOLD.enclose("关闭"))));
        list.add(new MenuItem(closeItem).setSlots(31).setPriority(10).setHandler(this.closeHandler));

        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_LEFT))
            .setSlots(27).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));
        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_RIGHT))
            .setSlots(35).setPriority(10).setHandler(ItemHandler.forNextPage(this)));
        return list;
    }

    @Override
    protected void loadAdditional() {
        this.tierName = ConfigValue.create("Tiers.Name", LIGHT_YELLOW.enclose(BOLD.enclose(TIER_NAME))).read(cfg);
        this.tierLore = ConfigValue.create("Tiers.Lore", Lists.newList(
            LIGHT_GRAY.enclose("管理该品质下每只宠物的属性满点最终值。"),
            "",
            LIGHT_GRAY.enclose(LIGHT_YELLOW.enclose("[▶]") + " 点击进入宠物列表。")
        )).read(cfg);
        this.tierSlots = ConfigValue.create("Tiers.Slots", new int[]{10, 12, 14, 16}).read(cfg);
    }
}
