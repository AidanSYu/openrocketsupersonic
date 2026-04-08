package info.openrocket.core.aerodynamics;

import static info.openrocket.core.models.atmosphere.AtmosphericConditions.GAMMA;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import info.openrocket.core.aerodynamics.shocks.NormalShockRelations;
import info.openrocket.core.aerodynamics.shocks.ObliqueShockSolver;
import info.openrocket.core.aerodynamics.shocks.PrandtlMeyerExpansion;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.util.Coordinate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-computed shock geometry along the rocket body for a given freestream Mach number.
 * <p>
 * At supersonic speeds, the nose generates an oblique shock that alters the local
 * flow conditions (Mach, pressure, temperature) for downstream components. Body
 * transitions (shoulders, boattails) create additional shocks or expansion fans.
 * <p>
 * This class walks the rocket body nose-to-tail, computing post-shock/expansion
 * flow conditions at each axial station. Component calculators (especially
 * {@link info.openrocket.core.aerodynamics.barrowman.FinSetCalc}) can then query
 * the local flow conditions at their axial position instead of using freestream values.
 * <p>
 * At subsonic Mach, this is a no-op passthrough — all local conditions equal freestream.
 *
 * @see info.openrocket.core.aerodynamics.barrowman.FinSetCalc
 * @see info.openrocket.core.aerodynamics.barrowman.SymmetricComponentCalc
 */
public class ShockGeometry {

	private static final Logger log = LoggerFactory.getLogger(ShockGeometry.class);

	/** Number of strips per symmetric component for surface marching */
	private static final int STRIPS_PER_COMPONENT = 20;

	/** Minimum angle change to trigger shock/expansion computation (radians) */
	private static final double MIN_TURN_ANGLE = 1e-6;

	/**
	 * Local flow conditions at a point along the rocket body.
	 */
	public static class LocalConditions {
		/** Axial position from rocket nose (meters) */
		public final double x;
		/** Local Mach number behind shock/expansion */
		public final double localMach;
		/** Static pressure ratio: p_local / p_freestream */
		public final double pressureRatio;
		/** Static temperature ratio: T_local / T_freestream */
		public final double temperatureRatio;
		/** Dynamic pressure ratio: q_local / q_freestream */
		public final double dynamicPressureRatio;

		public LocalConditions(double x, double localMach, double pressureRatio,
				double temperatureRatio, double dynamicPressureRatio) {
			this.x = x;
			this.localMach = localMach;
			this.pressureRatio = pressureRatio;
			this.temperatureRatio = temperatureRatio;
			this.dynamicPressureRatio = dynamicPressureRatio;
		}
	}

	/** Freestream (subsonic) passthrough conditions */
	private static final ShockGeometry SUBSONIC = new ShockGeometry();

	private final double freestreamMach;
	private final boolean isSupersonic;

	/** Sorted list of local conditions along the body (nose to tail) */
	private final List<LocalConditions> stations;

	/** Private constructor for subsonic passthrough */
	private ShockGeometry() {
		this.freestreamMach = 0;
		this.isSupersonic = false;
		this.stations = Collections.emptyList();
	}

	private ShockGeometry(double freestreamMach, List<LocalConditions> stations) {
		this.freestreamMach = freestreamMach;
		this.isSupersonic = true;
		this.stations = stations;
	}

	/**
	 * Compute the shock geometry for the given flight configuration and conditions.
	 * <p>
	 * At subsonic speeds (M &lt; 1.0), returns a no-op passthrough.
	 * At supersonic speeds, walks the body nose-to-tail computing local flow
	 * conditions behind shocks and expansion fans.
	 *
	 * @param configuration the current flight configuration
	 * @param conditions    the current flight conditions
	 * @return the shock geometry, or a subsonic passthrough
	 */
	public static ShockGeometry compute(FlightConfiguration configuration,
			FlightConditions conditions) {
		double mach = conditions.getMach();
		if (mach <= 1.0) {
			return SUBSONIC;
		}

		List<SymmetricComponent> bodyChain = buildBodyChain(configuration);
		if (bodyChain.isEmpty()) {
			return SUBSONIC;
		}

		List<LocalConditions> stations = computeStations(mach, bodyChain, configuration);
		return new ShockGeometry(mach, stations);
	}

