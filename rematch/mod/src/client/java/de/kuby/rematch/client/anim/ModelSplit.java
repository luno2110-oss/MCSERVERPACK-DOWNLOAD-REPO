package de.kuby.rematch.client.anim;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Teilt Rumpf, Arme und Beine des Spielermodells in eine obere und untere Hälfte (je 6 Pixel), damit Ellbogen,
 * Knie und Bauch biegen können. Die obere Hälfte bleibt das Minecraft-Teil (gleicher Name, gleicher Drehpunkt),
 * die untere hängt als Kind "kuby_lower" daran. In Ruhe sieht das Modell genau aus wie vorher.
 * Hautstellen: Box-UV des halben Würfels trifft genau die obere bzw. untere Hälfte der Seiten; Hand-, Fuß- und
 * Bauchunterseite bekommen einen flachen Deckel mit der richtigen Hautstelle.
 */
public final class ModelSplit {
    public static final String LOWER = "kuby_lower";
    public static final String LOWER_LAYER = "kuby_lower_layer";
    private static final CubeDeformation NONE = CubeDeformation.NONE;
    private static final CubeDeformation LAYER = new CubeDeformation(0.25f);

    private ModelSplit() { }

    public static void apply(MeshDefinition mesh, boolean slim) {
        PartDefinition root = mesh.getRoot();
        // Rumpf: Drehpunkt Hals (0,0,0), Brust oben, Bauch als Kind mit Drehpunkt an der Hüfte (0,12,0)
        limb(root, "body", "jacket", 0, 0, -4, 0, -2, 8, 4, 16, 16, 16, 32, 0, 12, 0);
        int aw = slim ? 3 : 4;
        // Arme: Drehpunkt Schulter (±5,2,0), Ellbogen bei (±6,6,0) im Modell
        limb(root, "right_arm", "right_sleeve", -5, 2, slim ? -2 : -3, -2, -2, aw, 4, 40, 16, 40, 32, -6, 6, 0);
        limb(root, "left_arm", "left_sleeve", 5, 2, -1, -2, -2, aw, 4, 32, 48, 48, 48, 6, 6, 0);
        // Beine: Drehpunkt Hüfte (±1.9,12,0), Knie bei (±1.9,18,0)
        limb(root, "right_leg", "right_pants", -1.9f, 12, -2, 0, -2, 4, 4, 0, 16, 0, 32, -1.9f, 18, 0);
        limb(root, "left_leg", "left_pants", 1.9f, 12, -2, 0, -2, 4, 4, 16, 48, 0, 48, 1.9f, 18, 0);
    }

    /**
     * Ein 12 Pixel langes Glied (Breite w, Tiefe d) mit Drehpunkt (px,py) und Würfel ab (x0,y0,z0) relativ dazu.
     * (lx,ly,lz): Drehpunkt der unteren Hälfte im Modell (absolut).
     */
    private static void limb(PartDefinition root, String name, String layer, float px, float py, float x0, float y0, float z0,
                             int w, int d, int u, int v, int lu, int lv, float lx, float ly, float lz) {
        PartDefinition upper = root.addOrReplaceChild(name,
                CubeListBuilder.create().texOffs(u, v).addBox(x0, y0, z0, w, 6, d, NONE), PartPose.offset(px, py, 0));
        upper.addOrReplaceChild(layer,
                CubeListBuilder.create().texOffs(lu, lv).addBox(x0, y0, z0, w, 6, d, LAYER), PartPose.ZERO);
        // untere Hälfte relativ zu ihrem Drehpunkt
        float rx = lx - px, ry = ly - py, rz = lz;
        float bx = x0 - rx, by = y0 + 6 - ry, bz = z0 - rz;
        PartDefinition lower = upper.addOrReplaceChild(LOWER,
                CubeListBuilder.create()
                        .texOffs(u, v + 6).addBox(bx, by, bz, w, 6, d, NONE)
                        // Deckel unten (Handfläche, Fußsohle, Rumpfunterseite): Boden-Hautstelle des ganzen Glieds
                        .texOffs(u, v).addBox(bx, by + 6.01f, bz, w, 0, d, NONE),
                PartPose.offset(rx, ry, rz));
        lower.addOrReplaceChild(LOWER_LAYER,
                CubeListBuilder.create()
                        .texOffs(lu, lv + 6).addBox(bx, by, bz, w, 6, d, LAYER)
                        .texOffs(lu, lv).addBox(bx, by + 6.01f, bz, w, 0, d, LAYER),
                PartPose.ZERO);
    }
}
