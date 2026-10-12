package net.bumblebee.claysoldiers.structure;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.plan.BuildPlans;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.init.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.List;

/**
 * Turns a kingdom structure's file (one detail level) into an easel blueprint, so builders build exactly that file
 * through the normal blueprint pipeline (same block order and blacklist as the blueprints of the {@code blueprint}
 * registry, or the order of the structure's pre-baked build plan when it has one, D80). The blueprint lives in memory only: like the scaffold test structures, an easel holding it keeps its
 * plan across a reload but not its name.
 */
public final class StructureBlueprints {
    private StructureBlueprints() {
    }

    public static BlueprintData blueprint(ServerLevel level, StructureMeta meta, int detailLevel) {
        StructureTemplate template = StructurePaster.template(level, meta, detailLevel);
        CompoundTag tag = template.save(new CompoundTag());
        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        List<Holder<Block>> blacklist = blocks.get(ModTags.Blocks.BLUEPRINT_BLACK_LISTED)
                .map(set -> set.stream().toList()).orElse(List.of());
        var blueprintTemplate = ImmutableTemplate.create(blocks, tag, blacklist);
        // The structure's pre-baked build plan (D80), when tools/build_plan.py made one: its order and standing spots.
        ResourceLocation file = meta.levelFile(detailLevel);
        if (file != null) {
            var plan = BuildPlans.load(level.getServer().getResourceManager(), file);
            if (plan.isPresent()) {
                blueprintTemplate = blueprintTemplate.withPlan(plan.get());
            }
        }
        String name = meta.id().getPath() + "_l" + detailLevel;
        BlueprintData data = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "structure/" + name),
                "structure." + name, 0);
        data.bindStructure(blueprintTemplate);
        return data;
    }
}
