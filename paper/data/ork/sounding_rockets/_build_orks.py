"""
Build sounding-rocket .ork files for the Rocket Flight Database v2.0 corpus.

Sources:
  Super Loki Robin Dart  : AFCRL-TR-73-0412 / DTIC AD-766737
                           "Design, Development and Flight Test of the Super Loki
                            Stable Booster Rocket Systems"
                           B. Bollermann, R. L. Walker, Space Data Corp,
                           30 June 1973.
                           Tables 3.1, 3.3, 4.1; Figures 3.4, 4.1.
  Arcas Rocketsonde      : DTIC AD-235341 (1960)
                           "Final Report: Development of the Arcas Rocketsonde
                            System", Webster, Roberts, Donnell,
                           Atlantic Research Corporation, 29 Feb 1960.
                           Tables I, II; Figures 1, 5.

Builds:
  super_loki_dart.ork
  arcas.ork
plus a sidecar README.md and a RASP .eng thrust file for each motor.

Unit conventions:
  All OpenRocket lengths/radii in meters; masses in kg; densities kg/m^3.
  1 inch = 0.0254 m;  1 lb = 0.45359237 kg;  1 lbf = 4.4482216 N.
"""
import io
import os
import uuid
import zipfile

IN = 0.0254
LB = 0.45359237
LBF = 4.4482216
HERE = os.path.dirname(os.path.abspath(__file__))


def newid():
    return str(uuid.uuid4())


# =========================================================================
#                         Super Loki  Robin  Dart
# =========================================================================
# Booster (Stable Super Loki motor, SDC P/N 600-13)  -- Table 3.1 + 3.3 + Fig 4.1
# ---------------------------------------------------------------------------
# Length              88.3 in  (table 3.1) ; Fig 3.2 says 88.82 in over igniter.
#                     Use 88.3 in for the motor body length proper.
# Diameter            4.0 in   (table 3.1)
# Fin span            8.0 in tip-to-tip (table 3.1) => fin half-span/height = 2.0 in
# Fin tip chord       14.8 in  (table 3.1)
# Fin root chord      16.6 in  (table 3.1)
# Fin area each       31.4 in^2  (verifies 0.5*(16.6+14.8)*2.0 = 31.4)
# Number of fins      4         (Fig 4.1)
# Fin sweep length    Not directly given. Trailing edge is approximately
#                     coincident with motor aft end in Fig 4.1 (typical Loki
#                     practice). Therefore LE sweep length = root - tip = 1.8 in.
#                     [confidence: medium -- inferred from drawing]
# Loaded motor mass   60.62 lb (table 3.1)  Propellant 43.48 lb. Inert 17.14 lb.
# Headcap+interstage  3.81 lb -> modeled as a forward inert mass on the booster
# Loaded CG (booster) 42.05 in from aft end of motor (table 4.1)
# Expended weight     16.12 lb
# Total impulse       9944 lbf-s (table 3.3); action time 2.09 s; avg 4757 lbf
#
# Robin Dart (1.625-in dart) -- Fig 4.1 + Table 4.1
# ---------------------------------------------------------------------------
# Diameter            1.625 in  (Fig 4.1)
# Total length        43.655 in (Fig 4.1)
# Nose cone           Steel ogive (sec. 4.3 text: "steel ogive containing lead
#                     ballast"). Length not directly given on Fig 4.1.
#                     [confidence: low -- estimated from Fig 4.1 proportions]
# Number of fins      4   (Fig 4.1: "4.95 in^2 Per Fin (4 Ea.)")
# Fin area each       4.95 in^2 (Fig 4.1)
# Fin planform        Trapezoidal in Fig 4.1; specific dimensions not given.
#                     Use root=3.0 in, tip=1.5 in, height=2.2 in (area=4.95) as
#                     consistent with Fig 4.1 proportions.
#                     [confidence: low -- estimated from Fig 4.1, area only]
# Dart weight         14.15 lb (Table 4.1)
# Dart CG             26.5 in from aft end of dart (Table 4.1)
# Dart Iyy            0.475 slug-ft^2 (Table 4.1) -- not propagated to .ork
# ---------------------------------------------------------------------------
SL_BOOSTER_LEN = 88.3 * IN          # 2.24282 m
SL_BOOSTER_RADIUS = (4.0 / 2) * IN  # 0.0508 m
SL_BOOSTER_LOADED_MASS = 60.62 * LB
SL_BOOSTER_PROP_MASS = 43.48 * LB
SL_BOOSTER_INERT_MASS = SL_BOOSTER_LOADED_MASS - SL_BOOSTER_PROP_MASS
SL_HEADCAP_MASS = 3.81 * LB
SL_FIN_ROOT = 16.6 * IN
SL_FIN_TIP = 14.8 * IN
SL_FIN_HEIGHT = 2.0 * IN
SL_FIN_SWEEP = (16.6 - 14.8) * IN   # estimated, see note above
SL_FIN_THICKNESS = 0.060 * IN       # estimated; Section 8.2 mentions 0.030" Thermolag coat

