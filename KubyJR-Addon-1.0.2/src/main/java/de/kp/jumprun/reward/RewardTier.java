package de.kp.jumprun.reward;

import java.util.List;

/**
 * Eine Belohnungsstufe: wer die Arena in höchstens {@code seconds} schafft, kann sie abholen.
 */
public record RewardTier(int seconds, int gems, int coins, int xp) {

    /** Stufen von 1:00 bis 10:00 in 30-Sekunden-Schritten. */
    public static final List<RewardTier> DEFAULTS = List.of(
            new RewardTier(60, 200, 5000, 1500),
            new RewardTier(90, 50, 1000, 500),
            new RewardTier(120, 0, 500, 250),
            new RewardTier(150, 0, 450, 200),
            new RewardTier(180, 0, 421, 187),
            new RewardTier(210, 0, 391, 174),
            new RewardTier(240, 0, 362, 161),
            new RewardTier(270, 0, 333, 148),
            new RewardTier(300, 0, 303, 135),
            new RewardTier(330, 0, 274, 122),
            new RewardTier(360, 0, 245, 109),
            new RewardTier(390, 0, 215, 96),
            new RewardTier(420, 0, 186, 83),
            new RewardTier(450, 0, 157, 70),
            new RewardTier(480, 0, 127, 57),
            new RewardTier(510, 0, 98, 44),
            new RewardTier(540, 0, 69, 31),
            new RewardTier(570, 0, 39, 18),
            new RewardTier(600, 0, 10, 5));

    /** Anzeige als m:ss, z. B. 1:30 */
    public String label() {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
