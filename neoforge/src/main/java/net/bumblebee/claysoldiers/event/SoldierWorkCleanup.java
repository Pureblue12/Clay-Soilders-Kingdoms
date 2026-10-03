package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/**
 * Stops a clay soldier's active work goal when the soldier leaves the level (chunk unload, discard,
 * dimension change), so goals can release shared state such as blueprint requests and dig progress.
 * <p>
 * This used to happen in {@code addAdditionalSaveData}, which also runs on every autosave and
 * cancelled work in progress. Deaths are skipped because {@code AbstractClaySoldierEntity.die} already does this.
 */
@EventBusSubscriber(modid = ClaySoldiersCommon.MOD_ID)
public final class SoldierWorkCleanup {
    private SoldierWorkCleanup() {
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof AbstractClaySoldierEntity soldier && soldier.getRemovalReason() != Entity.RemovalReason.KILLED) {
            soldier.getWorkSelector().resetGoal();
        }
    }
}
