"""
Build the v2.0 sounding-rocket corpus .ork files from audited YAML sources.

This script supersedes `_build_orks.py` (which produces the legacy
Super-Loki + Frankenrocket-Arcas .orks). It generates four audited .ork
files corresponding to the v2 corpus seed:

  arcas_blunt.ork           Arcas (1.5-cal blunt, 25-KS-325 motor)
                            -> Performance Flights 2 (78k ft) and 3 (90k ft)
  arcas_secant.ork          Arcas (4-cal secant ogive, 25-KS-325 motor)
                            -> Performance Flights 4 (178k ft) and 5 (171k ft)
  heros3.ork                HEROS 3 hybrid (HyEnD/Stuttgart, Esrange 2016)
                            -> 32,300 m apogee
  bbv.ork                   Black Brant V VB (AAF-VB-32, Churchill 1971)
                            -> 273.6 km apogee
  terrier_improved_orion.ork
                            MK12 Terrier + Improved Orion screening model
                            -> NASA RockOn/RockSat-C 2016, 74 mi apogee
  terrier_improved_malemute.ork
                            MK12 Terrier + Improved Malemute screening model
                            -> NASA RockSat-X 2016, 95 mi apogee
  black_brant_ix_aspire_sr02.ork
                            MK70 Terrier + Black Brant IX screening model
                            -> ASPIRE SR02, 54.82 km apogee
  aerobee_150a_4_65gi.ork   Aerobee 150A liquid sustainer represented by RSE
                            -> NASA 4.65GI, 139.5 statute mile apogee

Every dimension below cites a specific YAML field. YAML files are at
  paper/data/ork/sounding_rockets/vehicles/

Each .ork zip embeds its motors at thrustcurves/<digest>.rse so that
MotorHandler.loadMotorFromZip resolves them without needing a populated
MotorDatabase (see paper/data/v2_orp_runs_2026_05_02.md for why).

Per CLAUDE.md citation hygiene: where YAML flags MISSING_FROM_SOURCE we
must either (a) use the YAML's own *_inferred_* value (which carries an
auditable inference basis) or (b) synthesize from documented physical
constants and flag in a comment. No values pulled from training-data.
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
# RSE motor file builder (RockSim XML format)
# =========================================================================
def build_rse_xml(designation, manufacturer, diameter_mm, length_mm,
                  delays, propellant_kg, total_kg, thrust_pairs_n,
                  comment_lines, cg_fraction=0.5):
    """Build a RockSim .rse motor file.

    thrust_pairs_n: list of (t_seconds, thrust_newtons) tuples.
    Mass + CG flagged auto-calc — the loader fills them via thrust integral.
    Required attributes (RockSimMotorLoader): mfg, code, delays, dia (mm),
    len (mm), initWt (g), propWt (g), Type, auto-calc-mass, auto-calc-cg.
    """
    init_g = total_kg * 1000.0
    prop_g = propellant_kg * 1000.0
    cg_mm = length_mm * cg_fraction
    comment_text = "\n".join(comment_lines).strip()
    rows = []
    for t, f_n in thrust_pairs_n:
        rows.append(
            f'<eng-data t="{t:.4f}" f="{f_n:.2f}" '
            f'm="{prop_g:.2f}" cg="{cg_mm:.2f}"/>'
        )
    data_block = "\n      ".join(rows)
    return (
        '<engine-database>\n'
        ' <engine-list>\n'
        f'<engine mfg="{manufacturer}" code="{designation}" delays="{delays}"'
        f' Type="single-use"'
        f' dia="{diameter_mm:.3f}" len="{length_mm:.3f}"'
        f' initWt="{init_g:.2f}" propWt="{prop_g:.2f}"'
        f' auto-calc-mass="1" auto-calc-cg="1">\n'
        f'<comments>{comment_text}</comments>\n'
        '<data>\n'
        f'      {data_block}\n'
        '</data>\n'
        '</engine>\n'
        ' </engine-list>\n'
        '</engine-database>\n'
    )


def lbf_pairs_to_n(pairs):
    """Convert (t, F_lbf) -> (t, F_N) pairs."""
    return [(t, f * LBF) for (t, f) in pairs]


# =========================================================================
# Common helpers for motor mount XML
# =========================================================================
def make_motor_mount_xml(configid, designation, manufacturer,
                         diameter_m, length_m, digest,
                         ignition_event="automatic", ignition_delay=0.0):
    return f"""              <motormount>
                <ignitionevent>{ignition_event}</ignitionevent>
                <ignitiondelay>{ignition_delay}</ignitiondelay>
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
                  <ignitionevent>{ignition_event}</ignitionevent>
                  <ignitiondelay>{ignition_delay}</ignitiondelay>
                </ignitionconfiguration>
              </motormount>"""


def make_mass_component_xml(name, mass_kg, length_m=0.05, radius_m=0.02,
                            axial_method="middle", axial_offset_m=0.0,
                            component_type="masscomponent"):
    mid = newid()
    return f"""              <masscomponent>
                <name>{name}</name>
                <id>{mid}</id>
                <axialoffset method="{axial_method}">{axial_offset_m:.6f}</axialoffset>
                <position type="{axial_method}">{axial_offset_m:.6f}</position>
                <packedlength>{length_m:.6f}</packedlength>
                <packedradius>{radius_m:.6f}</packedradius>
                <radialposition>0.0</radialposition>
                <radialdirection>0.0</radialdirection>
                <mass>{mass_kg:.6f}</mass>
                <masscomponenttype>{component_type}</masscomponenttype>
              </masscomponent>"""


# =========================================================================
# 25-KS-325 motor (Arcas original short, used by Flights 2-5)
# Source: arcas_original_blunt.yaml lines 210-303
# =========================================================================
ARCAS_25KS325_THRUST_LBF = [
    (0.0,   0.0),
    (0.05,  280.0),
    (0.20,  318.0),
    (1.0,   322.0),
    (5.0,   325.0),
    (10.0,  326.0),
    (15.0,  326.0),
    (20.0,  322.0),
    (22.0,  315.0),
    (24.0,  270.0),
    (24.5,  130.0),
    (25.0,  0.0),
]
ARCAS_25KS325_PROP_KG = 16.43           # arcas_original_blunt.yaml:234
ARCAS_25KS325_TOTAL_KG = 26.0           # arcas_original_blunt.yaml:241
ARCAS_25KS325_LEN_M = 1.3830            # arcas_original_blunt.yaml:138
ARCAS_25KS325_DIA_M = 0.11430           # arcas_original_blunt.yaml:150


def build_arcas_25ks325_rse():
    return build_rse_xml(
        designation="25-KS-325",
        manufacturer="AtlanticResearch",
        diameter_mm=ARCAS_25KS325_DIA_M * 1000,
        length_mm=ARCAS_25KS325_LEN_M * 1000,
        delays="P",
        propellant_kg=ARCAS_25KS325_PROP_KG,
        total_kg=ARCAS_25KS325_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(ARCAS_25KS325_THRUST_LBF),
        comment_lines=[
            "Arcas 25-KS-325 MARC 2A1 motor (original short, Performance Flights 1-5)",
            "Source: DTIC AD-235341 p.4 design parameters",
            "Avg thrust 325 lbf, total impulse 8125 lbf-s, action time 25.0 s",
            "End-burning Arcite 373D propellant, 70 deg F",
        ],
    )


ARCAS_25KS325_DIGEST = "arcas-25ks325"


# =========================================================================
# Arcas blunt (1.5-cal nose) -- arcas_original_blunt.yaml
# =========================================================================
def build_arcas_blunt_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    motor_body_id = newid()
    fins_id = newid()

    # Per arcas_original_blunt.yaml:
    body_total_len = 1.9939          # :55  78.5 in
    body_dia = 0.11430               # :56  4.45 in
    nose_len = 0.169545              # :83  6.675 in (1.5-cal ogive)
    nose_mass = 0.272                # :96  ~0.6 lb inferred
    motor_case_len = body_total_len - nose_len  # ~1.824 m

    # Fins (yaml :170-205)
    fin_root = 0.1778                # 7.0 in (inferred)
    fin_tip = 0.1016                 # 4.0 in (inferred)
    fin_height = 0.10859             # 4.275 in
    fin_sweep = 0.0381               # 1.5 in (inferred)
    fin_thickness = 0.005080         # 0.20 in (estimated)
    fin_cant_rad = 0.5 * 3.141592653589793 / 180.0   # 0.5 deg cant -> 1-3 rps

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Arcas (original blunt 1.5-cal, 25-KS-325 motor)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Arcas Type C original-flight configuration (Performance Flights 1-3).
Source: DTIC AD-235341 p.2-15, Webster/Roberts/Donnell (Atlantic Research), 1960.
Geometry: Table I (PDF p.27); 1.5-cal secant ogive nose; 25-KS-325 MARC 2A1 motor.
Performance: Flight 2 = 78k ft, Flight 3 = 90.2k ft (PDF p.15-16).
Fins: 4 double-wedge cast aluminum, 0.5-deg cant for 1-3 rps roll (PDF p.2).
NOTE: nose modeled as tangent ogive (OpenRocket has no native secant variant).</comment>
    <designer>ORP v2 corpus build (audited from arcas_original_blunt.yaml)</designer>
    <revision>2026-05-03 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>25-KS-325</name>
      <stage number="0" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Arcas (single stage)</name>

        <subcomponents>
          <nosecone>
            <name>1.5-cal blunt nose (aluminum, secant ogive per source)</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.001524</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{body_dia / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>{nose_mass:.6f}</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
          </nosecone>

          <bodytube>
            <name>Motor case + fins (4130 steel, 54.45 in)</name>
            <id>{motor_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">4130 Steel</material>
            <length>{motor_case_len:.6f}</length>
            <thickness>0.001016</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <!-- Override = 0: motor .rse carries the full loaded motor mass
                 (26.0 kg = 41 lb prop + 23.5 lb inert per spec). Setting
                 the body-tube-plus-children to 0 prevents double-counting
                 the case + fin mass that's already in motor inert. -->
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "25-KS-325", "AtlanticResearch",
                     ARCAS_25KS325_DIA_M, ARCAS_25KS325_LEN_M,
                     ARCAS_25KS325_DIGEST)}

            <subcomponents>
              <trapezoidfinset>
                <name>4 double-wedge cast aluminum fins, 0.5-deg cant</name>
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
                <thickness>{fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>{fin_cant_rad:.6f}</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{fin_root:.6f}</rootchord>
                <tipchord>{fin_tip:.6f}</tipchord>
                <sweeplength>{fin_sweep:.6f}</sweeplength>
                <height>{fin_height:.6f}</height>
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


# =========================================================================
# Arcas secant ogive (4-cal nose) -- arcas_secant_ogive_original_motor.yaml
# Same motor, fins, body diameter as blunt; only nose changed.
# =========================================================================
def build_arcas_secant_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    motor_body_id = newid()
    fins_id = newid()

    body_total_len = 2.2530          # :62  88.7 in inferred
    body_dia = 0.11430
    nose_len = 0.45974               # :99  18.1 in (4-cal secant ogive)
    nose_mass = 0.6804               # :108 1.5 lb (Table I)
    motor_case_len = body_total_len - nose_len  # ~1.793 m

    fin_root = 0.1778
    fin_tip = 0.1016
    fin_height = 0.10859
    fin_sweep = 0.0381
    fin_thickness = 0.005080
    fin_cant_rad = 0.5 * 3.141592653589793 / 180.0

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Arcas (4-cal secant ogive, 25-KS-325 motor)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Arcas intermediate config (Performance Flights 4-5): 4-cal secant
ogive nose substituted for 1.5-cal blunt; original motor retained.
Source: DTIC AD-235341 p.16, p.27 Table I, p.50 Fig 5, p.52 Fig 7.
Performance: Flight 4 = 178k ft, Flight 5 = 171.4k ft.
NOTE: nose modeled as tangent ogive (OpenRocket has no native secant variant).
This is a clean nose-shape ablation against arcas_blunt.ork (same motor + fins).</comment>
    <designer>ORP v2 corpus build (audited from arcas_secant_ogive_original_motor.yaml)</designer>
    <revision>2026-05-03 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>25-KS-325</name>
      <stage number="0" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Arcas (single stage)</name>

        <subcomponents>
          <nosecone>
            <name>4-cal secant ogive (aluminum, modelled as tangent ogive)</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.001524</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{body_dia / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>{nose_mass:.6f}</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
          </nosecone>

          <bodytube>
            <name>Motor case + fins (4130 steel, 54.45 in)</name>
            <id>{motor_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">4130 Steel</material>
            <length>{motor_case_len:.6f}</length>
            <thickness>0.001016</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "25-KS-325", "AtlanticResearch",
                     ARCAS_25KS325_DIA_M, ARCAS_25KS325_LEN_M,
                     ARCAS_25KS325_DIGEST)}

            <subcomponents>
              <trapezoidfinset>
                <name>4 double-wedge cast aluminum fins, 0.5-deg cant</name>
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
                <thickness>{fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>{fin_cant_rad:.6f}</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{fin_root:.6f}</rootchord>
                <tipchord>{fin_tip:.6f}</tipchord>
                <sweeplength>{fin_sweep:.6f}</sweeplength>
                <height>{fin_height:.6f}</height>
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


# =========================================================================
# Nike-Apache -- nike_apache.yaml
# Two-stage Nike M5-E1 booster + Apache TE-307 Mod II sustainer.
# =========================================================================
NIKE_MOTOR_THRUST_LBF = [
    (0.0, 42500.0),
    (3.5, 42500.0),
    (3.5, 0.0),
]
NIKE_PROP_KG = 755.0 * LB
NIKE_MOTOR_LOADED_KG = 1215.0 * LB
NIKE_MOTOR_LEN_M = 149.75 * IN
NIKE_MOTOR_DIA_M = 16.5 * IN
NIKE_ADAPTER_MASS_KG = 27.0 * LB

APACHE_THRUST_LBF = [
    (0.00, 0.0),
    (0.06, 5250.0),
    (0.13, 6400.0),
    (0.16, 6050.0),
    (0.21, 5460.0),
    (0.26, 5320.0),
    (0.36, 5210.0),
    (0.56, 5000.0),
    (1.06, 4780.0),
    (1.56, 4750.0),
    (2.06, 4930.0),
    (2.56, 5200.0),
    (3.06, 5200.0),
    (3.56, 5360.0),
    (4.06, 5550.0),
    (4.56, 5740.0),
    (4.74, 5796.0),
    (4.86, 5710.0),
    (5.06, 5400.0),
    (5.48, 4100.0),
    (5.73, 3250.0),
    (5.86, 1950.0),
    (6.06, 650.0),
    (6.14, 340.0),
    (6.36, 0.0),
]
APACHE_PROP_KG = 131.0 * LB
APACHE_MOTOR_LOADED_KG = 189.75 * LB
APACHE_MOTOR_LEN_M = 107.0 * IN
APACHE_MOTOR_DIA_M = 6.5 * IN

NIKE_MOTOR_DIGEST = "nike-m5e1"
APACHE_MOTOR_DIGEST = "apache-te307-modii"


def build_nike_motor_rse():
    return build_rse_xml(
        designation="M5-E1",
        manufacturer="Hercules",
        diameter_mm=NIKE_MOTOR_DIA_M * 1000,
        length_mm=NIKE_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=NIKE_PROP_KG,
        total_kg=NIKE_MOTOR_LOADED_KG,
        thrust_pairs_n=lbf_pairs_to_n(NIKE_MOTOR_THRUST_LBF),
        comment_lines=[
            "Nike M5-E1 booster used by Nike-Apache.",
            "Source: NASA TM X-55700 / X-721-66-569 Appendix A p.64.",
            "Handbook trajectory input: flat 42,500 lbf thrust for 3.5 s.",
            "Loaded motor 1215 lb, propellant consumed 755 lb (Handbook p.6).",
        ],
        cg_fraction=67.5 / 149.75,
    )


def build_apache_motor_rse():
    return build_rse_xml(
        designation="TE-307 Mod II",
        manufacturer="Thiokol",
        diameter_mm=APACHE_MOTOR_DIA_M * 1000,
        length_mm=APACHE_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=APACHE_PROP_KG,
        total_kg=APACHE_MOTOR_LOADED_KG,
        thrust_pairs_n=lbf_pairs_to_n(APACHE_THRUST_LBF),
        comment_lines=[
            "Apache TE-307 Mod II sustainer.",
            "Source: NASA TM X-55700 / X-721-66-569 Appendix A p.65.",
            "Thiokol test curve 6 Feb 1961, TE-307 2506-BP15-316-8.",
            "Loaded motor 189.75 lb, propellant 131 lb, burn time 6.4 s (Handbook p.6).",
        ],
        cg_fraction=54.8 / 107.0,
    )


def build_nike_apache_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    payload_id = newid()
    apache_body_id = newid()
    apache_fins_id = newid()
    adapter_id = newid()
    nike_body_id = newid()
    nike_boattail_id = newid()
    nike_fins_id = newid()

    # Canonical Dembrow flight 14.108 GI: 76 lb payload, turnstile antennas.
    payload_mass_kg = 76.0 * LB
    payload_cyl_len = 6.0 * IN
    nose_len = 34.0 * IN
    payload_radius = 6.62 * IN / 2.0
    apache_radius = APACHE_MOTOR_DIA_M / 2.0
    nike_radius = 16.5 * IN / 2.0
    nike_aft_radius = 17.5 * IN / 2.0
    adapter_len = 12.0 * IN

    # The Apache flight-weight-no-payload is 217 lb = motor loaded + fin
    # assembly. The motor.rse carries the loaded motor, so the fin assembly
    # mass is represented by this physical fin component.
    apache_fin_mass_kg = 27.25 * LB
    apache_fin_root = 18.20 * IN
    apache_fin_tip = 4.90 * IN
    apache_fin_height = 9.97 * IN
    apache_fin_sweep = 13.30 * IN
    apache_fin_thickness = 0.225 * IN

    # Nike fins: 75 lb total, Fig. 5 planform. Use a mass override on the
    # fins because the material/thickness geometry is insufficient to recover
    # the steel-cuffed assembly weight.
    nike_fin_mass_kg = 75.0 * LB
    nike_fin_root = 23.3 * IN
    nike_fin_tip = 10.1 * IN
    nike_fin_height = 10.75 * IN
    nike_fin_sweep = 13.2 * IN
    nike_fin_thickness = 0.25 * IN

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Nike-Apache (NASA 14.108 GI / Dembrow canonical)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Two-stage Nike M5-E1 + Apache TE-307 Mod II sounding rocket.
Sources: NASA TM X-55700 / X-721-66-569 Nike Apache Performance Handbook; Dembrow and Jamieson 1963 postflight comparison.
Geometry/masses: Handbook Fig.5/Fig.6 and p.6 physical characteristics.
Thrust: Handbook Appendix A p.64 Nike rectangular input; p.65 Apache Thiokol test curve.
Flight target: NASA 14.108 GI, Wallops 9 Mar 1963, 76 lb payload, launcher set 75.7 deg elevation, effective flight-path angle slightly above 83 deg.
Apogee target is bracketed from Dembrow text/Fig.2 (~100 statute mi peak, theoretical 3.5 mi high); see nike_apache.yaml for confidence notes.</comment>
    <designer>ORP v2 corpus build (audited from nike_apache.yaml)</designer>
    <revision>2026-05-05 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Nike M5-E1 + Apache TE-307 Mod II</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Apache sustainer + payload</name>

        <subcomponents>
          <nosecone>
            <name>11-degree cone nose (34 in derived)</name>
            <id>{nose_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.002000</thickness>
            <shape>conical</shape>
            <aftradius>{payload_radius:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Payload / turnstile antenna section (76 lb)</name>
            <id>{payload_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{payload_cyl_len:.6f}</length>
            <thickness>0.002000</thickness>
            <radius>{payload_radius:.6f}</radius>
            <overridemass>{payload_mass_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Apache TE-307 Mod II motor + fin can</name>
            <id>{apache_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{APACHE_MOTOR_LEN_M:.6f}</length>
            <thickness>0.002000</thickness>
            <radius>{apache_radius:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "TE-307 Mod II", "Thiokol",
                     APACHE_MOTOR_DIA_M, APACHE_MOTOR_LEN_M,
                     APACHE_MOTOR_DIGEST,
                     ignition_event="burnout", ignition_delay=16.5)}

            <subcomponents>
              <trapezoidfinset>
                <name>Apache fins (AEDC full-scale, 27.25 lb assembly)</name>
                <id>{apache_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2700.0">Aluminum / steel leading-edge cuffs</material>
                <thickness>{apache_fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{apache_fin_root:.6f}</rootchord>
                <tipchord>{apache_fin_tip:.6f}</tipchord>
                <sweeplength>{apache_fin_sweep:.6f}</sweeplength>
                <height>{apache_fin_height:.6f}</height>
                <overridemass>{apache_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Nike booster</name>
        <separationevent>burnout</separationevent>
        <separationaltitude>200.0</separationaltitude>
        <separationdelay>0.0</separationdelay>
        <separationconfiguration configid="{cid}">
          <separationevent>burnout</separationevent>
          <separationaltitude>200.0</separationaltitude>
          <separationdelay>0.0</separationdelay>
        </separationconfiguration>

        <subcomponents>
          <transition>
            <name>Nike-Apache conical adapter (27 lb, 12 in inferred)</name>
            <id>{adapter_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{adapter_len:.6f}</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{apache_radius:.6f}</foreradius>
            <aftradius>{nike_radius:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>{NIKE_ADAPTER_MASS_KG:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>

          <bodytube>
            <name>Nike M5-E1 motor case</name>
            <id>{nike_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{NIKE_MOTOR_LEN_M:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{nike_radius:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "M5-E1", "Hercules",
                     NIKE_MOTOR_DIA_M, NIKE_MOTOR_LEN_M,
                     NIKE_MOTOR_DIGEST,
                     ignition_event="automatic", ignition_delay=0.0)}

            <subcomponents>
              <trapezoidfinset>
                <name>Nike cruciform fins (75 lb total)</name>
                <id>{nike_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="7850.0">Steel-cuffed Nike fin assembly</material>
                <thickness>{nike_fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="7850.0">Steel</filletmaterial>
                <rootchord>{nike_fin_root:.6f}</rootchord>
                <tipchord>{nike_fin_tip:.6f}</tipchord>
                <sweeplength>{nike_fin_sweep:.6f}</sweeplength>
                <height>{nike_fin_height:.6f}</height>
                <overridemass>{nike_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>

          <transition>
            <name>Nike aft skirt / boattail to 17.5 in max dia</name>
            <id>{nike_boattail_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>0.050800</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{nike_radius:.6f}</foreradius>
            <aftradius>{nike_aft_radius:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>
        </subcomponents>
      </stage>
    </subcomponents>
  </rocket>

  <simulations>
  </simulations>
</openrocket>
"""


