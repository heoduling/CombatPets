package su.nightexpress.combatpets.shop;

public final class ShopAccessPolicyCheck {

    private ShopAccessPolicyCheck() {
    }

    public static void main(String[] args) {
        assertAccess(true, "common", false, false, false, "player common tier");
        assertAccess(false, "rare", false, true, true, "player command must stay common-only");
        assertAccess(false, "mythic", true, false, true, "pro shop without admin root");
        assertAccess(false, "unique", true, true, false, "pro shop without proshop permission");
        assertAccess(true, "rare", true, true, true, "authorized pro shop");

        if (ShopManager.PLAYER_SHOP_TIER_SLOT != 13) {
            throw new AssertionError("player tier icon is not in center slot 13");
        }
    }

    private static void assertAccess(boolean expected, String tierId, boolean proShop,
                                     boolean admin, boolean proShopPermission, String label) {
        boolean actual = ShopManager.canAccessTier(tierId, proShop, admin, proShopPermission);
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
    }
}