	/**
	 * Get the local flow conditions at the given axial position.
	 * <p>
	 * For subsonic flight, returns freestream conditions (all ratios = 1).
	 * For supersonic flight, interpolates between computed stations.
	 *
	 * @param x axial position from rocket nose (meters)
	 * @return local flow conditions at that position
	 */
	public LocalConditions getConditionsAt(double x) {
		if (!isSupersonic || stations.isEmpty()) {
			return new LocalConditions(x, freestreamMach > 0 ? freestreamMach : 0.3,
					1.0, 1.0, 1.0);
		}

		// Before first station: use first station values
		if (x <= stations.get(0).x) {
			LocalConditions first = stations.get(0);
			return new LocalConditions(x, first.localMach, first.pressureRatio,
					first.temperatureRatio, first.dynamicPressureRatio);
		}

		// After last station: use last station values
		if (x >= stations.get(stations.size() - 1).x) {
			LocalConditions last = stations.get(stations.size() - 1);
			return new LocalConditions(x, last.localMach, last.pressureRatio,
					last.temperatureRatio, last.dynamicPressureRatio);
		}

		// Interpolate between stations
		for (int i = 0; i < stations.size() - 1; i++) {
			LocalConditions s1 = stations.get(i);
			LocalConditions s2 = stations.get(i + 1);
			if (x >= s1.x && x <= s2.x) {
				double t = (x - s1.x) / (s2.x - s1.x);
				return new LocalConditions(x,
						s1.localMach + t * (s2.localMach - s1.localMach),
						s1.pressureRatio + t * (s2.pressureRatio - s1.pressureRatio),
						s1.temperatureRatio + t * (s2.temperatureRatio - s1.temperatureRatio),
						s1.dynamicPressureRatio + t * (s2.dynamicPressureRatio - s1.dynamicPressureRatio));
			}
		}

		// Fallback (shouldn't reach here)
		LocalConditions last = stations.get(stations.size() - 1);
		return new LocalConditions(x, last.localMach, last.pressureRatio,
				last.temperatureRatio, last.dynamicPressureRatio);
	}

	/**
	 * @return true if this geometry was computed for supersonic conditions
	 */
	public boolean isSupersonic() {
		return isSupersonic;
	}

	/**
	 * @return the freestream Mach number used for this computation
	 */
	public double getFreestreamMach() {
		return freestreamMach;
	}

	/**
	 * @return the number of computed stations
	 */
	public int getStationCount() {
		return stations.size();
	}

	/**
	 * @return unmodifiable list of all computed stations
	 */
	public List<LocalConditions> getStations() {
		return Collections.unmodifiableList(stations);
	}

	// ---- Body chain construction ----

	/**
	 * Build the ordered list of SymmetricComponents from nose to tail
	 * following the linked-list chain via getNextSymmetricComponent().
	 */
	private static List<SymmetricComponent> buildBodyChain(FlightConfiguration configuration) {
		List<SymmetricComponent> chain = new ArrayList<>();

		// Find the foremost SymmetricComponent (nose)
		SymmetricComponent nose = null;
		for (RocketComponent comp : configuration.getActiveComponents()) {
			if (comp instanceof SymmetricComponent) {
				SymmetricComponent sym = (SymmetricComponent) comp;
				if (sym.getPreviousSymmetricComponent() == null) {
					nose = sym;
					break;
				}
			}
		}

		if (nose == null) {
			return chain;
		}

		// Walk the chain from nose to tail
		SymmetricComponent current = nose;
		while (current != null) {
			chain.add(current);
			current = current.getNextSymmetricComponent();
		}

		return chain;
	}

	// ---- Shock/expansion computation ----

