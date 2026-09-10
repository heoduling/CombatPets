package su.nightexpress.combatpets.config;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.combatpets.util.PetCreator;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.StringUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

public final class ChineseConfigMigration {

    private static final Map<String, String> TEXT = createTranslations();
    private static final Map<String, String> COMMENTS = createCommentTranslations();

    private ChineseConfigMigration() {

    }

    public static void migrate(@NotNull PetsPlugin plugin) {
        if (!plugin.getDetails().getLanguage().equalsIgnoreCase("zh")) return;

        FileConfig mainConfig = plugin.getConfig();
        translateTree(mainConfig);
        replaceExact(mainConfig, "Pets.Healthbar.Title",
            "<lyellow>%pet_name%</lyellow>   <gray>Lv. </gray><white>%pet_level%</white>   <lred>%pet_health%</lred><gray>/</gray><lred>%pet_max_health% ❤</lred>",
            "<lyellow>%pet_name%</lyellow>   <gray>等级 </gray><white>%pet_level%</white>   <lred>%pet_health%</lred><gray>/</gray><lred>%pet_max_health% ❤</lred>");
        migrateNamedSection(mainConfig, "Attributes", PetCreator::getAttributeName);
        migrateNamedSection(mainConfig, "Aspects", PetCreator::getAspectName);
        migrateNamedSection(mainConfig, "Food", PetCreator::getFoodCategoryName);
        mainConfig.saveChanges();

        Path dataFolder = plugin.getDataFolder().toPath();
        migrateFile(dataFolder.resolve("capturing.yml"), null);
        migrateFile(dataFolder.resolve("accessories.yml"), config -> {
            migrateNamedSection(config, "Variants", PetCreator::getVariantName, "DisplayName");
        });

        migrateDirectory(dataFolder.resolve("menu"), config -> {
            translateTree(config);
            migratePetRenameNotice(config);
            config.set("Settings.Auto_Refresh", Config.getGuiAutoRefreshMillis());
        });
        migrateDirectory(dataFolder.resolve("pets/tiers"), ChineseConfigMigration::migrateTier);
        migrateDirectory(dataFolder.resolve("pets/configs"), ChineseConfigMigration::migratePet);
    }

    public static void migrateLocale(@NotNull PetsPlugin plugin) {
        if (!plugin.getDetails().getLanguage().equalsIgnoreCase("zh")) return;

        Path dataFolder = plugin.getDataFolder().toPath();
        Path locale = dataFolder.resolve("lang/lang_zh.yml");
        if (!Files.isRegularFile(locale)) locale = dataFolder.resolve("lang/messages_zh.yml");
        if (!Files.isRegularFile(locale)) return;

        Path localePath = locale;
        migrateFile(localePath, config -> {
            config.addMissing("Command.PlayerHelp.Title", "Pets 玩家");
            config.addMissing("Command.PlayerHelp.Desc", "查看玩家指令。");
            config.addMissing("Command.AdminHelp.Title", "Pets 管理员");
            config.addMissing("Command.AdminHelp.Desc", "查看管理员指令。");
            config.addMissing("Command.AdminShop.Desc", "管理宠物商店价格。");
            config.addMissing("Command.AdminProShop.Desc", "打开管理员完整宠物蛋商店。");
            config.addMissing("Command.AdminAttributes.Desc", "管理宠物属性满点最终值。");
            config.addMissing("Command.AdminMenu.Desc", "打开管理员宠物测试菜单。");
            config.addMissing("Shop.Admin.Price.Prompt", List.of(
                "[title_times=\"20:60:20\",sound=\"minecraft:block.lava.pop;0.8;1.0\",type=\"title\"]",
                "<soft_yellow><b>修改商店价格</b></soft_yellow>",
                "<gray>请输入 <soft_yellow>%pet_tier_name% %pet_config_default_name%</soft_yellow> 的售价。</gray>",
                "<gray>输入 <soft_yellow>0</soft_yellow> 恢复该品质的默认价格。</gray>"
            ));
            config.addMissing("Shop.Admin.Price.Updated", "<gray>已将 <soft_yellow>%pet_tier_name% %pet_config_default_name%</soft_yellow> 的售价设置为 <soft_green>%price%</soft_green>。</gray>");
            config.addMissing("Shop.Admin.Price.Error.Currency", "<soft_red>该价格使用的货币不可用，价格未修改。</soft_red>");
            config.addMissing("Shop.Admin.Price.Error.Invalid", "<soft_red>请输入不小于 0 的有效数字；输入 0 可恢复品质默认价格。</soft_red>");
            config.addMissing("Pet.Admin.Attribute.Target.Prompt", List.of(
                "[title_times=\"20:60:20\",sound=\"minecraft:block.lava.pop;0.8;1.0\",type=\"title\"]",
                "<soft_yellow><b>修改属性满点值</b></soft_yellow>",
                "<gray>请输入 <soft_yellow>%name%</soft_yellow> 在全部属性点分配后的最终值。</gray>",
                "<gray>输入 <soft_yellow>reset</soft_yellow> 恢复宠物原有的 Per_Aspect 计算。</gray>"
            ));
            config.addMissing("Pet.Admin.Attribute.Target.Updated", "<gray>属性满点最终值已设置为 <soft_green>%value%</soft_green>。</gray>");
            config.addMissing("Pet.Admin.Attribute.Target.Reset", "<gray>属性目标值已重置，恢复使用宠物配置中的 Per_Aspect。</gray>");
            config.addMissing("Pet.Admin.Attribute.Target.Invalid", "<soft_red>请输入不小于 0 的有效数字，或输入 reset。</soft_red>");
            config.addMissing("Pet.Admin.Attribute.Target.Error", "<soft_red>该属性没有关联任何可用的品质属性点。</soft_red>");
            replaceExact(config, "Pet.Catch.Error.Conditions",
                "<gray><soft_red>%name%</soft_red> 尚未达到捕捉条件。</gray>",
                "<gray><soft_red>%name%</soft_red> 的生命值必须降至 <soft_red>%amount%%</soft_red> 或以下。</gray>");
            config.addMissing("Pet.Catch.Error.AlreadyCapturing", "<soft_red>你正在捕捉另一只生物。</soft_red>");
            config.addMissing("Pet.Catch.Error.NoTier", "<soft_red>当前没有启用可捕捉的宠物品质。</soft_red>");
            config.addMissing("Pet.Catch.Error.StartFailed", "<soft_red>无法开始捕捉，请稍后重试。</soft_red>");
            config.addMissing("Pet.Error.DataLoading", "<soft_yellow>宠物数据正在加载，请稍后再试。</soft_yellow>");
            config.addMissing("Pet.Spawn.Error.Peaceful", List.of(
                "[title_times=\"20:60:20\",sound=\"minecraft:entity.villager.no;0.8;1.0\",type=\"title\"]",
                "<soft_red><b>和平模式无法召唤！</b></soft_red>",
                "<gray>敌对宠物只能在简单、普通或困难模式中召唤。</gray>"
            ));
            config.addMissing("Pet.Spawn.Warning.WaterRestricted",
                "<soft_yellow>%pet_config_default_name% 是水域限定宠物，在陆地上不会攻击；附近找不到水域时会自动收回。</soft_yellow>");
        config.addMissing("Pet.Despawn.NoWater",
            "<gray>附近没有适合的水域，<soft_yellow>%pet_config_default_name%</soft_yellow> 已自动收回至宠物收藏。</gray>");
        });
    }

