package su.nightexpress.combatpets.item;

import su.nightexpress.combatpets.api.item.ItemType;

import java.util.Base64;
import java.util.Map;

public final class EggRecoveryDataCheck {

    private EggRecoveryDataCheck() {
    }

    public static void main(String[] args) {
        EggRecoveryData normal = new EggRecoveryData(
            ItemType.EGG,
            "sniffer",
            "common",
            Map.of("size", "3", "cow_variant", "warm")
        );
        assertEquals(normal, EggRecoveryData.decode(normal.encode()).orElseThrow(), "normal egg round trip");

        EggRecoveryData random = new EggRecoveryData(ItemType.MYSTERY_EGG, "", "", Map.of());
        assertEquals(random, EggRecoveryData.decode(random.encode()).orElseThrow(), "random mystery egg round trip");

        if (EggRecoveryData.decode("not-base64!").isPresent()) {
            throw new AssertionError("malformed recovery payload was accepted");
        }

        byte[] trailing = Base64.getUrlDecoder().decode(normal.encode());
        byte[] changed = java.util.Arrays.copyOf(trailing, trailing.length + 1);
        if (EggRecoveryData.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(changed)).isPresent()) {
            throw new AssertionError("payload with trailing data was accepted");
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
