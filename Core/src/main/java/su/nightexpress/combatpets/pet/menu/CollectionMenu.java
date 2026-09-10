package su.nightexpress.combatpets.pet.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Lang;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.combatpets.util.PetUtils;
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
import su.nightexpress.nightcore.util.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class CollectionMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<PetData>, Linked<Tier> {

    private static final String FILE_NAME = "pet_collection.yml";
    private static final String STATUS = "%status%";
    private static final String FILLER_ID = "filler";
    private static final int[] FILLER_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 18, 27, 36, 45, 17, 26, 35, 44, 46, 47, 48, 49, 50, 51, 52, 53};

    private final ViewLink<Tier> link;
    private final ItemHandler    returnHandler;

    private String       petName;
    private List<String> petLore;
    private int[]        petSlots;

    private List<String> petStatusDeadAuto;
    private List<String> petStatusDeadManual;
    private List<String> petStatusActive;
    private List<String> petStatusInactive;

    public CollectionMenu(@NotNull PetsPlugin plugin) {
        super(plugin, loadConfig(plugin));
        this.link = new ViewLink<>();

        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) -> {
            this.runNextTick(() -> plugin.getPetManager().openTierCollection(viewer.getPlayer()));
        }));

        this.load();

        this.getItems().forEach(PetUtils::applyMenuPlaceholders);
    }

    @NotNull
    private static FileConfig loadConfig(@NotNull PetsPlugin plugin) {
        FileConfig config = FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME);

        if (!config.contains("Content." + FILLER_ID)
            && config.contains("Content.Priority")
            && config.contains("Content.Item.Material")
            && Material.BLACK_STAINED_GLASS_PANE.name().equalsIgnoreCase(config.getString("Content.Item.Material"))
            && config.contains("Content.Slots")
            && config.contains("Content.Type")) {
            config.remove("Content.Priority");
            config.remove("Content.Item");
            config.remove("Content.Slots");
            config.remove("Content.Type");
            config.set("Content." + FILLER_ID + ".Priority", 0);
            config.setItem("Content." + FILLER_ID + ".Item", new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
            config.setIntArray("Content." + FILLER_ID + ".Slots", FILLER_SLOTS);
            config.set("Content." + FILLER_ID + ".Type", "default");
            config.saveChanges();
        }

        return config;
    }

    @Override
    protected void writeItem(@NotNull MenuItem menuItem, @NotNull String path) {
        if (path.equals(this.itemSection + ".")) {
            path += FILLER_ID;
        }
        super.writeItem(menuItem, path);
    }

    @NotNull
    @Override
    public ViewLink<Tier> getLink() {
        return link;
    }

    @Override
    public void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        PetUtils.applyMenuPlaceholders(viewer, options);
        this.autoFill(viewer);
    }

    @Override
    protected void onReady(@NotNull MenuViewer viewer, @NotNull Inventory inventory) {

    }

    @Override
    public void onAutoFill(@NotNull MenuViewer viewer, @NotNull AutoFill<PetData> autoFill) {
        Player player = viewer.getPlayer();
        Tier tier = this.getLink(player);
        if (tier == null) return;

        autoFill.setSlots(this.petSlots);
        autoFill.setItems(this.plugin.getUserManager().getOrFetch(player).getPets(tier).stream()
            .sorted(Comparator.comparing(data -> data.getTemplate().getId())).toList());
        autoFill.setItemCreator(petData -> {
            ActivePet petHolder = PetEntityBridge.getByPlayer(player);

            List<String> status = new ArrayList<>();

            if (petData.isDead()) {
                if (petData.isAutoRevivable()) {
                    status.addAll(this.petStatusDeadAuto);
                }
                else {
                    status.addAll(this.petStatusDeadManual);
                }
                status.replaceAll(str -> str
                    .replace(GENERIC_TIME, TimeUtil.formatDuration(petData.getReviveDate()))
                    .replace(GENERIC_COST, NumberUtil.format(petData.getTier().getReviveCost()))
                );
            }
            else {
                if (petHolder == null || petHolder.getTemplate() != petData.getTemplate() || petHolder.getTier() != petData.getTier()) {
                    status.addAll(this.petStatusInactive);
                }
                else {
                    status.addAll(this.petStatusActive);
                }
            }

            ItemStack item = ItemUtil.getSkinHead(petData.getTemplate().getEggTexture());
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.petName)
                .setLore(this.petLore)
                .replace(STATUS, status)
                .replace(petData.getPlaceholders())
                .replace(petData.getTier().getPlaceholders())
                .replace(petData.getTemplate().getPlaceholders())
                .writeMeta();

            return item;
        });
        autoFill.setClickAction(petData -> (viewer1, event) -> {
            ActivePet holder = PetEntityBridge.getByPlayer(player);

            if (event.isLeftClick()) {
                if (petData.isDead()) {
                    this.runNextTick(() -> plugin.getPetManager().openReviveMenu(player, petData));
                    return;
                }

                if (holder != null) {
                    if (holder.getTemplate() == petData.getTemplate() && holder.getTier() == petData.getTier()) {
                        Tier selectedTier = petData.getTier();
                        this.plugin.getPetManager().despawnPet(player,
                            () -> this.plugin.getPetManager().openPetsCollection(player, selectedTier));
                        return;
                    }

                    this.plugin.getPetManager().despawnPet(player, () -> {
                        if (this.plugin.getPetManager().spawnPet(player, petData)) {
                            player.closeInventory();
                        }
                    });
                    return;
                }
                this.plugin.getPetManager().spawnPet(player, petData);
                this.runNextTick(player::closeInventory);
                return;
            }

            if (event.getClick() == ClickType.DROP && Config.PET_RELEASE_ALLOWED.get()) {
                if (holder != null) {
                    if (holder.getTemplate() == petData.getTemplate() && holder.getTier() == petData.getTier()) {
                        //plugin.getMessage(Lang.PET_RELEASE_ERROR_ACTIVE).send(player);
                        return;
                    }
                }
                this.runNextTick(() -> plugin.getPetManager().openReleaseMenu(player, petData));
            }
        });
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("宠物收藏"), MenuSize.CHEST_54);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        list.add(new MenuItem(filler).setSlots(FILLER_SLOTS));

        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回")));
        });
        list.add(new MenuItem(backItem).setSlots(49).setPriority(10).setHandler(this.returnHandler));

        ItemStack prevPage = ItemUtil.getSkinHead(SKIN_ARROW_LEFT);
        ItemUtil.editMeta(prevPage, meta -> {
            meta.setDisplayName(WHITE.enclose(BOLD.enclose("← 上一页")));
        });
        list.add(new MenuItem(prevPage).setSlots(45).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));

        ItemStack nextPage = ItemUtil.getSkinHead(SKIN_ARROW_RIGHT);
        ItemUtil.editMeta(nextPage, meta -> {
            meta.setDisplayName(WHITE.enclose(BOLD.enclose("下一页 →")));
        });
        list.add(new MenuItem(nextPage).setSlots(53).setPriority(10).setHandler(ItemHandler.forNextPage(this)));

        return list;
    }

    @Override
    protected void loadAdditional() {
        this.petName = ConfigValue.create("Pet.Name",
            LIGHT_YELLOW.enclose(BOLD.enclose(PET_NAME))
        ).read(cfg);

        this.petLore = ConfigValue.create("Pet.Lore", Lists.newList(
            "",
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("等级：") + PET_LEVEL),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("经验：") + PET_XP + LIGHT_GRAY.enclose("/") + PET_REQUIRED_XP),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("饱食度：") + PET_SATURATION + LIGHT_GRAY.enclose("/") + PET_MAX_SATURATION),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("食物：") + PET_FOOD),
            "",
            LIGHT_YELLOW.enclose(BOLD.enclose("属性")),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("伤害：") + PET_ATTRIBUTE.apply(AttributeRegistry.ATTACK_DAMAGE)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("攻击速度：") + PET_ATTRIBUTE.apply(AttributeRegistry.ATTACK_SPEED) + LIGHT_GRAY.enclose("/秒")),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("生命值：") + PET_ATTRIBUTE.apply(AttributeRegistry.MAX_HEALTH)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("生命恢复：") + PET_ATTRIBUTE.apply(AttributeRegistry.HEALTH_REGENEATION_FORCE) + LIGHT_GRAY.enclose(" x ") + PET_ATTRIBUTE.apply(AttributeRegistry.HEALTH_REGENEATION_SPEED) + LIGHT_GRAY.enclose("/秒")),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("防御：") + PET_ATTRIBUTE.apply(AttributeRegistry.ARMOR)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("速度：地面 ") + PET_ATTRIBUTE.apply(AttributeRegistry.MOVEMENT_SPEED) + LIGHT_GRAY.enclose(" / 飞行 ") + PET_ATTRIBUTE.apply(AttributeRegistry.FLYING_SPEED)),
