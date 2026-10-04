package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.kingdom.ChronicleEntry;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomSavedData;
import net.bumblebee.claysoldiers.kingdom.KingdomStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for the saved kingdom data ({@link KingdomSavedData}): every field survives encoding, and a real save to disk.
 * Each asserts the CORRECT behavior. Tests that touch the world's kingdom data use the "kingdom_data" batch and clean up.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomDataGameTests {
    static final ResourceLocation BLUE = ResourceLocation.fromNamespaceAndPath("csr", "blue");

    /**
     * Encoding then decoding a kingdom with every field set to a non-default value gives an equal kingdom.
     */
    @GameTest(template = PLATFORM)
    public static void roundTripKeepsEveryField(GameTestHelper helper) {
        Kingdom kingdom = fullKingdom();
        Tag encoded = KingdomSavedData.encode(kingdom);
        Kingdom decoded = KingdomSavedData.decode(encoded);
        helper.assertTrue(decoded.id().equals(kingdom.id()), "id");
        helper.assertTrue(decoded.team().equals(kingdom.team()), "team");
        helper.assertTrue(decoded.dimension().equals(kingdom.dimension()), "dimension");
        helper.assertTrue(decoded.center().equals(kingdom.center()), "center");
        helper.assertTrue(decoded.claimRadius() == kingdom.claimRadius(), "claim radius");
        helper.assertTrue(decoded.king().equals(kingdom.king()), "king: " + decoded.king() + " vs " + kingdom.king());
        helper.assertTrue(decoded.status() == kingdom.status(), "status");
        helper.assertTrue(decoded.tier() == kingdom.tier(), "tier");
        helper.assertTrue(decoded.stockpile().equals(kingdom.stockpile()), "stockpile: " + decoded.stockpile());
        helper.assertTrue(decoded.buildings().equals(kingdom.buildings()), "buildings: " + decoded.buildings());
        helper.assertTrue(decoded.census().equals(kingdom.census()), "census: " + decoded.census());
        helper.assertTrue(decoded.renown() == kingdom.renown(), "renown");
        helper.assertTrue(decoded.foundedTick() == kingdom.foundedTick(), "founded tick");
        helper.assertTrue(decoded.chronicle().equals(kingdom.chronicle()), "chronicle: " + decoded.chronicle());
        helper.assertTrue(decoded.equals(kingdom), "Decoded kingdom differs: " + decoded);
        helper.succeed();
    }

    /**
     * A real server save writes the kingdom (with the data version) to the world folder, and reading the file back
     * gives the same kingdom.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_data")
    public static void serverSaveThenReloadKeepsKingdom(GameTestHelper helper) throws IOException {
        MinecraftServer server = helper.getLevel().getServer();
        KingdomSavedData data = KingdomSavedData.get(server);
        data.remove(BLUE);
        Kingdom kingdom = fullKingdom();
        try {
            data.add(kingdom);
            server.overworld().getDataStorage().save();
            IOUtilities.waitUntilIOWorkerComplete(); // NeoForge writes saved data in the background

            Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(KingdomSavedData.NAME + ".dat");
            CompoundTag saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data");
            helper.assertTrue(saved.getInt("csr_data_version") == KingdomSavedData.DATA_VERSION,
                    "The saved file has no csr_data_version " + KingdomSavedData.DATA_VERSION + ": " + saved.getAllKeys());
            KingdomSavedData reloaded = KingdomSavedData.load(saved, server.registryAccess());
            Optional<Kingdom> back = reloaded.byTeam(BLUE);
            helper.assertTrue(back.isPresent(), "The saved file has no kingdom for " + BLUE);
            helper.assertTrue(back.get().equals(kingdom), "The kingdom read back differs: " + back.get());
        } finally {
            data.remove(BLUE);
            server.overworld().getDataStorage().save();
        }
        helper.succeed();
    }

    /**
     * Data written by a newer format version is not thrown away: this version keeps it as it was when saving.
     */
    @GameTest(template = PLATFORM)
    public static void newerFormatIsKeptOnSave(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CompoundTag future = new CompoundTag();
        future.putInt("csr_data_version", KingdomSavedData.DATA_VERSION + 1);
        future.putString("something_new", "keep me");
        KingdomSavedData loaded = KingdomSavedData.load(future, server.registryAccess());
        CompoundTag saved = loaded.save(new CompoundTag(), server.registryAccess());
        helper.assertTrue("keep me".equals(saved.getString("something_new")) && saved.getInt("csr_data_version") == KingdomSavedData.DATA_VERSION + 1,
                "Newer data was not kept: " + saved);
        helper.succeed();
    }

    /**
     * A kingdom with every field set to something other than its default.
     */
    static Kingdom fullKingdom() {
        return new Kingdom(UUID.fromString("00000000-0000-0000-0000-00000000c0de"), BLUE, Level.OVERWORLD,
                new BlockPos(100, 64, -200), 40,
                new Kingdom.King(UUID.fromString("00000000-0000-0000-0000-00000000beef"), Optional.of("Arthur"), new BlockPos(103, 65, -198), 5000L),
                KingdomStatus.RUINED, 3,
                Map.of(ResourceLocation.withDefaultNamespace("oak_log"), 12, ResourceLocation.withDefaultNamespace("wheat"), 30),
                List.of(new KingdomBuilding(ResourceLocation.fromNamespaceAndPath("csr", "keep"), new BlockPos(100, 64, -195), Direction.SOUTH, KingdomBuilding.State.BUILT),
                        new KingdomBuilding(ResourceLocation.fromNamespaceAndPath("csr", "cottage"), new BlockPos(110, 64, -190), Direction.EAST, KingdomBuilding.State.PLANNED)),
                new Kingdom.Census(7, 4800L), 42, 1200L,
                List.of(new ChronicleEntry(1200L, "The kingdom of Blue was founded."), new ChronicleEntry(5000L, "King Arthur has died.")));
    }
}
