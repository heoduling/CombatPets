package su.nightexpress.combatpets.pet.menu;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.api.pet.ActivePet;
import su.nightexpress.combatpets.api.pet.PetEntityBridge;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.data.impl.PetUser;
import su.nightexpress.combatpets.pet.AttributeRegistry;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.combatpets.util.PetScheduler;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuSize;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.menu.impl.ConfigMenu;
import su.nightexpress.nightcore.menu.item.ItemHandler;
import su.nightexpress.nightcore.menu.item.MenuItem;
import su.nightexpress.nightcore.util.ItemReplacer;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Lists;
import su.nightexpress.nightcore.util.Players;

import java.util.ArrayList;
import java.util.List;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class PetMenu extends ConfigMenu<PetsPlugin> {

    public static final String FILE_NAME = "pet_overview.yml";

    private final ItemHandler despawnHandler;
    private final ItemHandler renameHandler;
    private final ItemHandler inventoryHandler;
    private final ItemHandler equipmentHandler;
    private final ItemHandler aspectsHandler;
    private final ItemHandler combatModeHandler;
    private final ItemHandler silentHandler;
    //private final ItemHandler rideHandler;

    public PetMenu(@NotNull PetsPlugin plugin) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE_NAME));
        
        this.addHandler(this.despawnHandler = new ItemHandler("pet_return", (viewer, event) -> {
            plugin.getPetManager().despawnPet(viewer.getPlayer());
            PetScheduler.runAtEntity(plugin, viewer.getPlayer(), viewer.getPlayer()::closeInventory);
        }));

        this.addHandler(this.renameHandler = new ItemHandler("pet_rename", (viewer, event) -> {
            Player player = viewer.getPlayer();
            this.runNextTick(player::closeInventory);
            this.plugin.getPetManager().startRename(player);
        }));


        this.addHandler(this.inventoryHandler = new ItemHandler("pet_inventory", (viewer, event) -> {
            ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
            if (petHolder == null) return;

            this.runNextTick(() -> viewer.getPlayer().openInventory(petHolder.getInventory()));
        }));


        this.addHandler(this.equipmentHandler = new ItemHandler("pet_equipment", (viewer, event) -> {
            ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
            if (petHolder == null) return;
            if (!petHolder.getTier().hasEquipment()) return;

            if (event.isLeftClick()) {
                petHolder.setEquipmentUnlocked(!petHolder.isEquipmentUnlocked());
                this.runNextTick(() -> this.flush(viewer));
            }
            else if (event.isRightClick()) {
                LivingEntity entity = petHolder.getEntity();
                EntityEquipment equipment = entity.getEquipment();
                if (equipment == null) return;

                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (PetUtils.isTransientEquipment(entity, slot)) continue;
                    ItemStack wear = equipment.getItem(slot);
                    if (wear.getType().isAir()) continue;

                    Players.addItem(viewer.getPlayer(), wear);
                    equipment.setItem(slot, null);
                }

                petHolder.saveData();
                PetUser user = plugin.getUserManager().getOrFetch(viewer.getPlayer());
                this.plugin.getUserManager().save(user);
            }
        }));


        this.addHandler(this.aspectsHandler = new ItemHandler("pet_aspects", (viewer, event) -> {
            PetScheduler.runAtEntity(plugin, viewer.getPlayer(), () -> plugin.getPetManager().openAspectsMenu(viewer.getPlayer()));
        }));


        this.addHandler(this.combatModeHandler = new ItemHandler("combat_mode", (viewer, event) -> {
            ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
            if (petHolder == null) return;

            petHolder.toggleCombatMode();
            this.runNextTick(() -> this.flush(viewer));
        }));


        this.addHandler(this.silentHandler = new ItemHandler("silent", (viewer, event) -> {
            ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
            if (petHolder == null) return;

            petHolder.setSilent(!petHolder.isSilent());
            this.runNextTick(() -> this.flush(viewer));
        }));


