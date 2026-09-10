package su.nightexpress.combatpets.pet.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.pet.PetManager;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.combatpets.util.PetUtils;
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
import su.nightexpress.nightcore.menu.link.Linked;
import su.nightexpress.nightcore.menu.link.ViewLink;
import su.nightexpress.nightcore.util.ItemReplacer;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Lists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

/** Administrator menu for selecting a pet template inside one tier. */
public class AttributeAdminPetsMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Template>, Linked<Tier> {

    private static final String FILE_NAME = "pet_admin_attributes_pets.yml";

    private final PetManager petManager;
    private final ViewLink<Tier> link;
    private final ItemHandler returnHandler;

    private String itemName;
    private List<String> itemLore;
    private int[] itemSlots;

    public AttributeAdminPetsMenu(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.petManager = petManager;
        this.link = new ViewLink<>();
        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer.getPlayer(), () -> this.petManager.openAdminAttributes(viewer.getPlayer()))));
        this.load();
    }

    @Override
    public ViewLink<Tier> getLink() {
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
    public void onAutoFill(@NotNull MenuViewer viewer, @NotNull AutoFill<Template> autoFill) {
        Tier tier = this.getLink(viewer);
        if (tier == null) return;

        autoFill.setSlots(this.itemSlots);
        autoFill.setItems(this.petManager.getTemplates().stream()
            .sorted(Comparator.comparing(Template::getDefaultName))
            .toList());
        autoFill.setItemCreator(template -> {
            ItemStack item = PetUtils.getRawEggItem(template);
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.itemName)
                .setLore(this.itemLore)
                .replace(template.getPlaceholders())
                .replace(tier.getPlaceholders())
                .writeMeta();
            return item;
        });
        autoFill.setClickAction(template -> (viewer1, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer1.getPlayer(), () ->
                this.petManager.openAdminAttributeValues(viewer1.getPlayer(), tier, template)));
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("满点属性管理 - 选择宠物"), MenuSize.CHEST_45);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.editMeta(info, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("宠物属性管理")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("左键选择宠物，管理它的属性最终值。"),
                LIGHT_GRAY.enclose("修改只影响当前品质下的这只宠物。"),
                "",
                LIGHT_GRAY.enclose("保存到对应品质配置文件。")
            ));
        });
        list.add(new MenuItem(info).setSlots(4).setPriority(10));

        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回品质列表"))));
        list.add(new MenuItem(backItem).setSlots(40).setPriority(10).setHandler(this.returnHandler));

        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_LEFT))
            .setSlots(36).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));
        list.add(new MenuItem(ItemUtil.getSkinHead(SKIN_ARROW_RIGHT))
            .setSlots(44).setPriority(10).setHandler(ItemHandler.forNextPage(this)));
        return list;
    }

    @Override
    protected void loadAdditional() {
        this.itemName = ConfigValue.create("Pets.Name", LIGHT_YELLOW.enclose(BOLD.enclose(TEMPLATE_DEFAULT_NAME))).read(cfg);
        this.itemLore = ConfigValue.create("Pets.Lore", Lists.newList(
            LIGHT_GRAY.enclose("品质：") + TIER_NAME,
            "",
            LIGHT_GRAY.enclose(LIGHT_YELLOW.enclose("[▶]") + " 点击管理该宠物的属性。")
        )).read(cfg);
        this.itemSlots = ConfigValue.create("Pets.Slots", java.util.stream.IntStream.range(0, 36).toArray()).read(cfg);
    }
}
