"""Freestyle-Animationen im Rematch-Stil auf dem Spieler-Gerüst (Minecraft-Maße).

Vorbild: Rematch-Clips des Besitzers (Release Rematch_Ref). Merkmale: Arme weit (T-Haltung) beim Ausholen, Körper
kippt seitlich über das Standbein, Bein peitscht durch und zieht hoch quer über den Körper, kleiner Hüpfer,
Landung auf dem Schussbein. Kontakt mit Marke (Hit-Stop, Blitz, Kamera-Wackeln macht die Mod).

Aufruf: python3 tools/bbanim/freestyle.py   (schreibt rematch/animationen/kuby_animationen.bbmodel und die JSON der Mod)
"""
import json
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import animkit as K  # noqa: E402
import render_bbmodel as R  # noqa: E402
import polish_shots as PS  # noqa: E402
import export_anim  # noqa: E402

VORLAGE = os.path.join(ROOT, "rematch", "animationen", "vorlage_spieler.bbmodel")
ZIEL = os.path.join(ROOT, "rematch", "animationen", "kuby_animationen.bbmodel")
MOD_ANIM = os.path.join(ROOT, "rematch", "mod", "src", "main", "resources", "assets", "kubyrematch", "rematch", "anim")

G = 330.0  # Schwerkraft in Pixel/s² für dieses Gerüst (knackige Sprünge)


def stand(c, f, look=(0, 0, 0)):
    c.pose(f, hipR=(3, 0, 2), hipL=(-3, 0, -2), kneeR=(-6,), kneeL=(-5,), shR=(3, 0, 7), shL=(-2, 0, -7),
           elR=(14,), elL=(12,), spine=(-3, 0, 0), chest=(1, 0, 0), head=look, legs=(0, 0, 0), torso1=(0, 0, 0),
           root=(0, 0, 0))