//        this.addHandler(this.rideHandler = new ItemHandler("pet_ride", (viewer, event) -> {
//            plugin.getPetManager().ridePet(viewer.getPlayer());
//            this.runNextTick(() -> viewer.getPlayer().closeInventory());
//        }));

        this.load();

        this.getItems().forEach(menuItem -> {
            ItemHandler handler = menuItem.getHandler();
            
            menuItem.getOptions().setVisibilityPolicy(viewer -> {
                ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
                if (petHolder == null) return false;

                /*if (handler == this.rideHandler) {
                    return petHolder.getConfig().isRideable();
                }
                else */if (handler == this.inventoryHandler) {
                    return petHolder.getTier().hasInventory() && petHolder.getTemplate().canHaveInventory();
                }
                else if (handler == this.equipmentHandler) {
                    return petHolder.getTier().hasEquipment();
                }
                return true;
            });

            menuItem.getOptions().addDisplayModifier((viewer, item) -> {
                ActivePet petHolder = PetEntityBridge.getByPlayer(viewer.getPlayer());
                if (petHolder == null) return;

                ItemReplacer.create(item).readMeta().hideFlags().trimmed()
                    .replace(petHolder.getPlaceholders())
                    .replace(petHolder.getTier().getPlaceholders())
                    .replace(petHolder.getTemplate().getPlaceholders())
                    .writeMeta();
            });

            PetUtils.applyMenuPlaceholders(menuItem);
        });
    }

    @Override
    protected void onPrepare(@NotNull MenuViewer viewer, @NotNull MenuOptions options) {
        PetUtils.applyMenuPlaceholders(viewer, options);
    }

    @Override
    protected void onReady(@NotNull MenuViewer viewer, @NotNull Inventory inventory) {
        
    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("宠物菜单"), MenuSize.CHEST_45);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack statsItem = ItemUtil.getSkinHead("15b52a5ba47b487a4ea723ccf404b33ac9ed80428c626c099ebee4bb7e6f6363");
        ItemUtil.editMeta(statsItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("宠物状态")));
            meta.setLore(Lists.newList(
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
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("速度：地面 ") + PET_ATTRIBUTE.apply(AttributeRegistry.MOVEMENT_SPEED) + LIGHT_GRAY.enclose(" / 飞行 ") + PET_ATTRIBUTE.apply(AttributeRegistry.FLYING_SPEED))
//                "",
//                LIGHT_YELLOW.enclose(BOLD.enclose("ASPECTS")),
//                LIGHT_YELLOW.enclose("▪ #ddeceeStrength: %pet_aspect_strength%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeVitality: %pet_aspect_vitality%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeDefense: %pet_aspect_defense%"),
//                LIGHT_YELLOW.enclose("▪ #ddeceeDexterity: %pet_aspect_dexterity%")
            ));
        });
        list.add(new MenuItem(statsItem).setPriority(10).setSlots(4));

        ItemStack despawnItem = ItemUtil.getSkinHead("15f1adb58db6e2e54a84739b2c79ddd4014b85f76511df41a9278d7151f6cdbf");
        ItemUtil.editMeta(despawnItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("收回宠物")));
            meta.setLore(Lists.newList(
                LIGHT_GRAY.enclose("将宠物收回收藏。")
            ));
        });
        list.add(new MenuItem(despawnItem).setPriority(10).setSlots(40).setHandler(this.despawnHandler));

        ItemStack silentItem = ItemUtil.getSkinHead("5159ea5fbc4e98a9b603854bbd4f1b07aefcd4df051b3fa6158bbf959be44413");
        ItemUtil.editMeta(silentItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("静音模式")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("状态：") + PET_SILENT),
                "",
                LIGHT_GRAY.enclose("关闭宠物的环境音效。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_YELLOW.enclose("切换") + "。")
            ));
        });
        list.add(new MenuItem(silentItem).setPriority(10).setSlots(19).setHandler(this.silentHandler));

