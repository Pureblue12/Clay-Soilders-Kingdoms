package net.bumblebee.claysoldiers.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * One building of a kingdom (no building behavior yet: stage 4 fills this list).
 *
 * @param structure the structure registry id, e.g. {@code csr:keep}
 * @param pos       where its easel stands (the builders' reference point)
 * @param facing    the build direction (like {@code /csr build})
 * @param state     how far it is
 */
public record KingdomBuilding(ResourceLocation structure, BlockPos pos, Direction facing, State state) {
    public static final Codec<KingdomBuilding> CODEC = RecordCodecBuilder.create(in -> in.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(KingdomBuilding::structure),
            BlockPos.CODEC.fieldOf("pos").forGetter(KingdomBuilding::pos),
            Direction.CODEC.fieldOf("facing").forGetter(KingdomBuilding::facing),
            State.CODEC.fieldOf("state").forGetter(KingdomBuilding::state)
    ).apply(in, KingdomBuilding::new));

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
