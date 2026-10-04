package net.bumblebee.claysoldiers.structure;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The metadata of one kingdom structure: what it is and where its geometry is. The geometry is a vanilla structure
 * file ({@code .nbt}, editable with structure blocks); this is the JSON next to it, in
 * {@code data/<namespace>/csr/structures/<name>.json}. See {@code docs/STRUCTURES.md}.
 *
 * @param id           unique id, e.g. {@code csr:test_hut}
 * @param name         display name
 * @param design       {@link #READY} (has geometry) or {@link #TBD} (placeholder, no geometry yet)
 * @param tier         kingdom tier it belongs to, 1 to {@code Tuning.TIERS}
 * @param zone         one of {@link #ZONES}
 * @param role         one of {@link #ROLES}
 * @param footprint    size x, y, z in blocks (must match the structure file), {@code null} for TBD
 * @param cost         materials (item id to count), or {@code null} for "auto": counted from the structure the way
 *                     builders count it
 * @param variantGroup structures that are variants of each other share this, or {@code null}
 * @param palette      material palette name (e.g. {@code oak}), or {@code null}
 * @param ruin         the structure file of its ruined version, or {@code null}
 * @param structure    the structure file location, e.g. {@code csr:kingdom/test_hut} for
 *                     {@code data/csr/structure/kingdom/test_hut.nbt}; {@code null} for TBD
 * @param source       the metadata file it was read from (for messages)
 */
public record StructureMeta(ResourceLocation id, String name, String design, int tier, String zone, String role,
                            @Nullable Vec3i footprint, @Nullable Map<ResourceLocation, Integer> cost,
                            @Nullable String variantGroup, @Nullable String palette, @Nullable ResourceLocation ruin,
                            @Nullable ResourceLocation structure, ResourceLocation source) {
    public static final String READY = "ready";
    public static final String TBD = "TBD";
    /** The kingdom zones a structure can belong to (the soft rings of D3). */
    public static final List<String> ZONES = List.of("keep", "inner", "wall", "outer");
    /** What a structure is for. Add a role here and in docs/STRUCTURES.md when a new kind of building needs one. */
    public static final List<String> ROLES = List.of("keep", "house", "farm", "resource", "storage", "defense", "gate",
            "military", "workshop", "market", "religious", "health", "food", "utility", "decoration");

    public boolean isReady() {
        return READY.equals(design);
    }

    /**
     * Reads one metadata file. Throws {@link IllegalArgumentException} with a readable reason when a field is missing or
     * has the wrong type (legal values are checked later by {@link StructureRegistry#validate}).
     */
    public static StructureMeta fromJson(ResourceLocation source, JsonElement element) {
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        JsonObject json = element.getAsJsonObject();
        ResourceLocation id = location(json, "id", true);
        String design = string(json, "design", true);
        Vec3i footprint = null;
        if (json.has("footprint")) {
            var array = json.getAsJsonArray("footprint");
            if (array.size() != 3) {
                throw new IllegalArgumentException("\"footprint\" needs 3 numbers [x, y, z]");
            }
            footprint = new Vec3i(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
        }
        Map<ResourceLocation, Integer> cost = null;
        if (json.has("cost") && !(json.get("cost").isJsonPrimitive() && "auto".equals(json.get("cost").getAsString()))) {
            if (!json.get("cost").isJsonObject()) {
                throw new IllegalArgumentException("\"cost\" must be \"auto\" or an object of item id to count");
            }
            cost = new LinkedHashMap<>();
            for (var entry : json.getAsJsonObject("cost").entrySet()) {
                ResourceLocation item = ResourceLocation.tryParse(entry.getKey());
                if (item == null) {
                    throw new IllegalArgumentException("\"cost\" has a bad item id " + entry.getKey());
                }
                cost.put(item, entry.getValue().getAsInt());
            }
        }
        if (!json.has("tier") || !json.get("tier").isJsonPrimitive() || !json.get("tier").getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("\"tier\" must be a number");
        }
        return new StructureMeta(id, string(json, "name", true), design, json.get("tier").getAsInt(),
                string(json, "zone", true), string(json, "role", true), footprint, cost,
                string(json, "variant_group", false), string(json, "palette", false), location(json, "ruin", false),
                location(json, "structure", false), source);
    }

    @Nullable
    private static String string(JsonObject json, String field, boolean required) {
        if (!json.has(field)) {
            if (required) {
                throw new IllegalArgumentException("missing \"" + field + "\"");
            }
            return null;
        }
        if (!json.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("\"" + field + "\" must be a string");
        }
        return json.get(field).getAsString();
    }

    @Nullable
    private static ResourceLocation location(JsonObject json, String field, boolean required) {
        String text = string(json, field, required);
        if (text == null) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(text);
        if (location == null) {
            throw new IllegalArgumentException("\"" + field + "\" is not a valid id: " + text);
        }
        return location;
    }
}
