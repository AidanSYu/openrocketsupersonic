"""
Build per-flight Nike-Apache .ork files for the 9 radar-tracked
1965 firings catalogued in NASA X-721-67-103 (Galloway & Maxen 1967).

Reads:
  - paper/data/ork/sounding_rockets/nike_apache.ork (baseline)
  - paper/data/ork/sounding_rockets/vehicles/nike_apache_1965_flights.yaml

Writes (one per flight):
  paper/data/ork/sounding_rockets/nike_apache_1965_<slug>.ork

For each flight only the per-flight parameters are mutated:
  - Payload bodytube <overridemass>  (kg) and <name>
  - Top-level <name> of the rocket  (flight_id appended)
  - Top-level <comment>             (flight provenance)
  - <revision>                      (per-build timestamp)
  - <simulations> block             (launch elev/az, site alt+lat, atmos)

The rocket geometry, motor curves, fin assembly, adapter, and Nike
booster are inherited identically from nike_apache.ork. The 14.78 UA
non-standard payload sketch (Fig 9) is NOT geometrically modeled --
its 115-lb mass override is the only change. (See yaml notes for
14.78 UA: simulated apogee will therefore be optimistic vs radar.)
"""

import os
import re
import sys
import zipfile
from datetime import datetime

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


HERE = os.path.dirname(os.path.abspath(__file__))
BASE_ORK = os.path.join(HERE, "nike_apache.ork")
YAML_PATH = os.path.join(HERE, "vehicles", "nike_apache_1965_flights.yaml")

LB_PER_KG = 0.45359237


def slugify(flight_id: str) -> str:
    """'14.75 GR' -> '14_75_gr'."""
    return flight_id.lower().replace(".", "_").replace(" ", "_")


def read_baseline_ork():
    """Load baseline ORK and return (rocket_xml, attachments_dict, configid)."""
    with zipfile.ZipFile(BASE_ORK, "r") as z:
        names = z.namelist()
        rocket_xml = z.read("rocket.ork").decode("utf-8")
        attachments = {}
        for n in names:
            if n != "rocket.ork":
                attachments[n] = z.read(n)

    m = re.search(r'<motorconfiguration\s+configid="([^"]+)"', rocket_xml)
    if not m:
        raise RuntimeError("No motorconfiguration configid in baseline rocket.ork")
    return rocket_xml, attachments, m.group(1)


def patch_payload_mass(rocket_xml: str, payload_mass_kg: float,
                       payload_lb_label: float) -> str:
    """Mutate the Apache payload bodytube override-mass and name."""
    # The payload section is the only bodytube whose <name> begins with
    # "Payload / turnstile antenna section".
    pattern = re.compile(
        r'(<bodytube>\s*<name>Payload / turnstile antenna section)\s*\([^)]*\)(</name>)'
    )
    new_name_suffix = f" ({payload_lb_label:g} lb)"
    out, n = pattern.subn(r'\1' + new_name_suffix + r'\2', rocket_xml, count=1)
    if n != 1:
        raise RuntimeError("Could not locate payload bodytube name")

    # Now mutate its <overridemass>. Locate within this bodytube only.
    bodytube_re = re.compile(
        r'(<bodytube>\s*<name>Payload / turnstile antenna section[^<]*</name>'
        r'.*?)<overridemass>[^<]*</overridemass>(.*?</bodytube>)',
        re.DOTALL,
    )
    out2, n2 = bodytube_re.subn(
        r'\1<overridemass>' + f"{payload_mass_kg:.6f}" + r'</overridemass>\2',
        out, count=1,
    )
    if n2 != 1:
        raise RuntimeError("Could not locate payload <overridemass>")
    return out2


def patch_rocket_header(rocket_xml: str, flight_id: str, flight_meta: dict,
                        site_meta: dict, build_stamp: str) -> str:
    """Append flight_id to <name>, replace <comment> and <revision>."""
    new_name = f"Nike-Apache (NASA {flight_id})"
    rocket_xml = re.sub(
        r'<name>Nike-Apache[^<]*</name>',
        f"<name>{new_name}</name>",
        rocket_xml, count=1,
    )

    # Replace top-level <comment>...</comment> (first occurrence).
    new_comment = (
        f"Per-flight ORK for NASA Nike-Apache {flight_id}, "
        f"GSFC ID {flight_meta.get('gsfc_id') or '(none)'}, "
        f"launched {flight_meta['launch_date']} from "
        f"{site_meta['name']}.\n"
        f"Source: NASA X-721-67-103 (Galloway and Maxen, 1967), "
        f"Table 1 and altitude-vs-time radar plot. "
        f"Payload mass {flight_meta['payload_mass_lb']} lb "
        f"({flight_meta['payload_mass_kg']:.3f} kg). "
        f"Predicted launch elevation {flight_meta['launch_elevation_deg_from_horizontal']} deg from "
        f"horizontal (= {flight_meta['launch_angle_deg_from_vertical']} deg from vertical). "
        f"Radar apogee {flight_meta['apogee_ft']:,} ft "
        f"({flight_meta['apogee_km']:.1f} km), precision +-"
        f"{flight_meta.get('apogee_precision_ft', 10000):,} ft. "
        f"Per-flight YAML: paper/data/ork/sounding_rockets/vehicles/"
        f"nike_apache_1965_flights.yaml.\n"
        f"Vehicle inherited from nike_apache.ork; only payload mass and "
        f"launch parameters differ between flights."
    )
    rocket_xml = re.sub(
        r'<comment>.*?</comment>',
        f"<comment>{new_comment}</comment>",
        rocket_xml, count=1, flags=re.DOTALL,
    )

    rocket_xml = re.sub(
        r'<revision>[^<]*</revision>',
        f"<revision>{build_stamp} v2 per-flight</revision>",
        rocket_xml, count=1,
    )
    return rocket_xml