//                "",
//                LIGHT_YELLOW.enclose(BOLD.enclose("ASPECTS")),
//                LIGHT_YELLOW.enclose("▪ #ddeceeStrength: %pet_aspect_strength%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeVitality: %pet_aspect_vitality%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeDefense: %pet_aspect_defense%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeDexterity: %pet_aspect_dexterity%")
                "",
                STATUS
        )).read(cfg);

        this.petStatusDeadAuto = ConfigValue.create("Pet.Status.Dead_Auto", Lists.newList(
            LIGHT_GRAY.enclose("状态：" + LIGHT_RED.enclose(BOLD.enclose("已死亡"))),
            LIGHT_GRAY.enclose("自动复活倒计时：" + LIGHT_RED.enclose(GENERIC_TIME)),
            "",
            LIGHT_RED.enclose("[▶] ") + LIGHT_GRAY.enclose("点击花费 " + LIGHT_RED.enclose("$" + GENERIC_COST) + " 立即复活。")
        )).read(cfg);

        this.petStatusDeadManual = ConfigValue.create("Pet.Status.Dead_Manual", Lists.newList(
            LIGHT_GRAY.enclose("状态：" + LIGHT_RED.enclose(BOLD.enclose("已死亡"))),
            "",
            LIGHT_RED.enclose("[▶] ") + LIGHT_GRAY.enclose("点击花费 " + LIGHT_RED.enclose("$" + GENERIC_COST) + " 复活。")
        )).read(cfg);

        this.petStatusActive = ConfigValue.create("Pet.Status.Active", Lists.newList(
            LIGHT_GRAY.enclose("状态：" + LIGHT_GREEN.enclose(BOLD.enclose("已召唤"))),
            "",
            LIGHT_GREEN.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_GREEN.enclose("收回") + "宠物。")
        )).read(cfg);

        this.petStatusInactive = ConfigValue.create("Pet.Status.Inactive", Lists.newList(
            LIGHT_GRAY.enclose("状态：" + LIGHT_YELLOW.enclose(BOLD.enclose("未召唤"))),
            "",
            LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("左键点击" + LIGHT_YELLOW.enclose("召唤") + "。"),
            LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("按 [Q/丢弃键] " + LIGHT_YELLOW.enclose("放生") + "。")
        )).read(cfg);

        this.petSlots = ConfigValue.create("Pets.Slots",
            new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}
        ).read(cfg);
    }
}
