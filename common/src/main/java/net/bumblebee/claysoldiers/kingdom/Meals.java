package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Stage 5: the builders of a building the kingdom pays for eat the wheat it paid ({@code economy.food_per_building}) and
 * are well fed while they build it: faster on their feet and tougher, for {@code economy.meal.ticks} at a time (the owner's
 * call 2026-10-16: food gives "some sort of status boost, more health, faster building"). Their normal pace stays as it is;
 * the meal is a bonus on top, earned by the kingdom's farmers. Vanilla effects, so players see the swirls and the effect
 * wears off by itself.
 */
public final class Meals {
    private Meals() {
    }

    /** Feeds {@code soldier} unless it is still well fed; returns whether it ate. */
    public static boolean feed(AbstractClaySoldierEntity soldier, Kingdom kingdom) {
        int ticks = Tuning.ECONOMY_MEAL_TICKS.get();
        int speed = Tuning.ECONOMY_MEAL_SPEED_LEVEL.get();
        int health = Tuning.ECONOMY_MEAL_HEALTH_LEVEL.get();
        if (ticks <= 0 || speed <= 0 && health <= 0 || isWellFed(soldier)) {
            return false;
        }
        if (speed > 0) {
            soldier.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, speed - 1));
        }
        if (health > 0) {
            soldier.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, ticks, health - 1));
            soldier.setHealth(soldier.getMaxHealth());
        }
        DebugLog.event(DebugCategory.ECONOMY, soldier, () -> "%s builder ate and is well fed for %d ticks (speed %d, health %d)"
                .formatted(kingdom.team(), ticks, speed, health));
        return true;
    }

    /** Whether the soldier still has a meal's effects. */
    public static boolean isWellFed(AbstractClaySoldierEntity soldier) {
        return soldier.hasEffect(MobEffects.MOVEMENT_SPEED) || soldier.hasEffect(MobEffects.HEALTH_BOOST);
    }
}