def schuss_hart(model):
    """Harter Schuss mit rechts aus dem Lauf: explosiv, Arme weit, Körper seitlich gekippt, Bein zieht hoch durch."""
    c = K.Clip(model, "schuss_hart", 38)
    P = c.pose
    # Anlauf: links setzt auf, rechts holt nach
    P(0, hipL=(26,), kneeL=(-12,), hipR=(-36,), kneeR=(-84,), shR=(42, 0, 10), elR=(74,), shL=(-40, 0, -10),
      elL=(62,), spine=(-16, -4, 0), torso1=(-4, 0, 0), chest=(-3, 4, 0), head=(-4, 2, 0), legs=(0, 4, 0),
      root=(0, 0, 0))
    P(2, hipL=(-8,), kneeL=(-20,), hipR=(20,), kneeR=(-110,), shR=(10, 0, 12), elR=(70,), shL=(-8, 0, -12),
      elL=(64,), spine=(-17, 0, 0), chest=(-4, 0, 0), head=(-6, 0, 0), legs=(0, 0, 0))
    # vorletzter Schritt rechts
    P(4, hipR=(46,), kneeR=(-26,), hipL=(-34,), kneeL=(-72,), shR=(-34, 0, 14), elR=(56,), shL=(36, 0, -12),
      elL=(70,), spine=(-15, 6, 0), chest=(-3, -4, 0), head=(-8, -2, 0), legs=(0, -6, 0))
    P(5, hipR=(30,), kneeR=(-22,), hipL=(-40,), kneeL=(-96,), shR=(-30, 0, 18), elR=(50,), shL=(32, 0, -16),
      elL=(64,), spine=(-14, 6, 0), chest=(-2, -4, 0), head=(-10, -2, 0), legs=(0, -6, 0))
    # langer letzter Satz auf links, rechtes Bein spannt schon, Arme gehen auf
    P(7, hipL=(52,), kneeL=(-22,), hipR=(-30,), kneeR=(-96,), shR=(-24, 0, 50), elR=(30,), shL=(26, 0, -46),
      elL=(30,), spine=(-6, -8, 6), torso1=(2, 0, 4), chest=(0, -6, 2), head=(-16, 6, -4), legs=(0, -10, 0))
    # Aufsetzen links neben dem Ball: Ausholen voll, Ferse hoch, Arme T, Körper zurück und nach links gekippt
    P(9, hipL=(30, 0, -4), kneeL=(-20,), hipR=(-56, 0, 10), kneeR=(-126,), shR=(-26, 0, 84), elR=(14,),
      shL=(22, 0, -82), elL=(12,), spine=(4, -14, 12), torso1=(4, 0, 4), chest=(4, -10, 4), head=(-30, 12, -8),
      legs=(0, -18, 0), root=(0, 0, 4))
    P(10, hipL=(26, 0, -4), kneeL=(-24,), hipR=(-60, 0, 10), kneeR=(-132,), shR=(-30, 0, 88), elR=(12,),
      shL=(26, 0, -84), elL=(10,), spine=(6, -16, 13), torso1=(5, 0, 5), chest=(5, -12, 4), head=(-32, 14, -8),
      legs=(0, -20, 0), root=(0, 0, 6))
    # Peitsche: Oberschenkel schnellt vor, Unterschenkel hängt nach
    P(11, hipL=(22, 0, -4), kneeL=(-24,), hipR=(6, 0, 4), kneeR=(-104,), shR=(-6, 0, 76), elR=(16,),
      shL=(4, 0, -82), elL=(12,), spine=(0, -4, 15), torso1=(2, 0, 6), chest=(0, -2, 4), head=(-30, 6, -10),
      legs=(0, -6, 0), root=(0, 0, 10))
    # Kontakt: Knie fast gestreckt, Körper stark nach links gekippt, Kopf über dem Ball
    P(12, hipL=(20, 0, -4), kneeL=(-22,), hipR=(38, 0, -4), kneeR=(-14,), shR=(18, 0, 64), elR=(22,),
      shL=(-22, 0, -80), elL=(14,), spine=(-4, 6, 16), torso1=(-2, 0, 6), chest=(-2, 4, 4), head=(-28, 0, -10),
      legs=(0, 4, 0), root=(0, 0, 14))
    # Durchziehen: Bein hoch und quer über den Körper, Standbein streckt (Absprung), Oberkörper dreht mit
    P(14, hipL=(-8, 0, -2), kneeL=(-8,), hipR=(88, 0, -16), kneeR=(-6,), shR=(42, -20, 34), elR=(48,),
      shL=(-46, 0, -72), elL=(18,), spine=(-2, 22, 10), torso1=(0, 0, 4), chest=(2, 10, 2), head=(-12, 8, -4),
      legs=(0, 16, 0), root=(0, 0, 10))
    P(16, hipL=(-22, 0, -2), kneeL=(-46,), hipR=(104, 0, -20), kneeR=(-12,), shR=(46, -24, 26), elR=(54,),
      shL=(-52, 0, -62), elL=(22,), spine=(0, 28, 7), torso1=(2, 0, 2), chest=(3, 12, 1), head=(-4, 12, 0),
      legs=(0, 22, 0), root=(0, 0, 6))
    # Bein kommt runter, Landung auf dem Schussbein
    P(19, hipL=(-32,), kneeL=(-72,), hipR=(34, 0, -6), kneeR=(-22,), shR=(22, -10, 22), elR=(48,),
      shL=(-24, 0, -40), elL=(30,), spine=(-6, 20, 3), torso1=(0, 0, 0), chest=(0, 8, 0), head=(-2, 10, 0),
      legs=(0, 16, 0), root=(0, 0, 0))
    P(21, hipL=(-24,), kneeL=(-54,), hipR=(16,), kneeR=(-38,), shR=(12, 0, 16), elR=(40,), shL=(-12, 0, -26),
      elL=(32,), spine=(-12, 14, 0), chest=(-2, 6, 0), head=(0, 8, 0), legs=(0, 12, 0))
    # Schritt links, aufrichten mit Blick zum Tor (Stil statt steif: Brust raus, Schultern locker)
    P(24, hipL=(18,), kneeL=(-16,), hipR=(-12,), kneeR=(-24,), shR=(-4, 0, 14), elR=(30,), shL=(6, 0, -16),
      elL=(30,), spine=(-8, 8, 0), chest=(0, 4, 0), head=(2, 6, 0), legs=(0, 6, 0))
    P(28, hipL=(8, 0, -3), kneeL=(-10,), hipR=(-6, 0, 3), kneeR=(-12,), shR=(0, 0, 10), elR=(20,), shL=(2, 0, -10),
      elL=(18,), spine=(-4, 4, 0), chest=(3, 2, 0), head=(4, 4, 0), legs=(0, 2, 0))
    stand(c, 38, look=(2, 0, 0))
    # Wurzel: Standfuß bleibt kleben, Flugphasen als Parabel
    c.phase(0, 2, "L")
    c.phase(2, 4, "air", land="R", carry=1.0, g=G)
    c.phase(4, 5, "R")
    c.phase(5, 9, "air", land="L", carry=1.25, g=G)
    c.phase(9, 14, "L")
    c.phase(14, 21, "air", land="R", drift=(-1.5, 0, -10), g=G * 1.2)
    c.phase(21, 24, "R")
    c.phase(24, 38, "LR", anchor="R")
    c.root_start = np.array([0.0, 0.0, 34.0])
    c.solve_root()
    contact = 12
    c.holds = [(contact, 1)]
    c.markers = [(contact, 0)]
    # Ball: rollt vor dem Spieler her (langsamer als er), liegt im Kontaktbild am Spann, fliegt flach steigend weg
    hit = PS.touch(c, contact, "R", (0.15, 0.25, -1.0), gap=0.1)
    hit[1] = max(hit[1], PS.BALL_R)
    for f in range(0, contact + 1, 3):
        c.ball_at(f, hit + np.array([0.0, 0.0, 22.0 * (1 - f / contact)]))
    c.ball_at(contact, hit)
    c.key("ball", 0, "rotation", (0, 0, 0), "linear")
    c.key("ball", contact, "rotation", (-540, 0, 0), "linear")
    PS.outgoing(c, contact, hit, (40, 150, -1500), 12, (-540, 0, 0), (-1980, 90, 0))
    return c


