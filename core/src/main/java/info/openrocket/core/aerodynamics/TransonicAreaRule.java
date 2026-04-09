package info.openrocket.core.aerodynamics;

import java.util.ArrayList;
import java.util.List;

import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.position.AxialMethod;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;

import java.util.Map;

/**
 * Whitcomb Transonic Area Rule wave drag computation.
 * <p>
 * At transonic speeds (near Mach 1), the wave drag of a body is determined
 * primarily by its longitudinal distribution of cross-sectional area S(x),
 * not by the shape of individual components. The area rule states that
 * the wave drag equals that of an equivalent body of revolution with the
 * same S(x) distribution.
 * <p>
 * The wave drag is computed from the second derivative S''(x) via the
 * double integral:
 * <pre>
 *   D_wave = -(1/(2*pi)) * integral integral S''(x1)*S''(x2)*|x1-x2|*(ln|x1-x2|-1) dx1 dx2
 *   Cd_wave = D_wave / (q * S_ref)
 * </pre>
 * <p>
 * This gives more accurate transonic drag predictions than simply summing
 * component wave drags, because it captures the interference effects between
 * components (e.g., fins mounted on a body tube create a total area
 * distribution that may be more or less favorable than the sum of parts).
 * <p>
 * Reference: Whitcomb, R.T. (1956). "A Study of the Zero-Lift Drag-Rise
 * Characteristics of Wing-Body Combinations Near the Speed of Sound".
 * NACA Report 1273.
 *
 * @see BarrowmanDragCalculator
 */
public class TransonicAreaRule {

	/** Default number of stations for area distribution computation. */
	public static final int DEFAULT_STATIONS = 200;

	/** Lower Mach for blending area rule back to component wave drag. */
	static final double BLEND_LOW = 1.2;

	/** Upper Mach for blending area rule back to component wave drag. */
	static final double BLEND_HIGH = 1.5;

	/**
	 * Result container for the area distribution computation.
	 */
	public static class AreaDistribution {
		/** Axial positions (meters from nose). */
		public final double[] x;
		/** Total cross-sectional area at each station (m^2). */
		public final double[] S;
		/** Total rocket length (meters). */
		public final double length;

		public AreaDistribution(double[] x, double[] S, double length) {
			this.x = x;
			this.S = S;
			this.length = length;
		}
	}

	/**
	 * Compute the cross-sectional area distribution S(x) along the rocket.
	 * <p>
	 * Walks the rocket nose-to-tail, summing:
	 * <ul>
	 *   <li>Body contribution: pi * r(x)^2 from SymmetricComponent radius profile</li>
	 *   <li>Fin contribution: chord(x) * thickness * nFins at each fin station</li>
	 * </ul>
	 *
	 * @param config the flight configuration
	 * @param N      number of stations (default 200)
	 * @return the area distribution
	 */
	public static AreaDistribution computeAreaDistribution(FlightConfiguration config, int N) {
		if (N < 10) {
			N = 10;
		}

		double rocketLength = config.getLengthAerodynamic();
		if (rocketLength <= 0) {
			return new AreaDistribution(new double[0], new double[0], 0);
		}

		double[] x = new double[N];
		double[] S = new double[N];
		double dx = rocketLength / (N - 1);

		for (int i = 0; i < N; i++) {
			x[i] = i * dx;
		}

		// Accumulate body area contributions
		InstanceMap imap = config.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent comp = entry.getKey();
			int instanceCount = entry.getValue().size();

			if (comp instanceof SymmetricComponent) {
				SymmetricComponent sym = (SymmetricComponent) comp;
				CoordinateIF[] absCoords = comp.toAbsolute(Coordinate.NUL);
				if (absCoords == null || absCoords.length == 0) continue;
				double compX = absCoords[0].getX();
				double compLen = comp.getLength();

				for (int i = 0; i < N; i++) {
					double localX = x[i] - compX;
					if (localX >= 0 && localX <= compLen) {
						double r = sym.getRadius(localX);
						S[i] += instanceCount * Math.PI * r * r;
					}
				}
			} else if (comp instanceof FinSet) {
				FinSet fin = (FinSet) comp;
				int nFins = fin.getFinCount() * instanceCount;
				double thickness = fin.getThickness();

				CoordinateIF[] absCoords = comp.toAbsolute(Coordinate.NUL);
				if (absCoords == null || absCoords.length == 0) continue;
				double finStartX = absCoords[0].getX();

				// Get fin points for chord computation
				CoordinateIF[] finPoints = fin.getFinPoints();
				if (finPoints == null || finPoints.length < 2) continue;

				double finLength = comp.getLength();

				for (int i = 0; i < N; i++) {
					double localX = x[i] - finStartX;
					if (localX >= 0 && localX <= finLength) {
						// Estimate chord height at this x station by interpolating fin points.
						// Fin points define the outer profile; the root is at y=0.
						double span = interpolateFinSpan(finPoints, localX);
						// Fin cross-sectional area contribution: span * thickness * nFins
						S[i] += nFins * span * thickness;
					}
				}
			}
		}

