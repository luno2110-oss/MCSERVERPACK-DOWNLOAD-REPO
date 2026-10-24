package de.kuby.kubyplugins.ui;

// ERZEUGT von tools/build_kit.py - nicht von Hand ändern.

/** Zeichen, Vorschübe und Schrift-Verschiebungen des Dialog-Kits (Schriften kuby:k/...). */
public final class KitData {
    private KitData() {
    }

    public static final int T_MAX = 8;
    public static final int B_MAX = 53;
    public static final int I_MIN = -24;
    public static final int I_MAX = 8;
    public static final int SEG = 32;
    public static final int CAP = 8;
    public static final int ROW = 9;
    public static final int BTN_H = 22;
    public static final int BAR_H = 7;

    /** Ein Hintergrund-Stück: Zeichen, Vorschub (Breite + 1), sichtbare Breite und Höhe. */
    public record Piece(char c, int advance, int width, int height) {}

    /** Kasten-Stile (Rahmen aus Zeilen zu 9 px). */
    public enum Box {
        NAVY(new Piece('\ue400', 9, 8, 9),
                new Piece('\ue500', 33, 32, 9),
                new Piece('\ue401', 9, 8, 9),
                new Piece('\ue402', 9, 8, 9),
                new Piece('\ue501', 33, 32, 9),
                new Piece('\ue403', 9, 8, 9),
                new Piece('\ue404', 9, 8, 9),
                new Piece('\ue502', 33, 32, 9),
                new Piece('\ue405', 9, 8, 9)),
        GREEN(new Piece('\ue406', 9, 8, 9),
                new Piece('\ue503', 33, 32, 9),
                new Piece('\ue407', 9, 8, 9),
                new Piece('\ue408', 9, 8, 9),
                new Piece('\ue504', 33, 32, 9),
                new Piece('\ue409', 9, 8, 9),
                new Piece('\ue40a', 9, 8, 9),
                new Piece('\ue505', 33, 32, 9),
                new Piece('\ue40b', 9, 8, 9)),
        GOLD(new Piece('\ue40c', 9, 8, 9),
                new Piece('\ue506', 33, 32, 9),
                new Piece('\ue40d', 9, 8, 9),
                new Piece('\ue40e', 9, 8, 9),
                new Piece('\ue507', 33, 32, 9),
                new Piece('\ue40f', 9, 8, 9),
                new Piece('\ue410', 9, 8, 9),
                new Piece('\ue508', 33, 32, 9),
                new Piece('\ue411', 9, 8, 9)),
        PURPLE(new Piece('\ue412', 9, 8, 9),
                new Piece('\ue509', 33, 32, 9),
                new Piece('\ue413', 9, 8, 9),
                new Piece('\ue414', 9, 8, 9),
                new Piece('\ue50a', 33, 32, 9),
                new Piece('\ue415', 9, 8, 9),
                new Piece('\ue416', 9, 8, 9),
                new Piece('\ue50b', 33, 32, 9),
                new Piece('\ue417', 9, 8, 9)),
        TEAL(new Piece('\ue418', 9, 8, 9),
                new Piece('\ue50c', 33, 32, 9),
                new Piece('\ue419', 9, 8, 9),
                new Piece('\ue41a', 9, 8, 9),
                new Piece('\ue50d', 33, 32, 9),
                new Piece('\ue41b', 9, 8, 9),
                new Piece('\ue41c', 9, 8, 9),
                new Piece('\ue50e', 33, 32, 9),
                new Piece('\ue41d', 9, 8, 9)),
        GRAY(new Piece('\ue41e', 9, 8, 9),
                new Piece('\ue50f', 33, 32, 9),
                new Piece('\ue41f', 9, 8, 9),
                new Piece('\ue420', 9, 8, 9),
                new Piece('\ue510', 33, 32, 9),
                new Piece('\ue421', 9, 8, 9),
                new Piece('\ue422', 9, 8, 9),
                new Piece('\ue511', 33, 32, 9),
                new Piece('\ue423', 9, 8, 9)),
        RED(new Piece('\ue424', 9, 8, 9),
                new Piece('\ue512', 33, 32, 9),
                new Piece('\ue425', 9, 8, 9),
                new Piece('\ue426', 9, 8, 9),
                new Piece('\ue513', 33, 32, 9),
                new Piece('\ue427', 9, 8, 9),
                new Piece('\ue428', 9, 8, 9),
                new Piece('\ue514', 33, 32, 9),
                new Piece('\ue429', 9, 8, 9)),
        CASINO(new Piece('\ue42a', 9, 8, 9),
                new Piece('\ue515', 33, 32, 9),
                new Piece('\ue42b', 9, 8, 9),
                new Piece('\ue42c', 9, 8, 9),
                new Piece('\ue516', 33, 32, 9),
                new Piece('\ue42d', 9, 8, 9),
                new Piece('\ue42e', 9, 8, 9),
                new Piece('\ue517', 33, 32, 9),
                new Piece('\ue42f', 9, 8, 9)),
        CASINO_DARK(new Piece('\ue430', 9, 8, 9),
                new Piece('\ue518', 33, 32, 9),
                new Piece('\ue431', 9, 8, 9),
                new Piece('\ue432', 9, 8, 9),
                new Piece('\ue519', 33, 32, 9),
                new Piece('\ue433', 9, 8, 9),
                new Piece('\ue434', 9, 8, 9),
                new Piece('\ue51a', 33, 32, 9),
                new Piece('\ue435', 9, 8, 9)),
        FELT(new Piece('\ue436', 9, 8, 9),
                new Piece('\ue51b', 33, 32, 9),
                new Piece('\ue437', 9, 8, 9),
                new Piece('\ue438', 9, 8, 9),
                new Piece('\ue51c', 33, 32, 9),
                new Piece('\ue439', 9, 8, 9),
                new Piece('\ue43a', 9, 8, 9),
                new Piece('\ue51d', 33, 32, 9),
                new Piece('\ue43b', 9, 8, 9)),
        BLACK(new Piece('\ue43c', 9, 8, 9),
                new Piece('\ue51e', 33, 32, 9),
                new Piece('\ue43d', 9, 8, 9),
                new Piece('\ue43e', 9, 8, 9),
                new Piece('\ue51f', 33, 32, 9),
                new Piece('\ue43f', 9, 8, 9),
                new Piece('\ue440', 9, 8, 9),
                new Piece('\ue520', 33, 32, 9),
                new Piece('\ue441', 9, 8, 9));

        /** [Zeile oben/mitte/unten][links/mitte/rechts] */
        public final Piece[][] p;

        Box(Piece... all) {
            p = new Piece[][]{{all[0], all[1], all[2]}, {all[3], all[4], all[5]}, {all[6], all[7], all[8]}};
        }
    }

