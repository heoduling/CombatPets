package su.nightexpress.combatpets.config;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.PetsPlugin;
import su.nightexpress.nightcore.config.FileConfig;
import su.nightexpress.nightcore.util.text.NightMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads and checks the standalone pet-name word list.
 *
 * <p>The live set is replaced atomically on every manager load, so Folia
 * region threads only ever read an immutable snapshot.</p>
 */
public final class PetNameFilter {

    private static final String FILE_NAME = "forbidden-words.yml";
    private static final String LEGACY_PATH = "Pets.Name.ForbiddenWords";
    private static final Pattern FORMAT_TAG = Pattern.compile("<[^>]+>");
    private static final int CONFIG_VERSION = 2;
    private static final List<String> BUILT_IN_RESOURCES = List.of(
        "lexicon/combatpets-base.txt",
        "lexicon/sensitive-terror.txt",
        "lexicon/sensitive-reactionary.txt",
        "lexicon/sensitive-adult.txt",
        "lexicon/sensitive-adult-types.txt",
        "lexicon/sensitive-weapons.txt",
        "lexicon/sensitive-corruption.txt",
        "lexicon/sensitive-supplement.txt"
    );

    private final PetsPlugin plugin;
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public PetNameFilter(@NotNull PetsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FileConfig config = FileConfig.loadOrExtract(this.plugin, FILE_NAME);
        Set<String> builtInWords = this.loadBuiltInWords();
        Set<String> configuredWords = migrateStandaloneConfig(config, builtInWords);

        // Preserve server-specific additions from the old main-config key.
        // The old three-letter default "ass" is intentionally not migrated:
        // substring matching made harmless names such as "class" fail.
        FileConfig mainConfig = this.plugin.getConfig();
        if (mainConfig.contains(LEGACY_PATH)) {
            mainConfig.getStringSet(LEGACY_PATH).stream()
                .filter(word -> !word.equalsIgnoreCase("ass"))
                .filter(word -> !builtInWords.contains(normalize(word)))
                .forEach(configuredWords::add);
            mainConfig.remove(LEGACY_PATH);
            mainConfig.saveChanges();
        }

        config.set("ForbiddenWords", configuredWords);
        this.addComments(config);
        config.saveChanges();

        LinkedHashSet<String> forbidden = new LinkedHashSet<>();
        if (config.getBoolean("Use_BuiltIn_Word_List")) forbidden.addAll(builtInWords);
        configuredWords.stream().map(PetNameFilter::normalize).filter(word -> !word.isEmpty()).forEach(forbidden::add);

        LinkedHashSet<String> allowed = new LinkedHashSet<>();
        config.getStringSet("AllowedWords").stream()
            .map(PetNameFilter::normalize)
            .filter(word -> !word.isEmpty())
            .forEach(allowed::add);
        this.snapshot = new Snapshot(Set.copyOf(forbidden), Set.copyOf(allowed));
    }

    @NotNull
    static Set<String> migrateStandaloneConfig(@NotNull FileConfig config, @NotNull Set<String> builtInWords) {
        LinkedHashSet<String> configuredWords = new LinkedHashSet<>(config.getStringSet("ForbiddenWords"));
        if (!config.contains("Use_BuiltIn_Word_List")) config.set("Use_BuiltIn_Word_List", true);
        if (!config.contains("AllowedWords")) config.set("AllowedWords", Set.of());

        // Version 1 stored the small bundled seed directly in the YAML. Move
        // those entries into the JAR-backed list so upgraded configs stay small,
        // while every server-specific addition remains untouched.
        if (config.getInt("ConfigVersion") < CONFIG_VERSION) {
            configuredWords.removeIf(word -> builtInWords.contains(normalize(word)));
            config.set("ConfigVersion", CONFIG_VERSION);
        }
        config.set("ForbiddenWords", configuredWords);
        return configuredWords;
    }

    public void clear() {
        this.snapshot = Snapshot.EMPTY;
    }

    public boolean isForbidden(@NotNull String name) {
        Snapshot current = this.snapshot;
        return isForbidden(name, current.forbiddenWords(), current.allowedNames());
    }

    static boolean isForbidden(@NotNull String name, @NotNull Set<String> forbiddenWords, @NotNull Set<String> allowedNames) {
        String normalizedName = normalize(name);
        if (normalizedName.isEmpty() || allowedNames.contains(normalizedName)) return false;
        return forbiddenWords.stream().anyMatch(normalizedName::contains);
    }

    @NotNull
    private Set<String> loadBuiltInWords() {
        LinkedHashSet<String> words = new LinkedHashSet<>();
        for (String resource : BUILT_IN_RESOURCES) {
            try (InputStream input = this.plugin.getResource(resource)) {
                if (input == null) throw new IllegalStateException("Missing built-in pet-name lexicon resource: " + resource);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(PetNameFilter::normalize)
                        .filter(word -> !word.isEmpty())
                        .forEach(words::add);
                }
            }
            catch (IOException exception) {
                throw new IllegalStateException("Could not read built-in pet-name lexicon resource: " + resource, exception);
            }
        }
        return Set.copyOf(words);
    }

    private void addComments(@NotNull FileConfig config) {
        config.setComments("ConfigVersion", "宠物名称词库配置版本，由插件自动升级，请勿手动修改。");
        config.setComments("Use_BuiltIn_Word_List",
            "是否启用插件内置的大型中文名称词库。",
            "内置词库随插件 JAR 更新，不需要把数千行词语写入本配置。"
        );
        config.setComments("AllowedWords",
            "完整宠物名称白名单。仅当规范化后的整个名称完全匹配时放行，避免白名单被拼接绕过。"
        );
        config.setComments("ForbiddenWords",
            "服务器自定义追加词库，每行填写一个词或短语。",
            "插件会忽略大小写、空格、常见符号、全角/半角及颜色格式后检查。",
            "保存后执行 /pets reload 即可生效。"
        );
    }

    @NotNull
    static String normalize(@NotNull String input) {
        String untagged = FORMAT_TAG.matcher(input).replaceAll("");
        String plain = Normalizer.normalize(NightMessage.stripAll(untagged), Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(plain.length());
        plain.codePoints().filter(Character::isLetterOrDigit).forEach(builder::appendCodePoint);
        return builder.toString();
    }

    private record Snapshot(Set<String> forbiddenWords, Set<String> allowedNames) {

        private static final Snapshot EMPTY = new Snapshot(Set.of(), Set.of());
    }
}
