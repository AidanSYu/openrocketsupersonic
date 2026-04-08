package info.openrocket.core.aerodynamics;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.CoordinateIF;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility to sweep Mach numbers and export per-component aerodynamic
 * coefficients to CSV. Designed for comparison against RASAero II and
 * analytical reference data.
 * <p>
 * Output columns: Mach, Component, FrictionCD, PressureCD, BaseCD, CD_total,
 * CNa, CP_x
 */
public class AeroCoeffientExporter {

	/**
	 * Result of a single Mach-point evaluation for one component.
	 */
	public static class ComponentAeroData {
		public final String componentName;
		public final String componentType;
		public final double mach;
		public final double aoa;
		public final double frictionCD;
		public final double pressureCD;
		public final double baseCD;
		public final double overrideCD;
		public final double totalCD;
		public final double cna;
		public final double cpX;
		public final double cn;
		public final double cm;

		public ComponentAeroData(String componentName, String componentType, double mach, double aoa,
				double frictionCD, double pressureCD, double baseCD, double overrideCD, double totalCD,
				double cna, double cpX, double cn, double cm) {
			this.componentName = componentName;
			this.componentType = componentType;
			this.mach = mach;
			this.aoa = aoa;
			this.frictionCD = frictionCD;
			this.pressureCD = pressureCD;
			this.baseCD = baseCD;
			this.overrideCD = overrideCD;
			this.totalCD = totalCD;
			this.cna = cna;
			this.cpX = cpX;
			this.cn = cn;
			this.cm = cm;
		}
	}

	/**
	 * Perform a Mach sweep and collect per-component aero data at each Mach point.
	 *
	 * @param rocket    the rocket to analyze
	 * @param machMin   minimum Mach number
	 * @param machMax   maximum Mach number
	 * @param machStep  Mach step size
	 * @param aoaRad    angle of attack in radians
	 * @return list of per-component data points across all Mach numbers
	 */
	public static List<ComponentAeroData> machSweep(Rocket rocket, double machMin, double machMax,
			double machStep, double aoaRad) {
		List<ComponentAeroData> results = new ArrayList<>();
		FlightConfiguration config = rocket.getSelectedConfiguration();
		BarrowmanCalculator calc = new BarrowmanCalculator();

		for (double mach = machMin; mach <= machMax + machStep * 0.01; mach += machStep) {
			double roundedMach = Math.round(mach * 1000.0) / 1000.0;
			FlightConditions conditions = new FlightConditions(config);
			conditions.setMach(roundedMach);
			conditions.setAOA(aoaRad);

			WarningSet warnings = new WarningSet();
			Map<RocketComponent, AerodynamicForces> forceMap = calc.getForceAnalysis(config, conditions, warnings);

			for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
				RocketComponent comp = entry.getKey();
				AerodynamicForces forces = entry.getValue();
				if (forces == null) continue;

				double cna = 0;
				double cpX = Double.NaN;
				CoordinateIF cp = forces.getCP();
				if (cp != null) {
					cna = cp.getWeight();
					cpX = cp.getX();
				}

				results.add(new ComponentAeroData(
						comp.getName(),
						comp.getClass().getSimpleName(),
						roundedMach,
						Math.toDegrees(aoaRad),
						nanToZero(forces.getFrictionCD()),
						nanToZero(forces.getPressureCD()),
						nanToZero(forces.getBaseCD()),
						nanToZero(forces.getOverrideCD()),
						nanToZero(forces.getCD()),
						nanToZero(cna),
						cpX,
						nanToZero(forces.getCN()),
						nanToZero(forces.getCm())));
			}
		}

		return results;
	}

	/**
	 * Extract only the total rocket data (component = Rocket) from a sweep.
	 */
	public static List<ComponentAeroData> filterTotalOnly(List<ComponentAeroData> data) {
		List<ComponentAeroData> totals = new ArrayList<>();
		for (ComponentAeroData d : data) {
			if ("Rocket".equals(d.componentType)) {
				totals.add(d);
			}
		}
		return totals;
	}

	/**
	 * Extract data for a single named component.
	 */
	public static List<ComponentAeroData> filterByName(List<ComponentAeroData> data, String componentName) {
		List<ComponentAeroData> filtered = new ArrayList<>();
		for (ComponentAeroData d : data) {
			if (componentName.equals(d.componentName)) {
				filtered.add(d);
			}
		}
		return filtered;
	}

	/**
	 * Build a map of Mach -> total CD from sweep data (rocket-level totals only).
	 */
	public static Map<Double, Double> getTotalCdVsMach(List<ComponentAeroData> data) {
		Map<Double, Double> result = new LinkedHashMap<>();
		for (ComponentAeroData d : filterTotalOnly(data)) {
			result.put(d.mach, d.totalCD);
		}
		return result;
	}

	/**
	 * Write sweep results to CSV.
	 */
	public static void writeCsv(List<ComponentAeroData> data, Writer writer) throws IOException {
		writer.write("Mach,AoA_deg,Component,Type,FrictionCD,PressureCD,BaseCD,OverrideCD,TotalCD,CNa,CP_x,CN,Cm\n");
		for (ComponentAeroData d : data) {
			writer.write(String.format("%.3f,%.1f,%s,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
					d.mach, d.aoa, d.componentName, d.componentType,
					d.frictionCD, d.pressureCD, d.baseCD, d.overrideCD, d.totalCD,
					d.cna, Double.isNaN(d.cpX) ? 0.0 : d.cpX, d.cn, d.cm));
		}
		writer.flush();
	}

	private static double nanToZero(double v) {
		return Double.isNaN(v) ? 0.0 : v;
	}
}