    /** Knopf-Stile (22 px hoch). */
    public enum Button {
        TEAL(new Piece('\ue600', 9, 8, 22), new Piece('\ue700', 33, 32, 22), new Piece('\ue601', 9, 8, 22),
                new Piece('\ue701', 17, 16, 22), new Piece('\ue602', 9, 8, 22), new Piece('\ue603', 5, 4, 22), new Piece('\ue604', 3, 2, 22), new Piece('\ue605', 2, 1, 22)),
        GREEN(new Piece('\ue606', 9, 8, 22), new Piece('\ue702', 33, 32, 22), new Piece('\ue607', 9, 8, 22),
                new Piece('\ue703', 17, 16, 22), new Piece('\ue608', 9, 8, 22), new Piece('\ue609', 5, 4, 22), new Piece('\ue60a', 3, 2, 22), new Piece('\ue60b', 2, 1, 22)),
        PURPLE(new Piece('\ue60c', 9, 8, 22), new Piece('\ue704', 33, 32, 22), new Piece('\ue60d', 9, 8, 22),
                new Piece('\ue705', 17, 16, 22), new Piece('\ue60e', 9, 8, 22), new Piece('\ue60f', 5, 4, 22), new Piece('\ue610', 3, 2, 22), new Piece('\ue611', 2, 1, 22)),
        GOLD(new Piece('\ue612', 9, 8, 22), new Piece('\ue706', 33, 32, 22), new Piece('\ue613', 9, 8, 22),
                new Piece('\ue707', 17, 16, 22), new Piece('\ue614', 9, 8, 22), new Piece('\ue615', 5, 4, 22), new Piece('\ue616', 3, 2, 22), new Piece('\ue617', 2, 1, 22)),
        RED(new Piece('\ue618', 9, 8, 22), new Piece('\ue708', 33, 32, 22), new Piece('\ue619', 9, 8, 22),
                new Piece('\ue709', 17, 16, 22), new Piece('\ue61a', 9, 8, 22), new Piece('\ue61b', 5, 4, 22), new Piece('\ue61c', 3, 2, 22), new Piece('\ue61d', 2, 1, 22)),
        DARK(new Piece('\ue61e', 9, 8, 22), new Piece('\ue70a', 33, 32, 22), new Piece('\ue61f', 9, 8, 22),
                new Piece('\ue70b', 17, 16, 22), new Piece('\ue620', 9, 8, 22), new Piece('\ue621', 5, 4, 22), new Piece('\ue622', 3, 2, 22), new Piece('\ue623', 2, 1, 22)),
        GOLD_LINE(new Piece('\ue624', 9, 8, 22), new Piece('\ue70c', 33, 32, 22), new Piece('\ue625', 9, 8, 22),
                new Piece('\ue70d', 17, 16, 22), new Piece('\ue626', 9, 8, 22), new Piece('\ue627', 5, 4, 22), new Piece('\ue628', 3, 2, 22), new Piece('\ue629', 2, 1, 22)),
        BROWN_LINE(new Piece('\ue62a', 9, 8, 22), new Piece('\ue70e', 33, 32, 22), new Piece('\ue62b', 9, 8, 22),
                new Piece('\ue70f', 17, 16, 22), new Piece('\ue62c', 9, 8, 22), new Piece('\ue62d', 5, 4, 22), new Piece('\ue62e', 3, 2, 22), new Piece('\ue62f', 2, 1, 22)),
        TEAL_LINE(new Piece('\ue630', 9, 8, 22), new Piece('\ue710', 33, 32, 22), new Piece('\ue631', 9, 8, 22),
                new Piece('\ue711', 17, 16, 22), new Piece('\ue632', 9, 8, 22), new Piece('\ue633', 5, 4, 22), new Piece('\ue634', 3, 2, 22), new Piece('\ue635', 2, 1, 22)),
        PURPLE_LINE(new Piece('\ue636', 9, 8, 22), new Piece('\ue712', 33, 32, 22), new Piece('\ue637', 9, 8, 22),
                new Piece('\ue713', 17, 16, 22), new Piece('\ue638', 9, 8, 22), new Piece('\ue639', 5, 4, 22), new Piece('\ue63a', 3, 2, 22), new Piece('\ue63b', 2, 1, 22)),
        GREEN_LINE(new Piece('\ue63c', 9, 8, 22), new Piece('\ue714', 33, 32, 22), new Piece('\ue63d', 9, 8, 22),
                new Piece('\ue715', 17, 16, 22), new Piece('\ue63e', 9, 8, 22), new Piece('\ue63f', 5, 4, 22), new Piece('\ue640', 3, 2, 22), new Piece('\ue641', 2, 1, 22)),
        RED_LINE(new Piece('\ue642', 9, 8, 22), new Piece('\ue716', 33, 32, 22), new Piece('\ue643', 9, 8, 22),
                new Piece('\ue717', 17, 16, 22), new Piece('\ue644', 9, 8, 22), new Piece('\ue645', 5, 4, 22), new Piece('\ue646', 3, 2, 22), new Piece('\ue647', 2, 1, 22)),
        NAVY_LINE(new Piece('\ue648', 9, 8, 22), new Piece('\ue718', 33, 32, 22), new Piece('\ue649', 9, 8, 22),
                new Piece('\ue719', 17, 16, 22), new Piece('\ue64a', 9, 8, 22), new Piece('\ue64b', 5, 4, 22), new Piece('\ue64c', 3, 2, 22), new Piece('\ue64d', 2, 1, 22));

        public final Piece l, m, r;
        /** Schmale Mittelstücke 16, 8, 4, 2, 1 px. */
        public final Piece[] narrow;

        Button(Piece l, Piece m, Piece r, Piece... narrow) {
            this.l = l;
            this.m = m;
            this.r = r;
            this.narrow = narrow;
        }
    }

    /** Balken-Füllfarben; Stücke in den Breiten 1, 2, 4, 8, 16, 32. */
    public enum Fill {
        GOLD(new Piece('\ue448', 2, 1, 5), new Piece('\ue449', 3, 2, 5), new Piece('\ue44a', 5, 4, 5), new Piece('\ue44b', 9, 8, 5), new Piece('\ue523', 17, 16, 5), new Piece('\ue524', 33, 32, 5)),
        CYAN(new Piece('\ue44c', 2, 1, 5), new Piece('\ue44d', 3, 2, 5), new Piece('\ue44e', 5, 4, 5), new Piece('\ue44f', 9, 8, 5), new Piece('\ue525', 17, 16, 5), new Piece('\ue526', 33, 32, 5)),
        GREEN(new Piece('\ue450', 2, 1, 5), new Piece('\ue451', 3, 2, 5), new Piece('\ue452', 5, 4, 5), new Piece('\ue453', 9, 8, 5), new Piece('\ue527', 17, 16, 5), new Piece('\ue528', 33, 32, 5)),
        PURPLE(new Piece('\ue454', 2, 1, 5), new Piece('\ue455', 3, 2, 5), new Piece('\ue456', 5, 4, 5), new Piece('\ue457', 9, 8, 5), new Piece('\ue529', 17, 16, 5), new Piece('\ue52a', 33, 32, 5)),
        RED(new Piece('\ue458', 2, 1, 5), new Piece('\ue459', 3, 2, 5), new Piece('\ue45a', 5, 4, 5), new Piece('\ue45b', 9, 8, 5), new Piece('\ue52b', 17, 16, 5), new Piece('\ue52c', 33, 32, 5));

