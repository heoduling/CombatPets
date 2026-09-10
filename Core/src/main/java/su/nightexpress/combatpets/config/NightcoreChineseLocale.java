package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.nightcore.NightCore;
import su.nightexpress.nightcore.config.FileConfig;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

public final class NightcoreChineseLocale {

    private static final String RESOURCE_PATH = "compat/nightcore_lang_zh.yml";

    private NightcoreChineseLocale() {

    }

    public static void install(@NotNull PetsPlugin plugin) {
        NightCore nightcore = NightCore.get();
        if (nightcore == null || !nightcore.getDetails().getLanguage().equalsIgnoreCase("zh")) return;

        Path localeFile = nightcore.getDataFolder().toPath().resolve("lang").resolve("lang_zh.yml");
        boolean installed = !Files.isRegularFile(localeFile);
        try (InputStream input = plugin.getResource(RESOURCE_PATH)) {
            if (installed) {
                if (input == null) {
                    plugin.error("缺少内置的 nightcore 简体中文语言资源！");
                    return;
                }

                Files.createDirectories(localeFile.getParent());
                Files.copy(input, localeFile);
            }

            FileConfig locale = FileConfig.load(localeFile);
            boolean changed = false;

            List<String> oldGeneral = List.of(
                "[prefix=\"false\"]",
                "<c:#FCE491><shadow:#C5741A:1.0><st>                                                            </st></shadow></c>",
                "<c:#FCC549><shadow:#B45115:1.0>  <b>%name% 命令</b></shadow></c>",
                " ",
                "  <sprite:\"gui\":\"icon/info\"><c:#D9D9D9><shadow:#5D5D5D:1.0>将鼠标悬停在命令上可查看说明。</shadow></c>",
                " ",
                "%entry%",
                "<c:#FCE491><shadow:#C5741A:1.0><st>                                                            </st></shadow></c>"
            );
            List<String> newGeneral = List.of(
                "[prefix=\"false\"]",
                "<c:#FCE491><shadow:#C5741A:1.0><st>                                                            </st></shadow></c>",
                "<c:#FCC549><shadow:#B45115:1.0>  <b>%name% 命令</b></shadow></c>",
                " ",
                "%entry%",
                "<c:#FCE491><shadow:#C5741A:1.0><st>                                                            </st></shadow></c>"
            );
            if (oldGeneral.equals(locale.getStringList("HelpPage.General"))) {
                locale.set("HelpPage.General", newGeneral);
                changed = true;
            }

            String oldEntry = "<hover:show_text:\"<c:#D9D9D9><shadow:#5D5D5D:1.0>%description%</shadow></c>\"><c:#FCC549><shadow:#B45115:1.0>  • /%command%</shadow></c></hover>";
            String newEntry = "<c:#FCC549><shadow:#B45115:1.0>  • /%command%</shadow></c> <c:#D9D9D9><shadow:#5D5D5D:1.0>- %description%</shadow></c>";
            if (oldEntry.equals(locale.getString("HelpPage.Entry"))) {
                locale.set("HelpPage.Entry", newEntry);
                changed = true;
            }

            List<String> editorExit = List.of(
                "[prefix=\"false\"]",
                "",
                "<gray>点击 <click:run_command:\"/#exit\"><hover:show_text:\"<gray>点击取消输入。</gray>\"><green>[这里]</green></hover></click> 退出输入模式。</gray>",
                ""
            );
            List<String> currentEditorExit = locale.getStringList("Editor.Action.Exit");
            if (currentEditorExit.isEmpty() || currentEditorExit.stream().anyMatch(line -> line.contains("leave input mode"))) {
                locale.set("Editor.Action.Exit", editorExit);
                changed = true;
            }
            List<String> currentDialogExit = locale.getStringList("Dialog.Info.Exit");
            if (currentDialogExit.isEmpty() || currentDialogExit.stream().anyMatch(line -> line.contains("leave input mode"))) {
                locale.set("Dialog.Info.Exit", editorExit);
                changed = true;
            }

            if (!locale.contains("Editor.Input.Header.Main")) {
                locale.set("Editor.Input.Header.Main", "<green><b>输入模式</b></green>");
                changed = true;
            }
            if (!locale.contains("Editor.Input.Header.Error")) {
                locale.set("Editor.Input.Header.Error", "<red><b>错误</b></red>");
                changed = true;
            }
            if (!locale.contains("Editor.Input.Error.NotInteger")) {
                locale.set("Editor.Input.Error.NotInteger", "<gray>请输入<red>整数</red>！</gray>");
                changed = true;
            }
            if (!locale.contains("Editor.Input.Error.Generic")) {
                locale.set("Editor.Input.Error.Generic", "<gray>输入内容无效！</gray>");
                changed = true;
            }

            if (changed || installed) {
                if (changed) locale.saveChanges();
                nightcore.getLangRegistry().loadLocale();
                plugin.info(installed ? "已为 nightcore 加载简体中文语言。" : "已更新 nightcore 简体中文帮助格式。");
            }
            loadLegacyCoreLocale(locale);
        }
        catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "无法安装 nightcore 简体中文语言文件。", exception);
        }
    }

    @SuppressWarnings("deprecation")
    private static void loadLegacyCoreLocale(@NotNull FileConfig locale) {
        su.nightexpress.nightcore.core.CoreLang.EDITOR_ACTION_EXIT.load(locale);
        su.nightexpress.nightcore.core.CoreLang.DIALOG_INFO_EXIT.load(locale);
        su.nightexpress.nightcore.core.CoreLang.EDITOR_INPUT_HEADER_MAIN.load(locale);
        su.nightexpress.nightcore.core.CoreLang.EDITOR_INPUT_HEADER_ERROR.load(locale);
        su.nightexpress.nightcore.core.CoreLang.EDITOR_INPUT_ERROR_NOT_INTEGER.load(locale);
        su.nightexpress.nightcore.core.CoreLang.EDITOR_INPUT_ERROR_GENERIC.load(locale);
    }
}