# =========================================================================
# HEROS 3 hybrid -- heros_3.yaml
# Synthesized motor: 10 kN x 15 s liquid + ~1.5 kN x 10 s gaseous blowdown.
# Total impulse ~165,000 N-s. Mass: 70% N2O fill (synthesized 130 kg N2O)
# + ~16 kg paraffin (synthesized from typical hybrid O/F=8) = ~146 kg prop.
# Empty mass 75 kg per yaml:268.
# =========================================================================
HEROS_THRUST_N = [
    # Faster ramp: hybrid motors hit design thrust within ~100 ms of
    # ignition once liquid N2O reaches the injector. The previous slow
    # 0.5-s ramp let the rocket sit on the rail with thrust < weight,
    # which OpenRocket aborted as a non-lift-off case.
    (0.0,    0.0),
    (0.05,  4000.0),
    (0.10,  8000.0),
    (0.20, 10000.0),
    (5.0,  10000.0),
    (10.0, 10000.0),
    (15.0, 10000.0),    # liquid N2O depleted at ~15 s (yaml:206)
    (15.5,  2500.0),    # transition to gas-phase blowdown
    (17.0,  1500.0),
    (20.0,  1300.0),
    (23.0,  1100.0),
    (24.5,   600.0),
    (25.0,     0.0),
]
HEROS_PROP_KG = 146.0    # synthesized: 130 kg N2O (70% fill) + 16 kg paraffin
HEROS_INERT_KG = 15.0    # ONLY chamber + nozzle inert (motor section).
                         # The remaining 60 kg of empty mass is structural
                         # (nose, electronics, recovery, tank, valves) and
                         # is distributed across body-tube overrides below.
                         # CRITICAL: collocating all 50 kg motor-inert at
                         # the aft motor section (previous v1 build) pushed
                         # loaded CG to z=6.5m on a 7.5m rocket -> CG aft
                         # of CP -> statically unstable -> tumbled at 3.8s.
                         # Redistributing the heavy oxidizer tank structure
                         # to its real mid-body location fixes the static
                         # margin without changing total mass.
HEROS_MOTOR_LEN_M = 1.300       # heros_3.yaml:236
HEROS_MOTOR_DIA_M = 0.223       # heros_3.yaml:90 body dia (assumed)


def build_heros_motor_rse():
    return build_rse_xml(
        designation="HyRES",
        manufacturer="HyEnD",
        diameter_mm=HEROS_MOTOR_DIA_M * 1000,
        length_mm=HEROS_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=HEROS_PROP_KG,
        total_kg=HEROS_PROP_KG + HEROS_INERT_KG,
        thrust_pairs_n=HEROS_THRUST_N,
        comment_lines=[
            "HEROS 3 HyRES hybrid motor (paraffin / N2O blowdown)",
            "Source: Kobald et al. 2018 JSASS, p.312-316 (DOI 10.2322/tastj.16.312)",
            "SYNTHESIZED thrust curve: 10 kN x 15 s liquid + ~1.5 kN x 10 s gas blowdown",
            "Absolute thrust curve NOT published in source (Fig.5 normalized only)",
            "Total impulse synthesized ~165 kN-s. Propellant 146 kg synthesized",
            "from 70% N2O fill (yaml:252) and assumed O/F=8 paraffin grain.",
        ],
    )


HEROS_MOTOR_DIGEST = "heros3-hyres"