        public final Piece[] p;

        Fill(Piece... p) {
            this.p = p;
        }
    }

    public static final Piece BAR_L = new Piece('\ue442', 9, 8, 7);
    public static final Piece BAR_M = new Piece('\ue521', 33, 32, 7);
    public static final Piece BAR_R = new Piece('\ue443', 9, 8, 7);
    public static final Piece[] BAR_NARROW = {new Piece('\ue522', 17, 16, 7), new Piece('\ue444', 9, 8, 7), new Piece('\ue445', 5, 4, 7), new Piece('\ue446', 3, 2, 7), new Piece('\ue447', 2, 1, 7)};
    public static final Piece BACK = new Piece('\ue780', 21, 20, 20);

    /** Icons: Zeichen und Vorschub für 16 px und 32 px (Schriften kuby:k/i<s>). */
    public enum Icon {
        ROD('\ue900', 16, '\ue901', 31),
        BOOK('\ue902', 17, '\ue903', 33),
        KNOWLEDGE_BOOK('\ue904', 17, '\ue905', 33),
        EMERALD('\ue906', 14, '\ue907', 27),
        GOLD_HELMET('\ue908', 14, '\ue909', 27),
        COD('\ue90a', 16, '\ue90b', 31),
        SALMON('\ue90c', 16, '\ue90d', 31),
        TROPICAL('\ue90e', 16, '\ue90f', 31),
        PUFFER('\ue910', 17, '\ue911', 33),
        GOLD_INGOT('\ue912', 17, '\ue913', 33),
        GOLD_NUGGET('\ue914', 12, '\ue915', 23),
        DIAMOND('\ue916', 15, '\ue917', 29),
        AMETHYST('\ue918', 15, '\ue919', 29),
        BUNDLE('\ue91a', 15, '\ue91b', 29),
        PAPER('\ue91c', 17, '\ue91d', 33),
        MAP('\ue91e', 17, '\ue91f', 33),
        COMPASS('\ue920', 16, '\ue921', 31),
        CLOCK('\ue922', 16, '\ue923', 31),
        NAUTILUS('\ue924', 16, '\ue925', 31),
        HEART_SEA('\ue926', 16, '\ue927', 31),
        PRISMARINE('\ue928', 16, '\ue929', 31),
        STRING('\ue92a', 16, '\ue92b', 31),
        SPIDER_EYE('\ue92c', 13, '\ue92d', 25),
        GLOW_INK('\ue92e', 15, '\ue92f', 29),
        INK('\ue930', 15, '\ue931', 29),
        EXPERIENCE('\ue932', 15, '\ue933', 29),
        MUSIC_DISC('\ue934', 16, '\ue935', 31),
        JUKEBOX('\ue936', 16, '\ue937', 31),
        NAME_TAG('\ue938', 16, '\ue939', 31),
        TOTEM('\ue93a', 16, '\ue93b', 31),
        STAR('\ue93c', 16, '\ue93d', 31),
        WRITABLE('\ue93e', 17, '\ue93f', 33),
        BARRIER('\ue940', 16, '\ue941', 31),
        IRON_INGOT('\ue942', 17, '\ue943', 33),
        HOPPER('\ue944', 16, '\ue945', 31),
        SUNFLOWER('\ue946', 13, '\ue947', 25),
        LILY('\ue948', 16, '\ue949', 31),
        RABBIT_FOOT('\ue94a', 15, '\ue94b', 29),
        FEATHER('\ue94c', 16, '\ue94d', 31),
        BOOK_PLAIN('\ue94e', 17, '\ue94f', 33),
        FISH_ROTAUGE('\uea00', 15, '\uec00', 29),
        FISH_FLUSSBARSCH('\uea01', 15, '\uec01', 29),
        FISH_BACHFORELLE('\uea02', 15, '\uec02', 29),
        FISH_KARPFEN('\uea03', 15, '\uec03', 29),
        FISH_ZANDER('\uea04', 15, '\uec04', 29),
        FISH_HECHT('\uea05', 15, '\uec05', 29),
        FISH_WELS('\uea06', 16, '\uec06', 31),
        FISH_STOER('\uea07', 16, '\uec07', 31),
        FISH_HERING('\uea08', 15, '\uec08', 29),
        FISH_MAKRELE('\uea09', 15, '\uec09', 29),
        FISH_KABELJAU('\uea0a', 15, '\uec0a', 29),
        FISH_DORADE('\uea0b', 15, '\uec0b', 29),
        FISH_THUNFISCH('\uea0c', 15, '\uec0c', 29),
        FISH_SCHWERTFISCH('\uea0d', 16, '\uec0d', 31),
        FISH_MONDFISCH('\uea0e', 14, '\uec0e', 27),
        FISH_WEISSER_HAI('\uea0f', 16, '\uec0f', 31),
        FISH_CLOWNFISCH('\uea10', 15, '\uec10', 29),
        FISH_DOKTORFISCH('\uea11', 15, '\uec11', 29),
        FISH_PAPAGEIFISCH('\uea12', 15, '\uec12', 29),
        FISH_KUGELFISCH('\uea13', 14, '\uec13', 27),
        FISH_FEUERFISCH('\uea14', 15, '\uec14', 29),
        FISH_MAHI_MAHI('\uea15', 16, '\uec15', 31),
        FISH_MANTAROCHEN('\uea16', 14, '\uec16', 27),
        FISH_MARLIN('\uea17', 16, '\uec17', 31),
        FISH_LODDE('\uea18', 15, '\uec18', 29),
        FISH_POLARDORSCH('\uea19', 15, '\uec19', 29),
        FISH_SAIBLING('\uea1a', 15, '\uec1a', 29),
        FISH_LACHS('\uea1b', 15, '\uec1b', 29),
        FISH_HEILBUTT('\uea1c', 15, '\uec1c', 29),
        FISH_STEINBEISSER('\uea1d', 15, '\uec1d', 29),
        FISH_GROENLANDHAI('\uea1e', 16, '\uec1e', 31),
        FISH_KRISTALLFORELLE('\uea1f', 15, '\uec1f', 29),
        FISH_SCHLAMMPEITZGER('\uea20', 15, '\uec20', 29),
        FISH_MODERLIESCHEN('\uea21', 15, '\uec21', 29),
        FISH_AAL('\uea22', 15, '\uec22', 29),
        FISH_SCHLEIE('\uea23', 15, '\uec23', 29),
        FISH_GROTTENOLM('\uea24', 15, '\uec24', 29),
        FISH_HOEHLENSALMLER('\uea25', 15, '\uec25', 29),
        FISH_LUNGENFISCH('\uea26', 15, '\uec26', 29),
        FISH_GLUEHWELS('\uea27', 16, '\uec27', 31),
        FISH_GLUECKS_KARPFEN('\uea28', 15, '\uec28', 29),
        FISH_STERNENHECHT('\uea29', 15, '\uec29', 29),
        FISH_GEISTERFISCH('\uea2a', 15, '\uec2a', 29),
        FISH_LAVAFORELLE('\uea2b', 15, '\uec2b', 29),
        FISH_KRAKEN_JUNGES('\uea2c', 17, '\uec2c', 33),
        FISH_PHOENIXFISCH('\uea2d', 15, '\uec2d', 29),
        FISH_LEVIATHAN('\uea2e', 15, '\uec2e', 29),
        FISH_UR_KOI('\uea2f', 15, '\uec2f', 29),
        FISHX_ROTAUGE('\uea30', 15, '\uec30', 29),
        FISHX_FLUSSBARSCH('\uea31', 15, '\uec31', 29),
        FISHX_BACHFORELLE('\uea32', 15, '\uec32', 29),
        FISHX_KARPFEN('\uea33', 15, '\uec33', 29),
        FISHX_ZANDER('\uea34', 15, '\uec34', 29),
        FISHX_HECHT('\uea35', 15, '\uec35', 29),
        FISHX_WELS('\uea36', 16, '\uec36', 31),
        FISHX_STOER('\uea37', 16, '\uec37', 31),
        FISHX_HERING('\uea38', 15, '\uec38', 29),
        FISHX_MAKRELE('\uea39', 15, '\uec39', 29),
        FISHX_KABELJAU('\uea3a', 15, '\uec3a', 29),
        FISHX_DORADE('\uea3b', 15, '\uec3b', 29),
        FISHX_THUNFISCH('\uea3c', 15, '\uec3c', 29),
        FISHX_SCHWERTFISCH('\uea3d', 16, '\uec3d', 31),
        FISHX_MONDFISCH('\uea3e', 14, '\uec3e', 27),
        FISHX_WEISSER_HAI('\uea3f', 16, '\uec3f', 31),
        FISHX_CLOWNFISCH('\uea40', 15, '\uec40', 29),
        FISHX_DOKTORFISCH('\uea41', 15, '\uec41', 29),
        FISHX_PAPAGEIFISCH('\uea42', 15, '\uec42', 29),
        FISHX_KUGELFISCH('\uea43', 14, '\uec43', 27),
        FISHX_FEUERFISCH('\uea44', 15, '\uec44', 29),
        FISHX_MAHI_MAHI('\uea45', 16, '\uec45', 31),
        FISHX_MANTAROCHEN('\uea46', 14, '\uec46', 27),
        FISHX_MARLIN('\uea47', 16, '\uec47', 31),
        FISHX_LODDE('\uea48', 15, '\uec48', 29),
        FISHX_POLARDORSCH('\uea49', 15, '\uec49', 29),
        FISHX_SAIBLING('\uea4a', 15, '\uec4a', 29),
        FISHX_LACHS('\uea4b', 15, '\uec4b', 29),
        FISHX_HEILBUTT('\uea4c', 15, '\uec4c', 29),
        FISHX_STEINBEISSER('\uea4d', 15, '\uec4d', 29),
        FISHX_GROENLANDHAI('\uea4e', 16, '\uec4e', 31),
        FISHX_KRISTALLFORELLE('\uea4f', 15, '\uec4f', 29),
        FISHX_SCHLAMMPEITZGER('\uea50', 15, '\uec50', 29),
        FISHX_MODERLIESCHEN('\uea51', 15, '\uec51', 29),
        FISHX_AAL('\uea52', 15, '\uec52', 29),
        FISHX_SCHLEIE('\uea53', 15, '\uec53', 29),
        FISHX_GROTTENOLM('\uea54', 15, '\uec54', 29),
        FISHX_HOEHLENSALMLER('\uea55', 15, '\uec55', 29),
        FISHX_LUNGENFISCH('\uea56', 15, '\uec56', 29),
        FISHX_GLUEHWELS('\uea57', 16, '\uec57', 31),
        FISHX_GLUECKS_KARPFEN('\uea58', 15, '\uec58', 29),
        FISHX_STERNENHECHT('\uea59', 15, '\uec59', 29),
        FISHX_GEISTERFISCH('\uea5a', 15, '\uec5a', 29),
        FISHX_LAVAFORELLE('\uea5b', 15, '\uec5b', 29),
        FISHX_KRAKEN_JUNGES('\uea5c', 17, '\uec5c', 33),
        FISHX_PHOENIXFISCH('\uea5d', 15, '\uec5d', 29),
        FISHX_LEVIATHAN('\uea5e', 15, '\uec5e', 29),
        FISHX_UR_KOI('\uea5f', 15, '\uec5f', 29),
        WORM('\uea60', 14, '\uec60', 27),
        LURE_GLITTER('\uea61', 15, '\uec61', 29),
        LURE_NIGHT('\uea62', 15, '\uec62', 29),
        LURE_GOLD('\uea63', 15, '\uec63', 29),
        HOOK_GOLD('\uea64', 13, '\uec64', 25),
        REEL('\uea65', 16, '\uec65', 31),
        EYE_ORB('\uea66', 15, '\uec66', 29),
        REEL_BIG('\uea67', 16, '\uec67', 31),
        FUNNEL('\uea68', 15, '\uec68', 29),
        TWO_FISH('\uea69', 16, '\uec69', 31),
        FISH_BOOK('\uea6a', 15, '\uec6a', 29),
        SCROLL('\uea6b', 15, '\uec6b', 29),
        BAIT_BAG('\uea6c', 15, '\uec6c', 29),
        CHEST('\uea6d', 15, '\uec6d', 29),
        SUN_HOUR('\uea6e', 16, '\uec6e', 31),
        BUOY('\uea6f', 15, '\uec6f', 29),
        COIN_STACK('\uea70', 14, '\uec70', 27);

