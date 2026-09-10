package su.nightexpress.combatpets.wardrobe.handler;

import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.combatpets.wardrobe.util.VariantHandler;
import su.nightexpress.nightcore.util.StringUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Narrow compatibility adapter for entity variants whose Bukkit API shape
 * differs between the older modules and 26.2.
 */
public final class ReflectiveVariantHandler extends VariantHandler<String> {

    private final Map<String, String> labels;
    private final Class<?>            entityClass;
    private final Method              getter;
    private final Method              setter;

    public ReflectiveVariantHandler(@NotNull String entityClassName,
                                    @NotNull String getterName,
                                    @NotNull String setterName,
                                    @NotNull String... valueLabels) {
        if (valueLabels.length % 2 != 0) {
            throw new IllegalArgumentException("Variant values and labels must be supplied in pairs.");
        }

        this.labels = new LinkedHashMap<>();
        for (int index = 0; index < valueLabels.length; index += 2) {
            this.labels.put(normalize(valueLabels[index]), valueLabels[index + 1]);
        }

        Class<?> resolvedClass = null;
        Method resolvedGetter = null;
        Method resolvedSetter = null;
        try {
            resolvedClass = Class.forName(entityClassName);
            resolvedGetter = resolvedClass.getMethod(getterName);
            resolvedSetter = resolvedClass.getMethod(setterName, resolvedGetter.getReturnType());
        }
        catch (ReflectiveOperationException | LinkageError ignored) {
            // Expected when this plugin is loaded on an older supported server.
        }

        this.entityClass = resolvedClass;
        this.getter = resolvedGetter;
        this.setter = resolvedSetter;
    }

    public boolean isAvailable() {
        return this.entityClass != null && this.getter != null && this.setter != null;
    }

    @Override
    @NotNull
    public List<String> values() {
        return new ArrayList<>(this.labels.keySet());
    }

    @Override
    @Nullable
    public String parse(@NotNull String raw) {
        String value = normalize(raw);
        return value.isEmpty() ? null : value;
    }

    @Override
    @Nullable
    public String read(@NotNull LivingEntity entity) {
        if (!this.isAvailable() || !this.entityClass.isInstance(entity)) return null;

        try {
            Object value = this.getter.invoke(entity);
            if (value instanceof Keyed keyed) return keyed.getKey().toString();
            if (value instanceof Enum<?> enumeration) return normalize(enumeration.name());
            return value == null ? null : normalize(value.toString());
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    @Override
    @NotNull
    public String getLocalized(@NotNull String value) {
        String key = withoutNamespace(normalize(value));
        return this.labels.getOrDefault(key, StringUtil.capitalizeUnderscored(key));
    }

    @Override
    @NotNull
    public String getRaw(@NotNull String value) {
        return normalize(value);
    }

    @Override
    public boolean apply(@NotNull LivingEntity entity, @Nullable String value) {
        if (value == null || !this.isAvailable() || !this.entityClass.isInstance(entity)) return false;

        Object resolved = this.resolve(value, this.setter.getParameterTypes()[0]);
        if (resolved == null) return false;

        try {
            this.setter.invoke(entity, resolved);
            return true;
        }
        catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    @Override
    public boolean alreadyHas(@NotNull LivingEntity entity, @Nullable String value) {
        return Objects.equals(comparable(this.read(entity)), comparable(value));
    }

    @Nullable
    private Object resolve(@NotNull String raw, @NotNull Class<?> valueType) {
        String normalized = normalize(raw);
        if (valueType == boolean.class || valueType == Boolean.class) {
            if (!normalized.equals("true") && !normalized.equals("false")) return null;
            return Boolean.parseBoolean(normalized);
        }

        String constantName = withoutNamespace(normalized).toUpperCase(Locale.ROOT);
        if (valueType.isEnum()) {
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object value = Enum.valueOf((Class<? extends Enum>) valueType, constantName);
                return value;
            }
            catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        if (Keyed.class.isAssignableFrom(valueType)) {
            try {
                NamespacedKey key = NamespacedKey.fromString(normalized.contains(":") ? normalized : "minecraft:" + normalized);
                if (key != null) {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    Registry<? extends Keyed> registry = Bukkit.getRegistry((Class) valueType);
                    if (registry != null) {
                        Keyed value = registry.get(key);
                        if (value != null) return value;
                    }
                }
            }
            catch (RuntimeException ignored) {
                // Invalid old data and compatibility types without a Bukkit registry are ignored safely.
            }
        }

        try {
            Field field = valueType.getField(constantName);
            if (Modifier.isStatic(field.getModifiers())) return field.get(null);
        }
        catch (ReflectiveOperationException ignored) {
        }

        try {
            Method valueOf = valueType.getMethod("valueOf", String.class);
            if (Modifier.isStatic(valueOf.getModifiers())) return valueOf.invoke(null, constantName);
        }
        catch (ReflectiveOperationException ignored) {
        }

        return null;
    }

    @NotNull
    private static String normalize(@NotNull String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Nullable
    private static String comparable(@Nullable String value) {
        return value == null ? null : withoutNamespace(normalize(value));
    }

    @NotNull
    private static String withoutNamespace(@NotNull String value) {
        int split = value.indexOf(':');
        return split < 0 ? value : value.substring(split + 1);
    }
}
