package net.bumblebee.claysoldiers.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;

/**
 * One building of a kingdom. Stage 4 adds the keep ({@code KingdomKeep}); later stages add the rest.
 *
 * @param structure the structure registry id, e.g. {@code csr:keep}
 * @param pos       where its easel stands (the builders' reference point)
 * @param facing    the build direction (like {@code /csr build})
 * @param state     how far it is
 * @param level     the structure's detail level being built (1 when not saved: data version 1)
 */
public record KingdomBuilding(ResourceLocation structure, BlockPos pos, Direction facing, State state, int level) {
    public static final Codec<KingdomBuilding> CODEC = RecordCodecBuilder.create(in -> in.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(KingdomBuilding::structure),
            BlockPos.CODEC.fieldOf("pos").forGetter(KingdomBuilding::pos),
            Direction.CODEC.fieldOf("facing").forGetter(KingdomBuilding::facing),
            State.CODEC.fieldOf("state").forGetter(KingdomBuilding::state),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("level", 1).forGetter(KingdomBuilding::level)
    ).apply(in, KingdomBuilding::new));

    /** A building of detail level 1. */
    public KingdomBuilding(ResourceLocation structure, BlockPos pos, Direction facing, State state) {
        this(structure, pos, facing, state, 1);
    }

    /** The same building in another state. */
    public KingdomBuilding withState(State state) {
        return new KingdomBuilding(structure, pos, facing, state, level);
    }

    public enum State implements StringRepresentable {
        PLANNED("planned"),
        BUILDING("building"),
        BUILT("built");

        public static final StringRepresentable.EnumCodec<State> CODEC = StringRepresentable.fromEnum(State::values);
        private final String name;

        State(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
