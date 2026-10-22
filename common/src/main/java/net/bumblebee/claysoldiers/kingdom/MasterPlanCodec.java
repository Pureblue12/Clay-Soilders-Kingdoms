package net.bumblebee.claysoldiers.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.kingdom.layout.Side;

import java.util.List;
import java.util.Locale;

/**
 * The saved format of a kingdom's master plan (stage 6, kingdoms data version 3). Cells are {@code [x, z]}, rectangles
 * {@code [minX, maxX, minZ, maxZ]}, sides lower-case names.
 */
public final class MasterPlanCodec {
    private MasterPlanCodec() {
    }

    static final Codec<Cell> CELL = Codec.INT.listOf().comapFlatMap(list -> list.size() == 2
                    ? DataResult.success(new Cell(list.get(0), list.get(1)))
                    : DataResult.error(() -> "a cell is [x, z], not " + list),
            cell -> List.of(cell.x(), cell.z()));
    static final Codec<Rect> RECT = Codec.INT.listOf().comapFlatMap(list -> list.size() == 4 && list.get(0) <= list.get(1) && list.get(2) <= list.get(3)
                    ? DataResult.success(new Rect(list.get(0), list.get(1), list.get(2), list.get(3)))
                    : DataResult.error(() -> "a rectangle is [minX, maxX, minZ, maxZ], not " + list),
            rect -> List.of(rect.minX(), rect.maxX(), rect.minZ(), rect.maxZ()));
    static final Codec<Side> SIDE = Codec.STRING.xmap(name -> Side.valueOf(name.toUpperCase(Locale.ROOT)), Side::lowerName);
    static final Codec<MasterPlan.Kind> KIND = Codec.STRING.xmap(name -> MasterPlan.Kind.valueOf(name.toUpperCase(Locale.ROOT)),
            MasterPlan.Kind::lowerName);
    static final Codec<MasterPlan.Plot> PLOT = RecordCodecBuilder.create(in -> in.group(
            Codec.STRING.fieldOf("key").forGetter(MasterPlan.Plot::key),
            KIND.fieldOf("kind").forGetter(MasterPlan.Plot::kind),
            Codec.BOOL.fieldOf("outer").forGetter(MasterPlan.Plot::outer),
            SIDE.fieldOf("facing").forGetter(MasterPlan.Plot::facing),
            Codec.INT.fieldOf("easel_x").forGetter(MasterPlan.Plot::easelX),
            Codec.INT.fieldOf("easel_z").forGetter(MasterPlan.Plot::easelZ),
            RECT.fieldOf("rect").forGetter(MasterPlan.Plot::rect)
    ).apply(in, MasterPlan.Plot::new));
    static final Codec<MasterPlan.Road> ROAD = RecordCodecBuilder.create(in -> in.group(
            CELL.fieldOf("from").forGetter(MasterPlan.Road::from),
            CELL.fieldOf("to").forGetter(MasterPlan.Road::to),
            Codec.INT.fieldOf("width").forGetter(MasterPlan.Road::width)
    ).apply(in, MasterPlan.Road::new));
    static final Codec<MasterPlan.Gate> GATE = RecordCodecBuilder.create(in -> in.group(
            CELL.fieldOf("at").forGetter(MasterPlan.Gate::at),
            SIDE.fieldOf("side").forGetter(MasterPlan.Gate::side)
    ).apply(in, MasterPlan.Gate::new));
    public static final Codec<MasterPlan> CODEC = RecordCodecBuilder.create(in -> in.group(
            CELL.fieldOf("center").forGetter(MasterPlan::center),
            RECT.fieldOf("keep").forGetter(MasterPlan::keep),
            SIDE.fieldOf("front").forGetter(MasterPlan::front),
            PLOT.listOf().fieldOf("plots").forGetter(MasterPlan::plots),
            ROAD.listOf().fieldOf("roads").forGetter(MasterPlan::roads),
            CELL.listOf().fieldOf("wall_corners").forGetter(MasterPlan::wallCorners),
            GATE.listOf().fieldOf("gates").forGetter(MasterPlan::gates),
            Codec.INT.fieldOf("claim_radius").forGetter(MasterPlan::claimRadius)
    ).apply(in, MasterPlan::new));
}