		return new AreaDistribution(x, S, rocketLength);
	}

	/**
	 * Compute the area distribution with the default number of stations.
	 */
	public static AreaDistribution computeAreaDistribution(FlightConfiguration config) {
		return computeAreaDistribution(config, DEFAULT_STATIONS);
	}

	/**
	 * Compute the transonic wave drag coefficient from the area distribution
	 * using the supersonic area rule (von Karman / Whitcomb).
	 * <p>
	 * The drag is related to the second derivative of the area distribution:
	 * <pre>
	 *   Cd_wave = -(1/(pi*S_ref)) * integral integral S''(x1)*S''(x2)*ln|x1-x2| dx1 dx2
	 * </pre>
	 * <p>
	 * S''(x) is computed via central finite differences. The double integral
	 * is evaluated with the trapezoidal rule. This is O(N^2) but with N=200
	 * is fast enough (~40k evaluations).
	 * <p>
	 * Note: the formula assumes a closed body (S=0 at both ends). For rocket
	 * bodies with an open base, the formula gives an approximation. The absolute
	 * value is taken to ensure a positive drag coefficient.
	 * <p>
	 * Reference: von Karman, Th. (1947). "The Similarity Law of Transonic Flow".
	 * J. Math. Phys., 26(3). Also: Ashley &amp; Landahl, "Aerodynamics of Wings
	 * and Bodies", Ch. 12.
	 *
	 * @param dist  the area distribution
	 * @param S_ref the reference area (m^2)
	 * @return wave drag coefficient (non-negative)
	 */
	public static double computeWaveDrag(AreaDistribution dist, double S_ref) {
		if (dist.x.length < 3 || S_ref <= 0) {
			return 0;
		}

		int N = dist.x.length;
		double dx = dist.x[1] - dist.x[0];
		if (dx <= 0) return 0;

		// Compute S''(x) via central differences
		double[] Spp = computeSecondDerivative(dist.S, dx);

		// Evaluate the double integral:
		//   I = sum_i sum_j S''(xi) * S''(xj) * ln|xi-xj| * dx^2
		// The kernel ln|r| has a logarithmic singularity at r=0 which is integrable;
		// we skip i==j terms (the diagonal has measure zero).
		double integral = 0;
		for (int i = 0; i < N; i++) {
			if (Math.abs(Spp[i]) < 1e-30) continue;
			for (int j = 0; j < N; j++) {
				if (i == j) continue;
				if (Math.abs(Spp[j]) < 1e-30) continue;

				double r = Math.abs(dist.x[i] - dist.x[j]);
				if (r < 1e-15) continue;

				integral += Spp[i] * Spp[j] * Math.log(r);
			}
		}

		// Apply trapezoidal integration weight
		integral *= dx * dx;

		// Cd_wave = -(1/(pi*S_ref)) * I
		// For a drag-producing body, this should be positive.
		// Take absolute value to handle open-base approximation.
		double Cd_wave = Math.abs(integral) / (Math.PI * S_ref);

		return Cd_wave;
	}

	/**
	 * Compute the blended transonic wave drag that transitions from area-rule
	 * wave drag (near Mach 1) to component-sum analytical wave drag (above M~1.3).
	 * <p>
	 * Below BLEND_LOW (M 1.2): pure area-rule wave drag.
	 * Above BLEND_HIGH (M 1.5): pure component wave drag.
	 * Between: smoothstep blend.
	 *
	 * @param mach             freestream Mach number
	 * @param areaRuleWaveDrag wave drag from area rule computation
	 * @param componentWaveDrag wave drag from analytical component sum
	 * @return blended wave drag coefficient
	 */
	public static double blendWaveDrag(double mach, double areaRuleWaveDrag,
			double componentWaveDrag) {
		if (mach <= BLEND_LOW) {
			return areaRuleWaveDrag;
		}
		if (mach >= BLEND_HIGH) {
			return componentWaveDrag;
		}

		double t = (mach - BLEND_LOW) / (BLEND_HIGH - BLEND_LOW);
		double s = smoothstep(t);
		return areaRuleWaveDrag * (1.0 - s) + componentWaveDrag * s;
	}

	/**
	 * Compute S''(x) using central finite differences.
	 * At endpoints, uses one-sided second-order finite differences.
	 */
	static double[] computeSecondDerivative(double[] S, double dx) {
		int N = S.length;
		double[] Spp = new double[N];
		double dx2 = dx * dx;

		if (N < 3) return Spp;

		// Interior points: central differences
		for (int i = 1; i < N - 1; i++) {
			Spp[i] = (S[i + 1] - 2.0 * S[i] + S[i - 1]) / dx2;
		}

		// Endpoints: forward/backward second-order differences
		Spp[0] = (S[2] - 2.0 * S[1] + S[0]) / dx2;
		Spp[N - 1] = (S[N - 1] - 2.0 * S[N - 2] + S[N - 3]) / dx2;

		return Spp;
	}

	/**
	 * Interpolate the fin outer profile span at a given x position.
	 * Fin points are given as (x, y) pairs defining the outer edge.
	 */
	private static double interpolateFinSpan(CoordinateIF[] points, double x) {
		if (points.length < 2) return 0;

		// Find the enclosing segment
		for (int i = 0; i < points.length - 1; i++) {
			double x0 = points[i].getX();
			double x1 = points[i + 1].getX();
			if (x >= x0 && x <= x1) {
				double segLen = x1 - x0;
				if (segLen < 1e-12) return points[i].getY();
				double t = (x - x0) / segLen;
				return points[i].getY() + t * (points[i + 1].getY() - points[i].getY());
			}
		}

		// Outside fin: check endpoints
		if (x <= points[0].getX()) return points[0].getY();
		return points[points.length - 1].getY();
	}

	/**
	 * Compute the theoretical minimum wave drag for a Sears-Haack body
	 * with given volume V and length L.
	 * <p>
	 * Cd_wave_min = 128 * V^2 / (pi * L^4 * S_ref)
	 * <p>
	 * where S_ref is the reference area. For a Sears-Haack body, the
	 * maximum cross-section area is S_max = pi * r_max^2 with
	 * r_max = (3*V / (2*pi*L))^(1/2) ... but this formula uses volume directly.
	 *
	 * @param volume total body volume (m^3)
	 * @param length body length (m)
	 * @param S_ref  reference area (m^2)
	 * @return theoretical minimum wave drag coefficient
	 */
	public static double searsHaackMinDrag(double volume, double length, double S_ref) {
		if (length <= 0 || S_ref <= 0) return 0;
		double L4 = length * length * length * length;
		return 128.0 * volume * volume / (Math.PI * L4 * S_ref);
	}

	/**
	 * Hermite smoothstep: 3t^2 - 2t^3.
	 */
	private static double smoothstep(double t) {
		t = MathUtil.clamp(t, 0.0, 1.0);
		return t * t * (3.0 - 2.0 * t);
	}
}
