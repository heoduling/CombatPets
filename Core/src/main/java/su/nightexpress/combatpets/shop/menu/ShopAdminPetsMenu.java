package su.nightexpress.combatpets.shop.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.shop.ShopManager;
import su.nightexpress.combatpets.shop.data.EggPrice;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.combatpets.util.PetUtils;
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

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

/** Administrator menu for editing every pet price inside one tier. */
public class ShopAdminPetsMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Template>, Linked<Tier> {

    private static final String FILE_NAME = "shop_admin_pets.yml";

    private final ShopManager shopManager;
    private final ViewLink<Tier> link;
    private final ItemHandler returnHandler;

    private String itemName;
    private List<String> itemLore;
    private int[] itemSlots;

    public ShopAdminPetsMenu(@NotNull PetsPlugin plugin, @NotNull ShopManager shopManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.shopManager = shopManager;
        this.link = new ViewLink<>();
        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer.getPlayer(), () -> this.shopManager.openAdminShop(viewer.getPlayer()))));
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
        autoFill.setItems(this.plugin.getPetManager().getTemplates().stream()
            .sorted(Comparator.comparing(Template::getDefaultName))
            .toList());
        autoFill.setItemCreator(template -> {
            EggPrice effective = this.shopManager.getEggPrice(tier, template);
            EggPrice custom = this.shopManager.getCustomEggPrice(tier, template);
            String price = effective == null ? "未上架" : effective.getCurrency().format(effective.getPrice());
            String source = custom == null ? "使用品质默认价格" : "已设置单独价格";

            ItemStack item = PetUtils.getRawEggItem(template);
            List<String> lore = this.itemLore.stream()
                .map(line -> line.replace(GENERIC_PRICE, price).replace(GENERIC_TYPE, source))
                .toList();
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.itemName)
                .setLore(lore)
                .replace(template.getPlaceholders())
                .replace(tier.getPlaceholders())
                .writeMeta();
            return item;
        });
        autoFill.setClickAction(template -> (viewer1, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer1.getPlayer(), () -> this.editPrice(viewer1.getPlayer(), tier, template)));
    }

    private void editPrice(@NotNull Player player, @NotNull Tier tier, @NotNull Template template) {
        Lang.SHOP_ADMIN_PRICE_PROMPT.message().send(player, replacer -> replacer
            .replace(TIER_NAME, tier.getName())
            .replace(TEMPLATE_DEFAULT_NAME, template.getDefaultName()));

        Dialog.create(player, (dialog, input) -> {
            String raw = input.getTextRaw().trim();
            double price;
            try {
                price = Double.parseDouble(raw);
            }
            catch (NumberFormatException exception) {
                this.sendInvalidPrice(player);
                return false;
            }

            if (!Double.isFinite(price) || price < 0D) {
                this.sendInvalidPrice(player);
                return false;
            }

            PetScheduler.runAtEntity(this.plugin, player, () -> {
                if (!this.shopManager.setCustomEggPrice(tier, template, price)) {
                    Lang.SHOP_ADMIN_PRICE_ERROR.message().send(player);
                    return;
                }

                Lang.SHOP_ADMIN_PRICE_UPDATED.message().send(player, replacer -> replacer
                    .replace(TIER_NAME, tier.getName())
                    .replace(TEMPLATE_DEFAULT_NAME, template.getDefaultName())
                    .replace(GENERIC_PRICE, price == 0D ? "品质默认价格" : NumberUtil.format(price)));
                if (su.nightexpress.nightcore.menu.impl.AbstractMenu.getMenu(player) == this) {
                    this.flush(player);
                }
            });
            return true;
        });
    }

    private void sendInvalidPrice(@NotNull Player player) {
        PetScheduler.runAtEntity(this.plugin, player, () -> Lang.SHOP_ADMIN_PRICE_ERROR_INVALID.message().send(player));
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("商店价格管理 - 宠物列表"), MenuSize.CHEST_45);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.editMeta(info, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("宠物售价管理")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("左键点击宠物修改售价。"),
                LIGHT_GRAY.enclose("输入 0 可恢复该品质的默认价格。"),
                "",
                LIGHT_GRAY.enclose("价格会立即写入 shop.yml。")
            ));
        });
        list.add(new MenuItem(info).setSlots(4).setPriority(10));

        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回品质列表"))));
        list.add(new MenuItem(backItem).setSlots(40).setPriority(10).setHandler(this.returnHandler));

        ItemStack prevPage = ItemUtil.getSkinHead(SKIN_ARROW_LEFT);
        ItemUtil.editMeta(prevPage, meta -> meta.setDisplayName(WHITE.enclose(BOLD.enclose("← 上一页"))));
        list.add(new MenuItem(prevPage).setSlots(36).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));

        ItemStack nextPage = ItemUtil.getSkinHead(SKIN_ARROW_RIGHT);
        ItemUtil.editMeta(nextPage, meta -> meta.setDisplayName(WHITE.enclose(BOLD.enclose("下一页 →"))));
        list.add(new MenuItem(nextPage).setSlots(44).setPriority(10).setHandler(ItemHandler.forNextPage(this)));

        return list;
    }

    @Override
    protected void loadAdditional() {
        this.itemName = ConfigValue.create("Egg.Name",
            LIGHT_YELLOW.enclose(BOLD.enclose(TEMPLATE_DEFAULT_NAME))
        ).read(cfg);
        this.itemLore = ConfigValue.create("Egg.Lore", Lists.newList(
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("当前售价：") + GENERIC_PRICE),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("价格来源：") + GENERIC_TYPE),
            "",
            LIGHT_GRAY.enclose(LIGHT_YELLOW.enclose("[▶]") + " 点击修改该宠物售价。")
        )).read(cfg);
        this.itemSlots = ConfigValue.create("Egg.Slots", java.util.stream.IntStream.range(0, 36).toArray()).read(cfg);
    }
}