    public static void translateComments(@NotNull PetsPlugin plugin) {
        if (!plugin.getDetails().getLanguage().equalsIgnoreCase("zh")) return;

        Path dataFolder = plugin.getDataFolder().toPath();
        if (!Files.isDirectory(dataFolder)) return;

        try (var files = Files.walk(dataFolder)) {
            for (Path file : files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".yml"))
                .toList()) {
                translateCommentFile(file);
            }
        }
        catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "无法汉化插件配置文件注释。", exception);
        }
    }

    private static void translateCommentFile(@NotNull Path file) throws java.io.IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        boolean changed = false;

        for (int index = 0; index < lines.size(); index++) {
            String original = lines.get(index);
            String translated = translateCommentLine(original);
            if (translated.equals(original)) continue;

            lines.set(index, translated);
            changed = true;
        }

        if (changed) Files.write(file, lines, StandardCharsets.UTF_8);
    }

    @NotNull
    private static String translateCommentLine(@NotNull String line) {
        String translated = line;
        int searchFrom = 0;

        while (searchFrom < translated.length()) {
            int marker = findCommentMarker(translated, searchFrom);
            if (marker < 0) break;

            int contentStart = marker + 1;
            while (contentStart < translated.length() && Character.isWhitespace(translated.charAt(contentStart))) {
                contentStart++;
            }

            String translation = COMMENTS.get(translated.substring(contentStart).trim());
            if (translation != null) {
                return translated.substring(0, contentStart) + translation;
            }

            // Comment examples can contain another comment marker, such as
            // "# vip: 1 # -> Player must be in ...".
            searchFrom = contentStart;
        }

        return translated;
    }

    private static int findCommentMarker(@NotNull String line, int fromIndex) {
        for (int index = Math.max(0, fromIndex); index < line.length(); index++) {
            if (line.charAt(index) == '#' && (index == 0 || Character.isWhitespace(line.charAt(index - 1)))) {
                return index;
            }
        }
        return -1;
    }

    private static void migrateFile(@NotNull Path path, java.util.function.Consumer<FileConfig> extra) {
        if (!Files.isRegularFile(path)) return;

        FileConfig config = FileConfig.load(path);
        translateTree(config);
        if (extra != null) extra.accept(config);
        config.saveChanges();
    }

    private static void migrateDirectory(@NotNull Path path, @NotNull java.util.function.Consumer<FileConfig> migration) {
        if (!Files.isDirectory(path)) return;

        try (var files = Files.walk(path)) {
            files.filter(file -> file.getFileName().toString().endsWith(".yml")).forEach(file -> {
                FileConfig config = FileConfig.load(file);
                migration.accept(config);
                config.saveChanges();
            });
        }
        catch (Exception exception) {
            throw new IllegalStateException("无法迁移中文配置目录：" + path, exception);
        }
    }

    private static void migrateTier(@NotNull FileConfig config) {
        String fileName = config.getPath().getFileName().toString();
        String id = fileName.substring(0, fileName.length() - FileConfig.EXTENSION.length());
        String current = config.getString("Name");
        String english = StringUtil.capitalizeUnderscored(id);

        if (current != null && stripTags(current).equals(english)) {
            config.set("Name", current.replace(english, PetCreator.getTierName(id)));
        }

        replaceExact(config, "Name_Format",
            "<gray>%pet_name%</gray> <lyellow>Lv. <lorange>%pet_level%</lorange></lyellow>",
            "<gray>%pet_name%</gray> <lyellow>等级 <lorange>%pet_level%</lorange></lyellow>");
    }

    private static void migratePetRenameNotice(@NotNull FileConfig config) {
        config.getSection("Items").forEach(id -> {
            String path = "Items." + id;
            if (!"pet_rename".equalsIgnoreCase(config.getString(path + ".Type"))) return;

            List<String> lore = new ArrayList<>(config.getStringList(path + ".Item.Lore"));
            if (lore.stream().anyMatch(line -> line.contains("请使用文明、友善的名称"))) return;

            int actionLine = -1;
            for (int index = 0; index < lore.size(); index++) {
                if (lore.get(index).contains("点击") && lore.get(index).contains("重命名")) {
                    actionLine = index;
                    break;
                }
            }

            int insertAt = actionLine < 0 ? lore.size() : actionLine;
            lore.add(insertAt++, "<lgray>请使用文明、友善的名称。</lgray>");
            lore.add(insertAt++, "<lgray>若名称不符合服务器规范，管理人员可能</lgray>");
            lore.add(insertAt++, "<lgray>协助调整名称，必要时移除相关宠物。</lgray>");
            lore.add(insertAt, "");
            config.set(path + ".Item.Lore", lore);
        });
    }

    private static void migratePet(@NotNull FileConfig config) {
        String rawType = config.getString("Entity_Type");
        if (rawType == null) return;

        try {
            EntityType type = EntityType.valueOf(rawType.toUpperCase(Locale.ROOT));
            replaceExact(config, "Default_Name", StringUtil.capitalizeUnderscored(type.name()), PetCreator.getEntityName(type));
        }
        catch (IllegalArgumentException ignored) {

        }
    }

    private static void migrateNamedSection(@NotNull FileConfig config, @NotNull String section,
                                            @NotNull Function<String, String> translator) {
        migrateNamedSection(config, section, translator, "Name");
    }

    private static void migrateNamedSection(@NotNull FileConfig config, @NotNull String section,
                                            @NotNull Function<String, String> translator, @NotNull String nameKey) {
        for (String id : config.getSection(section)) {
            String path = section + "." + id + "." + nameKey;
            replaceExact(config, path, StringUtil.capitalizeUnderscored(id), translator.apply(id));
        }
    }

    private static void translateTree(@NotNull FileConfig config) {
        Map<String, Object> values = new HashMap<>(config.getValues(true));

        values.forEach((path, value) -> {
            if (value instanceof String text) {
                String translated = TEXT.get(text);
                if (translated != null) config.set(path, translated);
                return;
            }

            if (value instanceof List<?> list) {
                List<Object> translated = new ArrayList<>(list.size());
                boolean changed = false;

                for (Object element : list) {
                    if (element instanceof String text && TEXT.containsKey(text)) {
                        translated.add(TEXT.get(text));
                        changed = true;
                    }
                    else translated.add(element);
                }

                if (changed) config.set(path, translated);
            }
        });
    }

    private static void replaceExact(@NotNull FileConfig config, @NotNull String path,
                                     @NotNull String expected, @NotNull String replacement) {
        String current = config.getString(path);
        if (expected.equals(current)) config.set(path, replacement);
    }

    @NotNull
    private static String stripTags(@NotNull String text) {
        return text.replaceAll("<[^>]+>", "");
    }

    @NotNull
    private static Map<String, String> createCommentTranslations() {
        Map<String, String> map = new HashMap<>();

        map.put("GUI title.", "GUI 标题。");
        map.put("GUI size. Must be multiply of 9.", "GUI 大小，必须是 9 的倍数。");
        map.put("Useful for 'CHEST' Inventory Type only.", "仅适用于 'CHEST' 背包类型。");
        map.put("GUI type.", "GUI 类型。");
        map.put("Sets GUI auto-refresh interval (in seconds). Set this to 0 to disable.", "设置 GUI 自动刷新间隔（秒），设为 0 可禁用。");
        map.put("Sets whether PlaceholderAPI placeholders will be applied on all items from the 'Content' section of this GUI.", "是否对该 GUI 的 'Content' 区域全部物品解析 PlaceholderAPI 占位符。");
        map.put("[*] Disable if you don't use any PlaceholderAPI placeholders on your items to improve GUI performance.", "[*] 如果物品未使用 PlaceholderAPI 占位符，建议禁用以提升 GUI 性能。");
        map.put("==================== GUI CONTENT ====================", "==================== GUI 内容 ====================");
        map.put("You can freely edit items in this section as you wish (add, remove, modify items).", "可按需自由编辑此区域中的物品（添加、删除或修改）。");
        map.put("The following values are available as button Types:", "可用的按钮类型如下：");
        map.put("==================== ITEM OPTIONS ====================", "==================== 物品选项 ====================");
        map.put("> Item: Item to display. Please check: https://nightexpressdev.com/nightcore/configuration/item-formation/", "> Item：显示的物品。格式说明：https://nightexpressdev.com/nightcore/configuration/item-formation/");
        map.put("> Priority: Button priority. Better values will override other item(s) in the same slot(s).", "> Priority：按钮优先级，数值更大的按钮会覆盖相同槽位中的其他物品。");
        map.put("> Slots: Button slots. From [0] to [Size - 1]. Split with commas.", "> Slots：按钮槽位，范围为 [0] 到 [Size - 1]，多个槽位用逗号分隔。");
        map.put("> Click_Commands: Execute custom commands on click. PlaceholderAPI available here.", "> Click_Commands：点击时执行自定义命令，此处支持 PlaceholderAPI。");
        map.put("Available click types: LEFT, RIGHT, SHIFT_LEFT, SHIFT_RIGHT, DROP_KEY, SWAP_KEY, NUMBER_1, NUMBER_2, NUMBER_3, NUMBER_4, NUMBER_5, NUMBER_6, NUMBER_7, NUMBER_8, NUMBER_9", "可用点击类型：LEFT、RIGHT、SHIFT_LEFT、SHIFT_RIGHT、DROP_KEY、SWAP_KEY、NUMBER_1、NUMBER_2、NUMBER_3、NUMBER_4、NUMBER_5、NUMBER_6、NUMBER_7、NUMBER_8、NUMBER_9");
        map.put("Use prefix 'player:' to run command by a player.", "使用 'player:' 前缀可让玩家执行命令。");
        map.put("- say Hello", "示例：- say 你好");
        map.put("- give %player_name% diamond 1", "示例：- give %player_name% diamond 1");
        map.put("- player: menu open shops", "示例：- player: menu open shops");
        map.put("LEFT:", "点击类型：LEFT");
        map.put("Click_Commands:", "点击命令：Click_Commands");
        map.put("Values:", "取值列表：Values");
        map.put("Permission_Prefix: 'example.prefix.'", "权限前缀示例：Permission_Prefix: 'example.prefix.'");
        map.put("ACCEPT", "确认按钮类型：ACCEPT");
        map.put("CANCEL", "取消按钮类型：CANCEL");
        map.put("CLOSE", "关闭按钮类型：CLOSE");
        map.put("COMBAT_MODE", "战斗模式按钮类型：COMBAT_MODE");
        map.put("CONFIRMATION_ACCEPT", "确认按钮类型：CONFIRMATION_ACCEPT");
        map.put("CONFIRMATION_DECLINE", "取消按钮类型：CONFIRMATION_DECLINE");
        map.put("PAGE_NEXT", "下一页按钮类型：PAGE_NEXT");
        map.put("PAGE_PREVIOUS", "上一页按钮类型：PAGE_PREVIOUS");
        map.put("PET_ASPECTS", "宠物属性按钮类型：PET_ASPECTS");
        map.put("PET_EQUIPMENT", "宠物装备按钮类型：PET_EQUIPMENT");
        map.put("PET_INVENTORY", "宠物背包按钮类型：PET_INVENTORY");
        map.put("PET_RENAME", "宠物重命名按钮类型：PET_RENAME");
        map.put("PET_RETURN", "收回宠物按钮类型：PET_RETURN");
        map.put("REALLOCATE_POINTS", "重置属性点按钮类型：REALLOCATE_POINTS");
        map.put("RETURN", "返回按钮类型：RETURN");
        map.put("SILENT", "静音按钮类型：SILENT");

        map.put("Available values: RANK, PERMISSION", "可用值：RANK、PERMISSION");
        map.put("==================== RANK MODE ====================", "==================== 权限组模式 ====================");
        map.put("Get value by player's permission group. All keys in 'Values' list will represent permission group names.", "根据玩家的权限组取值，'Values' 列表中的键均表示权限组名称。");
        map.put("If player has none of specified groups, the 'Default_Value' setting will be used then", "如果玩家不属于任何指定权限组，则使用 'Default_Value' 的值。");
        map.put("-> Player must be in 'vip' permission group.", "-> 玩家必须属于 'vip' 权限组。");
        map.put("-> Player must be in 'gold' permission group.", "-> 玩家必须属于 'gold' 权限组。");
        map.put("-> Player must be in 'emerald' permission group.", "-> 玩家必须属于 'emerald' 权限组。");
        map.put("==================== PERMISSION MODE ====================", "==================== 权限节点模式 ====================");
        map.put("Get value by player's permissions. All keys in 'Values' list will represent postfixes for the 'Permission_Prefix' setting (see below).", "根据玩家的权限节点取值，'Values' 列表中的键均表示 'Permission_Prefix' 后缀（见下方）。");
        map.put("If player has none of specified permissions, the 'Default_Value' setting will be used then", "如果玩家没有任何指定权限，则使用 'Default_Value' 的值。");
        map.put("-> Player must have 'example.prefix.vip' permission.", "-> 玩家必须拥有 'example.prefix.vip' 权限。");
        map.put("-> Player must have 'example.prefix.gold' permission.", "-> 玩家必须拥有 'example.prefix.gold' 权限。");
        map.put("-> Player must have 'example.prefix.emerald' permission.", "-> 玩家必须拥有 'example.prefix.emerald' 权限。");
        map.put("Sets permission prefix for the 'PERMISSION' mode.", "设置 'PERMISSION' 模式使用的权限前缀。");

        map.put("[20 ticks = 1 second]", "[20 游戏刻 = 1 秒]");
        map.put("[Decimals allowed]", "[允许使用小数]");
        map.put("[Default is 1]", "[默认值为 1]");
        map.put("[Default is 3]", "[默认值为 3]");
        map.put("[Default is 20]", "[默认值为 20]");
        map.put("[Default is 300 (5 minutes)]", "[默认值为 300（5 分钟）]");
        map.put("[Default is 1800000 (30 minutes)]", "[默认值为 1800000（30 分钟）]");
        map.put("[Default is System Locale]", "[默认使用系统语言]");
        map.put("[DO NOT DISABLE UNDER ANY CIRCUMSTANCES!]", "[任何情况下都不要禁用！]");
        map.put("[The minimum allowed value is 30000ms (30 seconds)]", "[允许的最小值为 30000 毫秒（30 秒）]");
        map.put("[*] Set to -1 for permanent data cache for offline players.", "[*] 设为 -1 可永久缓存离线玩家数据。");
        map.put("[*] Set to -1 to disable cache clean up and keep user data loaded until reboot.", "[*] 设为 -1 可禁用缓存清理，并持续保留玩家数据直到服务器重启。");
        map.put("[*] Set to 0 to disable data cache for offline players.", "[*] 设为 0 可禁用离线玩家数据缓存。");

        map.put("Plugin prefix. Used in messages.", "插件前缀，用于消息文本。");
        map.put("Localized plugin name. It's used in messages and with internal placeholders.", "本地化插件名称，用于消息文本和内部占位符。");
        map.put("Sets the plugin language.", "设置插件语言。");
        map.put("Basically it tells the plugin to use certain messages config from the '/lang/' sub-folder.", "插件将使用 '/lang/' 子目录中对应语言的消息配置。");
        map.put("If specified language is not available, default one (English) will be used instead.", "如果指定语言不可用，将改用默认语言（英语）。");
        map.put("Command names that will be registered as main plugin commands.", "注册为插件主命令的命令名称。");
        map.put("Do not leave this empty. Split multiple names with a comma.", "请勿留空，多个名称使用逗号分隔。");
        map.put("Sets database type.", "设置数据库类型。");
        map.put("Available values: MYSQL,SQLITE", "可用值：MYSQL、SQLITE");
        map.put("File name for the SQLite database file.", "SQLite 数据库文件名。");
        map.put("Actually it's a path to the file, so you can use directories here.", "此处实际为文件路径，因此可以包含目录。");
        map.put("Database host. Example: localhost:3306, 127.0.0.1:3306", "数据库地址，例如 localhost:3306 或 127.0.0.1:3306。");
        map.put("Name of the MySQL database where plugin will create tables.", "插件用于创建数据表的 MySQL 数据库名称。");
        map.put("Database user name.", "数据库用户名。");
        map.put("Database password.", "数据库密码。");
        map.put("Custom prefix for plugin tables in database.", "插件数据库表的自定义前缀。");
        map.put("Connection options. Do not touch unless you know what you're doing.", "数据库连接选项，不了解其用途时请勿修改。");
        map.put("This property controls the maximum lifetime of a connection in the pool.", "此项控制连接池中连接的最长存活时间。");
        map.put("A value of 0 indicates no maximum lifetime (infinite lifetime).", "值为 0 表示不限制最长存活时间。");
        map.put("Useless for SQLITE.", "对 SQLITE 无效。");
        map.put("Custom identifier of this server instance used in data syncing.", "数据同步时用于标识当前服务器实例的自定义 ID。");
        map.put("Sets how often (in seconds) plugin data will be fetched and loaded from the remote database.", "设置从远程数据库获取并加载插件数据的间隔（秒）。");
        map.put("Sets synchronization delay (in seconds) before the plugin can continue syncing data for a user after its scheduled save.", "设置玩家计划保存完成后继续同步数据前的延迟（秒）。");
        map.put("This might be helpful to prevent possible issues when synchronization process access the database before user data was fully saved.", "这可避免同步流程在玩家数据完全保存前访问数据库。");
        map.put("When a user marked for saving has been saved, their synchronization will be unlocked but delayed for this value.", "标记保存的玩家完成保存后会解除同步锁定，但仍按此值延迟同步。");
        map.put("Sets how often (in ticks) plugin will attempt to save data of users marked to be saved.", "设置插件尝试保存已标记玩家数据的间隔（游戏刻）。");
        map.put("This will save only users that are 'ready' to save (see 'Scheduled_Save_Delay').", "仅保存已达到可保存时间的玩家（参见 'Scheduled_Save_Delay'）。");
        map.put("Users marked for save when their data has been changed/affected in some way.", "玩家数据发生变化时会被标记为待保存。");
        map.put("Sets scheduled save delay (in seconds) for a user when marked to be saved.", "设置玩家被标记为待保存后的计划保存延迟（秒）。");
        map.put("This means that a user will be saved X seconds later after being marked.", "即玩家被标记后将在 X 秒后保存。");
        map.put("IMPORTANT NOTE #1: When a user is marked for saving, this will prevent them from syncing until saving occurs!", "重要说明 #1：玩家被标记为待保存后，在保存完成前将禁止同步！");
        map.put("IMPORTANT NOTE #2: Every time a user is marked for saving, their save time will be updated with this value!", "重要说明 #2：玩家每次被标记为待保存时，保存时间都会按此值重新计算！");
        map.put("Generally, you should keep this value less than 'Scheduled_Save_Interval' for best results,", "通常应让此值小于 'Scheduled_Save_Interval'，以获得最佳效果，");
        map.put("and not setting it extremely high, otherwise you may result in user data never saved & synchronized.", "也不要设置得过高，否则玩家数据可能一直无法保存和同步。");
        map.put("Sets cache lifetime for player's data.", "设置玩家数据的缓存时间。");
        map.put("Data loaded for offline players and data of previously online players will be cached in the memory for that time.", "离线玩家和此前在线玩家的数据将在内存中缓存指定时间。");
        map.put("When cache is expired, data have to be loaded from the database again.", "缓存过期后，需要重新从数据库加载数据。");
        map.put("Cache contains data loaded for offline players and data of previously online players", "缓存包含离线玩家和此前在线玩家的数据。");
        map.put("Sets how often (in seconds) plugin will clean up user data cache.", "设置插件清理玩家数据缓存的间隔（秒）。");
        map.put("Set to '-1' to disable.", "设为 '-1' 可禁用。");
        map.put("Enables the purge feature.", "是否启用数据清理功能。");
        map.put("Purge will remove all records from the plugin tables that are 'old' enough.", "数据清理会删除插件数据表中达到指定时间的旧记录。");
        map.put("Sets maximal 'age' for the database records before they will be purged.", "设置数据库记录被清理前允许保留的最长时间。");
        map.put("By default it's days of inactivity for the plugin users.", "默认按插件玩家未活跃的天数计算。");
        map.put("However this is higly depends on the plugin.", "具体含义取决于插件实现。");
        map.put("This option may have different behavior depends on the plugin.", "此选项的具体行为取决于插件实现。");
        map.put("Sound name. You can use Spigot sound names, or ones from your resource pack.", "音效名称，可使用 Spigot 音效名或资源包中的自定义音效。");
        map.put("Spigot Sounds: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html", "Spigot 音效列表：https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Sound.html");
        map.put("Sound volume. From 0.0 to 1.0.", "音效音量，范围为 0.0 到 1.0。");
        map.put("Sound speed. From 0.5 to 2.0", "音效音调，范围为 0.5 到 2.0。");

        return map;
    }

    @NotNull
    private static Map<String, String> createTranslations() {
        Map<String, String> map = new HashMap<>();

        map.put("<black>Pet Aspects</black>", "<black>宠物属性</black>");
        map.put("<black>Pet Collection</black>", "<black>宠物收藏</black>");
        map.put("<black>Pet Menu</black>", "<black>宠物菜单</black>");
        map.put("<black>Release the pet?</black>", "<black>确定要放生宠物吗？</black>");
        map.put("<black>Revive the pet?</black>", "<black>确定要复活宠物吗？</black>");
        map.put("<black>Pet Collection (Tiers)</black>", "<black>宠物收藏 - 选择品质</black>");
        map.put("<black>Are you sure?</black>", "<black>确认购买吗？</black>");
        map.put("<black>Egg Shop</black>", "<black>宠物蛋商店</black>");
        map.put("<black>Select a tier...</black>", "<black>选择宠物品质</black>");

        map.put("<soft_yellow><b>Back</b></soft_yellow>", "<lyellow><b>返回</b></lyellow>");
        map.put("<white><b>← <u>Previous Page</u></b></white>", "<white><b>← 上一页</b></white>");
        map.put("<white><b><u>Next Page</u> →</b></white>", "<white><b>下一页 →</b></white>");
        map.put("<red><b>Close</b></red>", "<lred><b>关闭</b></lred>");
        map.put("<lgreen><b>Accept</b></lgreen>", "<lgreen><b>确认</b></lgreen>");
        map.put("<lred><b>Cancel</b></lred>", "<lred><b>取消</b></lred>");
        map.put("<lgreen><b>Yes</b></lgreen>", "<lgreen><b>确认购买</b></lgreen>");
        map.put("<lred><b>No</b></lred>", "<lred><b>取消</b></lred>");

        map.put("<dgray>1 Point</dgray>", "<dgray>1 属性点</dgray>");
        map.put("<lyellow>▪ <lgray>Current: </lgray>%value%<lgray>/</lgray>%max%</lyellow>", "<lyellow>▪ <lgray>当前：</lgray>%value%<lgray>/</lgray>%max%</lyellow>");
        map.put("<lyellow>▪ <lgray>Balance: </lgray>%pet_aspect_points% Points</lyellow>", "<lyellow>▪ <lgray>可用：</lgray>%pet_aspect_points% 属性点</lyellow>");
        map.put("<lyellow><b>Affected Attributes:</b></lyellow>", "<lyellow><b>受影响的属性：</b></lyellow>");
        map.put("<lgray><lyellow>[▶]</lyellow> Click to <lyellow>upgrade</lyellow>.</lgray>", "<lgray><lyellow>[▶]</lyellow> 点击<lyellow>升级</lyellow>。</lgray>");
        map.put("<lgray><lred>✘</lred> You don't have <lred>aspect points</lred>.</lgray>", "<lgray><lred>✘</lred> 你没有可用的<lred>属性点</lred>。</lgray>");
        map.put("<lgray><lred>[❗]</lred> Aspect is at max. value.</lgray>", "<lgray><lred>[❗]</lred> 该属性已达到上限。</lgray>");
        map.put("<lyellow><b>Reallocate Points</b></lyellow>", "<lyellow><b>重置属性点</b></lyellow>");
        map.put("<lgray>Resets all aspect values to 0</lgray>", "<lgray>将全部属性值重置为 0，</lgray>");
        map.put("<lgray>and returns aspect points.</lgray>", "<lgray>并返还已经分配的属性点。</lgray>");
        map.put("<lgray><lyellow>[▶]</lyellow> Click to <lyellow>reallocate</lyellow>.</lgray>", "<lgray><lyellow>[▶]</lyellow> 点击<lyellow>重置</lyellow>。</lgray>");

        map.put("<lyellow>▪ <lgray>Level: </lgray>%pet_level%</lyellow>", "<lyellow>▪ <lgray>等级：</lgray>%pet_level%</lyellow>");
        map.put("<lyellow>▪ <lgray>XP: </lgray>%pet_exp%<lgray>/</lgray>%pet_max_exp%</lyellow>", "<lyellow>▪ <lgray>经验：</lgray>%pet_exp%<lgray>/</lgray>%pet_max_exp%</lyellow>");
        map.put("<lyellow>▪ <lgray>Saturation: </lgray>%pet_saturation%<lgray>/</lgray>%pet_max_saturation%</lyellow>", "<lyellow>▪ <lgray>饱食度：</lgray>%pet_saturation%<lgray>/</lgray>%pet_max_saturation%</lyellow>");
        map.put("<lyellow>▪ <lgray>Food: </lgray>%pet_food%</lyellow>", "<lyellow>▪ <lgray>食物：</lgray>%pet_food%</lyellow>");
        map.put("<lyellow><b>ATTRIBUTES</b></lyellow>", "<lyellow><b>属性</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Damage: </lgray>%pet_attribute_attack_damage%</lyellow>", "<lyellow>▪ <lgray>伤害：</lgray>%pet_attribute_attack_damage%</lyellow>");
        map.put("<lyellow>▪ <lgray>Attack Speed: </lgray>%pet_attribute_attack_speed%<lgray>/ sec.</lgray></lyellow>", "<lyellow>▪ <lgray>攻击速度：</lgray>%pet_attribute_attack_speed%<lgray>/秒</lgray></lyellow>");
        map.put("<lyellow>▪ <lgray>Health: </lgray>%pet_attribute_max_health%</lyellow>", "<lyellow>▪ <lgray>生命值：</lgray>%pet_attribute_max_health%</lyellow>");
        map.put("<lyellow>▪ <lgray>Regen: </lgray>%pet_attribute_health_regeneration_force%<lgray> x </lgray>%pet_attribute_health_regeneration_speed%<lgray> / sec.</lgray></lyellow>", "<lyellow>▪ <lgray>生命恢复：</lgray>%pet_attribute_health_regeneration_force%<lgray> x </lgray>%pet_attribute_health_regeneration_speed%<lgray>/秒</lgray></lyellow>");
        map.put("<lyellow>▪ <lgray>Defense: </lgray>%pet_attribute_armor%</lyellow>", "<lyellow>▪ <lgray>防御：</lgray>%pet_attribute_armor%</lyellow>");
        map.put("<lyellow>▪ <lgray>Speed: W: </lgray>%pet_attribute_movement_speed%<lgray> / F: </lgray>%pet_attribute_flying_speed%</lyellow>", "<lyellow>▪ <lgray>速度：地面 </lgray>%pet_attribute_movement_speed%<lgray> / 飞行 </lgray>%pet_attribute_flying_speed%</lyellow>");
        map.put("<lgray>Status: <lred><b>Dead</b></lred></lgray>", "<lgray>状态：<lred><b>已死亡</b></lred></lgray>");
        map.put("<lgray>Ressurection in: <lred>%time%</lred></lgray>", "<lgray>自动复活倒计时：<lred>%time%</lred></lgray>");
        map.put("<lred>[▶] </lred><lgray>Click to revive it for <lred>$%cost%</lred>.</lgray>", "<lred>[▶] </lred><lgray>点击花费 <lred>$%cost%</lred> 复活。</lgray>");
        map.put("<lgray>Status: <lgreen><b>Summoned</b></lgreen></lgray>", "<lgray>状态：<lgreen><b>已召唤</b></lgreen></lgray>");
        map.put("<lgreen>[▶] </lgreen><lgray>Click to <lgreen>despawn</lgreen>.</lgray>", "<lgreen>[▶] </lgreen><lgray>点击<lgreen>收回</lgreen>宠物。</lgray>");
        map.put("<lgray>Status: <lyellow><b>Idle</b></lyellow></lgray>", "<lgray>状态：<lyellow><b>未召唤</b></lyellow></lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Left-Click to <lyellow>summon</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>左键点击<lyellow>召唤</lyellow>。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>[Q/Drop] key to <lyellow>release</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>按 [Q/丢弃键] <lyellow>放生</lyellow>。</lgray>");

        map.put("<lyellow><b>STATS</b></lyellow>", "<lyellow><b>宠物状态</b></lyellow>");
        map.put("<lyellow><b>Return to Collection</b></lyellow>", "<lyellow><b>收回宠物</b></lyellow>");
        map.put("<lgray>Return pet to your collection.</lgray>", "<lgray>将宠物收回收藏。</lgray>");
        map.put("<lyellow><b>Silent Mode</b></lyellow>", "<lyellow><b>静音模式</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Status: </lgray>%pet_silent%</lyellow>", "<lyellow>▪ <lgray>状态：</lgray>%pet_silent%</lyellow>");
        map.put("<lgray>Disables pet ambient sounds.</lgray>", "<lgray>关闭宠物的环境音效。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Click to <lyellow>toggle</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>点击<lyellow>切换</lyellow>。</lgray>");
        map.put("<lyellow><b>Combat Mode</b></lyellow>", "<lyellow><b>战斗模式</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Current: </lgray>%pet_combat_mode%</lyellow>", "<lyellow>▪ <lgray>当前：</lgray>%pet_combat_mode%</lyellow>");
        map.put("<lyellow><b>Passive: </b></lyellow><lgray>Never attacks.</lgray>", "<lyellow><b>被动：</b></lyellow><lgray>不会主动攻击。</lgray>");
        map.put("<lyellow><b>Protective: </b></lyellow><lgray>Defends owner when attacked.</lgray>", "<lyellow><b>防御：</b></lyellow><lgray>主人受攻击时反击。</lgray>");
        map.put("<lyellow><b>Supportive: </b></lyellow><lgray>Supports owner's attacks.</lgray>", "<lyellow><b>支援：</b></lyellow><lgray>协助主人攻击目标。</lgray>");
        map.put("<lyellow><b>Rename</b></lyellow>", "<lyellow><b>重命名</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Current: </lgray>%pet_name%</lyellow>", "<lyellow>▪ <lgray>当前名称：</lgray>%pet_name%</lyellow>");
        map.put("<lgray>Give new name to your pet.</lgray>", "<lgray>为你的宠物设置新名称。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Click to <lyellow>rename</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>点击<lyellow>重命名</lyellow>。</lgray>");
        map.put("<lyellow><b>Aspects</b></lyellow>", "<lyellow><b>属性加点</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Aspect Points: </lgray>%pet_aspect_points%</lyellow>", "<lyellow>▪ <lgray>可用属性点：</lgray>%pet_aspect_points%</lyellow>");
        map.put("<lgray>Improve your pet by <lyellow>upgrading</lyellow></lgray>", "<lgray>分配属性点来<lyellow>强化</lyellow></lgray>");
        map.put("<lgray>certain its aspects!</lgray>", "<lgray>你的宠物。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Click to <lyellow>open</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>点击<lyellow>打开</lyellow>。</lgray>");
        map.put("<lyellow><b>Equipment</b></lyellow>", "<lyellow><b>装备</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Unlocked: </lgray>%pet_equipment_unlocked%</lyellow>", "<lyellow>▪ <lgray>允许装备：</lgray>%pet_equipment_unlocked%</lyellow>");
        map.put("<lgray>When <lyellow>unlocked</lyellow>, right-click the pet</lgray>", "<lgray>启用后，手持物品右键宠物</lgray>");
        map.put("<lgray>with item in hand to equip it.</lgray>", "<lgray>即可为它穿戴装备。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Left-Click to <lyellow>toggle</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>左键点击<lyellow>启用或禁用</lyellow>。</lgray>");
        map.put("<lyellow>[▶] </lyellow><lgray>Right-Click to <lyellow>unequip all</lyellow>.</lgray>", "<lyellow>[▶] </lyellow><lgray>右键点击<lyellow>卸下全部装备</lyellow>。</lgray>");
        map.put("<lyellow><b>Inventory</b></lyellow>", "<lyellow><b>宠物背包</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Storage: </lgray>%pet_inventory_filled%<lgray>/</lgray>%pet_tier_inventory_size%</lyellow>", "<lyellow>▪ <lgray>已用空间：</lgray>%pet_inventory_filled%<lgray>/</lgray>%pet_tier_inventory_size%</lyellow>");
        map.put("<lgray>You're making me carry</lgray>", "<lgray>让宠物替你携带物品。</lgray>");
        map.put("<lgray>the heavy stuff, aren't you?</lgray>", "<lgray></lgray>");

        map.put("<lgray>You are about to release this pet.</lgray>", "<lgray>这只宠物将被永久放生。</lgray>");
        map.put("<lgray>You are about to revive this pet.</lgray>", "<lgray>你即将复活这只宠物。</lgray>");
        map.put("<lgray>It will cost you <lred>$%pet_tier_death_revive_cost%</lred></lgray>", "<lgray>需要花费 <lred>$%pet_tier_death_revive_cost%</lred></lgray>");
        map.put("<lyellow>%amount%</lyellow> <lgray>pets.</lgray>", "<lyellow>%amount%</lyellow> <lgray>只宠物</lgray>");
        map.put("<lyellow><b>Purchase: </b></lyellow><white>%pet_config_default_name%</white> <lgray>(%pet_tier_name%)</lgray>", "<lyellow><b>购买：</b></lyellow><white>%pet_config_default_name%</white> <lgray>(%pet_tier_name%)</lgray>");
        map.put("<lgray>Yes, I'm sure!</lgray>", "<lgray>确认购买这枚宠物蛋。</lgray>");
        map.put("<lgray><lgreen>[▶]</lgreen> Click to <lgreen>purchase</lgreen> for <lgreen>%price%</lgreen>.</lgray>", "<lgray><lgreen>[▶]</lgreen> 点击花费 <lgreen>%price%</lgreen> <lgreen>购买</lgreen>。</lgray>");
        map.put("<lgray>No, I changed my mind.</lgray>", "<lgray>返回宠物蛋商店。</lgray>");

        map.put("<lyellow>▪ <lgray>Inventory: </lgray>%pet_tier_inventory_has% <lgray>(%pet_tier_inventory_size% slots)</lgray></lyellow>", "<lyellow>▪ <lgray>宠物背包：</lgray>%pet_tier_inventory_has% <lgray>(%pet_tier_inventory_size% 格)</lgray></lyellow>");
        map.put("<lyellow>▪ <lgray>Equipment: </lgray>%pet_tier_equipment_has%</lyellow>", "<lyellow>▪ <lgray>宠物装备：</lgray>%pet_tier_equipment_has%</lyellow>");
        map.put("<lyellow>▪ <lgray>Max. Saturation: </lgray>%pet_config_attribute_start_max_saturation%</lyellow>", "<lyellow>▪ <lgray>最大饱食度：</lgray>%pet_config_attribute_start_max_saturation%</lyellow>");
        map.put("<lyellow><b>START ATTRIBUTES</b></lyellow>", "<lyellow><b>初始属性</b></lyellow>");
        map.put("<lyellow>▪ <lgray>Damage: </lgray>%pet_config_attribute_start_attack_damage%</lyellow>", "<lyellow>▪ <lgray>伤害：</lgray>%pet_config_attribute_start_attack_damage%</lyellow>");
        map.put("<lyellow>▪ <lgray>Health: </lgray>%pet_config_attribute_start_max_health%</lyellow>", "<lyellow>▪ <lgray>生命值：</lgray>%pet_config_attribute_start_max_health%</lyellow>");
        map.put("<lyellow>▪ <lgray>Defense: </lgray>%pet_config_attribute_start_armor%</lyellow>", "<lyellow>▪ <lgray>防御：</lgray>%pet_config_attribute_start_armor%</lyellow>");
        map.put("<lyellow>▪ <lgray>Speed: </lgray>%pet_config_attribute_start_movement_speed%</lyellow>", "<lyellow>▪ <lgray>速度：</lgray>%pet_config_attribute_start_movement_speed%</lyellow>");
        map.put("<lgray>Click to purchase <lyellow>%pet_tier_name%</lyellow> pet eggs.</lgray>", "<lgray>点击购买 <lyellow>%pet_tier_name%</lyellow> 宠物蛋。</lgray>");

        map.put("<lpurple><b>Mystery Pet Egg</b></lpurple> <gray>(<white>%pet_config_default_name%</white>)</gray>", "<lpurple><b>神秘宠物蛋</b></lpurple> <gray>(<white>%pet_config_default_name%</white>)</gray>");
        map.put("<dgray>Hatches into egg with random tier.</dgray>", "<dgray>可孵化出随机品质的宠物蛋。</dgray>");
        map.put("<lgray><lpurple>[▶]</lpurple> Right-Click to <lpurple>hatch</lpurple>.</lgray>", "<lgray><lpurple>[▶]</lpurple> 右键点击<lpurple>孵化</lpurple>。</lgray>");
        map.put("<lyellow><b>Pet Egg</b></lyellow> <gray>(<white>%pet_config_default_name%</white>)</gray>", "<lyellow><b>宠物蛋</b></lyellow> <gray>(<white>%pet_config_default_name%</white>)</gray>");
        map.put("<dgray>Tier: <gray>%pet_tier_name%</gray></dgray>", "<dgray>品质：<gray>%pet_tier_name%</gray></dgray>");
        map.put("<lgray><lyellow>[▶]</lyellow> Right-Click to <lyellow>claim</lyellow>.</lgray>", "<lgray><lyellow>[▶]</lyellow> 右键点击<lyellow>领取</lyellow>宠物。</lgray>");
        map.put("<lorange><b>Capture Lead</b></lorange>", "<lorange><b>宠物捕捉绳</b></lorange>");
        map.put("<gray>Special lead used to capture mobs.</gray>", "<gray>用于捕捉生物并将其转化为宠物。</gray>");
        map.put("<lgray><lorange>[▶]</lorange> Right-Click a mob to <lorange>capture</lorange>.</lgray>", "<lgray><lorange>[▶]</lorange> 右键点击生物进行<lorange>捕捉</lorange>。</lgray>");
        map.put("<lyellow><b>Pet Accessory</b></lyellow> <gray>(<white>%type%</white>)</gray>", "<lyellow><b>宠物配饰</b></lyellow> <gray>(<white>%type%</white>)</gray>");
        map.put("<gray>Applies <lyellow>%name% %type%</lyellow></gray>", "<gray>为你的宠物应用 <lyellow>%name% %type%</lyellow></gray>");
        map.put("<gray>accessory on your pet.</gray>", "<gray>配饰。</gray>");
        map.put("<lgray><lyellow>[▶]</lyellow> Right-Click a pet to <lyellow>apply</lyellow>.</lgray>", "<lgray><lyellow>[▶]</lyellow> 右键点击宠物进行<lyellow>应用</lyellow>。</lgray>");

        return map;
    }
}