	/**
	 * March along the body computing post-shock flow conditions at each station.
	 * <p>
	 * Algorithm:
	 * 1. At the nose tip, compute the initial shock (oblique shock from tip half-angle)
	 * 2. March downstream, tracking local surface angle changes
	 * 3. Where the surface turns away from flow (convex): Prandtl-Meyer expansion
	 * 4. Where the surface turns into flow (concave): oblique shock compression
	 * 5. At body tube junctions: check for shoulder shocks or expansion fans
	 *
	 * @param mach      freestream Mach number
	 * @param bodyChain ordered symmetric components nose-to-tail
	 * @param config    flight configuration for absolute positions
	 * @return list of LocalConditions sorted by axial position
	 */
	private static List<LocalConditions> computeStations(double mach,
			List<SymmetricComponent> bodyChain, FlightConfiguration config) {
		List<LocalConditions> stations = new ArrayList<>();

		double localMach = mach;
		double pRatio = 1.0;  // cumulative p_local / p_freestream
		double tRatio = 1.0;  // cumulative T_local / T_freestream

		double prevSurfaceAngle = 0;
		boolean hadInitialShock = false;

		for (SymmetricComponent comp : bodyChain) {
			double compX = comp.toAbsolute(Coordinate.NUL)[0].getX();
			double compLength = comp.getLength();

			if (compLength < 1e-6) {
				continue;
			}

			// Determine if this is a nose/transition or body tube
			boolean isTransition = (comp instanceof Transition) && !(comp instanceof BodyTube);

			if (isTransition) {
				Transition trans = (Transition) comp;
				double r0 = trans.getForeRadius();
				double r1 = trans.getAftRadius();

				// Nose cone (r0 < r1): compute initial shock at tip
				if (r0 < r1 && !hadInitialShock) {
					double tipAngle = computeTipHalfAngle(trans);
					if (tipAngle > MIN_TURN_ANGLE && localMach > 1.0) {
						try {
							ObliqueShockSolver.ObliqueShockResult result =
									ObliqueShockSolver.solveCone(mach, tipAngle, GAMMA);
							localMach = result.m2;
							pRatio = result.pressureRatio;
							tRatio = result.temperatureRatio;
							hadInitialShock = true;
						} catch (IllegalArgumentException e) {
							// Detached shock — use normal shock approximation
							try {
								localMach = NormalShockRelations.downstreamMach(mach, GAMMA);
								pRatio = NormalShockRelations.pressureRatio(mach, GAMMA);
								tRatio = NormalShockRelations.temperatureRatio(mach, GAMMA);
								hadInitialShock = true;
							} catch (Exception e2) {
								// Keep freestream
							}
						}
					}
					prevSurfaceAngle = tipAngle;
				}

				// March along the transition surface
				int N = STRIPS_PER_COMPONENT;
				double dx = compLength / N;
				for (int i = 0; i <= N; i++) {
					double xLocal = i * dx;
					double xAbs = compX + xLocal;

					// Compute local surface angle
					double surfAngle = computeSurfaceAngle(trans, xLocal, compLength);

					// Compute turning from previous station
					double turnAngle = prevSurfaceAngle - surfAngle;

					if (localMach >= 1.0 && Math.abs(turnAngle) > MIN_TURN_ANGLE) {
						if (turnAngle > 0) {
							// Surface turns away from flow → expansion
							try {
								double newMach = PrandtlMeyerExpansion.downstreamMach(
										localMach, turnAngle, GAMMA);
								double pExpRatio = PrandtlMeyerExpansion.pressureRatio(
										localMach, newMach, GAMMA);
								double tExpRatio = PrandtlMeyerExpansion.temperatureRatio(
										localMach, newMach, GAMMA);
								pRatio *= pExpRatio;
								tRatio *= tExpRatio;
								localMach = newMach;
							} catch (IllegalArgumentException e) {
								// Exceeded max PM angle — keep current
							}
						} else {
							// Surface turns into flow → compression (oblique shock)
							try {
								ObliqueShockSolver.ObliqueShockResult result =
										ObliqueShockSolver.solve(localMach,
												Math.abs(turnAngle), GAMMA, true);
								pRatio *= result.pressureRatio;
								tRatio *= result.temperatureRatio;
								localMach = result.m2;
							} catch (IllegalArgumentException e) {
								// Shock fails — keep current
							}
						}
					}

					// Phase 4d: Guard against NaN/Inf from extreme conditions
					if (!Double.isFinite(localMach) || localMach <= 0) {
						localMach = mach;
						pRatio = 1.0;
						tRatio = 1.0;
					}

					double qRatio = computeDynamicPressureRatio(localMach, mach, pRatio);
					stations.add(new LocalConditions(xAbs, localMach, pRatio, tRatio, qRatio));

					prevSurfaceAngle = surfAngle;
				}
			} else {
				// Body tube: constant radius, surface angle = 0
				// Check for junction with previous component
				double surfAngle = 0;
				double turnAngle = prevSurfaceAngle - surfAngle;

				if (localMach >= 1.0 && turnAngle > MIN_TURN_ANGLE) {
					// Shoulder expansion (transition to body tube)
					try {
						double newMach = PrandtlMeyerExpansion.downstreamMach(
								localMach, turnAngle, GAMMA);
						double pExpRatio = PrandtlMeyerExpansion.pressureRatio(
								localMach, newMach, GAMMA);
						double tExpRatio = PrandtlMeyerExpansion.temperatureRatio(
								localMach, newMach, GAMMA);
						pRatio *= pExpRatio;
						tRatio *= tExpRatio;
						localMach = newMach;
					} catch (IllegalArgumentException e) {
						// Keep current
					}
				} else if (localMach >= 1.0 && turnAngle < -MIN_TURN_ANGLE) {
					// Compression at junction
					try {
						ObliqueShockSolver.ObliqueShockResult result =
								ObliqueShockSolver.solve(localMach,
										Math.abs(turnAngle), GAMMA, true);
						pRatio *= result.pressureRatio;
						tRatio *= result.temperatureRatio;
						localMach = result.m2;
					} catch (IllegalArgumentException e) {
						// Keep current
					}
				}

				double qRatio = computeDynamicPressureRatio(localMach, mach, pRatio);

				// Add start and end stations for body tube
				stations.add(new LocalConditions(compX, localMach, pRatio, tRatio, qRatio));
				stations.add(new LocalConditions(compX + compLength,
						localMach, pRatio, tRatio, qRatio));

				prevSurfaceAngle = 0;
			}
		}

		return stations;
	}

