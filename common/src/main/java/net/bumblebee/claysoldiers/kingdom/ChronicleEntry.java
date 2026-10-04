package net.bumblebee.claysoldiers.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One line of a kingdom's chronicle, written for a human ("The kingdom of Red was founded ...").
 *
 * @param gameTime the server's game time (ticks since the world was created) when it happened
 * @param text     the line
 */
public record ChronicleEntry(long gameTime, String text) {
    public static final Codec<ChronicleEntry> CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.LONG.fieldOf("time").forGetter(ChronicleEntry::gameTime),
            Codec.STRING.fieldOf("text").forGetter(ChronicleEntry::text)
    ).apply(in, ChronicleEntry::new));

    /**
     * Game time as "Day 3, 14:30" (day 1 starts at 06:00, like Minecraft's clock).
     */
    public static String formatTime(long gameTime) {
        long day = gameTime / 24000 + 1;
        long minutesOfDay = ((gameTime % 24000) * 60 / 1000 + 6 * 60) % (24 * 60);
        return "Day %d, %02d:%02d".formatted(day, minutesOfDay / 60, minutesOfDay % 60);
    }
}
