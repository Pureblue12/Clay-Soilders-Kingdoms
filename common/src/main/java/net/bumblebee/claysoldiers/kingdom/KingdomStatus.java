package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.util.StringRepresentable;

/**
 * Whether a kingdom lives. RUINED is set when its king dies (decay comes in a later stage).
 */
public enum KingdomStatus implements StringRepresentable {
    ACTIVE("active"),
    RUINED("ruined");

    public static final StringRepresentable.EnumCodec<KingdomStatus> CODEC = StringRepresentable.fromEnum(KingdomStatus::values);
    private final String name;

    KingdomStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
