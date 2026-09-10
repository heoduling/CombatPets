package su.nightexpress.combatpets.hook.impl;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;

import java.lang.reflect.Proxy;

public final class LandsHookPolicyCheck {

    private LandsHookPolicyCheck() {
    }

    public static void main(String[] args) {
        assertType(LandsHook.AttackTargetType.PLAYER, entity(Player.class, EntityType.PLAYER), "player target");
        assertType(LandsHook.AttackTargetType.MONSTER, entity(Enemy.class, EntityType.SLIME), "enemy target");
        assertType(LandsHook.AttackTargetType.MONSTER, entity(IronGolem.class, EntityType.IRON_GOLEM), "iron golem target");
        assertType(LandsHook.AttackTargetType.MONSTER, entity(Snowman.class, EntityType.SNOW_GOLEM), "snow golem target");
        assertType(LandsHook.AttackTargetType.ANIMAL, entity(LivingEntity.class, EntityType.COW), "animal target");
        assertType(LandsHook.AttackTargetType.OTHER, entity(ArmorStand.class, EntityType.ARMOR_STAND), "armor stand target");
    }

    private static LivingEntity entity(Class<? extends LivingEntity> type, EntityType entityType) {
        return (LivingEntity) Proxy.newProxyInstance(
            LandsHookPolicyCheck.class.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, args) -> method.getName().equals("getType") ? entityType : null
        );
    }

    private static void assertType(LandsHook.AttackTargetType expected, LivingEntity target, String label) {
        LandsHook.AttackTargetType actual = LandsHook.getAttackTargetType(target);
        if (actual != expected) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }
}