def jubel_knieslide(model):
    """Torjubel: Sprint, auf die Knie, rutschen mit zurückgelehntem Oberkörper und Armen weit nach oben, Fäuste
    pumpen, dann über einen Ausfallschritt aufstehen."""
    c = K.Clip(model, "jubel_knieslide", 66)
    P = c.pose
    P(0, hipL=(30,), kneeL=(-14,), hipR=(-38,), kneeR=(-90,), shR=(44, 0, 10), elR=(80,), shL=(-44, 0, -10),
      elL=(70,), spine=(-18, 0, 0), chest=(-4, 0, 0), head=(0, 0, 0), legs=(0, 0, 0), root=(0, 0, 0), torso1=(0, 0, 0))
    P(3, hipL=(-36,), kneeL=(-80,), hipR=(46,), kneeR=(-24,), shR=(-40, 0, 12), elR=(70,), shL=(40, 0, -12),
      elL=(80,), spine=(-16, 0, 0), chest=(-4, 0, 0), head=(3, 0, 0))
    # Absprung in den Slide: Beine gehen unter den Körper, Arme holen Schwung nach hinten
    P(5, hipL=(20,), kneeL=(-70,), hipR=(24,), kneeR=(-80,), shR=(-46, 0, 26), elR=(40,), shL=(-46, 0, -26),
      elL=(40,), spine=(-8, 0, 0), chest=(0, 0, 0), head=(4, 0, 0))
    # Knie setzen auf (Bild 7): Oberschenkel senkrecht, Schienbeine flach nach hinten
    P(7, hipL=(4, 0, -6), kneeL=(-92,), hipR=(4, 0, 6), kneeR=(-92,), shR=(-16, 0, 70), elR=(30,),
      shL=(-16, 0, -70), elL=(30,), spine=(1, 0, 0), torso1=(1, 0, 0), chest=(3, 0, 0), head=(4, 0, 0))
    # Slide: zurücklehnen (zusammen ~35°), Brust raus, Arme weit hoch, Blick nach oben
    P(11, hipL=(-4, 0, -8), kneeL=(-94,), hipR=(-4, 0, 8), kneeR=(-94,), shR=(-10, 0, 128), elR=(16,),
      shL=(-10, 0, -128), elL=(16,), spine=(6, 0, 0), torso1=(4, 0, 0), chest=(6, 0, 0), head=(9, 0, 0))
    P(18, hipL=(-8, 0, -10), kneeL=(-94,), hipR=(-8, 0, 10), kneeR=(-94,), shR=(-6, 0, 138), elR=(10,),
      shL=(-6, 0, -138), elL=(10,), spine=(9, 0, 0), torso1=(5, 0, 0), chest=(8, 0, 0), head=(12, -4, 0))
    # Fäuste pumpen zweimal
    P(22, shR=(-4, 0, 116), elR=(64,), shL=(-4, 0, -116), elL=(64,), spine=(6, 0, 0), chest=(5, 0, 0),
      head=(8, 6, 0), hipL=(-6, 0, -10), kneeL=(-94,), hipR=(-6, 0, 10), kneeR=(-94,))
    P(25, shR=(-10, 0, 142), elR=(8,), shL=(-10, 0, -142), elL=(8,), spine=(10, 0, 0), chest=(8, 0, 0),
      head=(13, -6, 0))
    P(28, shR=(-4, 0, 116), elR=(66,), shL=(-4, 0, -116), elL=(66,), spine=(6, 0, 0), chest=(5, 0, 0),
      head=(8, 6, 0))
    P(31, shR=(-10, 0, 144), elR=(6,), shL=(-10, 0, -144), elL=(6,), spine=(10, 0, 0), chest=(8, 0, 0),
      head=(14, 0, 0), hipL=(-8, 0, -10), kneeL=(-94,), hipR=(-8, 0, 10), kneeR=(-94,))
    P(42, shR=(-10, 0, 140), elR=(8,), shL=(-10, 0, -140), elL=(8,), spine=(10, 0, 0), chest=(8, 0, 0),
      head=(13, 0, 0), hipL=(-8, 0, -10), kneeL=(-94,), hipR=(-8, 0, 10), kneeR=(-94,), torso1=(5, 0, 0))
    # Aufstehen: rechtes Bein vor (Ausfallschritt), Arme runter, hoch
    P(48, shR=(10, 0, 30), elR=(40,), shL=(10, 0, -30), elL=(40,), spine=(-12, 0, 0), chest=(-4, 0, 0),
      head=(3, 0, 0), hipL=(-4, 0, -6), kneeL=(-96,), hipR=(78, 0, 6), kneeR=(-84,), torso1=(0, 0, 0))
    P(54, shR=(6, 0, 16), elR=(30,), shL=(6, 0, -16), elL=(30,), spine=(-14, 0, 0), chest=(-2, 0, 0),
      head=(4, 0, 0), hipL=(-18, 0, -4), kneeL=(-50,), hipR=(46, 0, 4), kneeR=(-60,))
    stand(c, 62, look=(4, 0, 0))
    stand(c, 66, look=(2, 0, 0))
    c.phase(0, 1, "L")
    c.phase(1, 3, "air", land="R", carry=1.0, g=G)
    c.phase(3, 4, "R")
    c.phase(4, 7, "air", land="body", carry=1.0, g=G)
    # Rutschen: tiefster Punkt (Knie/Schienbeine) bleibt am Boden, Tempo nimmt ab
    c.phase(7, 13, "body", drift=(0, 0, -34))
    c.phase(13, 20, "body", drift=(0, 0, -12))
    c.phase(20, 48, "body", drift=(0, 0, -1))
    c.phase(48, 66, "LR", anchor="R")
    c.root_start = np.array([0.0, 0.0, 30.0])
    c.solve_root()
    return c