SL_DART_LEN = 43.655 * IN
SL_DART_DIA = 1.625 * IN
SL_DART_RADIUS = SL_DART_DIA / 2.0
SL_DART_MASS = 14.15 * LB
# Nose cone length: Fig 4.1 shows long pointed ogive. From figure proportions
# the nose appears ~10 in. Body tube ~33.5 in.
SL_DART_NOSE_LEN = 10.0 * IN        # estimated from Fig 4.1
SL_DART_BODY_LEN = SL_DART_LEN - SL_DART_NOSE_LEN
SL_DART_FIN_ROOT = 3.0 * IN
SL_DART_FIN_TIP = 1.5 * IN
SL_DART_FIN_HEIGHT = 2.2 * IN       # area = 0.5*(3.0+1.5)*2.2 = 4.95 in^2 ✓
SL_DART_FIN_SWEEP = 0.75 * IN

SL_MOTOR_TOTAL_IMPULSE = 9944.0 * LBF        # = 44229.5 N-s
SL_MOTOR_BURN_TIME = 2.09
SL_MOTOR_AVG_THRUST = 4757.0 * LBF
SL_MOTOR_MAX_THRUST = 5954.0 * LBF


# =========================================================================
#                              Arcas
# =========================================================================
# Source: AD-235341 Table I + Figures 1, 5.
# Length overall      92.3 in  (Table I)
# Motor               60.8 in  (Table I)  -- final 4.5 EX2 MOD 0 / 29-KS-336
# Parachute housing   13.4 in  (Table I)
# Nose cone           18.1 in  (Table I)  -- 4-caliber secant ogive (text p.10)
# Diameter            4.45 in  (Table I; Fig 5)
# Fin tip-to-tip span 13.000 in (Fig 1 dimension)
# Fin area total      94 in^2 in 4 double-wedge fins -> 23.5 in^2 each
# Fin height          (13.0 - 4.45)/2 = 4.275 in
# Fin chord           Solved: 0.5*(root+tip)*4.275 = 23.5
#                     -> with root=7.0, tip=4.0 -> area = 23.51 ✓
#                     [confidence: medium -- root/tip individually not given]
# Total weight        77.0 lb  (Table I)
# Burnout weight      36.0 lb  (Table I)  -> prop+lost mass = 41.0 lb
# Payload (nominal)   12.0 lb  (Table I)
# Rocket motor + fins 64.5 lb  (Table I)
# Separation device   0.5 lb   (Table I)
# Motor avg thrust    336 lbf @ 70 deg F (Table I, p.6)
# Motor action time   29.0 s   (Table I, p.6)
# Motor total impulse 9089 lbf-s @ 70 F (Table I, p.6)
# CG loaded           54.3 in from nose tip (Fig 3)
# CG burnout          48.3 in from nose tip (Fig 3)
# ---------------------------------------------------------------------------
ARC_TOTAL_LEN = 92.3 * IN
ARC_RADIUS = (4.45 / 2) * IN
ARC_NOSE_LEN = 18.1 * IN
ARC_PARA_HOUSING_LEN = 13.4 * IN
ARC_MOTOR_LEN = 60.8 * IN
ARC_BURNOUT_MASS = 36.0 * LB
ARC_LOADED_MASS = 77.0 * LB
ARC_PAYLOAD_MASS = 12.0 * LB
ARC_PARA_MASS = 2.5 * LB
ARC_NOSE_MASS = 1.5 * LB
ARC_PARA_HOUSING_MASS = 1.5 * LB
ARC_MOTOR_INERT_MASS = ARC_BURNOUT_MASS - (ARC_PAYLOAD_MASS - 6.5 * LB) - 0.0  # rough
# Use the breakdown directly:
#   Motor + fins (loaded)  = 64.5 lb
#   Of which propellant    = 77 - 36 = 41 lb        (vehicle prop)
#   Inert motor + fins     = 64.5 - 41 = 23.5 lb
ARC_PROP_MASS = (ARC_LOADED_MASS - ARC_BURNOUT_MASS)   # 41 lb
ARC_MOTOR_INERT_MASS = (64.5 * LB) - ARC_PROP_MASS     # 23.5 lb

