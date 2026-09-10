package su.nightexpress.combatpets.item;

import org.jetbrains.annotations.NotNull;
import su.nightexpress.combatpets.api.item.ItemType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.Locale;

record EggRecoveryData(@NotNull ItemType itemType,
                       @NotNull String templateId,
                       @NotNull String tierId,
                       @NotNull Map<String, String> accessories) {

    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENCODED_LENGTH = 12_000;
    private static final int MAX_ACCESSORIES = 64;

    EggRecoveryData {
        if (itemType != ItemType.EGG && itemType != ItemType.MYSTERY_EGG) {
            throw new IllegalArgumentException("Unsupported recovery item type: " + itemType);
        }

        templateId = templateId.trim().toLowerCase(Locale.ROOT);
        tierId = tierId.trim().toLowerCase(Locale.ROOT);
        accessories = Collections.unmodifiableMap(new TreeMap<>(accessories));
    }

    @NotNull
    String encode() {
        if (this.accessories.size() > MAX_ACCESSORIES) {
            throw new IllegalArgumentException("Too many pet accessories: " + this.accessories.size());
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeByte(FORMAT_VERSION);
                output.writeByte(this.itemType == ItemType.EGG ? 1 : 2);
                output.writeUTF(this.templateId);
                output.writeUTF(this.tierId);
                output.writeByte(this.accessories.size());
                for (Map.Entry<String, String> entry : this.accessories.entrySet()) {
                    output.writeUTF(entry.getKey());
                    output.writeUTF(entry.getValue());
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not encode pet egg recovery data", exception);
        }
    }

    @NotNull
    static Optional<EggRecoveryData> decode(@NotNull String encoded) {
        if (encoded.isEmpty() || encoded.length() > MAX_ENCODED_LENGTH) return Optional.empty();

        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readUnsignedByte() != FORMAT_VERSION) return Optional.empty();

                ItemType itemType = switch (input.readUnsignedByte()) {
                    case 1 -> ItemType.EGG;
                    case 2 -> ItemType.MYSTERY_EGG;
                    default -> null;
                };
                if (itemType == null) return Optional.empty();

                String templateId = input.readUTF();
                String tierId = input.readUTF();
                if (!isShort(templateId, 128) || !isShort(tierId, 128)) return Optional.empty();
                if (itemType == ItemType.EGG && (templateId.isBlank() || tierId.isBlank())) return Optional.empty();
                if (itemType == ItemType.MYSTERY_EGG && !tierId.isEmpty()) return Optional.empty();

                int count = input.readUnsignedByte();
                if (count > MAX_ACCESSORIES) return Optional.empty();

                Map<String, String> accessories = new TreeMap<>();
                for (int index = 0; index < count; index++) {
                    String name = input.readUTF();
                    String value = input.readUTF();
                    if (!isShort(name, 128) || !isShort(value, 512) || name.isBlank() || value.isBlank()) {
                        return Optional.empty();
                    }
                    if (accessories.put(name, value) != null) return Optional.empty();
                }

                if (input.available() != 0) return Optional.empty();
                return Optional.of(new EggRecoveryData(itemType, templateId, tierId, accessories));
            }
        }
        catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean isShort(@NotNull String value, int maximum) {
        return value.length() <= maximum;
    }
}