def slideshot(model):
    """Slideshot wie in Rematch: Ausfallschritt, nach hinten fallen, Schussbein (rechts) gestreckt schräg nach oben
    in den fallenden Ball (Volley), Landung auf dem Rücken, kurz rutschen, Rolle rückwärts, wieder auf die Füße."""
    c = K.Clip(model, "slideshot", 34)
    P = c.pose
    # Anlauf: rechter Fuß setzt auf
    P(0, hipR=(28,), kneeR=(-14,), hipL=(-30,), kneeL=(-80,), shR=(-36, 0, 10), elR=(70,), shL=(38, 0, -10),
      elL=(76,), spine=(-14, 0, 0), torso1=(0, 0, 0), chest=(-2, 0, 0), head=(4, 0, 0), legs=(0, 0, 0),
      root=(0, 0, 0))
    P(2, hipR=(-20,), kneeR=(-40,), hipL=(30,), kneeL=(-60,), shR=(20, 0, 14), elR=(60,), shL=(-20, 0, -14),
      elL=(60,), spine=(-10, 0, 0), head=(0, 0, 0))
    # Ausfallschritt links: tief, Oberkörper zurück, rechtes Bein holt aus, linker Arm fliegt hoch
    P(4, hipL=(40, 0, -4), kneeL=(-70,), hipR=(-34, 0, 6), kneeR=(-96,), shL=(130, 0, -36), elL=(20,),
      shR=(-10, 0, 46), elR=(30,), spine=(12, 0, 4), torso1=(4, 0, 0), chest=(4, 0, 0), head=(-14, 0, 0),
      root=(8, 0, 0))
    # Absprung nach hinten: Standbein streckt, Schussbein schwingt hoch
    P(7, hipL=(16, 0, -4), kneeL=(-46,), hipR=(52, 0, 4), kneeR=(-70,), shL=(150, 0, -40), elL=(16,),
      shR=(14, 0, 70), elR=(24,), spine=(10, 0, 4), torso1=(4, 0, 0), chest=(4, 0, 0), head=(-26, 0, 0),
      root=(40, 0, 6))
    # fast waagerecht, Bein zielt auf den Ball
    P(9, hipL=(36, 0, -4), kneeL=(-96,), hipR=(78, 0, 2), kneeR=(-34,), shL=(150, 0, -46), elL=(14,),
      shR=(26, 0, 76), elR=(20,), spine=(6, 0, 2), torso1=(2, 0, 0), chest=(2, 0, 0), head=(-40, 0, 0),
      root=(68, 0, 6))
    # Kontakt: Bein gestreckt, schräg nach oben, Kopf schaut zum Fuß
    P(10, hipL=(42, 0, -4), kneeL=(-104,), hipR=(76, 0, 0), kneeR=(-4,), shL=(140, 0, -54), elL=(12,),
      shR=(34, 0, 80), elR=(18,), spine=(2, 0, 0), torso1=(0, 0, 0), chest=(0, 0, 0), head=(-44, 0, 0),
      root=(76, 0, 4))
    # Durchziehen, Landung auf dem Rücken (Bild 13), Beine bleiben oben
    P(12, hipL=(64, 0, -4), kneeL=(-80,), hipR=(96, 0, -4), kneeR=(-10,), shL=(110, 0, -70), elL=(20,),
      shR=(60, 0, 84), elR=(20,), spine=(-4, 0, 0), head=(-30, 0, 0), root=(92, 0, 0))
    P(14, hipL=(90, 0, -4), kneeL=(-70,), hipR=(108, 0, -2), kneeR=(-30,), shL=(100, 0, -60), elL=(30,),
      shR=(90, 0, 60), elR=(30,), spine=(-10, 0, 0), head=(-24, 0, 0), root=(100, 0, 0))
    # Rolle rückwärts: zusammenrollen, Hände neben den Kopf, über die Schulter
    P(17, hipL=(126, 0, -4), kneeL=(-128,), hipR=(126, 0, 4), kneeR=(-128,), shL=(160, 0, -24), elL=(110,),
      shR=(160, 0, 24), elR=(110,), spine=(-26, 0, 0), torso1=(-10, 0, 0), chest=(-10, 0, 0), head=(-30, 0, 0),
      root=(170, 0, 0))
    P(20, hipL=(120, 0, -4), kneeL=(-132,), hipR=(120, 0, 4), kneeR=(-132,), shL=(120, 0, -20), elL=(90,),
      shR=(120, 0, 20), elR=(90,), spine=(-24, 0, 0), torso1=(-8, 0, 0), chest=(-8, 0, 0), head=(-20, 0, 0),
      root=(265, 0, 0))
    # auf den Füßen in der Hocke (Bild 23), aufrichten und loslaufen
    P(23, hipL=(98, 0, -6), kneeL=(-134,), hipR=(92, 0, 6), kneeR=(-130,), shL=(50, 0, -20), elL=(40,),
      shR=(50, 0, 20), elR=(40,), spine=(-30, 0, 0), torso1=(-6, 0, 0), chest=(-6, 0, 0), head=(14, 0, 0),
      root=(340, 0, 0))
    P(26, hipL=(60, 0, -4), kneeL=(-100,), hipR=(30, 0, 4), kneeR=(-60,), shL=(20, 0, -12), elL=(50,),
      shR=(-20, 0, 12), elR=(50,), spine=(-20, 0, 0), torso1=(-2, 0, 0), chest=(-2, 0, 0), head=(10, 0, 0),
      root=(360, 0, 0))
    P(30, hipL=(14, 0, -3), kneeL=(-20,), hipR=(-10, 0, 3), kneeR=(-24,), shL=(4, 0, -10), elL=(30,),
      shR=(-2, 0, 10), elR=(30,), spine=(-8, 0, 0), torso1=(0, 0, 0), chest=(0, 0, 0), head=(4, 0, 0),
      root=(360, 0, 0))
    P(34, hipL=(5, 0, -3), kneeL=(-8,), hipR=(-5, 0, 3), kneeR=(-9,), shL=(2, 0, -7), elL=(14,),
      shR=(3, 0, 7), elR=(14,), spine=(-3, 0, 0), head=(2, 0, 0), legs=(0, 0, 0), root=(360, 0, 0))
    c.phase(0, 2, "R")
    c.phase(2, 4, "air", land="L", carry=1.0, g=G)
    c.phase(4, 7, "L")
    c.phase(7, 13, "air", land="body", drift=(0, 0, -14), g=G)
    c.phase(13, 16, "body", drift=(0, 0, -10))
    c.phase(16, 23, "body", drift=(0, 0, -6))
    c.phase(23, 34, "LR", anchor="L")
    c.root_start = np.array([0.0, 0.0, 30.0])
    c.solve_root()
    contact = 10
    c.holds = [(contact, 1)]
    c.markers = [(contact, 0)]
    # Ball: fällt von vorn oben herein, Volley trifft ihn am Spann, fliegt steigend und hart weg
    hit = PS.touch(c, contact, "R", (0.0, 0.6, -1.0), gap=0.1)
    PS.incoming(c, contact, hit, (2.0, 46.0, -40.0), (-360, 0, 0))
    PS.outgoing(c, contact, hit, (-20, 330, -1450), 12, (-360, 0, 0), (-1800, 0, 90))
    return c


def build():
    model = R.Model(json.load(open(VORLAGE)))
    clips = [schuss_hart(model), jubel_knieslide(model), slideshot(model)]
    data = json.load(open(VORLAGE))
    data["animations"] = [c.to_json() for c in clips]
    json.dump(data, open(ZIEL, "w"), indent=1)
    meta = {"schuss_hart": {"priority": 80, "blend_in": 0.1, "blend_out": 0.3, "ease_in": "out_quad",
                            "ease_out": "in_out_cubic", "cancel_after": 1.0},
            "slideshot": {"priority": 85, "blend_in": 0.08, "blend_out": 0.3, "ease_in": "out_quad",
                          "ease_out": "in_out_cubic", "cancel_after": 1.2},
            "jubel_knieslide": {"priority": 60, "blend_in": 0.15, "blend_out": 0.45, "ease_out": "in_out_quad"}}
    meta_path = os.path.join(ROOT, "rematch", "animationen", "meta.json")
    old = json.load(open(meta_path)) if os.path.exists(meta_path) else {}
    old.update(meta)
    json.dump(old, open(meta_path, "w"), indent=1, ensure_ascii=False)
    print("exportiert:", export_anim.export(ZIEL, MOD_ANIM))


if __name__ == "__main__":
    build()