def build_simulation_block(configid: str, flight_meta: dict,
                           site_meta: dict) -> str:
    """Build a <simulations> block with per-flight launch parameters.

    OpenRocket convention:
      - launchrodangle is from VERTICAL (radians in saved file? -- no:
        the example file shows degrees in <launchrodangle>0.0</launchrodangle>
        but radians elsewhere. Inspecting OpenRocket source confirms
        degrees for this tag).
      - launchroddirection: azimuth from north, degrees.
      - launchaltitude: meters above sea level (site elevation).
      - launchlatitude / launchlongitude: decimal degrees.
    """
    rod_angle_deg = flight_meta["launch_angle_deg_from_vertical"]
    rod_az_deg = flight_meta["launch_azimuth_deg"]
    site_alt_m = site_meta["altitude_m"]
    lat = site_meta["latitude_deg_N"]
    lon = -abs(site_meta["longitude_deg_W"])  # West is negative

    return f"""  <simulations>
    <simulation status="loaded">
      <name>{flight_meta['flight_id']} radar-tracked flight</name>
      <simulator>RK4Simulator</simulator>
      <calculator>BarrowmanCalculator</calculator>
      <conditions>
        <configid>{configid}</configid>
        <launchrodlength>0.0762</launchrodlength>
        <launchintowind>false</launchintowind>
        <launchrodangle>{rod_angle_deg:.3f}</launchrodangle>
        <launchroddirection>{rod_az_deg:.3f}</launchroddirection>
        <windaverage>2.0</windaverage>
        <windturbulence>0.1</windturbulence>
        <winddirection>1.5707963267948966</winddirection>
        <wind model="average">
          <speed>2.0</speed>
          <direction>1.5707963267948966</direction>
          <standarddeviation>0.2</standarddeviation>
        </wind>
        <windmodeltype>Average</windmodeltype>
        <launchaltitude>{site_alt_m:.3f}</launchaltitude>
        <launchlatitude>{lat:.4f}</launchlatitude>
        <launchlongitude>{lon:.4f}</launchlongitude>
        <geodeticmethod>spherical</geodeticmethod>
        <simulationsteppermethod>rk4</simulationsteppermethod>
        <atmosphere model="isa"/>
        <gravity model="wgs"/>
        <timestep>0.05</timestep>
        <maxtime>1200.0</maxtime>
      </conditions>
    </simulation>
  </simulations>
"""


def patch_simulations_block(rocket_xml: str, sim_xml: str) -> str:
    pattern = re.compile(r'<simulations>.*?</simulations>\s*', re.DOTALL)
    out, n = pattern.subn(sim_xml, rocket_xml, count=1)
    if n != 1:
        raise RuntimeError("Could not find <simulations> block")
    return out


def write_ork(out_path: str, rocket_xml: str, attachments: dict):
    with zipfile.ZipFile(out_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("rocket.ork", rocket_xml)
        for name, payload in attachments.items():
            z.writestr(name, payload)


def main():
    if not os.path.exists(BASE_ORK):
        print(f"Baseline ORK not found: {BASE_ORK}", file=sys.stderr)
        print("Run _build_v2_orks.py first.", file=sys.stderr)
        sys.exit(2)
    if not os.path.exists(YAML_PATH):
        print(f"Per-flight YAML not found: {YAML_PATH}", file=sys.stderr)
        sys.exit(2)

    with open(YAML_PATH, "r", encoding="utf-8") as f:
        manifest = yaml.safe_load(f)

    base_xml, attachments, configid = read_baseline_ork()
    build_stamp = datetime.now().strftime("%Y-%m-%d")

    site_table = manifest["launch_sites"]
    out_files = []
    for flight in manifest["flights"]:
        fid = flight["flight_id"]
        site_meta = site_table[flight["launch_site"]]

        xml = base_xml
        xml = patch_payload_mass(xml,
                                 payload_mass_kg=flight["payload_mass_kg"],
                                 payload_lb_label=flight["payload_mass_lb"])
        xml = patch_rocket_header(xml, fid, flight, site_meta, build_stamp)
        sim = build_simulation_block(configid, flight, site_meta)
        xml = patch_simulations_block(xml, sim)

        slug = slugify(fid)
        out_path = os.path.join(HERE, f"nike_apache_1965_{slug}.ork")
        write_ork(out_path, xml, attachments)
        sz = os.path.getsize(out_path)
        out_files.append((fid, out_path, sz))

    print("Per-flight Nike-Apache 1965 ORKs built:")
    for fid, p, sz in out_files:
        rel = os.path.relpath(p, os.path.dirname(HERE))
        print(f"   {fid:12s} -> {rel}  ({sz} bytes)")


if __name__ == "__main__":
    main()
