package su.nightexpress.combatpets.pet.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.api.pet.Template;
import su.nightexpress.combatpets.api.pet.Tier;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.config.Perms;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.pet.AttributeRegistry;
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

/** Lists every configured pet and summons a non-persistent test instance. */
public final class AdminTestPetsMenu extends ConfigMenu<PetsPlugin> implements AutoFilled<Template>, Linked<Tier> {

    private static final String FILE_NAME = "pet_admin_test_pets.yml";
    private static final String STATUS = "%status%";

    private final PetManager petManager;
    private final ViewLink<Tier> link;
    private final ItemHandler returnHandler;

    private String petName;
    private List<String> petLore;
    private List<String> activeStatus;
    private List<String> inactiveStatus;
    private int[] petSlots;

    public AdminTestPetsMenu(@NotNull PetsPlugin plugin, @NotNull PetManager petManager) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        this.petManager = petManager;
        this.link = new ViewLink<>();
        this.addHandler(this.returnHandler = ItemHandler.forReturn(this, (viewer, event) ->
            PetScheduler.runAtEntity(this.plugin, viewer.getPlayer(), () -> this.petManager.openAdminTestMenu(viewer.getPlayer()))));
        this.load();
    }

    @Override
    @NotNull
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

        Player player = viewer.getPlayer();
        autoFill.setSlots(this.petSlots);
        autoFill.setItems(this.petManager.getTemplates().stream()
            .sorted(Comparator.comparing(Template::getDefaultName))
            .toList());
        autoFill.setItemCreator(template -> {
            PetData data = PetData.create(template, tier);
            ActivePet holder = PetEntityBridge.getByPlayer(player);
            boolean active = holder != null && holder.getTemplate() == template && holder.getTier() == tier;

            ItemStack item = ItemUtil.getSkinHead(template.getEggTexture());
            ItemReplacer.create(item).hideFlags().trimmed()
                .setDisplayName(this.petName)
                .setLore(this.petLore)
                .replace(STATUS, active ? this.activeStatus : this.inactiveStatus)
                .replace(data.getPlaceholders())
                .replace(tier.getPlaceholders())
                .replace(template.getPlaceholders())
                .writeMeta();
            return item;
        });
        autoFill.setClickAction(template -> (viewer1, event) -> {
            if (!event.isLeftClick()) return;

            PetScheduler.runAtEntity(this.plugin, viewer1.getPlayer(), () -> {
                Player clicker = viewer1.getPlayer();
                if (!clicker.hasPermission(Perms.COMMAND_ADMIN) ||
                    !clicker.hasPermission(Perms.COMMAND_ADMIN_MENU)) {
                    clicker.closeInventory();
                    return;
                }

                ActivePet holder = PetEntityBridge.getByPlayer(clicker);
                if (holder != null && holder.getTemplate() == template && holder.getTier() == tier) {
                    this.petManager.despawnPet(clicker, () -> this.petManager.openAdminTestPets(clicker, tier));
                    return;
                }

                Runnable summon = () -> {
                    if (this.petManager.spawnTestPet(clicker, tier, template)) clicker.closeInventory();
                };
                if (holder == null) summon.run();
                else this.petManager.despawnPet(clicker, summon);
            });
        });
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("宠物测试菜单 - 全部宠物"), MenuSize.CHEST_54);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemUtil.editMeta(info, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("管理员宠物测试")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("列表包含当前加载的全部宠物配置。"),
                LIGHT_GRAY.enclose("左键可召唤、替换或收回测试宠物。"),
                "",
                LIGHT_GRAY.enclose("测试数据不会加入玩家收藏。")
            ));
        });
        list.add(new MenuItem(info).setSlots(4).setPriority(10));

        ItemStack backItem = ItemUtil.getSkinHead(SKIN_ARROW_DOWN);
        ItemUtil.editMeta(backItem, meta -> meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("返回品质列表"))));
        list.add(new MenuItem(backItem).setSlots(49).setPriority(10).setHandler(this.returnHandler));

        ItemStack previous = ItemUtil.getSkinHead(SKIN_ARROW_LEFT);
        ItemUtil.editMeta(previous, meta -> meta.setDisplayName(WHITE.enclose(BOLD.enclose("← 上一页"))));
        list.add(new MenuItem(previous).setSlots(45).setPriority(10).setHandler(ItemHandler.forPreviousPage(this)));

        ItemStack next = ItemUtil.getSkinHead(SKIN_ARROW_RIGHT);
        ItemUtil.editMeta(next, meta -> meta.setDisplayName(WHITE.enclose(BOLD.enclose("下一页 →"))));
        list.add(new MenuItem(next).setSlots(53).setPriority(10).setHandler(ItemHandler.forNextPage(this)));
        return list;
    }

    @Override
    protected void loadAdditional() {
        this.petName = ConfigValue.create("Pet.Name", LIGHT_YELLOW.enclose(BOLD.enclose(PET_NAME))).read(cfg);
        this.petLore = ConfigValue.create("Pet.Lore", Lists.newList(
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("品质：") + TIER_NAME),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("等级：") + PET_LEVEL),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("伤害：") + PET_ATTRIBUTE.apply(AttributeRegistry.ATTACK_DAMAGE)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("生命值：") + PET_ATTRIBUTE.apply(AttributeRegistry.MAX_HEALTH)),
            LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("地面速度：") + PET_ATTRIBUTE.apply(AttributeRegistry.MOVEMENT_SPEED)),
            "",
            STATUS
        )).read(cfg);
        this.activeStatus = ConfigValue.create("Pet.Status.Active", Lists.newList(
            LIGHT_GRAY.enclose("状态：") + LIGHT_GREEN.enclose(BOLD.enclose("测试中")),
            "",
            LIGHT_GREEN.enclose("[▶] ") + LIGHT_GRAY.enclose("左键收回测试宠物。")
        )).read(cfg);
        this.inactiveStatus = ConfigValue.create("Pet.Status.Inactive", Lists.newList(
            LIGHT_GRAY.enclose("状态：") + LIGHT_YELLOW.enclose(BOLD.enclose("未召唤")),
            "",
            LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("左键立即召唤测试宠物。"),
            LIGHT_GRAY.enclose("不会加入收藏或保存测试进度。")
        )).read(cfg);
        this.petSlots = ConfigValue.create("Pets.Slots",
            new int[]{10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34,37,38,39,40,41,42,43}
        ).read(cfg);
    }
}
