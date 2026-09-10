package su.nightexpress.combatpets.shop.menu;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.combatpets.shop.ShopManager;
import su.nightexpress.combatpets.shop.data.EggPrice;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.core.config.CoreLang;
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
import java.util.stream.IntStream;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class ShopEggsMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Template>, Linked<ShopEggsMenu.View> {

    private static final String FILE_NAME = "shop_pet_eggs.yml";

    private final ShopManager    shopManager;
    private final ViewLink<View> link;
    private final ItemHandler    returnHandler;

    private String       itemName;
    private List<String> itemLore;
    private int[]        itemSlots;

    public ShopEggsMenu(@NotNull PetsPlugin plugin, @NotNull ShopManager shopManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.shopManager = shopManager;
        this.link = new ViewLink<>();

        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) -> {
            View view = this.getLink(viewer);
            if (view == null) return;

            this.runNextTick(() -> this.shopManager.openTiersMenu(viewer.getPlayer(), view.proShop));
        }));

        this.load();
    }

    @NotNull
    @Override
    public ViewLink<View> getLink() {
        return link;
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
        View view = this.getLink(viewer);
        if (view == null) return;

        Tier tier = view.tier;

        autoFill.setSlots(this.itemSlots);
        autoFill.setItems(this.plugin.getPetManager().getTemplates().stream()
            .filter(config -> this.shopManager.isEggBuyable(tier, config))
            .sorted(Comparator.comparing(Template::getDefaultName)).toList());

        autoFill.setItemCreator(petConfig -> {
            EggPrice price = this.shopManager.getEggPrice(tier, petConfig);
            if (price == null) return new ItemStack(Material.AIR);

            ItemStack item = PetUtils.getRawEggItem(petConfig);
            List<String> lore = new ArrayList<>(this.itemLore);
            if (petConfig.getEntityType() == EntityType.AXOLOTL) {
                if (!lore.isEmpty()) lore.add("");
                lore.add(LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("商店购买的美西螈固定为粉红色。")));
                lore.add(LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("其他颜色需要在野外捕捉对应的美西螈。")));
            }
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.itemName)
                .setLore(lore)
                .replace(petConfig.getPlaceholders())
                .replace(tier.getPlaceholders())
                .replace(GENERIC_PRICE, price.getCurrency().format(price.getPrice()))
                .writeMeta();
            return item;
        });

        autoFill.setClickAction(config -> (viewer1, event) -> {
            this.runNextTick(() -> this.shopManager.openEggPurchaseConfirm(viewer1.getPlayer(), config, tier, view.proShop));
        });
    }

    public record View(@NotNull Tier tier, boolean proShop) {}

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("宠物蛋商店"), MenuSize.CHEST_36);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回")));
        });
        list.add(new MenuItem(backItem).setSlots(31).setPriority(10).setHandler(this.returnHandler));

        ItemStack prevPage = ItemUtil.getSkinHead(SKIN_ARROW_LEFT);
        ItemUtil.editMeta(prevPage, meta -> {
            meta.setDisplayName(WHITE.enclose(BOLD.enclose("← 上一页")));
        });
        list.add(new MenuItem(prevPage).setSlots(27).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));

        ItemStack nextPage = ItemUtil.getSkinHead(SKIN_ARROW_RIGHT);
        ItemUtil.editMeta(nextPage, meta -> {
            meta.setDisplayName(WHITE.enclose(BOLD.enclose("下一页 →")));
        });
        list.add(new MenuItem(nextPage).setSlots(35).setPriority(10).setHandler(ItemHandler.forNextPage(this)));

        return list;
    }

    @Override
    protected void loadAdditional() {
        this.itemName = ConfigValue.create("Egg.Name", 
            LIGHT_YELLOW.enclose(BOLD.enclose(TEMPLATE_DEFAULT_NAME))
        ).read(cfg);

        this.itemLore = ConfigValue.create("Egg.Lore", Lists.newList(
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("宠物背包：") + TIER_INVENTORY_HAS + " " + LIGHT_GRAY.enclose("(" + TIER_INVENTORY_SIZE + " 格)")),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("宠物装备：") + TIER_EQUIPMENT_HAS),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("最大饱食度：") + PET_CONFIG_ATTRIBUTE_START.apply(AttributeRegistry.MAX_SATURATION)),
            "",
            LIGHT_YELLOW.enclose(BOLD.enclose("初始属性")),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("伤害：") + PET_CONFIG_ATTRIBUTE_START.apply(AttributeRegistry.ATTACK_DAMAGE)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("生命值：") + PET_CONFIG_ATTRIBUTE_START.apply(AttributeRegistry.MAX_HEALTH)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("防御：") + PET_CONFIG_ATTRIBUTE_START.apply(AttributeRegistry.ARMOR)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("速度：") + PET_CONFIG_ATTRIBUTE_START.apply(AttributeRegistry.MOVEMENT_SPEED)),
            "",
            LIGHT_GRAY.enclose(LIGHT_GREEN.enclose("[▶]") + " 点击花费 " + LIGHT_GREEN.enclose(GENERIC_PRICE) + " " + LIGHT_GREEN.enclose("购买") + "。")
        )).read(cfg);

        this.itemSlots = ConfigValue.create("Egg.Slots", IntStream.range(0, 27).toArray()).read(cfg);
    }
}