	/**
	 * Compute the tip half-angle of a transition (nose cone).
	 * This is the angle of the surface tangent at the very tip.
	 */
	private static double computeTipHalfAngle(Transition trans) {
		double length = trans.getLength();
		if (length < 1e-6) return 0;

		double r0 = trans.getForeRadius();
		double dx = length * 1e-4;
		double rAtDx = trans.getRadius(dx);
		return Math.atan2(rAtDx - r0, dx);
	}

	/**
	 * Compute the local surface tangent angle at position x along a transition.
	 * Angle is measured from the axis (0 = parallel to axis).
	 */
	private static double computeSurfaceAngle(Transition trans, double xLocal,
			double compLength) {
		double dx = compLength * 1e-4;
		dx = Math.max(dx, 1e-6);

		double x1 = Math.max(0, xLocal - dx / 2);
		double x2 = Math.min(compLength, xLocal + dx / 2);
		if (x2 <= x1) return 0;

		double r1 = trans.getRadius(x1);
		double r2 = trans.getRadius(x2);
		double angle = Math.atan2(r2 - r1, x2 - x1);
		return Math.max(angle, 0); // Clamp negative angles (boattail handled separately)
	}

	/**
	 * Compute dynamic pressure ratio q_local / q_freestream.
	 * <p>
	 * q = 0.5 * rho * V^2 = 0.5 * gamma * p * M^2
	 * <p>
	 * Therefore q_local/q_free = (p_local/p_free) * (M_local/M_free)^2
	 */
	private static double computeDynamicPressureRatio(double localMach,
			double freestreamMach, double pressureRatio) {
		return pressureRatio * (localMach * localMach) / (freestreamMach * freestreamMach);
	}
}