ARC_FIN_ROOT = 7.0 * IN
ARC_FIN_TIP = 4.0 * IN
ARC_FIN_HEIGHT = 4.275 * IN
ARC_FIN_SWEEP = 1.5 * IN     # estimated, double-wedge fin trailing edge near aft end
ARC_FIN_THICKNESS = 0.20 * IN  # estimated; double-wedge cast aluminum

ARC_MOTOR_TOTAL_IMPULSE = 9089.0 * LBF
ARC_MOTOR_BURN_TIME = 29.0
ARC_MOTOR_AVG_THRUST = 336.0 * LBF


# =========================================================================
#                         XML emitter helpers
# =========================================================================
def make_motor_block(configid, designation, manufacturer, diameter_m, length_m,
                     propellant_kg, total_kg, digest):
    return f"""              <motormount>
                <ignitionevent>automatic</ignitionevent>
                <ignitiondelay>0.0</ignitiondelay>
                <overhang>0.0</overhang>
                <motor configid="{configid}">
                  <type>single</type>
                  <manufacturer>{manufacturer}</manufacturer>
                  <digest>{digest}</digest>
                  <designation>{designation}</designation>
                  <diameter>{diameter_m:.6f}</diameter>
                  <length>{length_m:.6f}</length>
                  <delay>none</delay>
                </motor>
                <ignitionconfiguration configid="{configid}">
                  <ignitionevent>automatic</ignitionevent>
                  <ignitiondelay>0.0</ignitiondelay>
                </ignitionconfiguration>
              </motormount>"""


# =========================================================================
#                       Build SUPER LOKI ROBIN DART
# =========================================================================
def build_super_loki():
    cid = newid()
    rocket_id = newid()

    # Stage 0: Dart (sustainer) - flies up and coasts after separation.
    # Stage 1: Booster (Stable Super Loki motor) - the propulsive stage.
    # OpenRocket convention: stage 0 is the topmost.

    # Robin Dart components
    dart_nose_id = newid()
    dart_body_id = newid()
    dart_fins_id = newid()

    # Booster components
    booster_motor_id = newid()
    booster_fins_id = newid()

    xml = f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP corpus build script (paper/data/ork/sounding_rockets/_build_orks.py)">
  <rocket>
    <name>Super Loki Robin Dart</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Super Loki Stable Booster + 1.625-in Robin Dart, 2-stage meteorological sounding rocket.
