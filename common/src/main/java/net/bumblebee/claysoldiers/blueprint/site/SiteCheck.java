package net.bumblebee.claysoldiers.blueprint.site;

import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;

/**
 * Result of {@link BuildSitePlanner#check}: either a valid {@link BuildSite}, or a refusal with a reason.
 *
 * @param site       the site, when valid
 * @param reason     why the site was refused, when invalid
 * @param message    plain-language explanation of the refusal (empty when valid)
 * @param problemPos where the problem is, if there is one place
 */
public record SiteCheck(@Nullable BuildSite site, @Nullable Reason reason, String message, @Nullable BlockPos problemPos) {
    public enum Reason {
        UNKNOWN_BLUEPRINT,
        BLUEPRINTS_DISABLED,
        OUTSIDE_BUILD_HEIGHT,
        NOT_LOADED,
        EASEL_SPOT_TAKEN,
        NO_EASEL_SUPPORT,
        OBSTRUCTED,
        NO_GROUND,
        EASEL_NEARBY
    }

    public static SiteCheck valid(BuildSite site) {
        return new SiteCheck(site, null, "", null);
    }

    public static SiteCheck refused(Reason reason, String message, @Nullable BlockPos problemPos) {
        return new SiteCheck(null, reason, message, problemPos);
    }

    public boolean isValid() {
        return site != null;
    }
}
