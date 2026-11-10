"""Polierte Schuss-Animationen für das Fußball-Testmodell (Rematch-Plan, A7).

Aufruf: python3 tools/bbanim/polish_shots.py <eingabe.bbmodel> <ausgabe.bbmodel>
Die Original-Skizzen bleiben in der Datei, die neuen Animationen kommen dazu:
  schuss_normal, sprung_volley, fallrueckzieher

Vorzeichen (wie in der .bbmodel gespeichert, Figur schaut nach -z):
  Hüfte/Schulter x+ = nach vorn, Knie x- = beugen, Ellbogen x+ = beugen, Rumpf/Kopf x- = nach vorn neigen,
  rechter Arm/Bein z+ = nach außen, linker z- = nach außen, y+ = nach links drehen.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import animkit as K  # noqa: E402
import render_bbmodel as R  # noqa: E402

BALL_R = 2.5


def idle(c, f, look=(0, 0, 0)):
    """Lockerer Stand."""
    c.pose(f, hipR=(2, 0, 3), hipL=(-2, 0, -3), kneeR=(-6,), kneeL=(-4,), shR=(4, 0, 6), shL=(-2, 0, -6),
           elR=(14,), elL=(10,), spine=(-3, 0, 0), chest=(1, 0, 0), head=look, legs=(0, 0, 0))


# ---------------------------------------------------------------------------------------------------------------------
def schuss(model):
    """Normaler Schuss mit links aus dem Lauf, Ball kommt flach entgegen."""
    c = K.Clip(model, "schuss_normal", 46)
    P = c.pose
    # Anlauf: linker Fuß setzt auf (Bild 0), Schritt, langer letzter Schritt auf rechts (Bild 8)
    P(0, hipL=(30,), kneeL=(-14,), hipR=(-30,), kneeR=(-72,), shR=(36, 0, 8), elR=(72,), shL=(-34, 0, -8),
      elL=(40,), spine=(-14, 4, 0), chest=(-3, 3, 0), head=(-8, -3, 0), legs=(0, -6, 0), root=(0, 0, 0))
    P(2, hipL=(4,), kneeL=(-26,), hipR=(2,), kneeR=(-104,), shR=(6, 0, 8), elR=(66,), shL=(-4, 0, -8),
      elL=(52,), spine=(-16, 0, 0), chest=(-4, 0, 0), head=(-10, 0, 0), legs=(0, 0, 0))
    P(3, hipL=(-30,), kneeL=(-18,), hipR=(34,), kneeR=(-82,), shR=(-26, 0, 10), elR=(48,), shL=(30, 0, -8),
      elL=(70,), spine=(-14, -4, 0), chest=(-2, -3, 0), head=(-10, 2, 0), legs=(0, 8, 0))
    # Flug, rechtes Bein greift weit nach vorn, linkes holt schon aus
    P(5, hipL=(-38,), kneeL=(-62,), hipR=(46,), kneeR=(-30,), shR=(-10, 0, 30), elR=(40,), shL=(14, 0, -20),
      elL=(60,), spine=(-9, -2, 0), chest=(0, -2, 0), head=(-14, 0, 0), legs=(0, 10, 0))
    # Aufsetzen rechts (Ferse), Körper leicht zurück und über das Standbein
    P(6, hipR=(42, 0, -2), kneeR=(-12,), hipL=(-40,), kneeL=(-88,), shR=(0, 0, 48), elR=(30,),
      shL=(-26, 0, -22), elL=(46,), spine=(-4, -6, 5), chest=(2, -6, 2), head=(-18, 4, 0), legs=(0, 14, 0))
    # Ausholen: Ferse hoch, Hüfte offen, rechter Arm weit raus
    P(8, hipR=(24, 0, -3), kneeR=(-30,), hipL=(-46, 0, -6), kneeL=(-112,), shR=(4, 0, 62), elR=(24,),
      shL=(-40, 0, -28), elL=(40,), spine=(-6, -10, 7), chest=(0, -6, 3), head=(-22, 6, 0), legs=(0, 18, 0))
    P(9, hipR=(16, 0, -3), kneeR=(-28,), hipL=(-40, 0, -6), kneeL=(-114,), shR=(6, 0, 64), elR=(22,),
      shL=(-36, 0, -30), elL=(42,), spine=(-8, -9, 7), chest=(-1, -5, 3), head=(-24, 6, 0), legs=(0, 16, 0))
    # Durchschwung: Oberschenkel zuerst, Unterschenkel peitscht nach
    P(11, hipR=(10, 0, -3), kneeR=(-24,), hipL=(-4, 0, -4), kneeL=(-92,), shR=(14, 0, 60), elR=(24,),
      shL=(-20, 0, -30), elL=(44,), spine=(-10, -4, 6), chest=(-2, -2, 2), head=(-26, 4, 0), legs=(0, 6, 0))
    # Kontakt: Knie fast gestreckt, Kopf über dem Ball
    P(12, hipR=(4, 0, -3), kneeR=(-20,), hipL=(22, 0, -2), kneeL=(-12,), shR=(24, 0, 56), elR=(28,),
      shL=(-14, 0, -34), elL=(40,), spine=(-11, 2, 5), chest=(-3, 2, 2), head=(-26, 0, 0), legs=(0, -2, 0))
    # Durchziehen: Bein hoch, Oberkörper gegen, Arme öffnen
    P(14, hipR=(-6, 0, -3), kneeR=(-10,), hipL=(70, 0, 2), kneeL=(-6,), shR=(30, -12, 44), elR=(70,),
      shL=(-30, 0, -58), elL=(30,), spine=(6, 10, 3), chest=(4, 6, 1), head=(-6, -4, 0), legs=(0, -14, 0))
    P(16, hipR=(-14, 0, -2), kneeR=(-18,), hipL=(80, 0, 2), kneeL=(-12,), shR=(32, -12, 38), elR=(76,),
      shL=(-26, 0, -60), elL=(34,), spine=(9, 12, 2), chest=(5, 6, 0), head=(4, -6, 0), legs=(0, -16, 0))
    # Bein kommt runter, Hüpfer nach vorn, Landung links (Bild 26)
    P(18, hipR=(-26, 0, 0), kneeR=(-46,), hipL=(52, 0, 0), kneeL=(-20,), shR=(20, -6, 28), elR=(64,),
      shL=(-14, 0, -44), elL=(40,), spine=(4, 10, 1), chest=(3, 4, 0), head=(6, -6, 0), legs=(0, -12, 0))
    P(20, hipR=(-30, 0, 0), kneeR=(-58,), hipL=(24, 0, 0), kneeL=(-16,), shR=(14, 0, 18), elR=(54,),
      shL=(-6, 0, -30), elL=(44,), spine=(-6, 6, 0), chest=(0, 2, 0), head=(4, -4, 0), legs=(0, -8, 0))
    P(22, hipR=(-6, 0, 0), kneeR=(-84,), hipL=(4, 0, 0), kneeL=(-32,), shR=(-2, 0, 14), elR=(48,),
      shL=(6, 0, -22), elL=(46,), spine=(-9, 3, 0), chest=(-1, 0, 0), head=(4, -2, 0), legs=(0, -4, 0))
    # Ausgleichsschritt rechts setzt auf (Bild 31), dann beide Füße
    P(24, hipR=(18, 0, 3), kneeR=(-14,), hipL=(-14, 0, -3), kneeL=(-24,), shR=(-6, 0, 10), elR=(36,),
      shL=(10, 0, -12), elL=(36,), spine=(-7, 0, 0), chest=(0, 0, 0), head=(6, 0, 0), legs=(0, 0, 0))
    P(27, hipR=(10, 0, 3), kneeR=(-20,), hipL=(-10, 0, -3), kneeL=(-22,), shR=(-2, 0, 8), elR=(24,),
      shL=(4, 0, -8), elL=(22,), spine=(-6, 0, 0), chest=(0, 0, 0), head=(8, 2, 0))
    P(33, hipR=(5, 0, 3), kneeR=(-9,), hipL=(-5, 0, -3), kneeL=(-8,), shR=(2, 0, 6), elR=(16,),
      shL=(0, 0, -6), elL=(12,), spine=(-2, 0, 0), chest=(1, 0, 0), head=(6, 4, 0))
    P(46, hipR=(5, 0, 3), kneeR=(-8,), hipL=(-5, 0, -3), kneeL=(-6,), shR=(3, 0, 6), elR=(14,),
      shL=(-1, 0, -6), elL=(10,), spine=(-3, 0, 0), chest=(1, 0, 0), head=(0, 0, 0))
    c.phase(0, 3, "L")
    c.phase(3, 6, "air", land="R", carry=1.1, g=420)
    c.phase(6, 13, "R")
    c.phase(13, 20, "air", land="L", drift=(1.5, 0, -8), g=330)
    c.phase(20, 23, "L")
    c.phase(23, 46, "LR", anchor="L")
    c.root_start = np.array([0.0, 0.0, 26.0])
    c.solve_root()
    contact = 12
    c.holds = [(contact, 2)]
    c.markers = [(contact, 0)]
    # Ball: liegt vor dem Spann im Kontaktbild, rollt vorher flach entgegen, fliegt danach steigend weg
    foot = c.foot_world(contact, "L")
    hit = np.array([foot[:, 0].mean(), BALL_R, foot[:, 2].min() - BALL_R + 0.3])
    c.ball_at(0, hit + [0, 0, -42.0])
    c.ball_at(contact, hit)
    c.key("ball", 0, "rotation", (0, 0, 0), "linear")
    c.key("ball", contact, "rotation", (900, 0, 0), "linear")
    outgoing(c, contact, hit, (14, 230, -1180), 12, (900, 0, 0), (1620, 40, 0))
    return c


# ---------------------------------------------------------------------------------------------------------------------
def incoming(c, contact, hit, start_off, spin, g=K.GRAVITY):
    """Ball fliegt als Wurfparabel von hit+start_off (Bild 0) nach hit (Kontaktbild), Drehung endet auf 90°-Raster."""
    T = contact / K.FPS
    p0 = hit + np.asarray(start_off, float)
    v0 = (hit - p0) / T + np.array([0, g * T / 2, 0])
    for f, p in enumerate(K.ballistic(p0, v0, contact, g)):
        if f % 3 == 0 or f == contact:
            c.ball_at(f, p)
    c.key("ball", 0, "rotation", (0, 0, 0), "linear")
    c.key("ball", contact, "rotation", spin, "linear")


def outgoing(c, contact, hit, v0, frames, spin_from, spin_to):
    c.scale("ball", contact - 1, (1, 1, 1))
    c.scale("ball", contact, (1.18, 1.18, 0.66))
    for i, p in enumerate(K.ballistic(hit, v0, frames)[1:], 1):
        c.ball_at(contact + i, p)
    c.scale("ball", contact + 1, (0.84, 0.84, 1.4))
    c.scale("ball", contact + 3, (1, 1, 1))
    c.key("ball", contact + 1, "rotation", spin_from, "linear")
    c.key("ball", contact + frames, "rotation", spin_to, "linear")


def sole(c, frame, side, push=0.0):
    """Mitte der Fußsohle (Unterseite des Unterschenkel-Würfels) in Weltkoordinaten."""
    p = c.foot_world(frame, side)
    lo = p[np.argsort(p[:, 1])[:4]]
    return lo.mean(axis=0)


def touch(c, frame, side, direction, gap=0.15):
    """Ballmitte von der Sohle aus in Richtung direction schieben, bis der Ball den Fuß gerade berührt."""
    p = c.foot_world(frame, side)
    o, g = p[0], np.linspace(0, 1, 9)
    ax = (p[4] - o, p[2] - o, p[1] - o)
    box = np.array([o + a * ax[0] + b * ax[1] + e * ax[2] for a in g for b in g for e in g])
    d = np.asarray(direction, float)
    d /= np.linalg.norm(d)
    s = sole(c, frame, side)
    for t in np.arange(0, 30, 0.05):
        q = s + d * t
        if np.linalg.norm(box - q, axis=1).min() >= BALL_R + gap:
            return q
    return s


# ---------------------------------------------------------------------------------------------------------------------
def volley(model):
    """Sprung-Volley mit links, Ball kommt als Flanke von links. Kontaktpose aus der Skizze des Besitzers."""
    c = K.Clip(model, "sprung_volley", 42)
    P = c.pose
    P(0, hipR=(8, 0, 5), hipL=(8, 0, -5), kneeR=(-16,), kneeL=(-16,), shR=(8, 0, 16), shL=(8, 0, -16), elR=(30,),
      elL=(30,), spine=(-6, 10, 0), chest=(0, 6, 0), torso1=(0, 0, 0), head=(10, 28, 0), legs=(0, 4, 0),
      root=(0, 4, 0))
    # Ausholen: tief in die Knie, Arme nach hinten
    P(4, hipR=(34, 0, 5), hipL=(30, 0, -6), kneeR=(-62,), kneeL=(-58,), shR=(-38, 0, 14), shL=(-36, 0, -14),
      elR=(24,), elL=(24,), spine=(-18, 14, 0), chest=(-2, 6, 0), head=(16, 26, 0), legs=(0, 6, 0), root=(0, 8, 0))
    # Absprung vom rechten Bein, links schwingt schon hoch, Körper kippt nach rechts
    P(6, hipR=(6, 0, 6), hipL=(34, 0, -26), kneeR=(-14,), kneeL=(-64,), shR=(22, 0, 42), shL=(34, 0, -44),
      elR=(30,), elL=(34,), spine=(-6, 10, -6), chest=(2, 2, 2), head=(14, 18, 0), legs=(0, 4, 0),
      root=(0, 10, -12))
    P(9, hipR=(30, 0, 30), hipL=(-22, 10, -58), kneeR=(-72,), kneeL=(-84,), shR=(0, -14, 64), shL=(42, -20, -68),
      elR=(20,), elL=(40,), spine=(0, 2, 0), chest=(8, 0, 6), torso1=(0, -6, 0), head=(12, 10, 0),
      root=(0, 13, -38))
    # Maximal ausgeholt
    P(11, hipR=(18, 0, 50), hipL=(-36, 15, -62), kneeR=(-36,), kneeL=(-98,), shR=(-2, -20, 58), shL=(48, -24, -64),
      elR=(16,), elL=(42,), spine=(0, 0, 0), chest=(12, -1, 7), torso1=(0, -10, 0), head=(10, 4, 0),
      root=(0, 13, -58))
    # Kontakt: Pose aus der Skizze, Knie fast gestreckt
    P(13, hipR=(0, 0, 57.5), hipL=(9, 15, -59), kneeR=(-17.5,), kneeL=(-14,), shR=(-1.25, -21, 56.25),
      shL=(45, -23.4, -62.6), elR=(15,), elL=(42.5,), spine=(0, 0, 0), chest=(12.4, -1.3, 7.4), torso1=(0, -10, 0),
      head=(10, 0, 0), legs=(0, 0, 0), root=(0, 12.5, -65))
    # Durchschwung, rechter Arm greift zum Boden
    P(16, hipR=(12, 0, 64), hipL=(56, 15, -50), kneeR=(-24,), kneeL=(-8,), shR=(0, -8, 70), shL=(40, -20, -78),
      elR=(10,), elL=(36,), chest=(10, 0, 6), torso1=(0, -6, 0), head=(4, -8, 4), root=(0, 4, -72))
    # Landung auf rechtem Fuß und rechter Hand
    P(19, hipR=(26, 0, 66), hipL=(36, 10, -40), kneeR=(-28,), kneeL=(-30,), shR=(4, 0, 72), shL=(38, -20, -80),
      elR=(14,), elL=(34,), chest=(6, 0, 6), torso1=(0, -4, 0), head=(6, -10, 6), root=(0, 0, -74))
    P(21, hipR=(46, 0, 58), hipL=(30, 6, -34), kneeR=(-58,), kneeL=(-40,), shR=(10, 0, 70), shL=(30, -14, -70),
      elR=(38,), elL=(36,), chest=(4, 0, 4), torso1=(0, -2, 0), head=(4, -8, 4), root=(0, 0, -70))
    # Hochdrücken
    P(23, hipR=(40, 0, 46), hipL=(24, 4, -28), kneeR=(-56,), kneeL=(-44,), shR=(4, 0, 62), shL=(22, -10, -56),
      elR=(14,), elL=(34,), chest=(2, 0, 2), torso1=(0, 0, 0), head=(2, -6, 2), root=(0, 0, -54))
    P(26, hipR=(26, 0, 18), hipL=(14, 0, -14), kneeR=(-44,), kneeL=(-30,), shR=(2, 0, 34), shL=(16, 0, -38),
      elR=(26,), elL=(30,), chest=(0, 0, 0), head=(2, -4, 0), root=(0, 0, -24))
    P(29, hipR=(18, 0, 8), hipL=(14, 0, -8), kneeR=(-34,), kneeL=(-28,), shR=(4, 0, 18), shL=(10, 0, -22),
      elR=(30,), elL=(30,), spine=(-10, 0, 0), head=(4, -2, 0), root=(0, 0, -4))
    P(33, hipR=(8, 0, 4), hipL=(6, 0, -4), kneeR=(-16,), kneeL=(-14,), shR=(4, 0, 10), shL=(4, 0, -10),
      elR=(20,), elL=(18,), spine=(-4, 0, 0), head=(6, 0, 0), root=(0, 0, 1))
    P(42, hipR=(4, 0, 3), hipL=(-3, 0, -3), kneeR=(-7,), kneeL=(-5,), shR=(3, 0, 6), shL=(-1, 0, -6),
      elR=(14,), elL=(10,), spine=(-3, 0, 0), chest=(1, 0, 0), head=(2, 0, 0), root=(0, 0, 0))
    c.phase(0, 7, "LR", anchor="R")
    c.phase(7, 19, "air", land="body", drift=(-5, 0, -8), g=520)
    c.phase(19, 42, "body", anchor="R")
    c.solve_root()
    contact = 13
    c.holds = [(contact, 2)]
    c.markers = [(contact, 0)]
    hit = touch(c, contact, "L", (0, 0, -1))
    incoming(c, contact, hit, (-150, 6, -36), (270, 0, 540))
    outgoing(c, contact, hit, (170, -20, -1150), 12, (270, 0, 540), (990, 60, 540))
    return c


# ---------------------------------------------------------------------------------------------------------------------
def fallrueckzieher(model):
    """Fallrückzieher mit rechts: Anlauf, Absprung rechts, links schwingt zuerst hoch (Schere), Kontakt über Kopf,
    Hit-Stop, gehockter Rückwärtssalto und Landung auf beiden Füßen (Arcade, wie in der Skizze)."""
    c = K.Clip(model, "fallrueckzieher", 48)
    P = c.pose
    P(0, hipL=(30,), kneeL=(-14,), hipR=(-30,), kneeR=(-72,), shR=(36, 0, 8), elR=(72,), shL=(-34, 0, -8),
      elL=(40,), spine=(-10, 4, 0), chest=(-2, 3, 0), head=(14, -3, 0), legs=(0, -6, 0), root=(0, 0, 0))
    P(2, hipL=(4,), kneeL=(-26,), hipR=(2,), kneeR=(-104,), shR=(6, 0, 8), elR=(66,), shL=(-4, 0, -8),
      elL=(52,), spine=(-12, 0, 0), chest=(-3, 0, 0), head=(16, 0, 0), legs=(0, 0, 0))
    P(3, hipL=(-30,), kneeL=(-18,), hipR=(34,), kneeR=(-82,), shR=(-26, 0, 10), elR=(48,), shL=(30, 0, -8),
      elL=(70,), spine=(-10, -4, 0), chest=(-2, -3, 0), head=(18, 2, 0), legs=(0, 8, 0))
    P(5, hipL=(-30,), kneeL=(-72,), hipR=(40,), kneeR=(-34,), shR=(-20, 0, 10), elR=(44,), shL=(26, 0, -10),
      elL=(64,), spine=(-6, -2, 0), chest=(0, -2, 0), head=(20, 0, 0), legs=(0, 6, 0))
    # Rechts setzt auf, Körper bremst und richtet sich auf, Blick zum Ball
    P(6, hipR=(32,), kneeR=(-16,), hipL=(-28,), kneeL=(-84,), shR=(-30, 0, 12), elR=(40,), shL=(32, 0, -10),
      elL=(58,), spine=(-2, 0, 0), chest=(0, 0, 0), head=(22, 0, 0), legs=(0, 0, 0), root=(0, 0, 0))
    # Ausholen: in die Knie, linkes Bein schwingt nach vorn, Arme nach hinten
    P(8, hipR=(20,), kneeR=(-50,), hipL=(44,), kneeL=(-78,), shR=(-46, 0, 16), elR=(28,), shL=(-40, 0, -16),
      elL=(28,), spine=(8, 0, 0), chest=(4, 0, 0), head=(24, 0, 0), root=(6, 0, 0))
    # Abdruck: links reißt hoch, Arme schwingen nach oben
    P(9, hipR=(-2,), kneeR=(-24,), hipL=(92,), kneeL=(-56,), shR=(56, 0, 30), elR=(40,), shL=(66, 0, -30),
      elL=(40,), spine=(14, 0, 0), chest=(6, 0, 0), head=(20, 0, 0), root=(16, 0, 0))
    P(10, hipR=(-10,), kneeR=(-12,), hipL=(110,), kneeL=(-46,), root=(24, 0, 0))
    # Flug: links oben (Schere), rechts holt aus
    P(13, hipR=(10,), kneeR=(-62,), hipL=(120,), kneeL=(-34,), shR=(40, 0, 76), elR=(20,), shL=(40, 0, -76),
      elL=(20,), spine=(10, 0, 0), chest=(8, 0, 0), head=(14, 0, 0), root=(62, 6, 6))
    P(16, hipR=(40, 0, 4), kneeR=(-100,), hipL=(70,), kneeL=(-30,), shR=(10, 0, 86), elR=(16,), shL=(10, 0, -86),
      elL=(16,), spine=(6, 0, 0), chest=(6, 0, 0), head=(10, 6, 0), root=(98, 10, 12))
    P(18, hipR=(46, 0, 6), kneeR=(-62,), hipL=(14,), kneeL=(-36,), shR=(-6, 0, 80), elR=(18,), shL=(-4, 0, -84),
      elL=(18,), head=(6, 8, 0), root=(118, 12, 16))
    # Kontakt genau über der Hüfte, Bein gestreckt
    P(20, hipR=(50, 0, 6), kneeR=(-4,), hipL=(-10,), kneeL=(-24,), shR=(-14, 0, 74), elR=(22,), shL=(-12, 0, -80),
      elL=(22,), spine=(4, 0, 0), chest=(4, 0, 0), head=(2, 8, 0), root=(130, 14, 18))
    # Durchziehen, dann hocken für schnellen Salto
    P(22, hipR=(74, 0, 4), kneeR=(-26,), hipL=(30,), kneeL=(-60,), shR=(30, 0, 50), elR=(50,), shL=(30, 0, -54),
      elL=(50,), spine=(-4, 0, 0), chest=(-4, 0, 0), head=(-6, 4, 0), root=(178, 10, 12))
    P(25, hipR=(104, 0, 2), kneeR=(-116,), hipL=(100,), kneeL=(-112,), shR=(62, 0, 20), elR=(90,), shL=(62, 0, -20),
      elL=(90,), spine=(-16, 0, 0), chest=(-8, 0, 0), head=(-14, 0, 0), root=(240, 6, 6))
    # Aufmachen zur Landung
    P(28, hipR=(42, 0, 4), kneeR=(-62,), hipL=(44, 0, -4), kneeL=(-64,), shR=(44, 0, 40), elR=(30,),
      shL=(44, 0, -40), elL=(30,), spine=(-14, 0, 0), chest=(-4, 0, 0), head=(-6, 0, 0), root=(318, 2, 2))
    P(31, hipR=(34, 0, 5), kneeR=(-62,), hipL=(36, 0, -5), kneeL=(-64,), shR=(46, 0, 34), elR=(30,),
      shL=(46, 0, -34), elL=(30,), spine=(-24, 0, 0), chest=(-4, 0, 0), head=(-4, 0, 0), root=(360, 0, 0))
    P(33, hipR=(52, 0, 6), kneeR=(-96,), hipL=(54, 0, -6), kneeL=(-98,), shR=(36, 0, 30), elR=(36,),
      shL=(36, 0, -30), elL=(36,), spine=(-30, 0, 0), chest=(-6, 0, 0), head=(4, 0, 0), root=(360, 0, 0))
    P(38, hipR=(18, 0, 5), kneeR=(-32,), hipL=(18, 0, -5), kneeL=(-30,), shR=(4, 0, 14), elR=(24,),
      shL=(4, 0, -14), elL=(22,), spine=(-8, 0, 0), chest=(0, 0, 0), head=(4, -14, 0))
    P(48, hipR=(4, 0, 3), hipL=(-3, 0, -3), kneeR=(-7,), kneeL=(-5,), shR=(3, 0, 6), shL=(-1, 0, -6),
      elR=(14,), elL=(10,), spine=(-3, 0, 0), chest=(1, 0, 0), head=(4, -26, 0), root=(360, 0, 0))
    c.phase(0, 3, "L")
    c.phase(3, 6, "air", land="R", carry=1.0, g=420)
    c.phase(6, 10, "R")
    c.phase(10, 31, "air", land="LR", drift=(3, 0, 12), g=300)
    c.phase(31, 48, "LR", anchor="R")
    c.root_start = np.array([0.0, 0.0, 30.0])
    c.solve_root()
    contact = 20
    c.holds = [(contact, 3)]
    c.markers = [(contact, 0)]
    hit = touch(c, contact, "R", (0, 1, -0.4))
    incoming(c, contact, hit, (12, 70, -200), (630, 0, 0))
    outgoing(c, contact, hit, (420, 120, 860), 12, (630, 0, 0), (1350, -90, 0))
    return c


# ---------------------------------------------------------------------------------------------------------------------
def fix_ball_pivot(data):
    """Drehpunkt des Balls auf die Würfelmitte legen, sonst eiert er beim Rollen und Drehen."""
    ball = next((g for g in data["groups"] if g["name"] == "ball"), None)
    if ball is None:
        return data
    for n in data["outliner"]:
        if isinstance(n, dict) and n["uuid"] == ball["uuid"]:
            cubes = [e for e in data["elements"] if e["uuid"] in n.get("children", [])]
            if cubes:
                e = cubes[0]
                ball["origin"] = [(a + b) / 2 for a, b in zip(e["from"], e["to"])]
                for c in cubes:
                    c["origin"] = list(ball["origin"])
    return data


def write(src, dst, clips):
    data = fix_ball_pivot(K.load(src))
    names = {c.name for c in clips}
    data["animations"] = [a for a in data["animations"] if a["name"] not in names] + [c.to_json() for c in clips]
    with open(dst, "w") as f:
        json.dump(data, f, indent="\t", ensure_ascii=False)


def main():
    src, dst = sys.argv[1], sys.argv[2]
    model = R.Model(fix_ball_pivot(K.load(src)))
    clips = [schuss(model), volley(model), fallrueckzieher(model)]
    write(src, dst, clips)
    for c in clips:
        print(c.name, c.length_frames(), "Bilder")


if __name__ == "__main__":
    main()