def build_heros3_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    fwd_id = newid()
    mid_id = newid()
    interstage_id = newid()
    tank_id = newid()
    valve_id = newid()
    motor_body_id = newid()
    fins_id = newid()

    # heros_3.yaml geometry
    body_dia = 0.223                       # :90
    nose_len = 0.270                       # :147
    fwd_bay_len = 0.760                    # :111 (1030 - 270)
    mid_bay_len = 0.510                    # :113
    interstage_len = 0.386                 # :115
    tank_len = 4.000                       # :117
    valve_len = 0.560                      # :120
    motor_len = 1.300                      # :122

    # Empty mass 75 kg distributed approximately:
    # forward structure (nose+electronics+recovery+interstage) ~ 15 kg
    # tank+oxidizer dry hardware ~ 10 kg
    # motor + fin structure ~ 50 kg (in HEROS_INERT_KG via motor.rse)
    # Total motor.rse inert (50 kg) + structure overrides (25 kg) = 75 kg empty.

    # Fins: yaml:158-174, root 470 mm, span 200 mm, tip+sweep MISSING.
    # Synthesize as clipped delta with 50% taper, 30-deg LE sweep.
    # Semispan increased from yaml-stated 200 mm to 320 mm: needed for
    # static stability at AoA>10 deg given the 7.5 m fineness-ratio-34
    # body, where Allen-Perkins crossflow CN_body grows with sin^2(AoA)
    # and shifts CP forward. Real HEROS 3 photos (Fig.1 p.314) show fins
    # noticeably wider than the 200 mm bracket in Fig.2, suggesting Fig.2's
    # fin bracket may not include the LE root extension. Documented
    # synthesis; not source-verifiable.
    fin_root = 0.470
    fin_tip = 0.235          # synthesized 50% taper
    fin_height = 0.320       # synthesized stability fix (was yaml 0.200)
    fin_sweep = 0.235        # synthesized
    fin_thickness = 0.005    # 5 mm CFRP (synthesized)

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>HEROS 3 (HyEnD/Stuttgart hybrid sounding rocket)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>HEROS 3 hybrid sounding rocket: paraffin/N2O blowdown propulsion.
Source: Kobald et al. 2018 JSASS Vol 16 No 3 pp.312-317, DOI 10.2322/tastj.16.312.
Geometry: Fig 2 (p.314); 7.5 m total, 0.223 m diameter, 75 kg empty mass.
Performance target: 32,300 m apogee (Esrange 8 Nov 2016, GPS-confirmed).
NOTE: many fields synthesized — see heros_3.yaml unresolved_for_v2_corpus.</comment>
    <designer>ORP v2 corpus build (audited from heros_3.yaml)</designer>
    <revision>2026-05-03 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>HyRES hybrid</name>
      <stage number="0" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>HEROS 3 (single stage)</name>

        <subcomponents>
          <nosecone>
            <name>Nose cone (CFRP, fineness 1.21)</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="1600.0">CFRP composite</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.005</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{body_dia / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>4.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
          </nosecone>

          <bodytube>
            <name>Forward bay (electronics + recovery, 760 mm) [10 kg]</name>
            <id>{fwd_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1600.0">CFRP/GFRP composite</material>
            <length>{fwd_bay_len:.6f}</length>
            <thickness>0.003</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>10.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Mid bay (510 mm) [5 kg]</name>
            <id>{mid_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1600.0">CFRP/GFRP composite</material>
            <length>{mid_bay_len:.6f}</length>
            <thickness>0.003</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>5.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Safety valve / interstage (386 mm) [4 kg]</name>
            <id>{interstage_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{interstage_len:.6f}</length>
            <thickness>0.003</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>4.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Oxidizer (N2O) tank, 4.0 m [25 kg structure]</name>
            <id>{tank_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1600.0">CFRP composite</material>
            <length>{tank_len:.6f}</length>
            <thickness>0.005</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <!-- 25 kg here is the dominant empty-mass concentration on
                 a real hybrid: 4 m carbon-fibre pressure vessel + main
                 valve + plumbing. Putting this mass mid-body keeps
                 loaded CG forward of CP for static stability. -->
            <overridemass>25.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Loading / main valve / measurement (560 mm) [12 kg]</name>
            <id>{valve_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{valve_len:.6f}</length>
            <thickness>0.003</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>12.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>HyRES hybrid motor section (1300 mm)</name>
            <id>{motor_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{motor_len:.6f}</length>
            <thickness>0.005</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <!-- Motor.rse carries 50 kg motor-inert mass; fins included
                 in subcomponents=true override at 0 (motor brings the
                 mass for case + fin support hardware). -->
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "HyRES", "HyEnD",
                     HEROS_MOTOR_DIA_M, HEROS_MOTOR_LEN_M,
                     HEROS_MOTOR_DIGEST)}

            <subcomponents>
              <trapezoidfinset>
                <name>3 fins (CFRP, 470/235/200/235 mm root/tip/h/sweep)</name>
                <id>{fins_id}</id>
                <instancecount>3</instancecount>
                <fincount>3</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="1600.0">CFRP composite</material>
                <thickness>{fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="1600.0">CFRP</filletmaterial>
                <rootchord>{fin_root:.6f}</rootchord>
                <tipchord>{fin_tip:.6f}</tipchord>
                <sweeplength>{fin_sweep:.6f}</sweeplength>
                <height>{fin_height:.6f}</height>
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


# =========================================================================
# Black Brant V VB (AAF-VB-32) -- black_brant_v.yaml
# Synthesized motor: 26KS20000 designation -> 26 s action, ~94 kN avg thrust.
# Total impulse ~2.45 MN-s synthesized from prop mass 1020.7 kg * Isp 245 s
# (typical PAC-aluminized propellant). Avg thrust = 2.45e6 / 26 = 94 kN.
# =========================================================================
BBV_PROP_KG = 1020.7              # black_brant_v.yaml:77 (Discharge Weight)
BBV_MOTOR_LOADED_KG = 1229.3      # :105 Motor Weight
BBV_MOTOR_LEN_M = 4.78            # :108
BBV_MOTOR_DIA_M = 0.43            # :107
# Synthesized profile: end-burning-style with brief ramp + plateau + taper
BBV_THRUST_N = [
    (0.0,    0.0),
    (0.1,  85000.0),
    (0.5,  92000.0),
    (1.0,  94000.0),
    (5.0,  96000.0),
    (10.0, 96000.0),
    (15.0, 95000.0),
    (20.0, 94000.0),
    (24.0, 90000.0),
    (25.5, 50000.0),
    (26.0,     0.0),
]
# Integrated: ~94 kN x 26 s = 2.44 MN-s (matches synthesized I_tot)


def build_bbv_motor_rse():
    return build_rse_xml(
        designation="26KS20000",
        manufacturer="BristolAerospace",
        diameter_mm=BBV_MOTOR_DIA_M * 1000,
        length_mm=BBV_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=BBV_PROP_KG,
        total_kg=BBV_MOTOR_LOADED_KG,
        thrust_pairs_n=BBV_THRUST_N,
        comment_lines=[
            "Bristol Aerospace 26KS20000 / BAW-MV-57 (Black Brant V VB)",
            "Source: DTIC AD0733141 p.2-3 designation; Bristol ER 67533 NOT on hand",
            "SYNTHESIZED thrust curve: 26 s action, ~94 kN avg, ~2.44 MN-s total",
            "Total impulse computed from prop mass 1020.7 kg * Isp 245 s (PAC propellant)",
            "Aluminized polyurethane / ammonium perchlorate single-grain solid",
        ],
    )


BBV_MOTOR_DIGEST = "bbv-26ks20000"


def build_bbv_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    fwd_body_id = newid()
    motor_body_id = newid()
    fins_id = newid()

    # Geometry from black_brant_v.yaml:
    body_total = 7.993               # :55
    body_dia = 0.43                  # :57
    payload_section_len = 3.213      # :61
    motor_section_len = 4.78         # :66
    # Synthesized: nose cone 1.5 m (3.5 cal), forward body = 1.713 m
    nose_len = 1.5
    fwd_body_len = payload_section_len - nose_len
    payload_mass = 240.4             # yaml:83

    # Synthesized fin geometry (yaml flags MISSING for all fin dims):
    fin_root = 1.20         # synthesized: typical BBV photo measurements
    fin_tip = 0.60          # synthesized
    fin_height = 0.50       # synthesized
    fin_sweep = 0.40        # synthesized
    fin_thickness = 0.012   # synthesized 12 mm aluminum

    # Cant for 3.7 rps target spin (yaml:177); cant angle MISSING in source
    # but can be solved from documented spin rate. Synthesize 1.5 deg as
    # rough order-of-magnitude (small canted fin produces ~3-4 rps for a
    # 7-meter, 1500-kg rocket; exact value TBD from sim).
    fin_cant_rad = 1.5 * 3.141592653589793 / 180.0

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Black Brant V VB (AAF-VB-32, Churchill 1971)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Single-stage solid sounding rocket. Bristol Aerospace BAW-MV-57 (26KS20000) motor.
Source: DTIC AD0733141, NRC Canada, Oct 1971 (Churchill 3 Mar 1971 launch AAF-VB-32).
Vehicle: 7.993 m total, 0.43 m diameter, 1469.7 kg liftoff, 469.8 kg burnout.
Performance target: 273.6 km apogee at T+264 s (predicted 262 km @ T+272 s).
NOTE: many fields synthesized -- see black_brant_v.yaml missing_from_source_summary.
Fin dims, cant, thrust curve all synthesized; expect reduced accuracy.</comment>
    <designer>ORP v2 corpus build (audited from black_brant_v.yaml)</designer>
    <revision>2026-05-03 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>26KS20000</name>
      <stage number="0" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>BBV (single stage)</name>

        <subcomponents>
          <nosecone>
            <name>Nose cone (magnesium, ogive, 1.5 m synthesized)</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="1738.0">Magnesium</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.003</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{body_dia / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Forward body / payload (240.4 kg)</name>
            <id>{fwd_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{fwd_body_len:.6f}</length>
            <thickness>0.003</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <overridemass>{payload_mass:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Motor case (BAW-MV-57, 4.78 m)</name>
            <id>{motor_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{motor_section_len:.6f}</length>
            <thickness>0.004</thickness>
            <radius>{body_dia / 2:.6f}</radius>
            <!-- Override = 0: motor.rse carries 1229.3 kg loaded
                 (1020.7 kg prop + 208.6 kg inert per yaml:88). Setting
                 the body-tube to 0 prevents double-counting the case. -->
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "26KS20000", "BristolAerospace",
                     BBV_MOTOR_DIA_M, BBV_MOTOR_LEN_M,
                     BBV_MOTOR_DIGEST)}

            <subcomponents>
              <trapezoidfinset>
                <name>3 fins (clipped delta, all dims synthesized; cant 1.5 deg)</name>
                <id>{fins_id}</id>
                <instancecount>3</instancecount>
                <fincount>3</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2700.0">Aluminum</material>
                <thickness>{fin_thickness:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>{fin_cant_rad:.6f}</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{fin_root:.6f}</rootchord>
                <tipchord>{fin_tip:.6f}</tipchord>
                <sweeplength>{fin_sweep:.6f}</sweeplength>
                <height>{fin_height:.6f}</height>
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


# =========================================================================
# Modern NASA solid-stack screening models
# =========================================================================
# These are deliberately separate from the audited Nike-Apache/BBV models.
# NASA/TP-20230006855 gives enough vehicle data for first-pass screening
# geometry, motor action time, average thrust, payload envelopes, and flight
# apogee references, but not enough for a gold-standard thrust/mass-history
# reconstruction. Fields below marked SYNTHESIZED should not be treated as
# publication-grade motor data.

TERRIER_MK12_MOTOR_DIGEST = "terrier-mk12-screen"
TERRIER_MK70_MOTOR_DIGEST = "terrier-mk70-screen"
IMPROVED_ORION_MOTOR_DIGEST = "improved-orion-screen"
IMPROVED_MALEMUTE_MOTOR_DIGEST = "improved-malemute-screen"
BLACK_BRANT_IX_MOTOR_DIGEST = "black-brant-ix-screen"

TERRIER_MOTOR_LEN_M = 169.0 * IN
TERRIER_MOTOR_DIA_M = 18.0 * IN
TERRIER_MK12_TOTAL_KG = 1907.0 * LB
TERRIER_MK70_TOTAL_KG = 2207.0 * LB
TERRIER_MK12_PROP_KG = 1500.0 * LB
TERRIER_MK70_PROP_KG = 1700.0 * LB
TERRIER_MK12_THRUST_LBF = [
    (0.00,     0.0),
    (0.08, 58000.0),
    (6.15, 58000.0),
    (6.23,     0.0),
]
TERRIER_MK70_THRUST_LBF = [
    (0.00,     0.0),
    (0.08, 65000.0),
    (5.85, 65000.0),
    (6.00,     0.0),
]

IMPROVED_ORION_LEN_M = 105.0 * IN
IMPROVED_ORION_DIA_M = 14.0 * IN
IMPROVED_ORION_TOTAL_KG = 943.0 * LB
IMPROVED_ORION_PROP_KG = 760.0 * LB
IMPROVED_ORION_THRUST_LBF = [
    (0.00,     0.0),
    (0.10, 20000.0),
    (6.00, 20000.0),
    (6.20,  4000.0),
    (24.00, 4000.0),
    (24.10,    0.0),
]

# Improved Malemute loaded mass is inferred from NASA/TP-20230006855 p.147:
# TIM MK12 stack no-payload mass 3315 lb minus TIO MK12 stack 2850 lb plus
# Improved Orion 943 lb -> 1408 lb. Average thrust is the legacy NASA
# Terrier-Malemute datasheet value; the new handbook only states burn time.
IMPROVED_MALEMUTE_LEN_M = 130.0 * IN
IMPROVED_MALEMUTE_DIA_M = 16.0 * IN
IMPROVED_MALEMUTE_TOTAL_KG = 1408.0 * LB
IMPROVED_MALEMUTE_PROP_KG = 550.0 * LB
IMPROVED_MALEMUTE_THRUST_LBF = [
    (0.00,     0.0),
    (0.10, 14200.0),
    (0.80, 11600.0),
    (3.00,  9800.0),
    (9.00,  9300.0),
    (11.50, 7000.0),
    (11.70,    0.0),
]

BLACK_BRANT_IX_MOTOR_LEN_M = 223.0 * IN
BLACK_BRANT_IX_MOTOR_DIA_M = 17.26 * IN
BLACK_BRANT_IX_TOTAL_KG = 2827.0 * LB
BLACK_BRANT_IX_PROP_KG = 2223.0 * LB
BLACK_BRANT_IX_THRUST_LBF = [
    (0.00,     0.0),
    (0.20, 23317.0),
    (5.00, 23317.0),
    (15.00, 23317.0),
    (25.00, 22000.0),
    (27.50,     0.0),
]


def build_terrier_mk12_motor_rse():
    return build_rse_xml(
        designation="Terrier MK12",
        manufacturer="USN-Surplus",
        diameter_mm=TERRIER_MOTOR_DIA_M * 1000,
        length_mm=TERRIER_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=TERRIER_MK12_PROP_KG,
        total_kg=TERRIER_MK12_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(TERRIER_MK12_THRUST_LBF),
        comment_lines=[
            "Terrier MK12 screening motor for NASA sounding-rocket stacks.",
            "Source-backed geometry: NASA/TP-20230006855 p.144, p.147: 18 in dia, 169 in long.",
            "SYNTHESIZED thrust/mass: 58,000 lbf class, 6.23 s action from ASPIRE SR01 first-stage burnout timing.",
            "Loaded mass inferred from TIO MK12 stack no-payload mass 2850 lb minus Improved Orion 943 lb.",
        ],
    )


def build_terrier_mk70_motor_rse():
    return build_rse_xml(
        designation="Terrier MK70",
        manufacturer="USN-Surplus",
        diameter_mm=TERRIER_MOTOR_DIA_M * 1000,
        length_mm=TERRIER_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=TERRIER_MK70_PROP_KG,
        total_kg=TERRIER_MK70_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(TERRIER_MK70_THRUST_LBF),
        comment_lines=[
            "Terrier MK70 screening motor for NASA sounding-rocket stacks.",
            "Source-backed geometry: NASA/TP-20230006855 p.139/p.144: 18 in dia, 169 in long.",
            "SYNTHESIZED thrust/mass: 65,000 lbf class, 6.0 s action.",
            "Loaded mass inferred from TIO MK70 stack no-payload mass 3150 lb minus Improved Orion 943 lb.",
        ],
    )


def build_improved_orion_motor_rse():
    return build_rse_xml(
        designation="Improved Orion",
        manufacturer="Surplus",
        diameter_mm=IMPROVED_ORION_DIA_M * 1000,
        length_mm=IMPROVED_ORION_LEN_M * 1000,
        delays="P",
        propellant_kg=IMPROVED_ORION_PROP_KG,
        total_kg=IMPROVED_ORION_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(IMPROVED_ORION_THRUST_LBF),
        comment_lines=[
            "Improved Orion screening motor.",
            "NASA/TP-20230006855 p.135/p.144: 14 in dia, 105 in long, 943 lb.",
            "Handbook thrust: ~20,000 lbf for first 6 s, ~4,000 lbf until burnout at 24 s.",
            "SYNTHESIZED propellant mass because handbook does not tabulate consumed mass.",
        ],
    )


def build_improved_malemute_motor_rse():
    return build_rse_xml(
        designation="Improved Malemute",
        manufacturer="Thiokol",
        diameter_mm=IMPROVED_MALEMUTE_DIA_M * 1000,
        length_mm=IMPROVED_MALEMUTE_LEN_M * 1000,
        delays="P",
        propellant_kg=IMPROVED_MALEMUTE_PROP_KG,
        total_kg=IMPROVED_MALEMUTE_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(IMPROVED_MALEMUTE_THRUST_LBF),
        comment_lines=[
            "Improved Malemute screening motor.",
            "NASA/TP-20230006855 p.147: 16 in dia, 130 in long, 11.7 s burn.",
            "Loaded mass inferred from NASA stack masses; thrust profile synthesized from legacy Terrier-Malemute average/max thrust.",
            "SYNTHESIZED propellant mass because handbook does not tabulate consumed mass.",
        ],
    )


def build_black_brant_ix_motor_rse():
    return build_rse_xml(
        designation="Black Brant IX upper",
        manufacturer="Magellan",
        diameter_mm=BLACK_BRANT_IX_MOTOR_DIA_M * 1000,
        length_mm=BLACK_BRANT_IX_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=BLACK_BRANT_IX_PROP_KG,
        total_kg=BLACK_BRANT_IX_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(BLACK_BRANT_IX_THRUST_LBF),
        comment_lines=[
            "Black Brant IX second-stage screening motor with extended exit cone.",
            "NASA/TP-20230006855 p.139: 23,317 lbf average thrust, 27.5 s action.",
            "NASA/TP-20230006855 p.139: 17.26 in dia, 223 in long, 2827 lb loaded, 2223 lb propellant.",
            "Profile is flat average-thrust table because handbook does not include time-resolved thrust.",
        ],
    )


def build_screening_two_stage_xml(name, designer_yaml, comment, upper_motor_name,
                                  upper_manufacturer, upper_digest, upper_len_m,
                                  upper_dia_m, upper_fin_count, upper_fin_root_in,
                                  upper_fin_tip_in, upper_fin_span_in,
                                  upper_fin_sweep_in, upper_fin_thickness_in,
                                  booster_motor_name, booster_manufacturer,
                                  booster_digest, booster_total_len_m,
                                  booster_dia_m, booster_fin_area_ft2,
                                  payload_dia_in, payload_len_in, payload_mass_lb,
                                  nose_shape, nose_len_in, nose_total_angle_deg=None,
                                  upper_cant_deg=0.0, booster_cant_deg=0.0):
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    payload_id = newid()
    upper_body_id = newid()
    upper_fins_id = newid()
    booster_body_id = newid()
    booster_fins_id = newid()

    payload_radius = payload_dia_in * IN / 2.0
    upper_radius = upper_dia_m / 2.0
    booster_radius = booster_dia_m / 2.0
    upper_fin_root = upper_fin_root_in * IN
    upper_fin_tip = upper_fin_tip_in * IN
    upper_fin_span = upper_fin_span_in * IN
    upper_fin_sweep = upper_fin_sweep_in * IN
    upper_fin_thickness = upper_fin_thickness_in * IN

    # Keep the documented fin area exact while using a conservative clipped
    # delta shape. Area per fin = (root + tip) / 2 * span.
    booster_fin_area_in2 = booster_fin_area_ft2 * 144.0
    booster_fin_root_in = 30.0 if booster_fin_area_ft2 > 3.0 else 24.0
    booster_fin_tip_in = 15.0 if booster_fin_area_ft2 > 3.0 else 12.0
    booster_fin_span_in = 2.0 * booster_fin_area_in2 / (booster_fin_root_in + booster_fin_tip_in)
    booster_fin_sweep_in = (booster_fin_root_in - booster_fin_tip_in) * 0.75
    booster_fin_root = booster_fin_root_in * IN
    booster_fin_tip = booster_fin_tip_in * IN
    booster_fin_span = booster_fin_span_in * IN
    booster_fin_sweep = booster_fin_sweep_in * IN

    if nose_shape == "conical":
        shape_xml = "<shape>conical</shape>"
    else:
        shape_xml = "<shape>ogive</shape>\n            <shapeparameter>1.0</shapeparameter>"

    nose_note = ""
    if nose_total_angle_deg is not None:
        nose_note = f" ({nose_total_angle_deg:g}-degree total cone)"

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus screening build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>{name}</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>{comment}</comment>
    <designer>ORP v2 screening build ({designer_yaml})</designer>
    <revision>2026-05-05 v2-screening</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>{booster_motor_name} + {upper_motor_name}</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>{upper_motor_name} sustainer + payload</name>

        <subcomponents>
          <nosecone>
            <name>Payload nose{nose_note}</name>
            <id>{nose_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_len_in * IN:.6f}</length>
            <thickness>0.002000</thickness>
            {shape_xml}
            <aftradius>{payload_radius:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Payload section ({payload_mass_lb:.0f} lb screening mass)</name>
            <id>{payload_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{payload_len_in * IN:.6f}</length>
            <thickness>0.002500</thickness>
            <radius>{payload_radius:.6f}</radius>
            <overridemass>{payload_mass_lb * LB:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>{upper_motor_name} motor body</name>
            <id>{upper_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Motor case / airframe</material>
            <length>{upper_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{upper_radius:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, upper_motor_name, upper_manufacturer,
                     upper_dia_m, upper_len_m, upper_digest,
                     ignition_event="burnout", ignition_delay=0.0)}

            <subcomponents>
              <trapezoidfinset>
                <name>{upper_motor_name} fins (screening geometry)</name>
                <id>{upper_fins_id}</id>
                <instancecount>{upper_fin_count}</instancecount>
                <fincount>{upper_fin_count}</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2700.0">Aluminum</material>
                <thickness>{upper_fin_thickness:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>{upper_cant_deg * 3.141592653589793 / 180.0:.6f}</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{upper_fin_root:.6f}</rootchord>
                <tipchord>{upper_fin_tip:.6f}</tipchord>
                <sweeplength>{upper_fin_sweep:.6f}</sweeplength>
                <height>{upper_fin_span:.6f}</height>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>{booster_motor_name} booster</name>
        <separationevent>burnout</separationevent>
        <separationdelay>0.0</separationdelay>

        <subcomponents>
          <bodytube>
            <name>{booster_motor_name} motor body</name>
            <id>{booster_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{booster_total_len_m:.6f}</length>
            <thickness>0.004000</thickness>
            <radius>{booster_radius:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, booster_motor_name, booster_manufacturer,
                     booster_dia_m, booster_total_len_m, booster_digest,
                     ignition_event="automatic", ignition_delay=0.0)}

            <subcomponents>
              <trapezoidfinset>
                <name>Terrier booster fins ({booster_fin_area_ft2:.1f} sq ft each)</name>
                <id>{booster_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2700.0">Aluminum</material>
                <thickness>0.012700</thickness>
                <crosssection>airfoil</crosssection>
                <cant>{booster_cant_deg * 3.141592653589793 / 180.0:.6f}</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2700.0">Aluminum</filletmaterial>
                <rootchord>{booster_fin_root:.6f}</rootchord>
                <tipchord>{booster_fin_tip:.6f}</tipchord>
                <sweeplength>{booster_fin_sweep:.6f}</sweeplength>
                <height>{booster_fin_span:.6f}</height>
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


def build_terrier_improved_orion_xml():
    return build_screening_two_stage_xml(
        name="Terrier-Improved Orion MK12 (RockOn/RockSat-C 2016 screening)",
        designer_yaml="NASA/TP-20230006855 + NASA Wallops 2016 flight page",
        comment="""Two-stage MK12 Terrier + Improved Orion screening model.
Sources: NASA/TP-20230006855 p.144-146; NASA Wallops 24 Jun 2016 RockOn/RockSat-C flight.
Handbook: Terrier 18 in dia, 169 in long, four 4.8 sq ft fins; Improved Orion 14 in dia, 105 in long; no-payload stack approx 2850 lb; upper motor approx 20,000 lbf for 6 s and 4,000 lbf until 24 s.
Flight target: MK12 Terrier-Improved Orion, Wallops 24 Jun 2016, 74 mile payload apogee.
SCREENING MODEL: Terrier thrust curve, propellant masses, payload mass/length, and upper fin geometry are synthesized where not tabulated.""",
        upper_motor_name="Improved Orion",
        upper_manufacturer="Surplus",
        upper_digest=IMPROVED_ORION_MOTOR_DIGEST,
        upper_len_m=IMPROVED_ORION_LEN_M,
        upper_dia_m=IMPROVED_ORION_DIA_M,
        upper_fin_count=4,
        upper_fin_root_in=24.0,
        upper_fin_tip_in=10.0,
        upper_fin_span_in=10.0,
        upper_fin_sweep_in=12.0,
        upper_fin_thickness_in=0.25,
        booster_motor_name="Terrier MK12",
        booster_manufacturer="USN-Surplus",
        booster_digest=TERRIER_MK12_MOTOR_DIGEST,
        booster_total_len_m=TERRIER_MOTOR_LEN_M,
        booster_dia_m=TERRIER_MOTOR_DIA_M,
        booster_fin_area_ft2=4.8,
        payload_dia_in=14.0,
        payload_len_in=90.0,
        payload_mass_lb=500.0,
        nose_shape="conical",
        nose_len_in=41.8,
        nose_total_angle_deg=19.0,
        upper_cant_deg=0.8)


def build_terrier_improved_malemute_xml():
    return build_screening_two_stage_xml(
        name="Terrier-Improved Malemute MK12 (RockSat-X 2016 screening)",
        designer_yaml="NASA/TP-20230006855 + NASA Wallops 2016 flight page",
        comment="""Two-stage MK12 Terrier + Improved Malemute screening model.
Sources: NASA/TP-20230006855 p.147-149; NASA Wallops 17 Aug 2016 RockSat-X flight.
Handbook: Terrier 18 in dia, 169 in long, four 4.8 sq ft fins; Improved Malemute 16 in dia, 130 in long, 11.7 s burn; no-payload stack approx 3315 lb; flown payloads 600-1000 lb with 180-250 in lengths.
Flight target: Terrier-Improved Malemute, Wallops 17 Aug 2016, 95 mile payload altitude.
SCREENING MODEL: Terrier/Malemute thrust curves, propellant masses, exact payload mass, and fin geometry are synthesized where not tabulated.""",
        upper_motor_name="Improved Malemute",
        upper_manufacturer="Thiokol",
        upper_digest=IMPROVED_MALEMUTE_MOTOR_DIGEST,
        upper_len_m=IMPROVED_MALEMUTE_LEN_M,
        upper_dia_m=IMPROVED_MALEMUTE_DIA_M,
        upper_fin_count=4,
        upper_fin_root_in=30.0,
        upper_fin_tip_in=12.0,
        upper_fin_span_in=12.0,
        upper_fin_sweep_in=18.0,
        upper_fin_thickness_in=0.30,
        booster_motor_name="Terrier MK12",
        booster_manufacturer="USN-Surplus",
        booster_digest=TERRIER_MK12_MOTOR_DIGEST,
        booster_total_len_m=TERRIER_MOTOR_LEN_M,
        booster_dia_m=TERRIER_MOTOR_DIA_M,
        booster_fin_area_ft2=4.8,
        payload_dia_in=17.26,
        payload_len_in=200.0,
        payload_mass_lb=700.0,
        nose_shape="conical",
        nose_len_in=90.0,
        nose_total_angle_deg=11.0,
        upper_cant_deg=0.8)


def build_black_brant_ix_aspire_xml():
    return build_screening_two_stage_xml(
        name="Black Brant IX MOD2 (ASPIRE SR02 screening)",
        designer_yaml="NASA/TP-20230006855 + NTRS 20190028247",
        comment="""Two-stage MK70 Terrier + Black Brant IX MOD2 screening model.
Sources: NASA/TP-20230006855 p.139-143; NTRS 20190028247 ASPIRE Table 1 and payload geometry.
Handbook: Terrier MK70 first stage, 18 in dia, 169 in long, four 2.5 sq ft fins; Black Brant upper 17.26 in dia, 223 in long, 2827 lb loaded, 2223 lb propellant, 23,317 lbf average thrust, 27.5 s action.
ASPIRE SR02 target: 31 Mar 2018, 2nd-stage burnout T+34.10 s, payload separation T+103.99 s, apogee T+123.49 s at 54.82 km, payload mass approx 1100 kg.
SCREENING MODEL: Terrier thrust curve, exact ASPIRE payload mass variation, and detailed Black Brant fin geometry are not in the source set.""",
        upper_motor_name="Black Brant IX upper",
        upper_manufacturer="Magellan",
        upper_digest=BLACK_BRANT_IX_MOTOR_DIGEST,
        upper_len_m=BLACK_BRANT_IX_MOTOR_LEN_M,
        upper_dia_m=BLACK_BRANT_IX_MOTOR_DIA_M,
        upper_fin_count=3,
        upper_fin_root_in=48.0,
        upper_fin_tip_in=24.0,
        upper_fin_span_in=20.0,
        upper_fin_sweep_in=24.0,
        upper_fin_thickness_in=0.50,
        booster_motor_name="Terrier MK70",
        booster_manufacturer="USN-Surplus",
        booster_digest=TERRIER_MK70_MOTOR_DIGEST,
        booster_total_len_m=TERRIER_MOTOR_LEN_M,
        booster_dia_m=TERRIER_MOTOR_DIA_M,
        booster_fin_area_ft2=2.5,
        payload_dia_in=28.50,
        payload_len_in=262.23,
        payload_mass_lb=1100.0 / LB,
        nose_shape="conical",
        nose_len_in=47.05,
        nose_total_angle_deg=19.0,
        upper_cant_deg=0.8)


# =========================================================================
# Aerobee 150A, NASA 4.65GI -- NASA TR R-226 / NTRS 19660005621
# Liquid sustainer and solid booster represented as tabular RSE motors.
# =========================================================================
AEROBEE_BOOSTER_DIGEST = "aerobee-25ks18000"
AEROBEE_SUSTAINER_DIGEST = "aerobee-150a-liquid"
AEROBEE_DIA_M = 15.0 * IN
AEROBEE_SUSTAINER_LEN_M = 303.98 * IN
AEROBEE_BOOSTER_LEN_M = (382.02 - 303.98) * IN
AEROBEE_BOOSTER_DIA_M = 15.0 * IN
AEROBEE_BOOSTER_TOTAL_KG = 520.0 * LB
AEROBEE_BOOSTER_PROP_KG = 260.0 * LB
AEROBEE_SUSTAINER_TOTAL_KG = (279.1 + 758.2 + 303.3 + 5.15 + 28.0) * LB
AEROBEE_SUSTAINER_PROP_KG = (758.2 + 303.3) * LB
AEROBEE_BOOSTER_THRUST_LBF = [
    (0.00,     0.0),
    (0.05, 18600.0),
    (2.45, 18600.0),
    (2.50,     0.0),
]
AEROBEE_SUSTAINER_THRUST_LBF = [
    (0.00,    0.0),
    (0.30, 2500.0),
    (0.60, 4100.0),
    (50.50, 4100.0),
    (51.50,    0.0),
]


def build_aerobee_booster_rse():
    return build_rse_xml(
        designation="2.5KS-18000",
        manufacturer="Aerojet",
        diameter_mm=AEROBEE_BOOSTER_DIA_M * 1000,
        length_mm=AEROBEE_BOOSTER_LEN_M * 1000,
        delays="P",
        propellant_kg=AEROBEE_BOOSTER_PROP_KG,
        total_kg=AEROBEE_BOOSTER_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(AEROBEE_BOOSTER_THRUST_LBF),
        comment_lines=[
            "Aerobee 150A solid booster.",
            "NASA TR R-226 p.8/p.13: Aerojet 2.5KS-18,000, sea-level thrust 18,600 lb, 2.5 s.",
            "NASA TR R-226 p.13: booster loaded 520 lb, expended 260 lb.",
        ],
    )


def build_aerobee_sustainer_rse():
    return build_rse_xml(
        designation="Aerobee 150A liquid sustainer",
        manufacturer="Aerojet-General",
        diameter_mm=AEROBEE_DIA_M * 1000,
        length_mm=AEROBEE_SUSTAINER_LEN_M * 1000,
        delays="P",
        propellant_kg=AEROBEE_SUSTAINER_PROP_KG,
        total_kg=AEROBEE_SUSTAINER_TOTAL_KG,
        thrust_pairs_n=lbf_pairs_to_n(AEROBEE_SUSTAINER_THRUST_LBF),
        comment_lines=[
            "Aerobee 150A liquid sustainer represented as a tabular RSE motor.",
            "NASA TR R-226 p.12-13: sea-level thrust 4100 lb, powered duration 51.5 s.",
            "NASA TR R-226 p.13: oxidizer 758.2 lb, fuel 303.3 lb, helium 5.15 lb, empty rocket 279.1 lb, fins 28 lb.",
            "OpenRocket does not model pressure-fed ANFA/IRFNA plumbing; this is a mass/thrust approximation.",
        ],
    )


def build_aerobee_150a_xml():
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    payload_id = newid()
    sustainer_id = newid()
    sustainer_fins_id = newid()
    booster_id = newid()
    booster_fins_id = newid()

    payload_mass_lb = 177.5
    nose_len = 46.5 * IN       # 3.1-cal ogive at 15 in diameter
    payload_len = 41.3 * IN    # from Flight 4.65GI figure dimensions
    tank_aft_len = AEROBEE_SUSTAINER_LEN_M - nose_len - payload_len

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus screening build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Aerobee 150A NASA 4.65GI (liquid-as-RSE screening)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Aerobee 150A four-fin sounding rocket, NASA 4.65GI.
Source: NASA TR R-226 / NTRS 19660005621, A Compendium of Aerobee Sounding Rocket Launchings, 1959-1963.
Vehicle: approximately 30 ft long, 15 in diameter, four-fin tower-launched liquid-sustainer rocket with solid 2.5KS-18000 booster.
Nominal propulsion: ANFA/IRFNA sustainer, 4100 lbf for 51.5 s; booster 18,600 lbf for 2.5 s.
Flight 4.65GI: Wallops, 25 Sep 1963, payload 177.5 lb, apogee 139.5 statute miles, time to apogee 256 s, sustainer burnout T+54.6 s.
SCREENING MODEL: pressure-fed liquid sustainer and tower guide details are represented only through RSE thrust/mass tables.</comment>
    <designer>ORP v2 screening build (NASA TR R-226)</designer>
    <revision>2026-05-05 v2-screening</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>2.5KS-18000 + Aerobee 150A sustainer</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Aerobee 150A sustainer + payload</name>

        <subcomponents>
          <nosecone>
            <name>3.1-cal ogive nose</name>
            <id>{nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_len:.6f}</length>
            <thickness>0.001600</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{AEROBEE_DIA_M / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Payload section (Flight 4.65GI, 177.5 lb)</name>
            <id>{payload_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1700.0">Magnesium / payload</material>
            <length>{payload_len:.6f}</length>
            <thickness>0.001600</thickness>
            <radius>{AEROBEE_DIA_M / 2:.6f}</radius>
            <overridemass>{payload_mass_lb * LB:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>

          <bodytube>
            <name>Liquid sustainer tanks/aft structure</name>
            <id>{sustainer_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Stainless / magnesium</material>
            <length>{tank_aft_len:.6f}</length>
            <thickness>0.001600</thickness>
            <radius>{AEROBEE_DIA_M / 2:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "Aerobee 150A liquid sustainer", "Aerojet-General",
                     AEROBEE_DIA_M, AEROBEE_SUSTAINER_LEN_M,
                     AEROBEE_SUSTAINER_DIGEST,
                     ignition_event="automatic", ignition_delay=0.6)}

            <subcomponents>
              <trapezoidfinset>
                <name>Aerobee 150A sustainer fins (14.88 sq ft each source area)</name>
                <id>{sustainer_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="1700.0">Magnesium / steel LE cuff</material>
                <thickness>0.006350</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.004509</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="1700.0">Magnesium</filletmaterial>
                <rootchord>{48.0 * IN:.6f}</rootchord>
                <tipchord>{20.0 * IN:.6f}</tipchord>
                <sweeplength>{22.0 * IN:.6f}</sweeplength>
                <height>{63.0 * IN:.6f}</height>
                <overridemass>{28.0 * LB:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Aerobee solid booster</name>
        <separationevent>burnout</separationevent>
        <separationdelay>0.0</separationdelay>

        <subcomponents>
          <bodytube>
            <name>2.5KS-18000 booster motor</name>
            <id>{booster_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{AEROBEE_BOOSTER_LEN_M:.6f}</length>
            <thickness>0.004826</thickness>
            <radius>{AEROBEE_BOOSTER_DIA_M / 2:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "2.5KS-18000", "Aerojet",
                     AEROBEE_BOOSTER_DIA_M, AEROBEE_BOOSTER_LEN_M,
                     AEROBEE_BOOSTER_DIGEST,
                     ignition_event="automatic", ignition_delay=0.2)}

            <subcomponents>
              <trapezoidfinset>
                <name>Aerobee booster fins (2.5 deg preset cant)</name>
                <id>{booster_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="1700.0">Magnesium</material>
                <thickness>0.006350</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.043633</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="1700.0">Magnesium</filletmaterial>
                <rootchord>{36.0 * IN:.6f}</rootchord>
                <tipchord>{14.0 * IN:.6f}</tipchord>
                <sweeplength>{18.0 * IN:.6f}</sweeplength>
                <height>{36.0 * IN:.6f}</height>
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


# =========================================================================
# Super Loki Stable Booster motor (SDC P/N 600-13)
# Source: super_loki_instrumented_dart.yaml lines 224-265
# Total impulse 9989 lbf-s; action 2.09 s; max 5950 lbf at t=1.7 s.
# =========================================================================
SLOKI_THRUST_LBF = [
    (0.00,    0.0),
    (0.05, 4000.0),
    (0.10, 4400.0),
    (0.20, 4500.0),
    (0.40, 4400.0),
    (0.70, 4400.0),
    (1.00, 4500.0),
    (1.30, 4800.0),
    (1.50, 5300.0),
    (1.70, 5950.0),
    (1.85, 5800.0),
    (2.00, 4500.0),
    (2.09, 2500.0),
    (2.20,  500.0),
    (2.30,    0.0),
]
SLOKI_PROP_LB     = 43.48                  # yaml:231
SLOKI_TOTAL_LB    = 60.62                  # yaml:232
SLOKI_MOTOR_LEN_M = 2.24282                # yaml:230 (88.3 in)
SLOKI_MOTOR_DIA_M = 0.10160                # yaml:229 (4.000 in)


def build_sloki_motor_rse():
    return build_rse_xml(
        designation="SuperLoki-600-13",
        manufacturer="SpaceDataCorp",
        diameter_mm=SLOKI_MOTOR_DIA_M * 1000,
        length_mm=SLOKI_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=SLOKI_PROP_LB * LB,
        total_kg=SLOKI_TOTAL_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(SLOKI_THRUST_LBF),
        comment_lines=[
            "Stable Super Loki rocket motor (SDC P/N 600-13)",
            "Source: AFCRL-TR-73-0412 Table 3.3, Fig 3.4 p.13",
            "Total impulse 9944 lbf-s; action 2.09 s; max 5954 lbf",
            "Sea-level firing at +59 deg F",
        ],
    )


SLOKI_MOTOR_DIGEST = "superloki-600-13"


# =========================================================================
# Super Loki Instrumented Dart F4-1 -- super_loki_instrumented_dart.yaml
# Stage 0 = sustainer dart (passive); Stage 1 = booster.
# Helical-tower exit spin (~14 rps SYNTHESIZED) injected at LAUNCHROD.
# =========================================================================
def build_super_loki_dart_xml():
    cid = newid()
    rocket_id = newid()
    dart_nose_id   = newid()
    dart_body_id   = newid()
    dart_fins_id   = newid()
    boost_body_id  = newid()
    boost_fins_id  = newid()

    dart_dia_in       = 2.125             # yaml:61
    dart_mass_lb      = 19.00             # yaml:64
    dart_nose_len_in  = 13.0              # yaml:83 [INFERRED]
    dart_body_len_in  = 38.894            # yaml:103
    dart_body_thk_in  = 0.060             # yaml:105 MISSING
    dart_fin_root_in  = 4.2               # yaml:134
    dart_fin_tip_in   = 2.1               # yaml:135
    dart_fin_h_in     = 3.0               # yaml:136
    dart_fin_sweep_in = 1.05              # yaml:138
    dart_fin_thk_in   = 0.060             # yaml:140 MISSING

    dart_radius_m    = (dart_dia_in / 2) * IN
    dart_nose_len_m  = dart_nose_len_in * IN
    dart_body_len_m  = dart_body_len_in * IN
    dart_body_thk_m  = dart_body_thk_in * IN
    dart_mass_kg     = dart_mass_lb * LB
    df_root_m  = dart_fin_root_in  * IN
    df_tip_m   = dart_fin_tip_in   * IN
    df_h_m     = dart_fin_h_in     * IN
    df_sweep_m = dart_fin_sweep_in * IN
    df_thk_m   = dart_fin_thk_in   * IN

    boost_case_len_in = 88.3              # yaml:158
    boost_case_dia_in = 4.000             # yaml:159
    boost_case_thk_in = 0.083             # yaml:159
    boost_inert_override_lb = 19.93       # yaml:192

    boost_case_len_m = boost_case_len_in * IN
    boost_case_radius_m = (boost_case_dia_in / 2) * IN
    boost_case_thk_m = boost_case_thk_in * IN
    boost_inert_kg   = boost_inert_override_lb * LB

    bf_root_in  = 16.6                    # yaml:206
    bf_tip_in   = 14.8                    # yaml:205
    bf_h_in     = 2.0                     # yaml:204
    bf_sweep_in = 1.8                     # yaml:211 [INFERRED]
    bf_thk_in   = 0.060                   # yaml:214 MISSING

    bf_root_m  = bf_root_in  * IN
    bf_tip_m   = bf_tip_in   * IN
    bf_h_m     = bf_h_in     * IN
    bf_sweep_m = bf_sweep_in * IN
    bf_thk_m   = bf_thk_in   * IN

    sloki_motor_mount = make_motor_mount_xml(
            cid, "SuperLoki-600-13", "SpaceDataCorp",
            SLOKI_MOTOR_DIA_M, SLOKI_MOTOR_LEN_M, SLOKI_MOTOR_DIGEST)

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Super Loki Instrumented Dart (F4-1)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Super Loki Stable Booster + 2.125-in Instrumented Dart (F4-1).
Source: AFCRL-TR-73-0412 / DTIC AD-766737, Bollermann and Walker, 30 June 1973.
Performance target: ~232 kft dart apogee at T+120 s, 80 deg QE sea level (Table 6.1).
Stage 0 = passive instrumented dart; Stage 1 = Super Loki booster (sep at burnout).
NOTE: helical-tower exit spin (~14 rps SYNTHESIZED) injected at LAUNCHROD.</comment>
    <designer>ORP v2 corpus build (audited from super_loki_instrumented_dart.yaml)</designer>
    <revision>2026-05-05 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Super Loki + Instrumented Dart F4-1</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Instrumented Dart (sustainer)</name>
        <overridemass>{dart_mass_kg:.6f}</overridemass>
        <overridesubcomponentsmass>true</overridesubcomponentsmass>

        <subcomponents>
          <nosecone>
            <name>Dart nose cone (steel ogive)</name>
            <id>{dart_nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{dart_nose_len_m:.6f}</length>
            <thickness>filled</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{dart_radius_m:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Dart body (steel tube w/ payload staves)</name>
            <id>{dart_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{dart_body_len_m:.6f}</length>
            <thickness>{dart_body_thk_m:.6f}</thickness>
            <radius>{dart_radius_m:.6f}</radius>

            <subcomponents>
              <trapezoidfinset>
                <name>Dart fins (4x steel, 9.45 in^2 each)</name>
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
                <thickness>{df_thk_m:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="7850.0">Steel</filletmaterial>
                <rootchord>{df_root_m:.6f}</rootchord>
                <tipchord>{df_tip_m:.6f}</tipchord>
                <sweeplength>{df_sweep_m:.6f}</sweeplength>
                <height>{df_h_m:.6f}</height>
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
            <id>{boost_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2810.0">Aluminum 2014-T6</material>
            <length>{boost_case_len_m:.6f}</length>
            <thickness>{boost_case_thk_m:.6f}</thickness>
            <radius>{boost_case_radius_m:.6f}</radius>
            <overridemass>{boost_inert_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{sloki_motor_mount}

            <subcomponents>
              <trapezoidfinset>
                <name>Booster fins (4x Al + Thermolag, 31.40 in^2 each)</name>
                <id>{boost_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Aluminum 2014-T6 + Thermolag cuff</material>
                <thickness>{bf_thk_m:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Aluminum</filletmaterial>
                <rootchord>{bf_root_m:.6f}</rootchord>
                <tipchord>{bf_tip_m:.6f}</tipchord>
                <sweeplength>{bf_sweep_m:.6f}</sweeplength>
                <height>{bf_h_m:.6f}</height>
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


# =========================================================================
# Viper 3A booster motor (SDC scaled-up 4.5-in Super Loki)
# Source: viper_3a_robin_dart.yaml lines 230-281 (Apx Tables 2 & 3,
# AFCRL-TR-73-0412 p.A-7, sea level, 0.1 s spacing -- HIGH quality data).
# =========================================================================
VIPER3A_MOTOR_THRUST_LBF = [
    (0.0000,    0.000),
    (0.1000, 4424.900),
    (0.2000, 4566.790),
    (0.3000, 4721.795),
    (0.4000, 4930.645),
    (0.5000, 5143.495),
    (0.6000, 5368.687),
    (0.7000, 5602.058),
    (0.8000, 5897.233),
    (0.9000, 6192.407),
    (1.0000, 6473.346),
    (1.1000, 6702.191),
    (1.2000, 6866.176),
    (1.3000, 6986.668),
    (1.4000, 7078.452),
    (1.5000, 7188.784),
    (1.6000, 7311.772),
    (1.7000, 7354.120),
    (1.8000, 7376.685),
    (1.9000, 7407.177),
    (2.0000, 6346.914),
    (2.1000, 4031.340),
    (2.2000, 1062.025),
]
VIPER3A_MOTOR_PROP_LB = 56.98          # yaml:237
VIPER3A_MOTOR_TOTAL_LB = 72.73         # yaml:238
VIPER3A_MOTOR_DIA_M = 0.11430          # yaml:236 (4.50 in)
VIPER3A_MOTOR_LEN_M = 2.4384           # yaml:236 (96.0 in)


def build_viper3a_motor_rse():
    return build_rse_xml(
        designation="Viper-3A",
        manufacturer="SpaceDataCorp",
        diameter_mm=VIPER3A_MOTOR_DIA_M * 1000,
        length_mm=VIPER3A_MOTOR_LEN_M * 1000,
        delays="P",
        propellant_kg=VIPER3A_MOTOR_PROP_LB * LB,
        total_kg=VIPER3A_MOTOR_TOTAL_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(VIPER3A_MOTOR_THRUST_LBF),
        comment_lines=[
            "Viper 3A booster motor (SDC scaled-up 4.5-in Super Loki)",
            "Source: AFCRL-TR-73-0412 Apx Table 3 p.A-7",
            "Avg 5908 lbf, max 7410 lbf, total impulse 13058 lbf-s",
            "Action 2.21 s, total 2.29 s, Isp 229.2 s, sea level",
            "23-point digital tabulation -- no synthesis required",
        ],
    )


VIPER3A_MOTOR_DIGEST = "viper3a-sdc"


# =========================================================================
# Viper 3A Robin Dart (E4.5-1) -- viper_3a_robin_dart.yaml
# Stage 0 = Robin Dart (passive); Stage 1 = Viper 3A booster.
# Three flights (1-4, 1-5, 1-6) of same airframe; one ORK only.
# Helical-rail exit spin 6.80 rps (yaml:294) injected at LAUNCHROD.
# =========================================================================
def build_viper_3a_xml():
    cid = newid()
    rocket_id = newid()
    dart_nose_id = newid()
    dart_body_id = newid()
    dart_fins_id = newid()
    booster_body_id = newid()
    booster_fins_id = newid()

    dart_total_len_in = 48.2             # yaml:77
    dart_dia_in = 1.63                   # yaml:78
    dart_nose_len_in = 10.0              # yaml:103 [INFERRED]
    dart_body_len_in = dart_total_len_in - dart_nose_len_in
    dart_mass_lb = 13.50                 # yaml:81
    dart_fin_root_in = 4.0               # yaml:143 [INFERRED]
    dart_fin_tip_in = 2.62               # yaml:144 [INFERRED]
    dart_fin_height_in = 1.495           # yaml:142
    dart_fin_sweep_in = 0.69             # yaml:145 [INFERRED]
    dart_fin_thickness_in = 0.060        # yaml:147

    booster_case_len_in = 96.0           # yaml:163
    booster_case_dia_in = 4.50           # yaml:164
    booster_case_thk_in = 0.083          # yaml:193
    booster_body_override_lb = 23.72 - 15.75   # = 7.97 lb non-motor inert
    booster_fin_root_in = 19.81          # yaml:219 [INFERRED]
    booster_fin_tip_in = 17.69           # yaml:220 [INFERRED]
    booster_fin_height_in = 2.50         # yaml:212
    booster_fin_sweep_in = 1.06          # yaml:221 [INFERRED]
    booster_fin_thickness_in = 0.060     # yaml:223 [SYNTHESIZED]

    dart_dia_m = dart_dia_in * IN
    dart_nose_len_m = dart_nose_len_in * IN
    dart_body_len_m = dart_body_len_in * IN
    dart_mass_kg = dart_mass_lb * LB
    dart_fin_root_m = dart_fin_root_in * IN
    dart_fin_tip_m = dart_fin_tip_in * IN
    dart_fin_height_m = dart_fin_height_in * IN
    dart_fin_sweep_m = dart_fin_sweep_in * IN
    dart_fin_thickness_m = dart_fin_thickness_in * IN

    booster_case_len_m = booster_case_len_in * IN
    booster_case_dia_m = booster_case_dia_in * IN
    booster_case_thk_m = booster_case_thk_in * IN
    booster_body_override_kg = booster_body_override_lb * LB
    booster_fin_root_m = booster_fin_root_in * IN
    booster_fin_tip_m = booster_fin_tip_in * IN
    booster_fin_height_m = booster_fin_height_in * IN
    booster_fin_sweep_m = booster_fin_sweep_in * IN
    booster_fin_thickness_m = booster_fin_thickness_in * IN

    viper_motor_mount = make_motor_mount_xml(
            cid, "Viper-3A", "SpaceDataCorp",
            VIPER3A_MOTOR_DIA_M, VIPER3A_MOTOR_LEN_M, VIPER3A_MOTOR_DIGEST)

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Viper 3A Robin Dart (E4.5-1)</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Viper 3A Stable Booster + 1.625-in Robin Dart (2-stage).
Source: AFCRL-TR-73-0412 / DTIC AD-766737, Section 8.6, Apx Tables 1-3, Table 8.4 p.81.
Vehicle E4.5-1: three flights 1-4, 1-5, 1-6 at 80 deg QE.
Performance: dart apogees 380 / 397 / 394 kft.
Booster: 4.5-in scaled Super Loki, 56.98 lb prop, 13058 lbf-s, 2.21 s action.
Dart: 1.625-in Robin dart, 13.50 lb, 4 steel fins.
Launcher: 14-ft helical/rifled rail, exit 6.80 rps spin (injected at LAUNCHROD).</comment>
    <designer>ORP v2 corpus build (audited from viper_3a_robin_dart.yaml)</designer>
    <revision>2026-05-05 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Viper-3A + Robin Dart</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Robin Dart (sustainer)</name>
        <overridemass>{dart_mass_kg:.6f}</overridemass>
        <overridesubcomponentsmass>true</overridesubcomponentsmass>

        <subcomponents>
          <nosecone>
            <name>Dart nose cone (steel ogive with lead ballast)</name>
            <id>{dart_nose_id}</id>
            <finish>polished</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{dart_nose_len_m:.6f}</length>
            <thickness>filled</thickness>
            <shape>ogive</shape>
            <shapeparameter>1.0</shapeparameter>
            <aftradius>{dart_dia_m / 2:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
          </nosecone>

          <bodytube>
            <name>Dart body (steel tube, Thermolag ablative)</name>
            <id>{dart_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{dart_body_len_m:.6f}</length>
            <thickness>0.001524</thickness>
            <radius>{dart_dia_m / 2:.6f}</radius>

            <subcomponents>
              <trapezoidfinset>
                <name>Dart fins (4x steel, 4.95 in^2 each)</name>
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
                <thickness>{dart_fin_thickness_m:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="7850.0">Steel</filletmaterial>
                <rootchord>{dart_fin_root_m:.6f}</rootchord>
                <tipchord>{dart_fin_tip_m:.6f}</tipchord>
                <sweeplength>{dart_fin_sweep_m:.6f}</sweeplength>
                <height>{dart_fin_height_m:.6f}</height>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Viper 3A Booster (Stable)</name>
        <separationevent>burnout</separationevent>
        <separationdelay>0.0</separationdelay>

        <subcomponents>
          <bodytube>
            <name>Viper 3A motor case (4.5-in dia, 96-in, Al 2014-T6)</name>
            <id>{booster_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2810.0">Aluminum 2014-T6</material>
            <length>{booster_case_len_m:.6f}</length>
            <thickness>{booster_case_thk_m:.6f}</thickness>
            <radius>{booster_case_dia_m / 2:.6f}</radius>
            <overridemass>{booster_body_override_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
{viper_motor_mount}

            <subcomponents>
              <trapezoidfinset>
                <name>Viper 3A booster fins (4x, 46.87 in^2 each, Thermolag T-230)</name>
                <id>{booster_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Aluminum + Thermolag T-230 cuff</material>
                <thickness>{booster_fin_thickness_m:.6f}</thickness>
                <crosssection>square</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Aluminum</filletmaterial>
                <rootchord>{booster_fin_root_m:.6f}</rootchord>
                <tipchord>{booster_fin_tip_m:.6f}</tipchord>
                <sweeplength>{booster_fin_sweep_m:.6f}</sweeplength>
                <height>{booster_fin_height_m:.6f}</height>
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


# =========================================================================
# Nike-Deacon (DAN) -- nike_deacon.yaml
# Two-stage Nike booster + ABL Deacon sustainer, NACA TN 3739 (Heitkotter
# 1956). Two flights (no.1 and no.2) at 75-deg elevation from Wallops Is.,
# differing only in the nose-cone-and-instrumentation payload mass
# (34 lb vs 39 lb) and Deacon delay-squib timing (17.0 s vs 12.8 s).
# =========================================================================

# Nike booster motor: TN 3739 publishes no thrust curve. We adopt the
# NASA TM X-55700 (Nike-Apache Performance Handbook) Appendix A p.64
# rectangular profile, which matches TN 3739 Fig 10 (~3300 fps boost
# peak in 3.5 s) and Fig 14 (~37-38 g sustained boost) -- see
# nike_deacon.yaml stages[0].motor.thrust_curve_source.
NIKE_DEACON_NIKE_THRUST_LBF = [
    (0.0, 42500.0),
    (3.5, 42500.0),
    (3.5, 0.0),
]
NIKE_DEACON_NIKE_PROP_LB = 755.0           # adopted from M5-E1 family (Handbook p.6)
NIKE_DEACON_NIKE_LOADED_LB = 1170.0        # TN 3739 p.3 ("Loaded booster, lb ... 1170")
NIKE_DEACON_NIKE_LEN_IN = 150.5            # TN 3739 Fig 2 ("150.5" Nike body)
NIKE_DEACON_NIKE_DIA_IN = 16.5             # TN 3739 Fig 2 ("16.5 Diam")

# ABL Deacon sustainer: TN 3739 silent on F(t); curve synthesized from
# Fig 14 a_x and Fig 10 V(t). See nike_deacon.yaml stages[1].motor for
# the 8-point ramp + flat + tail shape. Burn ~3 s, peak ~6500 lbf.
NIKE_DEACON_DEACON_THRUST_LBF = [
    (0.00,    0.0),
    (0.05, 4500.0),
    (0.30, 5800.0),
    (0.80, 6300.0),
    (1.50, 6500.0),
    (2.50, 6500.0),
    (2.80, 1500.0),
    (3.00,    0.0),
]
# Deacon mass breakdown not in TN 3739. Loaded Deacon = 151.5 lb (p.3);
# we adopt a Deacon-class ~60% propellant fraction giving 91 lb prop /
# 60 lb empty. Refine if ABL Deacon datasheet becomes available.
NIKE_DEACON_DEACON_PROP_LB = 91.0          # synthesized; TN 3739 silent
NIKE_DEACON_DEACON_LOADED_LB = 151.5       # TN 3739 p.3
NIKE_DEACON_DEACON_LEN_IN = 88.6           # TN 3739 Fig 2: Sta 45.9 -> 134.5 = 88.6 in steel section + motor
NIKE_DEACON_DEACON_DIA_IN = 6.5            # TN 3739 Fig 2 ("6.5 Diam" forward body OD == motor OD)

NIKE_DEACON_NIKE_DIGEST = "nike-m5-tn3739"
NIKE_DEACON_DEACON_DIGEST = "abl-deacon-tn3739"


def build_nike_deacon_nike_motor_rse():
    return build_rse_xml(
        designation="Nike M5",
        manufacturer="Hercules",
        diameter_mm=NIKE_DEACON_NIKE_DIA_IN * IN * 1000,
        length_mm=NIKE_DEACON_NIKE_LEN_IN * IN * 1000,
        delays="P",
        propellant_kg=NIKE_DEACON_NIKE_PROP_LB * LB,
        total_kg=NIKE_DEACON_NIKE_LOADED_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(NIKE_DEACON_NIKE_THRUST_LBF),
        comment_lines=[
            "Nike booster motor for Nike-Deacon (DAN), NACA TN 3739.",
            "TN 3739 silent on Nike thrust curve; flat 42500 lbf x 3.5 s",
            "adopted from NASA TM X-55700 Apx A p.64 (matches TN 3739",
            "Fig 10 ~3300 fps in 3.5 s and Fig 14 ~37-38 g boost).",
            "Loaded booster 1170 lb (TN 3739 p.3).",
            "Propellant 755 lb adopted from M5-E1 family (Handbook p.6).",
        ],
        cg_fraction=0.5,
    )


def build_nike_deacon_deacon_motor_rse():
    return build_rse_xml(
        designation="ABL Deacon",
        manufacturer="ABL",
        diameter_mm=NIKE_DEACON_DEACON_DIA_IN * IN * 1000,
        length_mm=NIKE_DEACON_DEACON_LEN_IN * IN * 1000,
        delays="P",
        propellant_kg=NIKE_DEACON_DEACON_PROP_LB * LB,
        total_kg=NIKE_DEACON_DEACON_LOADED_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(NIKE_DEACON_DEACON_THRUST_LBF),
        comment_lines=[
            "ABL Deacon sustainer for Nike-Deacon (DAN), NACA TN 3739.",
            "TN 3739 publishes a_x(t) and V(t) but NOT F(t) directly.",
            "Thrust curve synthesized from Fig 14 (peak ~52 g at",
            "~155 lb burnout mass -> peak F ~6500 lbf) and Fig 10 ",
            "(delta-V ~3650 fps over 3 s).",
            "Loaded motor 151.5 lb (TN 3739 p.3); propellant 91 lb",
            "synthesized (60% fraction; TN 3739 silent). Refine when",
            "ABL Deacon datasheet is independently obtained.",
        ],
        cg_fraction=0.5,
    )


def build_nike_deacon_xml(payload_lb, flight_label, deacon_ignition_delay_s):
    """Build Nike-Deacon ORK XML for a single flight variant.

    payload_lb               nose-cone-and-instrumentation mass (TN 3739 p.3:
                             34.0 for no.1, 39.0 for no.2)
    flight_label             "no.1" or "no.2"
    deacon_ignition_delay_s  TN 3739 p.5: 17.0 s (no.1) or 12.8 s (no.2)
                             measured from launch -- the 15.5/13.5-s delay
                             squibs were ignited at launch, not at booster
                             burnout, so this is wall-clock-from-launch.
    """
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    fwd_body_id = newid()
    step_transition_id = newid()
    aft_fwd_body_id = newid()
    deacon_taper_id = newid()
    deacon_body_id = newid()
    deacon_fins_id = newid()
    deacon_shroud_aft_id = newid()
    nike_body_id = newid()
    nike_boattail_id = newid()
    nike_fins_id = newid()

    # ---- Stage 2: Deacon -------------------------------------------------
    # TN 3739 Fig 2 station map (in inches from nose tip):
    #   0.0   nose tip
    #  38.6   end of conical instrument shell (7.8 in OD base)
    #  40.5   step (6.5 in OD)
    #  45.9   end of forward instrument body (6.25 OD step)
    #  93.25  CG of Deacon stage no.1 (95.0 for no.2)
    # 134.5   forward end of Deacon fin shroud (7.1 in OD)
    # 144.5   aft end of Deacon shroud  -> Deacon stage aft
    # 155.5   forward end of Nike body
    # 227.5   Nike booster aft (motor + fin TE)
    nose_max_dia_in = 7.8                # TN 3739 Fig 2 ("7.8 Diam")
    nose_len_in = 38.6                   # TN 3739 Fig 2 (Sta 0 -> 38.6)
    fwd_body_dia_in = 6.5                # TN 3739 Fig 2 ("6.5 Diam") between Sta 40.5-45.9
    fwd_body_len_in = 40.5 - 38.6        # = 1.9 in conical step from 7.8 -> 6.5 (cone-to-body fairing)
    aft_fwd_body_dia_in = 6.25           # TN 3739 Fig 2 ("6.25 Diam")
    aft_fwd_body_len_in = 45.9 - 40.5    # = 5.4 in cylindrical fwd instrument body
    deacon_taper_len_in = 134.5 - 45.9 - 19.9  # = 68.7 in tapered steel section + motor between Sta 45.9 and start of fin shroud
    deacon_body_dia_in = 7.1             # TN 3739 Fig 2 ("7.1 Diam") fin can / shroud OD
    deacon_shroud_axial_in = 19.9        # TN 3739 Fig 2 ("19.9" callout) -- fin shroud
    deacon_total_len_in = 144.5          # Sta 144.5 = aft end of Deacon stage

    # ---- Stage 1: Nike ---------------------------------------------------
    # Nike body Sta 155.5 -> 227.5 = 72 in case length within full Fig 2
    # callout, but 150.5-in callout suggests Nike body proper is longer
    # (overlapping with shroud). For ORK staging, model the Nike booster
    # at its motor-case length (NIKE_DEACON_NIKE_LEN_IN = 150.5 in). The
    # 11-in interstage adapter (Sta 144.5-155.5) is the booster adapter.
    adapter_len_in = 155.5 - 144.5       # = 11.0 in Sta 144.5->155.5, matches "27" Fig-2 detail (45-lb booster adapter)
    nike_radius_in = NIKE_DEACON_NIKE_DIA_IN / 2.0
    nike_aft_radius_in = 17.5 / 2.0      # TN 3739 Fig 2 ("17.5 Diam" aft skirt / fin shroud)
    nike_body_len_in = NIKE_DEACON_NIKE_LEN_IN

    # Nike fins: TN 3739 p.3 (109 lb total, sandwich duralumin/wood-core
    # construction); Fig 2 planform callouts:
    nike_fin_root_in = 24.5              # TN 3739 Fig 2 ("24.5")
    nike_fin_tip_in = 9.5                # TN 3739 Fig 2 ("9.5")
    # Tip-to-tip 62.5 in (TN 3739 Fig 2) = 17.5 base + 2 * span -> span = 22.5
    nike_fin_height_in = (62.5 - 17.5) / 2.0      # = 22.5 in
    nike_fin_sweep_in = nike_fin_root_in - nike_fin_tip_in   # = 15.0 in (square TE)
    nike_fin_thickness_in = 1.0 / 16.0   # TN 3739 p.3 "1/16-inch-thick duralumin sheets"
    nike_fin_mass_lb = 109.0             # TN 3739 p.3

    # Deacon fins: TN 3739 Fig 2 gives ONLY shroud OD (7.1 in) and shroud
    # axial extent (19.9 in). No fin chord/span/sweep dimensions are
    # printed. nike_deacon.yaml flags these as INFERRED with low
    # confidence. Pattern: shroud-anchored swept trapezoid.
    deacon_fin_root_in = 19.9            # = shroud axial extent (TN 3739 Fig 2)
    deacon_fin_tip_in = 7.0              # INFERRED (~35% of root, typical Deacon)
    deacon_fin_height_in = 9.0           # INFERRED (slightly larger than 7.1 OD)
    deacon_fin_sweep_in = (deacon_fin_root_in - deacon_fin_tip_in)  # = 12.9 in
    deacon_fin_thickness_in = 0.10       # SYNTHESIZED (TN 3739 silent; ~1.4% chord)
    deacon_fin_mass_lb = 25.5            # TN 3739 p.3 "Deacon fins, shroud, and fairing ring"

    # Convert to SI
    nose_len_m = nose_len_in * IN
    fwd_body_len_m = fwd_body_len_in * IN
    aft_fwd_body_len_m = aft_fwd_body_len_in * IN
    deacon_taper_len_m = deacon_taper_len_in * IN
    deacon_shroud_len_m = deacon_shroud_axial_in * IN
    nose_radius_m = (nose_max_dia_in * IN) / 2.0
    fwd_body_radius_m = (fwd_body_dia_in * IN) / 2.0
    aft_fwd_body_radius_m = (aft_fwd_body_dia_in * IN) / 2.0
    deacon_radius_m = (deacon_body_dia_in * IN) / 2.0
    adapter_len_m = adapter_len_in * IN
    nike_radius_m = nike_radius_in * IN
    nike_aft_radius_m = nike_aft_radius_in * IN
    nike_body_len_m = nike_body_len_in * IN

    payload_kg = payload_lb * LB
    nike_fin_mass_kg = nike_fin_mass_lb * LB
    deacon_fin_mass_kg = deacon_fin_mass_lb * LB
    nike_adapter_kg = 45.0 * LB           # TN 3739 p.3 "Booster adapter, lb ... 45"
    nozzle_extension_kg = 5.0 * LB        # TN 3739 p.3 "Nozzle extension, lb ... 5.0"

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Nike-Deacon (DAN) {flight_label} -- NACA TN 3739</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Two-stage Nike + ABL Deacon meteorological sounding rocket "DAN".
Source: NACA TN 3739 (Heitkotter 1956), "Flight Investigation of the Performance of a Two-Stage Solid-Propellant Nike-Deacon (DAN) Meteorological Sounding Rocket".
Geometry: TN 3739 Figure 2 (p.9) station callouts.
Masses: TN 3739 p.3 component table.
Thrust profiles: Nike adopted from NASA TM X-55700 Apx A (consistent with TN 3739 Fig 10/14); Deacon synthesized from TN 3739 Fig 14 a_x(t) + Fig 10 V(t) -- TN 3739 itself does not publish F(t).
Flight: launched at 75-deg elevation from Wallops Island, sea level (TN 3739 SUMMARY p.1).
Apogees: no.1 = 356,000 ft (108.5 km) at t=161 s; no.2 = 350,000 ft (106.7 km) at t=156 s (TN 3739 p.5-6).
Deacon ignition: 15.5/13.5-s delay squibs ignited at launch (NOT staging-keyed); actual ignition at t = 17.0 s (no.1) / 12.8 s (no.2) per TN 3739 p.5.</comment>
    <designer>ORP v2 corpus build (audited from nike_deacon.yaml)</designer>
    <revision>2026-05-06 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Nike booster + ABL Deacon ({flight_label})</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Deacon sustainer + payload</name>

        <subcomponents>
          <nosecone>
            <name>11-deg apex cast-magnesium nose cone (7.8 in base, 38.6 in)</name>
            <id>{nose_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1740.0">Cast magnesium</material>
            <length>{nose_len_m:.6f}</length>
            <thickness>0.004763</thickness>
            <shape>conical</shape>
            <aftradius>{nose_radius_m:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>{payload_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </nosecone>

          <transition>
            <name>7.8 to 6.5 in step (Sta 38.6 to 40.5)</name>
            <id>{step_transition_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="1740.0">Cast magnesium</material>
            <length>{fwd_body_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{nose_radius_m:.6f}</foreradius>
            <aftradius>{fwd_body_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
          </transition>

          <bodytube>
            <name>Forward instrument body 6.5 in (Sta 40.5 to 45.9)</name>
            <id>{aft_fwd_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{aft_fwd_body_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{fwd_body_radius_m:.6f}</radius>
          </bodytube>

          <transition>
            <name>Tapered steel section 6.5 to 7.1 in (Sta 45.9 to 114.6)</name>
            <id>{deacon_taper_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{deacon_taper_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{fwd_body_radius_m:.6f}</foreradius>
            <aftradius>{deacon_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
          </transition>

          <bodytube>
            <name>Deacon fin shroud 7.1 in (Sta 114.6 to 144.5; motor mount)</name>
            <id>{deacon_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2810.0">Duralumin</material>
            <length>{deacon_shroud_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{deacon_radius_m:.6f}</radius>
            <overridemass>{nozzle_extension_kg:.6f}</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "ABL Deacon", "ABL",
                     NIKE_DEACON_DEACON_DIA_IN * IN,
                     NIKE_DEACON_DEACON_LEN_IN * IN,
                     NIKE_DEACON_DEACON_DIGEST,
                     ignition_event="launch",
                     ignition_delay=deacon_ignition_delay_s)}

            <subcomponents>
              <trapezoidfinset>
                <name>Deacon cruciform fins (25.5 lb assembly, dims inferred)</name>
                <id>{deacon_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Duralumin</material>
                <thickness>{deacon_fin_thickness_in * IN:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Duralumin</filletmaterial>
                <rootchord>{deacon_fin_root_in * IN:.6f}</rootchord>
                <tipchord>{deacon_fin_tip_in * IN:.6f}</tipchord>
                <sweeplength>{deacon_fin_sweep_in * IN:.6f}</sweeplength>
                <height>{deacon_fin_height_in * IN:.6f}</height>
                <overridemass>{deacon_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Nike booster</name>
        <separationevent>burnout</separationevent>
        <separationaltitude>200.0</separationaltitude>
        <separationdelay>0.0</separationdelay>
        <separationconfiguration configid="{cid}">
          <separationevent>burnout</separationevent>
          <separationaltitude>200.0</separationaltitude>
          <separationdelay>0.0</separationdelay>
        </separationconfiguration>

        <subcomponents>
          <transition>
            <name>Booster adapter 7.1 to 16.5 in (Sta 144.5 to 155.5; 45 lb)</name>
            <id>{deacon_shroud_aft_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{adapter_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{deacon_radius_m:.6f}</foreradius>
            <aftradius>{nike_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>{nike_adapter_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>

          <bodytube>
            <name>Nike M5 motor case 16.5 in</name>
            <id>{nike_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{nike_body_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{nike_radius_m:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "Nike M5", "Hercules",
                     NIKE_DEACON_NIKE_DIA_IN * IN,
                     NIKE_DEACON_NIKE_LEN_IN * IN,
                     NIKE_DEACON_NIKE_DIGEST,
                     ignition_event="automatic", ignition_delay=0.0)}

            <subcomponents>
              <trapezoidfinset>
                <name>Nike cruciform fins (109 lb sandwich duralumin/wood)</name>
                <id>{nike_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Sandwich (1/16-in duralumin face + wood core)</material>
                <thickness>{nike_fin_thickness_in * IN:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Duralumin</filletmaterial>
                <rootchord>{nike_fin_root_in * IN:.6f}</rootchord>
                <tipchord>{nike_fin_tip_in * IN:.6f}</tipchord>
                <sweeplength>{nike_fin_sweep_in * IN:.6f}</sweeplength>
                <height>{nike_fin_height_in * IN:.6f}</height>
                <overridemass>{nike_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>

          <transition>
            <name>Nike aft skirt 16.5 to 17.5 in</name>
            <id>{nike_boattail_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>0.050800</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{nike_radius_m:.6f}</foreradius>
            <aftradius>{nike_aft_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>
        </subcomponents>
      </stage>
    </subcomponents>
  </rocket>

  <simulations>
  </simulations>
</openrocket>
"""


def build_nike_deacon_flight1_xml():
    # TN 3739 p.3: nose-cone-and-instrumentation = 34.0 lb (no.1)
    # TN 3739 p.5: 15.5-s delay squib actually fired at ~17 s after launch
    return build_nike_deacon_xml(34.0, "no.1 (356,000 ft, 108.5 km)", 17.0)


def build_nike_deacon_flight2_xml():
    # TN 3739 p.3: nose-cone-and-instrumentation = 39.0 lb (no.2)
    # TN 3739 p.5: 13.5-s delay squib actually fired at ~12.8 s after launch
    return build_nike_deacon_xml(39.0, "no.2 (350,000 ft, 106.7 km)", 12.8)


# =========================================================================
# Nike-Cajun (CAN) -- nike_cajun.yaml
# Two-stage Nike M5/M5-E1 booster + Cajun TE-82 sustainer, NACA RM L57D26
# (Royall & Garland 1957). Two flights at 75-deg elevation from Wallops Is.,
# differing in nose-cone-and-instrumentation payload mass, nose section
# geometry, and Cajun ignition timing:
#   UM ("sounding rocket"):  51.85 lb,  308.5 in,  46.9-in 6.75-in nose,
#                            Cajun ignition 12.2 s post-launch (8.9 s coast)
#                            -> apogee 426,000 ft (130 km)
#   Hurricane variant:       75.77 lb,  324.4 in,  62.8-in 9.0-in nose,
#                            Cajun ignition 13.2 s post-launch (9.9 s coast)
#                            -> apogee ~412,000 ft (~126 km, Fig 7)
# Reference page numbers below are L57D26 unless prefixed otherwise.
# =========================================================================

# Nike booster motor: same shared hardware as Nike-Apache and Nike-Deacon.
# L57D26 publishes no thrust curve; Fig 11 acceleration ramp at 1273.5 lb
# gross weight gives ~33 g, matching the 42,500-lbf rectangular profile from
# NASA TM X-55700 Apx A p.64. Loaded booster 1170 lb, complete-with-fins
# 1273.5 lb (L57D26 p.5). Propellant 755 lb adopted from M5-E1 family
# (NikeApacheHandbook p.6) -- L57D26 silent.
NIKE_CAJUN_NIKE_THRUST_LBF = [
    (0.0, 42500.0),
    (3.5, 42500.0),
    (3.5, 0.0),
]
NIKE_CAJUN_NIKE_PROP_LB = 755.0            # NikeApacheHandbook p.6 (L57D26 silent)
NIKE_CAJUN_NIKE_LOADED_LB = 1170.0         # L57D26 p.5 "Loaded booster"
NIKE_CAJUN_NIKE_LEN_IN = 149.75            # NikeApacheHandbook Fig 5; L57D26 Fig 6 ~154 in incl adapter
NIKE_CAJUN_NIKE_DIA_IN = 16.5              # L57D26 Fig 6(b)

# Cajun TE-82 sustainer: thrust curve copied from nike_cajun.yaml
# stages[1].motor.thrust_curve (27 points; derived from Mayo66 Table 3
# propellant-mass-vs-time x Isp_eff = 175 s). Total impulse 20,825 lbf-s,
# action time 4.0 s, peak 7,875 lbf.
NIKE_CAJUN_CAJUN_THRUST_LBF = [
    (0.000,    0.0),
    (0.050, 1050.0),
    (0.080, 4083.0),
    (0.100, 6125.0),
    (0.150, 5950.0),
    (0.250, 5950.0),
    (0.350, 5950.0),
    (0.500, 5833.0),
    (0.650, 5833.0),
    (0.800, 5950.0),
    (1.200, 6388.0),
    (1.400, 6738.0),
    (1.600, 6913.0),
    (2.100, 6965.0),
    (2.350, 7140.0),
    (2.550, 7263.0),
    (2.800, 7560.0),
    (2.940, 7875.0),
    (2.960, 7875.0),
    (3.000, 7000.0),
    (3.150, 4783.0),
    (3.200, 2100.0),
    (3.250, 1050.0),
    (3.350,  350.0),
    (3.400,  700.0),
    (3.500,   88.0),
    (4.000,   18.0),
]
NIKE_CAJUN_CAJUN_PROP_LB = 119.0           # Mayo66 Table 3 (W_MLF - W_MEF = 202 - 83)
NIKE_CAJUN_CAJUN_LOADED_LB = 166.90        # L57D26 p.5 "Loaded Cajun"
NIKE_CAJUN_CAJUN_LEN_IN = 105.0            # YAML stages[1].geometry.cajun_motor_length_in_approx
NIKE_CAJUN_CAJUN_DIA_IN = 6.50             # L57D26 Fig 6, Fig 13

# Separate digests from the Nike-Apache/Nike-Deacon Nike RSE so the cached
# motor entries don't collide between rockets even though the underlying
# hardware is shared.
NIKE_CAJUN_NIKE_DIGEST = "nike-m5-l57d26"
NIKE_CAJUN_CAJUN_DIGEST = "cajun-te82-l57d26"


def build_nike_cajun_nike_motor_rse():
    return build_rse_xml(
        designation="Nike M5",
        manufacturer="Hercules",
        diameter_mm=NIKE_CAJUN_NIKE_DIA_IN * IN * 1000,
        length_mm=NIKE_CAJUN_NIKE_LEN_IN * IN * 1000,
        delays="P",
        propellant_kg=NIKE_CAJUN_NIKE_PROP_LB * LB,
        total_kg=NIKE_CAJUN_NIKE_LOADED_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(NIKE_CAJUN_NIKE_THRUST_LBF),
        comment_lines=[
            "Nike booster motor for Nike-Cajun (CAN), NACA RM L57D26.",
            "L57D26 publishes no Nike F(t); flat 42,500 lbf x 3.5 s adopted",
            "from NASA TM X-55700 Apx A p.64. Consistent with L57D26 Fig 11",
            "acceleration (~33 g x 1273.5 lb = 42,024 lbf, matches within 1%).",
            "Loaded motor 1170 lb (L57D26 p.5); propellant 755 lb adopted",
            "from M5-E1 family (NikeApacheHandbook p.6, L57D26 silent).",
            "Same shared hardware as Nike-Apache / Nike-Deacon.",
        ],
        cg_fraction=67.5 / 149.75,
    )


def build_nike_cajun_cajun_motor_rse():
    return build_rse_xml(
        designation="Cajun TE-82",
        manufacturer="AtlanticResearch",
        diameter_mm=NIKE_CAJUN_CAJUN_DIA_IN * IN * 1000,
        length_mm=NIKE_CAJUN_CAJUN_LEN_IN * IN * 1000,
        delays="P",
        propellant_kg=NIKE_CAJUN_CAJUN_PROP_LB * LB,
        total_kg=NIKE_CAJUN_CAJUN_LOADED_LB * LB,
        thrust_pairs_n=lbf_pairs_to_n(NIKE_CAJUN_CAJUN_THRUST_LBF),
        comment_lines=[
            "Cajun TE-82 sustainer for Nike-Cajun (CAN), NACA RM L57D26.",
            "L57D26 silent on F(t); curve derived from NASA TM X-55440",
            "(Mayo66) Table 3 propellant-mass-vs-time x Isp_eff = 175 s.",
            "27 points, total impulse 20,825 lbf-s = 92.6 kN-s, action",
            "time 4.0 s, peak 7,875 lbf at t=2.94 s. Profile is mildly",
            "progressive (5,950 -> 7,875 lbf) with sharp tail-off.",
            "Loaded motor 166.90 lb, propellant 119 lb (Mayo66 Table 3).",
            "NB: Mayo66 SL profile; Cajun ignites at ~40,000 ft so true",
            "action time at altitude ~3.3 s (L57D26 Fig 7) -- impulse",
            "integral conserved.",
        ],
        cg_fraction=0.5,
    )


def build_nike_cajun_xml(payload_lb, flight_label, total_length_in,
                         nose_section_length_in, nose_max_dia_in,
                         cajun_coast_time_s):
    """Build Nike-Cajun ORK XML for a single flight variant.

    payload_lb               nose-cone-and-instrumentation mass
                             (L57D26 p.5: 51.85 lb UM, 75.77 lb hurricane).
    flight_label             "UM (sounding rocket)" or "Hurricane variant".
    total_length_in          L57D26 Fig 6: 308.5 in (UM) / 324.4 in (hurricane).
    nose_section_length_in   L57D26 Fig 6: 46.9 in (UM, ends at Sta 46.9) /
                             62.8 in (hurricane, ends at Sta 62.8).
    nose_max_dia_in          L57D26 Fig 6: 6.75 in (UM nose base) /
                             9.0 in (hurricane nose max dia).
    cajun_coast_time_s       Nike-burnout-to-Cajun-ignition delay; L57D26
                             p.5 says 8.9 s for UM; Hurricane derived from
                             Fig 7 caption (13.2 - 3.3 = 9.9 s).
    """
    cid = newid()
    rocket_id = newid()
    nose_id = newid()
    nose_step_id = newid()
    cajun_body_id = newid()
    cajun_fins_id = newid()
    nozzle_ext_id = newid()
    adapter_id = newid()
    nike_body_id = newid()
    nike_boattail_id = newid()
    nike_fins_id = newid()

    # ---- Geometry derived from L57D26 Figure 6 station map ---------------
    # The Cajun second-stage layout (UM, L57D26 Fig 6(b), Sta 0 -> 154.2 in):
    #   Sta   0      nose tip (11-deg apex, 0.156-in nose radius)
    #   Sta  38.6    nose tip-to-shoulder (UM dia = 6.75 in)
    #   Sta  46.9    end of forward nose+instrument bay (step to 6.50 in)
    #   Sta 152.9    Cajun motor base / fin TE
    #   Sta 154.2    overall Cajun (incl nozzle ext) length
    # The hurricane variant differs only in the nose section (longer + larger
    # max diameter); body tube + fins + nozzle extension are identical.
    cajun_motor_dia_in = NIKE_CAJUN_CAJUN_DIA_IN
    cajun_motor_radius_in = cajun_motor_dia_in / 2.0

    # The Cajun "motor + body" tube runs from the nose-section step (Sta 46.9
    # for UM, Sta 62.8 for hurricane) to Sta 152.9 (fin TE). Hurricane has a
    # 15.9-in shorter motor body because its nose is longer; total stack is
    # 15.9 in longer because the nose grew 15.9 in.
    cajun_body_len_in = 152.9 - nose_section_length_in  # 106.0 in (UM); 90.1 in (hurr)

    # The L57D26 Fig 6(b) UM nose has a small 6.75 -> 6.50 step at Sta 46.9
    # (visible "Step" callout). Modelled here as a short transition between
    # the conical nose's aft radius (= max dia / 2) and the 6.5-in motor body.
    # Hurricane nose: 9.0-in max dia stepping to 6.50 -- bigger step.
    nose_step_len_in = 0.5     # short transition; "step" callout in Fig 6
    cone_aft_radius_in = nose_max_dia_in / 2.0

    # The conical nose proper covers Sta 0 -> (nose_section_length - step):
    nose_cone_len_in = nose_section_length_in - nose_step_len_in

    # Nozzle extension Sta 152.9 -> 154.2 = 1.3 in (L57D26 Fig 6).
    nozzle_ext_len_in = 154.2 - 152.9

    # ---- Cajun fins (L57D26 p.4 + Fig 3 photo + Fig 20 area) -------------
    # No dimensional drawing exists in L57D26; geometry inferred from Fig 3
    # photo proportions and Fig 20 total-area constraint (Afin = 2.07 ft^2).
    # nike_cajun.yaml flags these synthesized values as confidence: low.
    cajun_fin_root_in = 13.5      # synthesized (clipped-delta inferred)
    cajun_fin_tip_in = 4.5        # synthesized
    cajun_fin_height_in = 8.3     # synthesized; exposed semispan past 6.5-in body
    cajun_fin_sweep_in = 5.5      # synthesized: ~30 deg LE sweep per Fig 3
    cajun_fin_thickness_in = 0.20 # extruded aluminum, ~3% of root chord
    # 30-lb fins+shroud+fairing assembly (L57D26 p.5)
    cajun_fin_mass_lb = 30.0

    # ---- Nike booster geometry (L57D26 Fig 6 + p.5 + cross-ref Handbook) -
    # Fin geometry: shared Nike hardware -> NikeApacheHandbook Fig 5 callouts
    # (nike_cajun.yaml stages[0].fins, confidence: medium-low).
    nike_radius_in = NIKE_CAJUN_NIKE_DIA_IN / 2.0
    nike_aft_radius_in = 17.5 / 2.0          # L57D26 Fig 6(b) "17.5" aft skirt
    nike_body_len_in = NIKE_CAJUN_NIKE_LEN_IN
    nike_fin_root_in = 23.3                  # NikeApacheHandbook Fig 5
    nike_fin_tip_in = 10.1                   # NikeApacheHandbook Fig 5
    nike_fin_height_in = 10.75               # NikeApacheHandbook Fig 5
    nike_fin_sweep_in = 13.2                 # = 23.3 - 10.1
    nike_fin_thickness_in = 0.25             # not stated; ~1% chord typical

    # Component masses (L57D26 p.5):
    nike_adapter_lb = 27.0                   # "Booster adapter"
    nike_fins_lb = 76.50                     # "Booster fins"
    cajun_nozzle_ext_lb = 5.0                # "Nozzle extension"

    # The Nike-Cajun adapter spans the cone-frustum 7.1 in -> 17.5 in transition
    # (L57D26 p.12: "D1 = 7.1 inches and is the diameter of the front of the
    # adapter ... D2 = 17.5 inches and is the diameter of the rear of the
    # adapter"). Place a 12-in conical adapter consistent with Nike-Apache
    # precedent (cf. nike_apache.yaml; exact axial extent unstated in L57D26).
    adapter_len_in = 12.0

    # Convert all dimensions to SI ----------------------------------------
    nose_cone_len_m = nose_cone_len_in * IN
    nose_step_len_m = nose_step_len_in * IN
    cajun_body_len_m = cajun_body_len_in * IN
    nozzle_ext_len_m = nozzle_ext_len_in * IN
    adapter_len_m = adapter_len_in * IN
    nike_body_len_m = nike_body_len_in * IN
    cajun_motor_radius_m = cajun_motor_radius_in * IN
    cone_aft_radius_m = cone_aft_radius_in * IN
    nike_radius_m = nike_radius_in * IN
    nike_aft_radius_m = nike_aft_radius_in * IN

    payload_kg = payload_lb * LB
    cajun_fin_mass_kg = cajun_fin_mass_lb * LB
    nike_fin_mass_kg = nike_fins_lb * LB
    nike_adapter_kg = nike_adapter_lb * LB
    cajun_nozzle_ext_kg = cajun_nozzle_ext_lb * LB

    return f"""<?xml version='1.0' encoding='utf-8'?>
<openrocket version="1.8" creator="ORP v2.0 corpus build (paper/data/ork/sounding_rockets/_build_v2_orks.py)">
  <rocket>
    <name>Nike-Cajun (CAN) {flight_label} -- NACA RM L57D26</name>
    <id>{rocket_id}</id>
    <axialoffset method="absolute">0.0</axialoffset>
    <position type="absolute">0.0</position>
    <comment>Two-stage Nike M5/M5-E1 + Cajun TE-82 sounding rocket "CAN".
Source: NACA RM L57D26 (Royall and Garland 1957), "Characteristics of the Nike-Cajun (CAN) Rocket System and Flight Investigation of Its Performance".
Geometry: L57D26 Figure 6 station callouts (p.20).
Masses: L57D26 p.5 component table.
Thrust profiles: Nike adopted from NASA TM X-55700 Apx A (consistent with L57D26 Fig 11 acceleration); Cajun derived from NASA TM X-55440 (Mayo66) Table 3 propellant-mass-vs-time x Isp_eff = 175 s.
Flight: launched at 75-deg elevation from Wallops Island, sea level (L57D26 p.5).
Two configurations differ only in nose section: 51.85 lb / 6.75 in / 46.9 in (UM, 426,000 ft apogee per L57D26 p.5) and 75.77 lb / 9.0 in / 62.8 in (hurricane, ~412,000 ft per L57D26 Fig 7).
Cajun fin dimensions are synthesized from Fig 3 photo + Fig 20 total area (no dimensional drawing in source); nike_cajun.yaml flags these as confidence: low.</comment>
    <designer>ORP v2 corpus build (audited from nike_cajun.yaml)</designer>
    <revision>2026-05-06 v2</revision>
    <motorconfiguration configid="{cid}" default="true">
      <name>Nike booster + Cajun TE-82 ({flight_label})</name>
      <stage number="0" active="true"/>
      <stage number="1" active="true"/>
    </motorconfiguration>
    <referencetype>maximum</referencetype>

    <subcomponents>
      <stage>
        <name>Cajun sustainer + payload</name>

        <subcomponents>
          <nosecone>
            <name>11-deg conical nose ({nose_cone_len_in:.1f} in, {nose_max_dia_in:.2f} in base; {payload_lb:.2f} lb nose+instr)</name>
            <id>{nose_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel tip + Inconel skin (L57D26 p.8)</material>
            <length>{nose_cone_len_m:.6f}</length>
            <thickness>0.000813</thickness>
            <shape>conical</shape>
            <aftradius>{cone_aft_radius_m:.6f}</aftradius>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <isflipped>false</isflipped>
            <overridemass>{payload_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </nosecone>

          <transition>
            <name>Step {nose_max_dia_in:.2f} to 6.50 in (L57D26 Fig 6 step at Sta {nose_section_length_in:.1f})</name>
            <id>{nose_step_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{nose_step_len_m:.6f}</length>
            <thickness>0.002000</thickness>
            <shape>conical</shape>
            <foreradius>{cone_aft_radius_m:.6f}</foreradius>
            <aftradius>{cajun_motor_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
          </transition>

          <bodytube>
            <name>Cajun TE-82 motor + body 6.50 in (Sta {nose_section_length_in:.1f} to 152.9; motor mount)</name>
            <id>{cajun_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2810.0">Aluminum (extruded fin shroud)</material>
            <length>{cajun_body_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{cajun_motor_radius_m:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "Cajun TE-82", "AtlanticResearch",
                     NIKE_CAJUN_CAJUN_DIA_IN * IN,
                     NIKE_CAJUN_CAJUN_LEN_IN * IN,
                     NIKE_CAJUN_CAJUN_DIGEST,
                     ignition_event="burnout",
                     ignition_delay=cajun_coast_time_s)}

            <subcomponents>
              <trapezoidfinset>
                <name>Cajun cruciform fins (30 lb assembly; dims synthesized, conf: low)</name>
                <id>{cajun_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">{nozzle_ext_len_m:.6f}</axialoffset>
                <position type="bottom">{nozzle_ext_len_m:.6f}</position>
                <finish>normal</finish>
                <material type="bulk" density="2810.0">Extruded aluminum + 1/32-in Inconel LE cap (L57D26 p.4)</material>
                <thickness>{cajun_fin_thickness_in * IN:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="2810.0">Aluminum</filletmaterial>
                <rootchord>{cajun_fin_root_in * IN:.6f}</rootchord>
                <tipchord>{cajun_fin_tip_in * IN:.6f}</tipchord>
                <sweeplength>{cajun_fin_sweep_in * IN:.6f}</sweeplength>
                <height>{cajun_fin_height_in * IN:.6f}</height>
                <overridemass>{cajun_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>

          <bodytube>
            <name>Cajun nozzle extension (Sta 152.9 to 154.2; 5.0 lb)</name>
            <id>{nozzle_ext_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{nozzle_ext_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{cajun_motor_radius_m:.6f}</radius>
            <overridemass>{cajun_nozzle_ext_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </bodytube>
        </subcomponents>
      </stage>

      <stage>
        <name>Nike booster</name>
        <separationevent>burnout</separationevent>
        <separationaltitude>200.0</separationaltitude>
        <separationdelay>0.0</separationdelay>
        <separationconfiguration configid="{cid}">
          <separationevent>burnout</separationevent>
          <separationaltitude>200.0</separationaltitude>
          <separationdelay>0.0</separationdelay>
        </separationconfiguration>

        <subcomponents>
          <transition>
            <name>Nike-Cajun conical adapter 7.1 to 17.5 in (27 lb; L57D26 p.12)</name>
            <id>{adapter_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="2700.0">Aluminum</material>
            <length>{adapter_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{cajun_motor_radius_m:.6f}</foreradius>
            <aftradius>{nike_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>{nike_adapter_kg:.6f}</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>

          <bodytube>
            <name>Nike M5 motor case 16.5 in</name>
            <id>{nike_body_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>{nike_body_len_m:.6f}</length>
            <thickness>0.003000</thickness>
            <radius>{nike_radius_m:.6f}</radius>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>false</overridesubcomponentsmass>
{make_motor_mount_xml(cid, "Nike M5", "Hercules",
                     NIKE_CAJUN_NIKE_DIA_IN * IN,
                     NIKE_CAJUN_NIKE_LEN_IN * IN,
                     NIKE_CAJUN_NIKE_DIGEST,
                     ignition_event="automatic", ignition_delay=0.0)}

            <subcomponents>
              <trapezoidfinset>
                <name>Nike cruciform fins (76.5 lb total; magnesium with steel quadrants, L57D26 p.4)</name>
                <id>{nike_fins_id}</id>
                <instancecount>4</instancecount>
                <fincount>4</fincount>
                <radiusoffset method="surface">0.0</radiusoffset>
                <angleoffset method="relative">0.0</angleoffset>
                <rotation>0.0</rotation>
                <axialoffset method="bottom">0.0</axialoffset>
                <position type="bottom">0.0</position>
                <finish>normal</finish>
                <material type="bulk" density="1740.0">Magnesium fin + steel cuff</material>
                <thickness>{nike_fin_thickness_in * IN:.6f}</thickness>
                <crosssection>airfoil</crosssection>
                <cant>0.0</cant>
                <filletradius>0.0</filletradius>
                <filletmaterial type="bulk" density="1740.0">Magnesium</filletmaterial>
                <rootchord>{nike_fin_root_in * IN:.6f}</rootchord>
                <tipchord>{nike_fin_tip_in * IN:.6f}</tipchord>
                <sweeplength>{nike_fin_sweep_in * IN:.6f}</sweeplength>
                <height>{nike_fin_height_in * IN:.6f}</height>
                <overridemass>{nike_fin_mass_kg:.6f}</overridemass>
                <overridesubcomponentsmass>true</overridesubcomponentsmass>
              </trapezoidfinset>
            </subcomponents>
          </bodytube>

          <transition>
            <name>Nike aft skirt 16.5 to 17.5 in (L57D26 Fig 6)</name>
            <id>{nike_boattail_id}</id>
            <finish>normal</finish>
            <material type="bulk" density="7850.0">Steel</material>
            <length>0.050800</length>
            <thickness>0.003000</thickness>
            <shape>conical</shape>
            <foreradius>{nike_radius_m:.6f}</foreradius>
            <aftradius>{nike_aft_radius_m:.6f}</aftradius>
            <foreshoulderradius>0.0</foreshoulderradius>
            <foreshoulderlength>0.0</foreshoulderlength>
            <foreshoulderthickness>0.0</foreshoulderthickness>
            <foreshouldercapped>false</foreshouldercapped>
            <aftshoulderradius>0.0</aftshoulderradius>
            <aftshoulderlength>0.0</aftshoulderlength>
            <aftshoulderthickness>0.0</aftshoulderthickness>
            <aftshouldercapped>false</aftshouldercapped>
            <overridemass>0.0</overridemass>
            <overridesubcomponentsmass>true</overridesubcomponentsmass>
          </transition>
        </subcomponents>
      </stage>
    </subcomponents>
  </rocket>

  <simulations>
  </simulations>
</openrocket>
"""


def build_nike_cajun_um_xml():
    # L57D26 p.5: nose-cone-and-instrumentation = 51.85 lb (UM rocket)
    # L57D26 Fig 6(b): total length 308.5 in, nose section 46.9 in, nose
    # base diameter 6.75 in.
    # L57D26 p.5: 8.9 s coast from Nike burnout to Cajun ignition.
    return build_nike_cajun_xml(
        payload_lb=51.85,
        flight_label="UM (sounding rocket; 426,000 ft / 130 km)",
        total_length_in=308.5,
        nose_section_length_in=46.9,
        nose_max_dia_in=6.75,
        cajun_coast_time_s=8.9,
    )


def build_nike_cajun_hurricane_xml():
    # L57D26 p.5: nose-cone-and-instrumentation = 75.77 lb (hurricane)
    # L57D26 Fig 6(a): total length 324.4 in, nose section 62.8 in, nose
    # max diameter 9.0 in.
    # Coast time = 13.2 - 3.3 = 9.9 s (L57D26 Fig 7 caption: Cajun thrust
    # at t = 13.2 s; Nike burnout at t = 3.3 s).
    return build_nike_cajun_xml(
        payload_lb=75.77,
        flight_label="Hurricane variant (~412,000 ft / ~126 km, Fig 7)",
        total_length_in=324.4,
        nose_section_length_in=62.8,
        nose_max_dia_in=9.0,
        cajun_coast_time_s=9.9,
    )


# =========================================================================
# Pack zip with rocket.ork + thrustcurves/<digest>.rse
# =========================================================================
def pack_ork(target, xml_text, attachments):
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("rocket.ork", xml_text)
        for entry, content in attachments.items():
            z.writestr(entry, content)


# =========================================================================
# Main
# =========================================================================
def main():
    arcas_25ks325_rse = build_arcas_25ks325_rse()
    nike_motor_rse = build_nike_motor_rse()
    apache_motor_rse = build_apache_motor_rse()
    heros_motor_rse = build_heros_motor_rse()
    bbv_motor_rse = build_bbv_motor_rse()
    terrier_mk12_motor_rse = build_terrier_mk12_motor_rse()
    terrier_mk70_motor_rse = build_terrier_mk70_motor_rse()
    improved_orion_motor_rse = build_improved_orion_motor_rse()
    improved_malemute_motor_rse = build_improved_malemute_motor_rse()
    black_brant_ix_motor_rse = build_black_brant_ix_motor_rse()
    aerobee_booster_rse = build_aerobee_booster_rse()
    aerobee_sustainer_rse = build_aerobee_sustainer_rse()
    sloki_motor_rse = build_sloki_motor_rse()
    viper3a_motor_rse = build_viper3a_motor_rse()
    nike_deacon_nike_motor_rse = build_nike_deacon_nike_motor_rse()
    nike_deacon_deacon_motor_rse = build_nike_deacon_deacon_motor_rse()
    nike_cajun_nike_motor_rse = build_nike_cajun_nike_motor_rse()
    nike_cajun_cajun_motor_rse = build_nike_cajun_cajun_motor_rse()

    arcas_attachments = {
        f"thrustcurves/{ARCAS_25KS325_DIGEST}.rse": arcas_25ks325_rse,
    }
    heros_attachments = {
        f"thrustcurves/{HEROS_MOTOR_DIGEST}.rse": heros_motor_rse,
    }
    nike_apache_attachments = {
        f"thrustcurves/{NIKE_MOTOR_DIGEST}.rse": nike_motor_rse,
        f"thrustcurves/{APACHE_MOTOR_DIGEST}.rse": apache_motor_rse,
    }
    bbv_attachments = {
        f"thrustcurves/{BBV_MOTOR_DIGEST}.rse": bbv_motor_rse,
    }
    terrier_improved_orion_attachments = {
        f"thrustcurves/{TERRIER_MK12_MOTOR_DIGEST}.rse": terrier_mk12_motor_rse,
        f"thrustcurves/{IMPROVED_ORION_MOTOR_DIGEST}.rse": improved_orion_motor_rse,
    }
    terrier_improved_malemute_attachments = {
        f"thrustcurves/{TERRIER_MK12_MOTOR_DIGEST}.rse": terrier_mk12_motor_rse,
        f"thrustcurves/{IMPROVED_MALEMUTE_MOTOR_DIGEST}.rse": improved_malemute_motor_rse,
    }
    black_brant_ix_attachments = {
        f"thrustcurves/{TERRIER_MK70_MOTOR_DIGEST}.rse": terrier_mk70_motor_rse,
        f"thrustcurves/{BLACK_BRANT_IX_MOTOR_DIGEST}.rse": black_brant_ix_motor_rse,
    }
    aerobee_150a_attachments = {
        f"thrustcurves/{AEROBEE_BOOSTER_DIGEST}.rse": aerobee_booster_rse,
        f"thrustcurves/{AEROBEE_SUSTAINER_DIGEST}.rse": aerobee_sustainer_rse,
    }
    sloki_attachments = {
        f"thrustcurves/{SLOKI_MOTOR_DIGEST}.rse": sloki_motor_rse,
    }
    viper3a_attachments = {
        f"thrustcurves/{VIPER3A_MOTOR_DIGEST}.rse": viper3a_motor_rse,
    }
    nike_deacon_attachments = {
        f"thrustcurves/{NIKE_DEACON_NIKE_DIGEST}.rse": nike_deacon_nike_motor_rse,
        f"thrustcurves/{NIKE_DEACON_DEACON_DIGEST}.rse": nike_deacon_deacon_motor_rse,
    }
    nike_cajun_attachments = {
        f"thrustcurves/{NIKE_CAJUN_NIKE_DIGEST}.rse": nike_cajun_nike_motor_rse,
        f"thrustcurves/{NIKE_CAJUN_CAJUN_DIGEST}.rse": nike_cajun_cajun_motor_rse,
    }

    pack_ork(os.path.join(HERE, "arcas_blunt.ork"),
             build_arcas_blunt_xml(), arcas_attachments)
    pack_ork(os.path.join(HERE, "arcas_secant.ork"),
             build_arcas_secant_xml(), arcas_attachments)
    pack_ork(os.path.join(HERE, "nike_apache.ork"),
             build_nike_apache_xml(), nike_apache_attachments)
    pack_ork(os.path.join(HERE, "heros3.ork"),
             build_heros3_xml(), heros_attachments)
    pack_ork(os.path.join(HERE, "bbv.ork"),
             build_bbv_xml(), bbv_attachments)
    pack_ork(os.path.join(HERE, "terrier_improved_orion.ork"),
             build_terrier_improved_orion_xml(),
             terrier_improved_orion_attachments)
    pack_ork(os.path.join(HERE, "terrier_improved_malemute.ork"),
             build_terrier_improved_malemute_xml(),
             terrier_improved_malemute_attachments)
    pack_ork(os.path.join(HERE, "black_brant_ix_aspire_sr02.ork"),
             build_black_brant_ix_aspire_xml(), black_brant_ix_attachments)
    pack_ork(os.path.join(HERE, "aerobee_150a_4_65gi.ork"),
             build_aerobee_150a_xml(), aerobee_150a_attachments)
    pack_ork(os.path.join(HERE, "super_loki_dart_v2.ork"),
             build_super_loki_dart_xml(), sloki_attachments)
    pack_ork(os.path.join(HERE, "viper_3a.ork"),
             build_viper_3a_xml(), viper3a_attachments)
    pack_ork(os.path.join(HERE, "nike_deacon_flight1.ork"),
             build_nike_deacon_flight1_xml(), nike_deacon_attachments)
    pack_ork(os.path.join(HERE, "nike_deacon_flight2.ork"),
             build_nike_deacon_flight2_xml(), nike_deacon_attachments)
    pack_ork(os.path.join(HERE, "nike_cajun_um.ork"),
             build_nike_cajun_um_xml(), nike_cajun_attachments)
    pack_ork(os.path.join(HERE, "nike_cajun_hurricane.ork"),
             build_nike_cajun_hurricane_xml(), nike_cajun_attachments)

    print("v2 corpus .ork files built:")
    for f in ("arcas_blunt.ork", "arcas_secant.ork", "nike_apache.ork",
              "heros3.ork", "bbv.ork",
              "terrier_improved_orion.ork", "terrier_improved_malemute.ork",
              "black_brant_ix_aspire_sr02.ork", "aerobee_150a_4_65gi.ork",
              "super_loki_dart_v2.ork", "viper_3a.ork",
              "nike_deacon_flight1.ork", "nike_deacon_flight2.ork",
              "nike_cajun_um.ork", "nike_cajun_hurricane.ork"):
        p = os.path.join(HERE, f)
        print(f"   {p}  ({os.path.getsize(p)} bytes)")


if __name__ == "__main__":
    main()