        public final char c16, c32;
        public final int adv16, adv32;

        Icon(char c16, int adv16, char c32, int adv32) {
            this.c16 = c16;
            this.adv16 = adv16;
            this.c32 = c32;
            this.adv32 = adv32;
        }
    }

    /** Textzeichen der Schriften kuby:k/t<s> (sortiert) und ihr Vorschub ohne Fett. */
    static final String TEXT_CHARS = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b9\u00ba"
            + "\u00bb\u00bc\u00bd\u00be\u00bf\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff\u0100\u0101\u0102\u0103\u0104\u0105\u0106\u0107\u0108\u0109\u010a\u010b\u010c\u010d\u010e\u010f\u0110\u0111\u0112\u0113\u0114\u0115\u0116\u0117\u0118\u0119\u011a\u011b\u011c\u011d\u011e\u011f\u0120\u0121\u0122\u0123\u0124\u0125\u0126\u0127\u0128\u0129\u012a\u012b\u012c\u012d\u012e\u012f\u0130\u0131\u0132"
            + "\u0133\u0134\u0135\u0136\u0137\u0138\u0139\u013a\u013b\u013c\u013d\u013e\u013f\u0140\u0141\u0142\u0143\u0144\u0145\u0146\u0147\u0148\u0149\u014a\u014b\u014c\u014d\u014e\u014f\u0150\u0151\u0152\u0153\u0154\u0155\u0156\u0157\u0158\u0159\u015a\u015b\u015c\u015d\u015e\u015f\u0160\u0161\u0162\u0163\u0164\u0165\u0166\u0167\u0168\u0169\u016a\u016b\u016c\u016d\u016e\u016f\u0170\u0171\u0172\u0173\u0174\u0175\u0176\u0177\u0178\u0179\u017a\u017b\u017c\u017d\u017e\u017f\u0180\u0181\u0182\u0183\u0184\u0185\u0186\u0187\u0188\u0189\u018a\u018b\u018c\u018d\u018e\u018f\u0190\u0191\u0192\u0193\u0194\u0195\u0196\u0197\u0198\u0199\u019a\u019b\u019c\u019d\u019e\u019f\u01a0\u01a1\u01a2\u01a3\u01a4\u01a5\u01a6\u01a7\u01a8\u01a9\u01aa"
            + "\u01ab\u01ac\u01ad\u01ae\u01af\u01b0\u01b1\u01b2\u01b3\u01b4\u01b5\u01b6\u01b7\u01b8\u01b9\u01ba\u01bb\u01bc\u01bd\u01be\u01bf\u01c0\u01c1\u01c2\u01c3\u01c4\u01c5\u01c6\u01c7\u01c8\u01c9\u01ca\u01cb\u01cc\u01cd\u01ce\u01cf\u01d0\u01d1\u01d2\u01d3\u01d4\u01d5\u01d6\u01d7\u01d8\u01d9\u01da\u01db\u01dc\u01dd\u01de\u01df\u01e0\u01e1\u01e2\u01e3\u01e4\u01e5\u01e6\u01e7\u01e8\u01e9\u01ea\u01eb\u01ec\u01ed\u01ee\u01ef\u01f0\u01f1\u01f2\u01f3\u01f4\u01f5\u01f6\u01f7\u01f8\u01f9\u01fa\u01fb\u01fc\u01fd\u01fe\u01ff\u0200\u0201\u0202\u0203\u0204\u0205\u0206\u0207\u0208\u0209\u020a\u020b\u020c\u020d\u020e\u020f\u0210\u0211\u0212\u0213\u0214\u0215\u0216\u0217\u0218\u0219\u021a\u021b\u021c\u021d\u021e\u021f\u0220\u0221\u0222"
            + "\u0223\u0224\u0225\u0226\u0227\u0228\u0229\u022a\u022b\u022c\u022d\u022e\u022f\u0230\u0231\u0232\u0233\u0234\u0235\u0236\u0237\u0238\u0239\u023a\u023b\u023c\u023d\u023e\u023f\u0240\u0241\u0242\u0243\u0244\u0245\u0246\u0247\u0248\u0249\u024a\u024b\u024c\u024d\u024e\u024f\u0250\u0251\u0252\u0253\u0254\u0255\u0256\u0257\u0258\u0259\u025a\u025b\u025c\u025d\u025e\u025f\u0260\u0261\u0262\u0263\u0264\u0265\u0266\u0267\u0268\u0269\u026a\u026b\u026c\u026d\u026e\u026f\u0270\u0271\u0272\u0273\u0274\u0275\u0276\u0277\u0278\u0279\u027a\u027b\u027c\u027d\u027e\u027f\u0280\u0281\u0282\u0283\u0284\u0285\u0286\u0287\u0288\u0289\u028a\u028b\u028c\u028d\u028e\u028f\u0290\u0291\u0292\u0293\u0294\u0295\u0296\u0297\u0298\u0299\u029a"
            + "\u029b\u029c\u029d\u029e\u029f\u02a0\u02a1\u02a2\u02a3\u02a4\u02a5\u02a6\u02a7\u02a8\u02a9\u02aa\u02ab\u02ac\u02ad\u02ae\u02af\u02bb\u02cc\u02d9\u0327\u037e\u0386\u0387\u0388\u0389\u038a\u038c\u038e\u038f\u0390\u0391\u0392\u0393\u0394\u0395\u0396\u0397\u0398\u0399\u039a\u039b\u039c\u039d\u039e\u039f\u03a0\u03a1\u03a3\u03a4\u03a5\u03a6\u03a7\u03a8\u03a9\u03aa\u03ab\u03ac\u03ad\u03ae\u03af\u03b0\u03b1\u03b2\u03b3\u03b4\u03b5\u03b6\u03b7\u03b8\u03b9\u03ba\u03bb\u03bc\u03bd\u03be\u03bf\u03c0\u03c1\u03c2\u03c3\u03c4\u03c5\u03c6\u03c7\u03c8\u03c9\u03ca\u03cb\u03cc\u03cd\u03ce\u03d3\u03d4\u03db\u0400\u0401\u0402\u0403\u0404\u0405\u0406\u0407\u0408\u0409\u040a\u040b\u040c\u040d\u040e\u040f\u0410\u0411\u0412\u0413\u0414"
            + "\u0415\u0416\u0417\u0418\u0419\u041a\u041b\u041c\u041d\u041e\u041f\u0420\u0421\u0422\u0423\u0424\u0425\u0426\u0427\u0428\u0429\u042a\u042b\u042c\u042d\u042e\u042f\u0430\u0431\u0432\u0433\u0434\u0435\u0436\u0437\u0438\u0439\u043a\u043b\u043c\u043d\u043e\u043f\u0440\u0441\u0442\u0443\u0444\u0445\u0446\u0447\u0448\u0449\u044a\u044b\u044c\u044d\u044e\u044f\u0450\u0451\u0452\u0453\u0454\u0455\u0456\u0457\u0458\u0459\u045a\u045b\u045c\u045d\u045e\u045f\u0462\u0463\u0472\u0473\u0474\u0475\u0476\u0477\u048a\u048b\u048c\u048d\u048e\u048f\u0490\u0491\u0492\u0493\u0494\u0495\u0496\u0497\u0498\u0499\u049a\u049b\u049c\u049d\u049e\u049f\u04a0\u04a1\u04a2\u04a3\u04a4\u04a5\u04a6\u04a7\u04a8\u04a9\u04aa\u04ab\u04ac\u04ad\u04ae"
            + "\u04af\u04b0\u04b1\u04b2\u04b3\u04b4\u04b5\u04b6\u04b7\u04b8\u04b9\u04ba\u04bb\u04bc\u04bd\u04be\u04bf\u04c0\u04c1\u04c2\u04c3\u04c4\u04c5\u04c6\u04c7\u04c8\u04c9\u04ca\u04cb\u04cc\u04cd\u04ce\u04d0\u04d1\u04d2\u04d3\u04d4\u04d5\u04d6\u04d7\u04d8\u04d9\u04da\u04db\u04dc\u04dd\u04de\u04df\u04e0\u04e1\u04e2\u04e3\u04e4\u04e5\u04e6\u04e7\u04e8\u04e9\u04ea\u04eb\u04ec\u04ed\u04ee\u04ef\u04f0\u04f1\u04f2\u04f3\u04f4\u04f5\u04f6\u04f7\u04f8\u04f9\u04fa\u04fb\u04fc\u04fd\u04fe\u04ff\u0500\u0502\u0503\u0504\u0505\u0506\u0507\u0508\u0509\u050a\u050b\u050c\u050d\u050e\u050f\u0510\u0511\u0512\u0513\u0514\u0515\u0516\u0517\u0518\u0519\u051a\u051b\u051c\u051d\u051e\u051f\u0520\u0521\u0522\u0523\u0524\u0525\u0526\u0527\u0528"
            + "\u0529\u052a\u052b\u052c\u052d\u052e\u052f\u0531\u0532\u0533\u0534\u0535\u0536\u0537\u0538\u0539\u053a\u053b\u053c\u053d\u053e\u053f\u0540\u0541\u0542\u0543\u0544\u0545\u0546\u0547\u0548\u0549\u054a\u054b\u054c\u054d\u054e\u054f\u0550\u0551\u0552\u0553\u0554\u0555\u0556\u0559\u055a\u055b\u055c\u055d\u055e\u055f\u0560\u0561\u0562\u0563\u0564\u0565\u0566\u0567\u0568\u0569\u056a\u056b\u056c\u056d\u056e\u056f\u0570\u0571\u0572\u0573\u0574\u0575\u0576\u0577\u0578\u0579\u057a\u057b\u057c\u057d\u057e\u057f\u0580\u0581\u0582\u0583\u0584\u0585\u0586\u0587\u0588\u058a\u058f\u05be\u05c3\u05c6\u05d0\u05d1\u05d2\u05d3\u05d4\u05d5\u05d6\u05d7\u05d8\u05d9\u05da\u05db\u05dc\u05dd\u05de\u05df\u05e0\u05e1\u05e2\u05e3\u05e4\u05e5"
            + "\u05e6\u05e7\u05e8\u05e9\u05ea\u05f0\u05f1\u05f2\u05f3\u05f4\u07c8\u10a0\u10a1\u10a2\u10a3\u10a4\u10a5\u10a6\u10a7\u10a8\u10a9\u10aa\u10ab\u10ac\u10ad\u10ae\u10af\u10b0\u10b1\u10b2\u10b3\u10b4\u10b5\u10b6\u10b7\u10b8\u10b9\u10ba\u10bb\u10bc\u10bd\u10be\u10bf\u10c0\u10c1\u10c2\u10c3\u10c4\u10c5\u10c7\u10cd\u10d0\u10d1\u10d2\u10d3\u10d4\u10d5\u10d6\u10d7\u10d8\u10d9\u10da\u10db\u10dc\u10dd\u10de\u10df\u10e0\u10e1\u10e2\u10e3\u10e4\u10e5\u10e6\u10e7\u10e8\u10e9\u10ea\u10eb\u10ec\u10ed\u10ee\u10ef\u10f0\u10f1\u10f2\u10f3\u10f4\u10f5\u10f6\u10f7\u10f8\u10f9\u10fa\u10fb\u10fc\u10fd\u10fe\u10ff\u15e1\u15fa\u1614\u16a0\u16a1\u16a2\u16a3\u16a4\u16a5\u16a6\u16a7\u16a8\u16a9\u16aa\u16ab\u16ac\u16ad\u16ae\u16af\u16b0\u16b1"
            + "\u16b2\u16b3\u16b4\u16b5\u16b6\u16b7\u16b8\u16b9\u16ba\u16bb\u16bc\u16bd\u16be\u16bf\u16c0\u16c1\u16c2\u16c3\u16c4\u16c5\u16c6\u16c7\u16c8\u16c9\u16ca\u16cb\u16cc\u16cd\u16ce\u16cf\u16d0\u16d1\u16d2\u16d3\u16d4\u16d5\u16d6\u16d7\u16d8\u16d9\u16da\u16db\u16dc\u16dd\u16de\u16df\u16e0\u16e1\u16e2\u16e3\u16e4\u16e5\u16e6\u16e7\u16e8\u16e9\u16ea\u16eb\u16ec\u16ed\u16ee\u16ef\u16f0\u16f1\u16f2\u16f3\u16f4\u16f5\u16f6\u16f7\u16f8\u1d00\u1d02\u1d04\u1d05\u1d07\u1d09\u1d0a\u1d0b\u1d0d\u1d0f\u1d14\u1d18\u1d1a\u1d1b\u1d1c\u1d20\u1d21\u1d22\u1d6b\u1d6c\u1d6d\u1d6e\u1d6f\u1d70\u1d71\u1d72\u1d73\u1d74\u1d75\u1d76\u1d77\u1d7a\u1d80\u1d81\u1d82\u1d83\u1d84\u1d85\u1d86\u1d87\u1d88\u1d89\u1d8a\u1d8b\u1d8c\u1d8d\u1d8e\u1d8f\u1d90"
            + "\u1d91\u1d92\u1d93\u1d94\u1d95\u1d96\u1d97\u1d98\u1d99\u1d9a\u1e00\u1e01\u1e02\u1e03\u1e04\u1e05\u1e06\u1e07\u1e08\u1e09\u1e0a\u1e0b\u1e0c\u1e0d\u1e0e\u1e0f\u1e10\u1e11\u1e12\u1e13\u1e14\u1e15\u1e16\u1e17\u1e18\u1e19\u1e1a\u1e1b\u1e1c\u1e1d\u1e1e\u1e1f\u1e20\u1e21\u1e22\u1e23\u1e24\u1e25\u1e26\u1e27\u1e28\u1e29\u1e2a\u1e2b\u1e2c\u1e2d\u1e2e\u1e2f\u1e30\u1e31\u1e32\u1e33\u1e34\u1e35\u1e36\u1e37\u1e38\u1e39\u1e3a\u1e3b\u1e3c\u1e3d\u1e3e\u1e3f\u1e40\u1e41\u1e42\u1e43\u1e44\u1e45\u1e46\u1e47\u1e48\u1e49\u1e4a\u1e4b\u1e4c\u1e4d\u1e4e\u1e4f\u1e50\u1e51\u1e52\u1e53\u1e54\u1e55\u1e56\u1e57\u1e58\u1e59\u1e5a\u1e5b\u1e5c\u1e5d\u1e5e\u1e5f\u1e60\u1e61\u1e62\u1e63\u1e64\u1e65\u1e66\u1e67\u1e68\u1e69\u1e6a\u1e6b\u1e6c\u1e6d"
            + "\u1e6e\u1e6f\u1e70\u1e71\u1e72\u1e73\u1e74\u1e75\u1e76\u1e77\u1e78\u1e79\u1e7a\u1e7b\u1e7c\u1e7d\u1e7e\u1e7f\u1e80\u1e81\u1e82\u1e83\u1e84\u1e85\u1e86\u1e87\u1e88\u1e89\u1e8a\u1e8b\u1e8c\u1e8d\u1e8e\u1e8f\u1e90\u1e91\u1e92\u1e93\u1e94\u1e95\u1e96\u1e97\u1e98\u1e99\u1e9a\u1e9b\u1e9c\u1e9d\u1e9e\u1e9f\u1ea0\u1ea1\u1ea2\u1ea3\u1ea4\u1ea5\u1ea6\u1ea7\u1ea8\u1ea9\u1eaa\u1eab\u1eac\u1ead\u1eae\u1eaf\u1eb0\u1eb1\u1eb2\u1eb3\u1eb4\u1eb5\u1eb6\u1eb7\u1eb8\u1eb9\u1eba\u1ebb\u1ebc\u1ebd\u1ebe\u1ebf\u1ec0\u1ec1\u1ec2\u1ec3\u1ec4\u1ec5\u1ec6\u1ec7\u1ec8\u1ec9\u1eca\u1ecb\u1ecc\u1ecd\u1ece\u1ecf\u1ed0\u1ed1\u1ed2\u1ed3\u1ed4\u1ed5\u1ed6\u1ed7\u1ed8\u1ed9\u1eda\u1edb\u1edc\u1edd\u1ede\u1edf\u1ee0\u1ee1\u1ee2\u1ee3\u1ee4\u1ee5"
            + "\u1ee6\u1ee7\u1ee8\u1ee9\u1eea\u1eeb\u1eec\u1eed\u1eee\u1eef\u1ef0\u1ef1\u1ef2\u1ef3\u1ef4\u1ef5\u1ef6\u1ef7\u1ef8\u1ef9\u1efa\u1efb\u1efc\u1efd\u1efe\u1eff\u1f00\u1f01\u1f02\u1f03\u1f04\u1f05\u1f06\u1f07\u1f08\u1f09\u1f0a\u1f0b\u1f0c\u1f0d\u1f0e\u1f0f\u1f10\u1f11\u1f12\u1f13\u1f14\u1f15\u1f18\u1f19\u1f1a\u1f1b\u1f1c\u1f1d\u1f20\u1f21\u1f22\u1f23\u1f24\u1f25\u1f26\u1f27\u1f28\u1f29\u1f2a\u1f2b\u1f2c\u1f2d\u1f2e\u1f2f\u1f30\u1f31\u1f32\u1f33\u1f34\u1f35\u1f36\u1f37\u1f38\u1f39\u1f3a\u1f3b\u1f3c\u1f3d\u1f3e\u1f3f\u1f40\u1f41\u1f42\u1f43\u1f44\u1f45\u1f48\u1f49\u1f4a\u1f4b\u1f4c\u1f4d\u1f50\u1f51\u1f52\u1f53\u1f54\u1f55\u1f56\u1f57\u1f59\u1f5b\u1f5d\u1f5f\u1f60\u1f61\u1f62\u1f63\u1f64\u1f65\u1f66\u1f67\u1f68\u1f69"
            + "\u1f6a\u1f6b\u1f6c\u1f6d\u1f6e\u1f6f\u1f70\u1f71\u1f72\u1f73\u1f74\u1f75\u1f76\u1f77\u1f78\u1f79\u1f7a\u1f7b\u1f7c\u1f7d\u1f80\u1f81\u1f82\u1f83\u1f84\u1f85\u1f86\u1f87\u1f88\u1f89\u1f8a\u1f8b\u1f8c\u1f8d\u1f8e\u1f8f\u1f90\u1f91\u1f92\u1f93\u1f94\u1f95\u1f96\u1f97\u1f98\u1f99\u1f9a\u1f9b\u1f9c\u1f9d\u1f9e\u1f9f\u1fa0\u1fa1\u1fa2\u1fa3\u1fa4\u1fa5\u1fa6\u1fa7\u1fa8\u1fa9\u1faa\u1fab\u1fac\u1fad\u1fae\u1faf\u1fb0\u1fb1\u1fb2\u1fb3\u1fb4\u1fb6\u1fb7\u1fb8\u1fb9\u1fba\u1fbb\u1fbc\u1fc2\u1fc3\u1fc4\u1fc6\u1fc7\u1fc8\u1fc9\u1fca\u1fcb\u1fcc\u1fd0\u1fd1\u1fd2\u1fd3\u1fd6\u1fd7\u1fd8\u1fd9\u1fda\u1fdb\u1fe0\u1fe1\u1fe2\u1fe3\u1fe4\u1fe5\u1fe6\u1fe7\u1fe8\u1fe9\u1fea\u1feb\u1fec\u1ff2\u1ff3\u1ff4\u1ff6\u1ff7\u1ff8\u1ff9"
            + "\u1ffa\u1ffb\u1ffc\u200c\u2010\u2011\u2013\u2014\u2018\u2019\u201a\u201c\u201d\u201e\u2020\u2021\u2022\u2026\u2030\u2031\u2032\u2033\u2034\u2035\u2036\u2037\u2039\u203a\u203b\u203c\u203d\u2042\u2047\u2048\u2049\u204a\u204b\u204e\u204f\u2051\u2052\u2057\u2070\u2071\u2074\u2075\u2076\u2077\u2078\u2079\u207a\u207b\u207c\u207d\u207e\u207f\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089\u208a\u208b\u208c\u208d\u208e\u20a0\u20a1\u20a2\u20a3\u20a4\u20a5\u20a6\u20a7\u20a8\u20a9\u20aa\u20ab\u20ac\u20ad\u20ae\u20af\u20b0\u20b1\u20b2\u20b3\u20b4\u20b5\u20b6\u20b7\u20b8\u20b9\u20ba\u20bb\u20bc\u20bd\u20be\u20bf\u2116\u2117\u2122\u2132\u2139\u2141\u2144\u214b\u2150\u2151\u2152\u2153\u2154\u2155\u2156\u2157\u2158"
            + "\u2159\u215a\u215b\u215c\u215d\u215e\u215f\u2189\u2190\u2191\u2192\u2193\u2194\u21bb\u21c4\u21cf\u21d2\u21d4\u21f5\u2200\u2201\u2202\u2203\u2204\u2205\u2208\u2209\u220b\u220c\u2211\u2212\u2213\u2219\u221a\u221b\u221c\u221d\u221e\u2225\u2227\u2228\u2229\u222b\u222e\u2234\u2235\u2248\u2254\u2260\u2261\u2262\u2264\u2265\u2282\u2283\u2284\u2285\u2286\u2287\u22a2\u22a4\u22a5\u22a8\u22bb\u22bc\u22bd\u22c3\u22c6\u2300\u2302\u2318\u231a\u231b\u2320\u2321\u23cf\u23e9\u23ea\u23ed\u23ee\u23ef\u23f3\u23f4\u23f5\u23f6\u23f7\u23f8\u23f9\u23fa\u23fb\u23fc\u23fd\u2460\u2461\u2462\u2463\u2464\u2465\u2466\u2467\u2468\u2469\u246a\u246b\u246c\u246d\u246e\u246f\u2470\u2471\u2472\u2473\u24b6\u24b7\u24b8\u24b9\u24ba\u24bb\u24bc\u24bd"
            + "\u24be\u24bf\u24c0\u24c1\u24c2\u24c3\u24c4\u24c5\u24c6\u24c7\u24c8\u24c9\u24ca\u24cb\u24cc\u24cd\u24ce\u24cf\u24d0\u24d1\u24d2\u24d3\u24d4\u24d5\u24d6\u24d7\u24d8\u24d9\u24da\u24db\u24dc\u24dd\u24de\u24df\u24e0\u24e1\u24e2\u24e3\u24e4\u24e5\u24e6\u24e7\u24e8\u24e9\u24ea\u2500\u2502\u250c\u2510\u2514\u2518\u251c\u2524\u252c\u2534\u253c\u2550\u2551\u2552\u2553\u2554\u2555\u2556\u2557\u2558\u2559\u255a\u255b\u255c\u255d\u255e\u255f\u2560\u2561\u2562\u2563\u2564\u2565\u2566\u2567\u2568\u2569\u256a\u256b\u256c\u2580\u2584\u2588\u258c\u2590\u2591\u2592\u2593\u25a0\u25a1\u25b2\u25b3\u25b6\u25b7\u25b8\u25bc\u25bd\u25c0\u25c1\u25c2\u25c6\u25c7\u25cb\u25ce\u25cf\u25d8\u25e6\u2600\u2601\u2602\u2603\u2604\u2605\u2606\u2608"
            + "\u2610\u2611\u2612\u2614\u2618\u261c\u261e\u2620\u262e\u262f\u2630\u2631\u2632\u2633\u2634\u2635\u2636\u2637\u2639\u263a\u263b\u263d\u2640\u2642\u2648\u2660\u2661\u2662\u2663\u2664\u2665\u2666\u2667\u2669\u266a\u266b\u266c\u266d\u266e\u266f\u2680\u2681\u2682\u2683\u2684\u2685\u2690\u2691\u2693\u2694\u2697\u26a0\u26a1\u26a5\u26c4\u26c8\u26cf\u26e8\u2702\u2709\u270e\u2713\u2714\u2717\u2718\u2726\u2731\u273f\u2744\u274c\u2763\u2764\u279c\u27d8\u2928\u295d\u29c8\u2b50\u2b58\u2bea\u2beb\u2c62\u2c65\u2c66\u2c6d\u2c6f\u2c71\u2c7e\u2c7f\u2d00\u2d01\u2d02\u2d03\u2d04\u2d05\u2d06\u2d07\u2d08\u2d09\u2d0a\u2d0b\u2d0c\u2d0d\u2d0e\u2d0f\u2d10\u2d11\u2d12\u2d13\u2d14\u2d15\u2d16\u2d17\u2d18\u2d19\u2d1a\u2d1b\u2d1c\u2d1d\u2d1e"
            + "\u2d1f\u2d20\u2d21\u2d22\u2d23\u2d24\u2d25\u2e18\u2e2e\u2e35\u2e38\u2e41\u2e4b\u3012\u3125\ua680\ua681\ua682\ua683\ua684\ua685\ua686\ua687\ua688\ua689\ua68a\ua68b\ua68c\ua68d\ua68e\ua68f\ua690\ua691\ua692\ua693\ua694\ua695\ua696\ua697\ua726\ua727\ua728\ua729\ua730\ua731\ua732\ua733\ua734\ua735\ua736\ua737\ua738\ua739\ua73a\ua73b\ua73c\ua73d\ua73e\ua73f\ua74e\ua74f\ua75a\ua75b\ua760\ua761\ua779\ua780\ua781\ua794\ua795\ua7a8\ua7a9\ua7af\ua7b0\ua7c4\ua7c6\uab50\uab51\uab63\ufb00\ufb01\ufb02\ufb03\ufb04\ufb05\ufb06\ufb13\ufb14\ufb15\ufb16\ufb17\ufb1d\ufb1f\ufb2b\ufb2e\ufb2f\ufb31\ufb35\ufb3b\ufb44\ufb4a\ufb4b\ufb4c\ufb4e\uff0b\ufffd\u10330\u10331\u10332\u10333\u10334\u10335\u10336\u10337\u10338\u10339\u1033a\u1033b\u1033c\u1033d"
            + "\u1033e\u1033f\u10340\u10341\u10342\u10343\u10344\u10345\u10346\u10347\u10348\u10349\u1034a\u1f30a\u1f327\u1f356\u1f3a3\u1f3f9\u1f514\u1f525\u1f531\u1f5e1\u1f6e1\u1f9ea\u1fa93\u1faa3";
    static final String TEXT_ADV = "424666624446262666666666662256567666666664666666666666666664646636666656626536666666466666642472668626485764865655367245"
            + "78886666666:6666644447666666666666666666666:6666633446666666666666666666666666666666877666666666666666666668755445543426"
            + "5666556363646475666666866666666::666666666666666465646666666666666666666666647866776777866666676769446546676688888666666"
            + "4746886678666666666662462:::::8:::664466666666666666666::7766656666666:::66966666::6666666666665566666666666666646666686"
            + "666666666666666664856::666766666786677777778866666677668668657666666643465366667766:866676666666676744866666676666666666"
            + "76656766:::989:886678222328288688866666666646666666666666664666636666666663566666666766666846668:76668666446877666666667"
            + "686666666666666867689786686666576666656666666666666677766866675662468775666776677777777666676659766766676767777997766666"
            + "666778777666688884866577667766776666::66666686666666666666666666666666658776666669988779999667766669999::666666999977777"
            + "7::777766776666776576666767666666676666656666333434666677675667637666676767666666678665876677666246656624662666662466666"
            + "666664443566886688866876887866866666668666686666866666666666686666666666666666666656766666666644666666556666554555644665"
            + "455566656664646247664656462466455455666445666786866662658326666668536666:66626666:6666666:886888888666967576377666667777"
            + "777673767666666666666666666666666666666665666666666666554465656563646464666666666666666666666666666666666666666666666464"
            + "646466666666666666666666666666666666666664666455666666666666666666666666666666666666666666444266666666666666888888888866"
            + "668888888888666666668765676666676699::::9966666699::::6666666699::::99436666557:98987766666699::::666666669::98888888899"
            + "::::9:666666436666886666666699::::996666666699::::998888888899::::9:6666666669966666699996544455547766666666669998888899"
            + "99604479333555663889357357548468:885742449525555554443355455555555444336667668::887776:6868866866786776:896:66699:99999:"
            + "88999979868698899978566686666666678878466666667867766666666666666664888888567788784466466882::::::::::::::::::::::::::::"
            + ":::::::::::::::::::::::::::::::::::::::::::::969696969999899968899968899968899999999999959899666677566775666854499888888"
            + ":::88::8::66666666668946866666666468844688888888888:66799888887778888768869688888876668666776667666666677666666666666676"
            + "6666666662626667766:9667777667666776697668766:::::::9:9:966::65666636776666766:8679:6::::::24666645663666866666666626666"
            + "66666666666669999987998878";
}
