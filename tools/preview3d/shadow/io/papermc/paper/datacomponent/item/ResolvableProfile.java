package io.papermc.paper.datacomponent.item;

import io.papermc.paper.datacomponent.DataComponentBuilder;
import net.kyori.adventure.key.Key;
import org.bukkit.profile.PlayerTextures;

import java.util.function.Consumer;

/** Ersatz für die Vorschau: merkt sich Name und Skin. */
public interface ResolvableProfile {
    String name();

    SkinPatch skinPatch();

    static Builder resolvableProfile() {
        return new Builder() {
            String name;
            SkinPatch patch;

            @Override
            public Builder name(String n) {
                name = n;
                return this;
            }

            @Override
            public Builder skinPatch(Consumer<SkinPatchBuilder> c) {
                Key[] body = new Key[1];
                PlayerTextures.SkinModel[] model = new PlayerTextures.SkinModel[1];
                c.accept(new SkinPatchBuilder() {
                    @Override
                    public SkinPatchBuilder body(Key k) {
                        body[0] = k;
                        return this;
                    }

                    @Override
                    public SkinPatchBuilder model(PlayerTextures.SkinModel m) {
                        model[0] = m;
                        return this;
                    }

                    @Override
                    public SkinPatch build() {
                        return null;
                    }
                });
                patch = new SkinPatch() {
                    public Key body() {
                        return body[0];
                    }

                    public PlayerTextures.SkinModel model() {
                        return model[0];
                    }
                };
                return this;
            }

            @Override
            public ResolvableProfile build() {
                String n = name;
                SkinPatch p = patch;
                return new ResolvableProfile() {
                    public String name() {
                        return n;
                    }

                    public SkinPatch skinPatch() {
                        return p;
                    }

                    @Override
                    public String toString() {
                        return "profile:" + (p != null && p.body() != null ? p.body().asString() + ":" + p.model() : n);
                    }
                };
            }
        };
    }

    interface Builder extends DataComponentBuilder<ResolvableProfile> {
        Builder name(String n);

        Builder skinPatch(Consumer<SkinPatchBuilder> c);
    }

    interface SkinPatch {
        Key body();

        PlayerTextures.SkinModel model();
    }

    interface SkinPatchBuilder extends DataComponentBuilder<SkinPatch> {
        SkinPatchBuilder body(Key k);

        SkinPatchBuilder model(PlayerTextures.SkinModel m);
    }
}
