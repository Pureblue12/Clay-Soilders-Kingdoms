package net.bumblebee.claysoldiers.blueprint.plan;

import com.google.gson.JsonParser;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.Optional;

/**
 * Finds the pre-baked build plan of a structure file (D82): {@code data/<namespace>/build_plan/<path>.json} for the
 * structure {@code <namespace>:<path>}, written by {@code tools/build_plan.py}. A structure without one builds as before.
 */
public final class BuildPlans {
    private BuildPlans() {
    }

    public static ResourceLocation planFile(ResourceLocation structure) {
        return ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), "build_plan/" + structure.getPath() + ".json");
    }

    public static Optional<BuildPlan> load(ResourceManager resources, ResourceLocation structure) {
        ResourceLocation file = planFile(structure);
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            DebugLog.event(DebugCategory.BUILD, null, "no build plan for %s", structure);
            return Optional.empty();
        }
        try (Reader reader = resource.get().openAsReader()) {
            BuildPlan plan = BuildPlan.fromJson(JsonParser.parseReader(reader).getAsJsonObject());
            if (plan == null) {
                DebugLog.event(DebugCategory.BUILD, null, "build plan %s has an unknown format, ignored", file);
            }
            return Optional.ofNullable(plan);
        } catch (Exception e) {
            DebugLog.event(DebugCategory.BUILD, null, "build plan %s unreadable (%s), ignored", file, e.getMessage());
            return Optional.empty();
        }
    }
}
