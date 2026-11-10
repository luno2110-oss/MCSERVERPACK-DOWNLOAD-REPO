package de.kuby.kubysurvival.angeln;

import java.util.List;

// ERZEUGT von tools/build_fish.py - nicht von Hand ändern.

/** Fischart: ID (= Textur kuby:item/fish/<id>), Name, Gewässer, Seltenheit, Gewicht von-bis, nachtaktiv. */
public record FishSpecies(String id, String name, Water water, Rarity rarity, double minKg, double maxKg,
                          boolean night, int index) {

    public static final List<FishSpecies> ALL = List.of(
            new FishSpecies("rotauge", "Rotauge", Water.FLUSS, Rarity.GEWOEHNLICH, 0.08, 0.6, false, 0),
            new FishSpecies("flussbarsch", "Flussbarsch", Water.FLUSS, Rarity.GEWOEHNLICH, 0.1, 1.2, false, 1),
            new FishSpecies("brasse", "Brasse", Water.FLUSS, Rarity.GEWOEHNLICH, 0.3, 5.0, false, 2),
            new FishSpecies("bachforelle", "Bachforelle", Water.FLUSS, Rarity.UNGEWOEHNLICH, 0.3, 2.5, false, 3),
            new FishSpecies("karpfen", "Karpfen", Water.FLUSS, Rarity.UNGEWOEHNLICH, 1.0, 14.0, false, 4),
            new FishSpecies("aesche", "Äsche", Water.FLUSS, Rarity.UNGEWOEHNLICH, 0.2, 2.5, false, 5),
            new FishSpecies("zander", "Zander", Water.FLUSS, Rarity.SELTEN, 0.8, 9.0, true, 6),
            new FishSpecies("hecht", "Hecht", Water.FLUSS, Rarity.SELTEN, 1.5, 16.0, false, 7),
            new FishSpecies("barbe", "Barbe", Water.FLUSS, Rarity.SELTEN, 0.5, 6.0, true, 8),
            new FishSpecies("wels", "Wels", Water.FLUSS, Rarity.EPISCH, 5.0, 60.0, true, 9),
            new FishSpecies("huchen", "Huchen", Water.FLUSS, Rarity.EPISCH, 5.0, 50.0, false, 10),
            new FishSpecies("stoer", "Stör", Water.FLUSS, Rarity.LEGENDAER, 10.0, 120.0, false, 11),
            new FishSpecies("hering", "Hering", Water.OZEAN, Rarity.GEWOEHNLICH, 0.05, 0.5, false, 12),
            new FishSpecies("makrele", "Makrele", Water.OZEAN, Rarity.GEWOEHNLICH, 0.2, 1.5, false, 13),
            new FishSpecies("sardine", "Sardine", Water.OZEAN, Rarity.GEWOEHNLICH, 0.03, 0.2, false, 14),
            new FishSpecies("kabeljau", "Kabeljau", Water.OZEAN, Rarity.UNGEWOEHNLICH, 1.0, 12.0, false, 15),
            new FishSpecies("dorade", "Dorade", Water.OZEAN, Rarity.UNGEWOEHNLICH, 0.4, 3.0, false, 16),
            new FishSpecies("rotbarsch", "Rotbarsch", Water.OZEAN, Rarity.UNGEWOEHNLICH, 0.5, 6.0, false, 17),
            new FishSpecies("thunfisch", "Thunfisch", Water.OZEAN, Rarity.SELTEN, 10.0, 250.0, false, 18),
            new FishSpecies("schwertfisch", "Schwertfisch", Water.OZEAN, Rarity.SELTEN, 20.0, 300.0, false, 19),
            new FishSpecies("seeteufel", "Seeteufel", Water.OZEAN, Rarity.SELTEN, 2.0, 40.0, true, 20),
            new FishSpecies("mondfisch", "Mondfisch", Water.OZEAN, Rarity.EPISCH, 50.0, 1000.0, false, 21),
            new FishSpecies("barrakuda", "Barrakuda", Water.OZEAN, Rarity.EPISCH, 3.0, 40.0, false, 22),
            new FishSpecies("weisser_hai", "Weißer Hai", Water.OZEAN, Rarity.LEGENDAER, 300.0, 1800.0, false, 23),
            new FishSpecies("clownfisch", "Clownfisch", Water.WARM, Rarity.GEWOEHNLICH, 0.05, 0.3, false, 24),
            new FishSpecies("doktorfisch", "Doktorfisch", Water.WARM, Rarity.GEWOEHNLICH, 0.2, 1.0, false, 25),
            new FishSpecies("riffbarsch", "Riffbarsch", Water.WARM, Rarity.GEWOEHNLICH, 0.02, 0.15, false, 26),
            new FishSpecies("papageifisch", "Papageifisch", Water.WARM, Rarity.UNGEWOEHNLICH, 1.0, 9.0, false, 27),
            new FishSpecies("kugelfisch", "Kugelfisch", Water.WARM, Rarity.UNGEWOEHNLICH, 0.3, 3.0, false, 28),
            new FishSpecies("kaiserfisch", "Kaiserfisch", Water.WARM, Rarity.UNGEWOEHNLICH, 0.3, 1.5, false, 29),
            new FishSpecies("feuerfisch", "Feuerfisch", Water.WARM, Rarity.SELTEN, 0.3, 1.5, false, 30),
            new FishSpecies("mahi_mahi", "Mahi-Mahi", Water.WARM, Rarity.SELTEN, 5.0, 40.0, false, 31),
            new FishSpecies("seepferdchen", "Seepferdchen", Water.WARM, Rarity.SELTEN, 0.01, 0.05, false, 32),
            new FishSpecies("mantarochen", "Mantarochen", Water.WARM, Rarity.EPISCH, 200.0, 2000.0, false, 33),
            new FishSpecies("hammerhai", "Hammerhai", Water.WARM, Rarity.EPISCH, 100.0, 450.0, false, 34),
            new FishSpecies("marlin", "Blauer Marlin", Water.WARM, Rarity.LEGENDAER, 100.0, 800.0, false, 35),
            new FishSpecies("lodde", "Lodde", Water.KALT, Rarity.GEWOEHNLICH, 0.02, 0.1, false, 36),
            new FishSpecies("polardorsch", "Polardorsch", Water.KALT, Rarity.GEWOEHNLICH, 0.1, 0.8, false, 37),
            new FishSpecies("seehase", "Seehase", Water.KALT, Rarity.GEWOEHNLICH, 1.0, 6.0, false, 38),
            new FishSpecies("saibling", "Seesaibling", Water.KALT, Rarity.UNGEWOEHNLICH, 0.3, 4.0, false, 39),
            new FishSpecies("lachs", "Lachs", Water.KALT, Rarity.UNGEWOEHNLICH, 2.0, 20.0, false, 40),
            new FishSpecies("rotlachs", "Rotlachs", Water.KALT, Rarity.UNGEWOEHNLICH, 1.5, 7.0, false, 41),
            new FishSpecies("heilbutt", "Heilbutt", Water.KALT, Rarity.SELTEN, 5.0, 200.0, false, 42),
            new FishSpecies("steinbeisser", "Steinbeißer", Water.KALT, Rarity.SELTEN, 2.0, 20.0, false, 43),
            new FishSpecies("eisfisch", "Eisfisch", Water.KALT, Rarity.SELTEN, 0.3, 2.0, false, 44),
            new FishSpecies("groenlandhai", "Grönlandhai", Water.KALT, Rarity.EPISCH, 200.0, 1000.0, true, 45),
            new FishSpecies("heringshai", "Heringshai", Water.KALT, Rarity.EPISCH, 100.0, 250.0, false, 46),
            new FishSpecies("kristallforelle", "Kristallforelle", Water.KALT, Rarity.LEGENDAER, 1.0, 12.0, false, 47),
            new FishSpecies("schlammpeitzger", "Schlammpeitzger", Water.SUMPF, Rarity.GEWOEHNLICH, 0.02, 0.15, false, 48),
            new FishSpecies("moderlieschen", "Moderlieschen", Water.SUMPF, Rarity.GEWOEHNLICH, 0.01, 0.05, false, 49),
            new FishSpecies("rotfeder", "Rotfeder", Water.SUMPF, Rarity.GEWOEHNLICH, 0.1, 1.5, false, 50),
            new FishSpecies("aal", "Aal", Water.SUMPF, Rarity.UNGEWOEHNLICH, 0.3, 5.0, true, 51),
            new FishSpecies("schleie", "Schleie", Water.SUMPF, Rarity.UNGEWOEHNLICH, 0.3, 6.0, false, 52),
            new FishSpecies("piranha", "Piranha", Water.SUMPF, Rarity.UNGEWOEHNLICH, 0.3, 3.5, false, 53),
            new FishSpecies("grottenolm", "Grottenolm", Water.SUMPF, Rarity.SELTEN, 0.01, 0.05, false, 54),
            new FishSpecies("hoehlensalmler", "Höhlensalmler", Water.SUMPF, Rarity.SELTEN, 0.02, 0.2, false, 55),
            new FishSpecies("axolotl", "Axolotl", Water.SUMPF, Rarity.SELTEN, 0.05, 0.3, false, 56),
            new FishSpecies("lungenfisch", "Lungenfisch", Water.SUMPF, Rarity.EPISCH, 2.0, 20.0, true, 57),
            new FishSpecies("schlangenkopf", "Schlangenkopf", Water.SUMPF, Rarity.EPISCH, 2.0, 15.0, true, 58),
            new FishSpecies("gluehwels", "Glühwels", Water.SUMPF, Rarity.LEGENDAER, 3.0, 40.0, true, 59),
            new FishSpecies("gluecks_karpfen", "Glücks-Karpfen", Water.MYTHOS, Rarity.SELTEN, 3.0, 25.0, false, 60),
            new FishSpecies("sternenhecht", "Sternenhecht", Water.MYTHOS, Rarity.SELTEN, 2.0, 18.0, true, 61),
            new FishSpecies("kristallqualle", "Kristallqualle", Water.MYTHOS, Rarity.SELTEN, 0.5, 8.0, false, 62),
            new FishSpecies("geisterfisch", "Geisterfisch", Water.MYTHOS, Rarity.EPISCH, 0.5, 6.0, true, 63),
            new FishSpecies("lavaforelle", "Lavaforelle", Water.MYTHOS, Rarity.EPISCH, 1.0, 10.0, false, 64),
            new FishSpecies("drachenfisch", "Drachenfisch", Water.MYTHOS, Rarity.EPISCH, 0.5, 6.0, true, 65),
            new FishSpecies("kraken_junges", "Kraken-Junges", Water.MYTHOS, Rarity.LEGENDAER, 5.0, 80.0, false, 66),
            new FishSpecies("phoenixfisch", "Phönixfisch", Water.MYTHOS, Rarity.LEGENDAER, 2.0, 30.0, false, 67),
            new FishSpecies("nebelrochen", "Nebelrochen", Water.MYTHOS, Rarity.LEGENDAER, 50.0, 600.0, true, 68),
            new FishSpecies("leviathan", "Leviathan", Water.MYTHOS, Rarity.MYTHISCH, 500.0, 5000.0, false, 69),
            new FishSpecies("ur_koi", "Ur-Koi", Water.MYTHOS, Rarity.MYTHISCH, 5.0, 50.0, false, 70),
            new FishSpecies("abgrundangler", "Abgrundangler", Water.MYTHOS, Rarity.MYTHISCH, 10.0, 120.0, true, 71));

    public static FishSpecies byId(String id) {
        for (FishSpecies s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    public static List<FishSpecies> of(Water w) {
        return ALL.stream().filter(s -> s.water == w).toList();
    }
}