//        ItemStack rideItem = ItemUtil.getSkinHead("");
//        ItemUtil.editMeta(rideItem, meta -> {
//            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("Ride")));
//            meta.setLore(Lists.newList(
//                LIGHT_GRAY.enclose("Ride your pet!")
//            ));
//        });
//        list.add(new MenuItem(rideItem).setPriority(10).setSlots(10).setHandler(this.rideHandler));

        ItemStack combatItem = ItemUtil.getSkinHead("509dedccbde876c98bc002bfeedb8a8ad4640128f45e479dd7287d9f80663075");
        ItemUtil.editMeta(combatItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("战斗模式")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("当前：") + PET_COMBAT_MODE),
                "",
                LIGHT_YELLOW.enclose(BOLD.enclose("被动：")) + LIGHT_GRAY.enclose("不会主动攻击。"),
                "",
                LIGHT_YELLOW.enclose(BOLD.enclose("防御：")) + LIGHT_GRAY.enclose("主人受攻击时反击。"),
                "",
                LIGHT_YELLOW.enclose(BOLD.enclose("支援：")) + LIGHT_GRAY.enclose("协助主人攻击目标。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_YELLOW.enclose("切换") + "。")
            ));
        });
        list.add(new MenuItem(combatItem).setPriority(10).setSlots(23).setHandler(this.combatModeHandler));

        ItemStack renameItem = ItemUtil.getSkinHead("8ff88b122ff92513c6a27b7f67cb3fea97439e078821d6861b74332a2396");
        ItemUtil.editMeta(renameItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("重命名")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("当前名称：") + PET_NAME),
                "",
                LIGHT_GRAY.enclose("为你的宠物设置新名称。"),
                "",
                LIGHT_GRAY.enclose("请使用文明、友善的名称。"),
                LIGHT_GRAY.enclose("若名称不符合服务器规范，管理人员可能"),
                LIGHT_GRAY.enclose("协助调整名称，必要时移除相关宠物。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_YELLOW.enclose("重命名") + "。")
            ));
        });
        list.add(new MenuItem(renameItem).setPriority(10).setSlots(21).setHandler(this.renameHandler));

        ItemStack aspectsItem = ItemUtil.getSkinHead("b62651879d870499da50e34036800ddffd52f3e4e1993c5fc0fc825d03446d8b");
        ItemUtil.editMeta(aspectsItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("属性加点")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("可用属性点：") + PET_ASPECT_POINTS),
                "",
                LIGHT_GRAY.enclose("分配属性点来" + LIGHT_YELLOW.enclose("强化")),
                LIGHT_GRAY.enclose("你的宠物。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_YELLOW.enclose("打开") + "。")
            ));
        });
        list.add(new MenuItem(aspectsItem).setPriority(10).setSlots(25).setHandler(this.aspectsHandler));

        ItemStack equipItem = ItemUtil.getSkinHead("d1d2b7dd66ffd86ad4709927b175e83f1a9e10fbc864b2390403708f39d8efd8");
        ItemUtil.editMeta(equipItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("装备")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("允许装备：") + PET_EQUIPMENT_UNLOCKED),
                "",
                LIGHT_GRAY.enclose("启用后，手持物品右键宠物"),
                LIGHT_GRAY.enclose("即可为它穿戴装备。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("左键点击" + LIGHT_YELLOW.enclose("启用或禁用") + "。"),
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("右键点击" + LIGHT_YELLOW.enclose("卸下全部装备") + "。")
            ));
        });
        list.add(new MenuItem(equipItem).setPriority(10).setSlots(2).setHandler(this.equipmentHandler));

        ItemStack inventoryItem = ItemUtil.getSkinHead("c390eede381bb8447f7d72e15b56347683e02c17c9b8fc6becd726f0a52c7fc1");
        ItemUtil.editMeta(inventoryItem, meta -> {
            meta.setDisplayName(LIGHT_YELLOW.enclose(BOLD.enclose("宠物背包")));
            meta.setLore(Lists.newList(
                LIGHT_YELLOW.enclose("▪ " + LIGHT_GRAY.enclose("已用空间：") + PET_INVENTORY_FILLED + LIGHT_GRAY.enclose("/") + TIER_INVENTORY_SIZE),
                "",
                LIGHT_GRAY.enclose("让宠物替你携带物品。"),
                "",
                LIGHT_YELLOW.enclose("[▶] ") + LIGHT_GRAY.enclose("点击" + LIGHT_YELLOW.enclose("打开") + "。")
            ));
        });
        list.add(new MenuItem(inventoryItem).setPriority(10).setSlots(6).setHandler(this.inventoryHandler));


        
        return list;
    }

    @Override
    protected void loadAdditional() {

    }
}
