#version 330

// Bildeffekte der Rematch-Mod (ein Durchgang). Werte kommen jedes Bild aus der Mod (ScreenFx):
// Fx0: x Treffer-Stärke (0..1, klingt ab), y Zeit seit dem Treffer (s), zw Treffpunkt auf dem Bildschirm (0..1)
// Fx1: x Speedlines (0..1), y Treffer-Bild (gemalte Duoton-Farben, 0..1), z Blitz (0..1), w Vignette (0..1)
// Fx2: x Uhr (s), y Seitenverhältnis (Breite/Höhe), z Schockwelle (0..1), w frei

uniform sampler2D InSampler;

layout(std140) uniform KubyFx {
    vec4 Fx0;
    vec4 Fx1;
    vec4 Fx2;
};

in vec2 texCoord;

out vec4 fragColor;

float hash(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

void main() {
    float strength = Fx0.x;
    float t = Fx0.y;
    vec2 center = Fx0.zw;
    float aspect = max(Fx2.y, 0.1);

    vec2 uv = texCoord;
    vec2 d = uv - center;
    vec2 da = vec2(d.x * aspect, d.y);
    float r = length(da);
    vec2 dir = r > 1e-4 ? vec2(da.x / aspect, da.y) / r : vec2(0.0);

    // Schockwelle: Ring läuft vom Treffpunkt nach außen und verzerrt das Bild
    float waveR = t * 1.7;
    float wave = Fx2.z * exp(-pow((r - waveR) / 0.05, 2.0)) * (1.0 - clamp(t / 0.55, 0.0, 1.0));
    uv -= dir * wave * 0.035;

    // Radiale Zoom-Unschärfe zum Treffpunkt mit Farbsäumen
    float blur = strength * 0.075 + Fx1.x * 0.012;
    float ab = strength * 0.009 + Fx1.x * 0.003;
    vec3 col = vec3(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 10; i++) {
        float k = float(i) / 9.0;
        vec2 p = center + (uv - center) * (1.0 - blur * k);
        vec2 off = (p - center) * ab * 3.0;
        float w = 1.0 - k * 0.65;
        col.r += texture(InSampler, p + off).r * w;
        col.g += texture(InSampler, p).g * w;
        col.b += texture(InSampler, p - off).b * w;
        wsum += w;
    }
    col /= wsum;

    // Helle Stellen glühen auf (Bloom-Ersatz), warm getönt
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col += (strength * 0.7 + Fx1.z) * max(0.0, lum - 0.5) * vec3(1.0, 0.9, 0.7) * 1.6;
    // Schockwellen-Ring leuchtet leicht
    col += wave * vec3(1.0, 0.85, 0.55) * 0.35;

    // Blitz: warmes Weiß aus dem Treffpunkt
    col = mix(col, vec3(1.0, 0.97, 0.88), clamp(Fx1.z * (1.0 - smoothstep(0.0, 1.3, r)) * 0.9, 0.0, 1.0));

    // Speedlines: feine Strahlen vom Bildrand zur Mitte, flackern pro Bild
    if (Fx1.x > 0.001) {
        vec2 q = vec2((texCoord.x - 0.5) * aspect, texCoord.y - 0.5);
        float ang = atan(q.y, q.x);
        float rr = length(q);
        float sectors = 110.0;
        float a = (ang + 3.14159265) / 6.2831853 * sectors;
        float id = floor(a);
        float frame = floor(Fx2.x * 20.0);
        float rnd = hash(id + frame * 17.31);
        float inner = 0.30 + 0.18 * hash(id * 3.7 + frame);
        float line = step(0.72, rnd) * smoothstep(inner, inner + 0.35, rr);
        float f = fract(a);
        line *= smoothstep(0.0, 0.4, f) * smoothstep(1.0, 0.6, f);
        col = mix(col, vec3(1.0, 0.98, 0.94), line * Fx1.x * 0.6);
    }

    // Treffer-Bild: gemalte Duoton-Farben (dunkles Blau-Lila, warmes Orange, helles Creme)
    if (Fx1.y > 0.001) {
        float l = dot(col, vec3(0.299, 0.587, 0.114));
        vec3 duo = l > 0.62 ? vec3(1.0, 0.95, 0.84) : (l > 0.54 ? vec3(0.96, 0.46, 0.17) : vec3(0.09, 0.06, 0.18));
        col = mix(col, duo, Fx1.y);
    }

    // Vignette
    float vig = smoothstep(0.42, 1.0, length(vec2((texCoord.x - 0.5) * aspect, texCoord.y - 0.5)) * 1.15);
    col *= 1.0 - vig * Fx1.w * 0.65;

    fragColor = vec4(col, 1.0);
}
