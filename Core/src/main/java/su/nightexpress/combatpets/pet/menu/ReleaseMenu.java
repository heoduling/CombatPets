package su.nightexpress.combatpets.pet.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.config.Config;
import su.nightexpress.combatpets.data.impl.PetData;
import su.nightexpress.combatpets.util.PetUtils;
import su.nightexpress.nightcore.config.ConfigValue;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.menu.MenuOptions;
import su.nightexpress.nightcore.menu.MenuSize;
import su.nightexpress.nightcore.menu.MenuViewer;
import su.nightexpress.nightcore.menu.item.MenuItem;
import su.nightexpress.nightcore.util.ItemUtil;
import su.nightexpress.nightcore.util.Lists;

import java.util.ArrayList;
import java.util.List;

import static su.nightexpress.combatpets.Placeholders.*;
import static su.nightexpress.nightcore.util.text.tag.Tags.*;

public class ReleaseMenu extends ConfirmMenu {

    private static final String FILE = "pet_release.yml";

    public ReleaseMenu(@NotNull PetsPlugin plugin) {
        super(plugin, FileConfig.loadOrExtract(plugin, Config.DIR_MENU, FILE));
        this.load();

        this.getItems().forEach(PetUtils::applyMenuPlaceholders);
    }

    @Override
    protected void onConfirm(@NotNull MenuViewer viewer, @NotNull PetData petData) {
        this.plugin.getPetManager().releasePet(viewer.getPlayer(), petData);
        this.runNextTick(() -> viewer.getPlayer().closeInventory());
    }

    @Override
    protected void onDecline(@NotNull MenuViewer viewer, @NotNull PetData petData) {
        this.runNextTick(() -> plugin.getPetManager().openPetsCollection(viewer.getPlayer(), petData.getTier()));
    }

    @Override
    protected void onReady(@NotNull MenuViewer viewer, @NotNull Inventory inventory) {

    }

    @Override
    @NotNull
    protected MenuOptions createDefaultOptions() {
        return Config.createMenuOptions(BLACK.enclose("确定要放生宠物吗？"), MenuSize.CHEST_9);
    }

    @Override
    @NotNull
    protected List<MenuItem> createDefaultItems() {
        List<MenuItem> list = new ArrayList<>();

        ItemStack acceptItem = ItemUtil.getSkinHead(SKIN_CHECK_MARK);
        ItemUtil.editMeta(acceptItem, meta -> {
            meta.setDisplayName(LIGHT_GREEN.enclose(BOLD.enclose("确定放生")));
        });
        list.add(new MenuItem(acceptItem).setPriority(10).setSlots(8).setHandler(this.acceptHandler));

        ItemStack denyItem = ItemUtil.getSkinHead(SKIN_WRONG_MARK);
        ItemUtil.editMeta(denyItem, meta -> {
            meta.setDisplayName(LIGHT_RED.enclose(BOLD.enclose("取消")));
        });
        list.add(new MenuItem(denyItem).setPriority(10).setSlots(0).setHandler(this.declineHandler));

        return list;
    }

    @Override
    protected void loadAdditional() {
        this.iconName = ConfigValue.create("PetIcon.Name",
            LIGHT_YELLOW.enclose(BOLD.enclose(PET_NAME))
        ).read(cfg);

        this.iconLore = ConfigValue.create("PetIcon.Lore", Lists.newList(
            LIGHT_GRAY.enclose("这只宠物将被永久放生。")
        )).read(cfg);

        this.iconSlot = ConfigValue.create("PetIcon.Slot", 4).read(cfg);
    }
}
