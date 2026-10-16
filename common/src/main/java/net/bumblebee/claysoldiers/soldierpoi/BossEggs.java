package net.bumblebee.claysoldiers.soldierpoi;

import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The original mod turns a soldier that picks up an egg into a random clay boss that attacks everyone (the egg's
 * soldier POI). Chickens lay eggs anywhere, so in a kingdom world a boss turns up by itself and kills the king (the
 * owner's stage 4 test, D94). With {@code soldier.boss_from_eggs} 0 an egg on the ground is no POI for soldiers.
 */
public final class BossEggs {
    private BossEggs() {
    }

    /** {@code poi} for {@code stack} lying on the ground, or none for an egg while bosses from eggs are off. */
    @Nullable
    public static SoldierPoi filter(ItemStack stack, @Nullable SoldierPoi poi) {
        if (poi != null && stack.is(Items.EGG) && Tuning.SOLDIER_BOSS_FROM_EGGS.get() == 0) {
            return null;
        }
        return poi;
    }
}