Source: AFCRL-TR-73-0412 / DTIC AD-766737, Bollermann and Walker (Space Data Corp), 30 June 1973.
Geometry: Tables 3.1 (booster motor), 4.1 (mass properties); Figures 3.4 (thrust), 4.1 (vehicle config).
Apogee: ~106 km (Robin dart variant). Action time 2.09 s. Total impulse 9944 lbf-s.
NOTE: dart fin geometry (root/tip/sweep) inferred from area only; nose cone length estimated from Fig 4.1.</comment>
    <designer>ORP corpus build (auto-generated from source PDFs)</designer>
    <revision>2026-05-02 v1</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Super Loki + Robin Dart</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Robin Dart (sustainer)</name>
        <overridemass>{SL_DART_MASS:.6f}</overridemass>
        <overridesubcomponentsmass>true</overridesubcomponentsmass>

        <subcomponents>
          <nosecone>
            <name>Dart nose cone (steel ogive)</name>
            <id>{dart_nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{SL_DART_NOSE_LEN:.6f}</length>
            <thickness>filled</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{SL_DART_RADIUS:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Dart body (steel tube + Thermolag coating)</name>
            <id>{dart_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{SL_DART_BODY_LEN:.6f}</length>
            <thickness>0.001524</thickness>
            <radius>{SL_DART_RADIUS:.6f}</radius>

            <subcomponents>
              <trapezoidfinset>
                <name>Dart fins (4x, 4.95 in^2 each, steel)</name>
                <id>{dart_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="7850.0">Steel</material>
                <thickness>0.001524</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="7850.0">Steel</filletmaterial>
                <rootchord>{SL_DART_FIN_ROOT:.6f}</rootchord>
                <tipchord>{SL_DART_FIN_TIP:.6f}</tipchord>
                <sweeplength>{SL_DART_FIN_SWEEP:.6f}</sweeplength>
                <height>{SL_DART_FIN_HEIGHT:.6f}</height>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Stable Super Loki Booster</name>
        <separationevent>burnout</separationevent>
        <separationdelay>0.0</separationdelay>

        <subcomponents>
          <bodytube>
            <name>Stable Super Loki motor case (SDC P/N 600-13)</name>
            <id>{booster_motor_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2810.0">Aluminum 2014-T6</material>
            <length>{SL_BOOSTER_LEN:.6f}</length>
            <thickness>0.002108</thickness>
            <radius>{SL_BOOSTER_RADIUS:.6f}</radius>
            <overridemass>{SL_BOOSTER_INERT_MASS + SL_HEADCAP_MASS:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_block(cid, "SuperLoki-600-13", "Space Data Corp",
                  SL_BOOSTER_RADIUS * 2, SL_BOOSTER_LEN,
                  SL_BOOSTER_PROP_MASS, SL_BOOSTER_LOADED_MASS,
                  "superloki600-13")}

            <subcomponents>
              <trapezoidfinset>
                <name>Booster fins (4x, 31.4 in^2 each)</name>
                <id>{booster_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Aluminum 2014-T6 + Thermolag cuff</material>
                <thickness>{SL_FIN_THICKNESS:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Aluminum</filletmaterial>
                <rootchord>{SL_FIN_ROOT:.6f}</rootchord>
                <tipchord>{SL_FIN_TIP:.6f}</tipchord>
                <sweeplength>{SL_FIN_SWEEP:.6f}</sweeplength>
                <height>{SL_FIN_HEIGHT:.6f}</height>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>
    </subcomponents>
  </rocket>

  <simulations>
  </simulations>
</openrocket>
"""
    return xml


# =========================================================================
#                          Build  ARCAS
# =========================================================================
def build_arcas():
    cid = newid()
    rocket_id = newid()

    nose_id = newid()
    para_housing_id = newid()
    motor_body_id = newid()
    fins_id = newid()

    xml = f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP corpus build script (paper/data/ork/sounding_rockets/_build_orks.py)">
  <rocket>
    <name>Arcas Rocketsonde</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Arcas Type C meteorological sounding rocket (4.5 EX2 MOD 0 / 29-KS-336 MARC 2B1 motor).
Source: DTIC AD-235341, "Final Report: Development of the Arcas Rocketsonde System",
Webster, Roberts, Donnell (Atlantic Research Corp), 29 February 1960.
Geometry: Table I (master dimensions); Figures 1 (general), 5 (motor).
Single-stage end-burning solid; 12.5 lb payload, ~210,000 ft apogee nominal,
best demonstrated 249,000 ft (Section "Performance Flights").
Total impulse 9089 lbf-s @ 70F; action time 29.0 s; avg thrust 336 lbf.</comment>
    <designer>ORP corpus build (auto-generated from source PDFs)</designer>
    <revision>2026-05-02 v1</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>4.5 EX2 MOD 0</name>
      <stage number="0" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Arcas</name>

        <subcomponents>
          <nosecone>
            <name>Nose cone (4-caliber secant ogive, 18.1 in)</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="2700.0">Aluminum (Table I p.10: aluminum cone with steel tip)</material>
            <length>{ARC_NOSE_LEN:.6f}</length>
            <thickness>0.001524</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{ARC_RADIUS:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>{ARC_NOSE_MASS:.6f}</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
          </nosecone>

          <bodytube>
            <name>Parachute housing (13.4 in aluminum)</name>
            <id>{para_housing_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{ARC_PARA_HOUSING_LEN:.6f}</length>
            <thickness>0.001524</thickness>
            <radius>{ARC_RADIUS:.6f}</radius>
            <overridemass>{ARC_PARA_HOUSING_MASS + ARC_PAYLOAD_MASS + ARC_PARA_MASS:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>

            <subcomponents>
              <parachute>
                <name>15-ft silk parachute</name>
                <id>{newid()}</id>
                <axialoffset method="middle">0.0</axialoffset>
                <position type="middle">0.0</position>
                <packedlength>0.10</packedlength>
                <packedradius>0.04</packedradius>
                <radialposition>0.0</radialposition>
                <radialdirection>0.0</radialdirection>
                <cd>auto</cd>
                <material type="surface" density="0.05">Silk metallized for radar</material>
                <deployevent>apogee</deployevent>
                <deployaltitude>200.0</deployaltitude>
                <deploydelay>0.0</deploydelay>
                <diameter>4.572</diameter>
                <linecount>8</linecount>
                <linelength>4.572</linelength>
                <linematerial type="line" density="0.0035">Nylon</linematerial>
              </parachute>
            </subcomponents>
          </bodytube>

          <bodytube>
            <name>Arcas motor case (4130 steel, 60.7 in, 4.5 EX2 MOD 0)</name>
            <id>{motor_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">4130 Steel</material>
            <length>{ARC_MOTOR_LEN:.6f}</length>
            <thickness>0.001016</thickness>
            <radius>{ARC_RADIUS:.6f}</radius>
            <overridemass>{ARC_MOTOR_INERT_MASS:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_block(cid, "29-KS-336", "Atlantic Research Corp",
                  ARC_RADIUS * 2 * 0.92, ARC_MOTOR_LEN * 0.95,
                  ARC_PROP_MASS, ARC_PROP_MASS + 5.0 * LB,
                  "arcas-29ks336")}

            <subcomponents>
              <trapezoidfinset>
                <name>Arcas fins (4x double-wedge cast aluminum, 23.5 in^2 each)</name>
                <id>{fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2700.0">Cast aluminum</material>
                <thickness>{ARC_FIN_THICKNESS:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{ARC_FIN_ROOT:.6f}</rootchord>
                <tipchord>{ARC_FIN_TIP:.6f}</tipchord>
                <sweeplength>{ARC_FIN_SWEEP:.6f}</sweeplength>
                <height>{ARC_FIN_HEIGHT:.6f}</height>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>
    </subcomponents>
  </rocket>

  <simulations>
  </simulations>
</openrocket>
"""
    return xml


# =========================================================================
#                       RASP .eng thrust files
# =========================================================================
# Super Loki thrust curve digitized from Fig 3.4 (sea level firing, +59 deg F).
# Smoothly increases from ~4500 lbf at ignition, peaks ~5950 lbf at t=1.7 s,
# tails off to ~0 at t=2.3 s. Action time 2.09 s. Total impulse 9944 lbf-s.
SL_THRUST_TIME_LBF = [
    (0.00,    0.0),
    (0.05, 4000.0),
    (0.10, 4400.0),
    (0.20, 4500.0),
    (0.40, 4400.0),
    (0.70, 4400.0),
    (1.00, 4500.0),
    (1.30, 4800.0),
    (1.50, 5300.0),
    (1.70, 5950.0),   # max thrust per Table 3.3
    (1.85, 5800.0),
    (2.00, 4500.0),
    (2.09, 2500.0),   # action time
    (2.20,  500.0),
    (2.30,    0.0),
]

# Arcas thrust curve from Fig 6 (Table I summary, 70 deg F).
# End-burning grain -> nearly flat thrust profile around 336 lbf for 29 s,
# rising start, gradual taper near end. Total impulse 9089 lbf-s.
ARC_THRUST_TIME_LBF = [
    # Tuned so integrated impulse ~ 9089 lbf-s spec (Table I, 70 F).
    # End-burning grain: brief ramp-up from ignition, gentle taper at burnout.
    (0.0,     0.0),
    (0.05,  290.0),
    (0.20,  325.0),
    (1.0,   330.0),
    (5.0,   328.0),
    (10.0,  325.0),
    (15.0,  322.0),
    (20.0,  320.0),
    (25.0,  315.0),
    (27.0,  308.0),
    (28.0,  250.0),
    (28.5,  130.0),
    (29.0,    0.0),
]


def write_eng(filepath, motor_name, diameter_mm, length_mm, delays,
              propellant_kg, total_kg, manufacturer, thrust_lbf_pairs,
              comment_lines):
    """Write a RASP-format .eng motor file."""
    lines = []
    for c in comment_lines:
        lines.append(";" + c)
    lines.append(f"{motor_name} {diameter_mm:.1f} {length_mm:.1f} {delays} "
                 f"{propellant_kg:.4f} {total_kg:.4f} {manufacturer}")
    for t, f_lbf in thrust_lbf_pairs:
        lines.append(f"   {t:.3f} {f_lbf * LBF:.2f}")
    lines.append(";")
    with open(filepath, "w", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


# =========================================================================
#                           Pack zip + emit
# =========================================================================
def pack_ork(target, xml_text):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("rocket.ork", xml_text)


def main():
    sl = build_super_loki()
    arc = build_arcas()

    pack_ork(os.path.join(HERE, "super_loki_dart.ork"), sl)
    pack_ork(os.path.join(HERE, "arcas.ork"), arc)

    # --- Verify total-impulse for the .eng files ---------------------------
    def integrate(pairs):
        I = 0.0
        for (t1, f1), (t2, f2) in zip(pairs[:-1], pairs[1:]):
            I += 0.5 * (f1 + f2) * (t2 - t1)
        return I
    sl_I = integrate(SL_THRUST_TIME_LBF)        # in lbf-s
    arc_I = integrate(ARC_THRUST_TIME_LBF)
    print(f"Super Loki integrated impulse from curve: {sl_I:.1f} lbf-s "
          f"(spec: 9944).")
    print(f"Arcas integrated impulse from curve:      {arc_I:.1f} lbf-s "
          f"(spec: 9089).")

    # --- Write .eng files --------------------------------------------------
    write_eng(
        os.path.join(HERE, "SuperLoki-600-13.eng"),
        motor_name="SuperLoki-600-13",
        diameter_mm=4.0 * 25.4,
        length_mm=88.3 * 25.4,
        delays="P",
        propellant_kg=43.48 * LB,
        total_kg=60.62 * LB,
        manufacturer="SpaceDataCorp",
        thrust_lbf_pairs=SL_THRUST_TIME_LBF,
        comment_lines=[
            " Stable Super Loki rocket motor (SDC P/N 600-13)",
            " Source: AFCRL-TR-73-0412 / DTIC AD-766737, Table 3.3, Figure 3.4",
            " Sea-level firing at +59 deg F",
            " Total impulse 9944 lbf-s; action time 2.09 s; max thrust 5954 lbf",
            f" Curve digitized from Fig 3.4; integrated I = {sl_I:.0f} lbf-s",
        ],
    )

    write_eng(
        os.path.join(HERE, "Arcas-29KS336.eng"),
        motor_name="29-KS-336",
        diameter_mm=4.45 * 25.4,
        length_mm=60.7 * 25.4,
        delays="P",
        propellant_kg=ARC_PROP_MASS,
        total_kg=ARC_PROP_MASS + ARC_MOTOR_INERT_MASS,
        manufacturer="AtlanticResearch",
        thrust_lbf_pairs=ARC_THRUST_TIME_LBF,
        comment_lines=[
            " Arcas 4.5 EX2 MOD 0 / 29-KS-336 MARC 2B1 rocket motor",
            " Source: DTIC AD-235341, Table I (p.6) and Figure 6",
            " End-burning Arcite 373D propellant, 70 deg F",
            " Total impulse 9089 lbf-s; action time 29.0 s; avg thrust 336 lbf",
            f" Curve schematized from Fig 6 narrative; integrated I = {arc_I:.0f} lbf-s",
        ],
    )

    print("Built:")
    for f in ("super_loki_dart.ork", "arcas.ork",
              "SuperLoki-600-13.eng", "Arcas-29KS336.eng"):
        p = os.path.join(HERE, f)
        print(f"   {p}  ({os.path.getsize(p)} bytes)")


if __name__ == "__main__":
    main()
