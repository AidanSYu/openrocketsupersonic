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
import info.openrocket.core.util.CoordinateIF;

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

	/** Upper Mach limit for smooth shock geometry activation blending.
	 *  Below M=1.0: no shock geometry. Above this: full corrections.
	 *  Between M=1.0 and this value: corrections are linearly blended toward freestream. */
	private static final double SHOCK_BLEND_MACH = 1.1;

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

		// Smooth activation: blend corrections toward freestream near M=1.0
		// to eliminate the step discontinuity when shock geometry first activates.
		if (mach < SHOCK_BLEND_MACH) {
			double blend = (mach - 1.0) / (SHOCK_BLEND_MACH - 1.0);
			blend = Math.max(0, Math.min(1, blend));
			for (int i = 0; i < stations.size(); i++) {
				LocalConditions s = stations.get(i);
				double bMach = mach + blend * (s.localMach - mach);
				double bP = 1.0 + blend * (s.pressureRatio - 1.0);
				double bT = 1.0 + blend * (s.temperatureRatio - 1.0);
				double bQ = 1.0 + blend * (s.dynamicPressureRatio - 1.0);
				stations.set(i, new LocalConditions(s.x, bMach, bP, bT, bQ));
			}
		}

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

		// Binary search for the enclosing interval (stations sorted by x)
		int lo = 0, hi = stations.size() - 1;
		while (hi - lo > 1) {
			int mid = (lo + hi) >>> 1;
			if (stations.get(mid).x <= x) {
				lo = mid;
			} else {
				hi = mid;
			}
		}

		LocalConditions s1 = stations.get(lo);
		LocalConditions s2 = stations.get(hi);
		double dx = s2.x - s1.x;

		// Guard against division by zero when two stations share the same x
		if (dx < 1e-12) {
			return new LocalConditions(x, s1.localMach, s1.pressureRatio,
					s1.temperatureRatio, s1.dynamicPressureRatio);
		}

		double t = (x - s1.x) / dx;
		return new LocalConditions(x,
				s1.localMach + t * (s2.localMach - s1.localMach),
				s1.pressureRatio + t * (s2.pressureRatio - s1.pressureRatio),
				s1.temperatureRatio + t * (s2.temperatureRatio - s1.temperatureRatio),
				s1.dynamicPressureRatio + t * (s2.dynamicPressureRatio - s1.dynamicPressureRatio));
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
			CoordinateIF[] absCoords = comp.toAbsolute(Coordinate.NUL);
			if (absCoords == null || absCoords.length == 0) {
				continue;
			}
			double compX = absCoords[0].getX();
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
							if (Double.isFinite(result.m2) && result.m2 > 0
									&& Double.isFinite(result.pressureRatio)
									&& Double.isFinite(result.temperatureRatio)) {
								localMach = result.m2;
								pRatio = result.pressureRatio;
								tRatio = result.temperatureRatio;
								hadInitialShock = true;
							} else {
								log.warn("Non-finite nose cone shock result: m2={}, pRatio={}, tRatio={} — skipping",
										result.m2, result.pressureRatio, result.temperatureRatio);
							}
						} catch (IllegalArgumentException e) {
							// Detached shock — use normal shock approximation
							log.debug("Detached nose shock at M={}, tipAngle={}: {}", mach, tipAngle, e.getMessage());
							try {
								double nsMach = NormalShockRelations.downstreamMach(mach, GAMMA);
								double nsP = NormalShockRelations.pressureRatio(mach, GAMMA);
								double nsT = NormalShockRelations.temperatureRatio(mach, GAMMA);
								if (Double.isFinite(nsMach) && nsMach > 0
										&& Double.isFinite(nsP) && Double.isFinite(nsT)) {
									localMach = nsMach;
									pRatio = nsP;
									tRatio = nsT;
									hadInitialShock = true;
								} else {
									log.warn("Non-finite normal shock fallback: m2={}, pRatio={}, tRatio={} — keeping freestream",
											nsMach, nsP, nsT);
								}
							} catch (Exception e2) {
								log.warn("Normal shock fallback failed at M={}: {}", mach, e2.getMessage());
							}
						}
					}
					// For marching consistency, prevSurfaceAngle starts at tipAngle.
					// For shaped noses (ogive, Von Karman) the local slope at x=0 is
					// much larger than tipAngle = arctan(R/L), but clamping (below)
					// prevents that from triggering a spurious compression at the first strip.
					prevSurfaceAngle = tipAngle;
				}

				// Whether this component is the initial nose cone (fore tip, r0≈0).
				// Its surface must only EXPAND the flow (slope decreasing from tip to base).
				// Spurious compression shocks caused by tip-slope singularities in shaped
				// noses (Von Karman, power series) are suppressed by clamping surfAngle ≤
				// prevSurfaceAngle in the loop below. This is NOT applied to mid-body
				// shoulder transitions (which legitimately create compression shocks).
				boolean isInitialNoseCone = (r0 < 1e-6 && r1 > 0);

				// March along the transition surface
				int N = STRIPS_PER_COMPONENT;
				double dx = compLength / N;
				for (int i = 0; i <= N; i++) {
					double xLocal = i * dx;
					double xAbs = compX + xLocal;

					// Compute local surface angle
					double surfAngle = computeSurfaceAngle(trans, xLocal, compLength);

					// For initial nose cones only: clamp surface angle to be non-increasing.
					// Shaped noses (Von Karman, ogive, power series) have a large local slope
					// near x=0 that exceeds tipAngle = arctan(R/L). Without clamping, the
					// first strip would see prevSurfaceAngle < surfAngle → turnAngle < 0
					// (compression), triggering a detached shock even though the nose has an
					// attached shock. Clamping enforces the physical reality that a convex
					// nose can only expand the flow after the initial tip shock.
					if (isInitialNoseCone) {
						surfAngle = Math.min(surfAngle, prevSurfaceAngle);
					}

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
								// FIX 1: Guard NaN/Inf BEFORE accumulating into pRatio/tRatio
								if (Double.isFinite(newMach) && newMach > 0
										&& Double.isFinite(pExpRatio) && Double.isFinite(tExpRatio)) {
									pRatio *= pExpRatio;
									tRatio *= tExpRatio;
									localMach = newMach;
								} else {
									log.warn("Non-finite expansion result at x={}: newMach={}, pExpRatio={}, tExpRatio={} — skipping",
											xAbs, newMach, pExpRatio, tExpRatio);
								}
							} catch (IllegalArgumentException e) {
								log.warn("PM expansion failed at x={}: {}", xAbs, e.getMessage());
							}
						} else {
							// Surface turns into flow → compression (oblique shock)
							try {
								ObliqueShockSolver.ObliqueShockResult result =
										ObliqueShockSolver.solve(localMach,
												Math.abs(turnAngle), GAMMA, true);
								// FIX 1: Guard NaN/Inf BEFORE accumulating into pRatio/tRatio
								if (Double.isFinite(result.m2) && result.m2 > 0
										&& Double.isFinite(result.pressureRatio)
										&& Double.isFinite(result.temperatureRatio)) {
									pRatio *= result.pressureRatio;
									tRatio *= result.temperatureRatio;
									localMach = result.m2;
								} else {
									log.warn("Non-finite oblique shock result at x={}: m2={}, pRatio={}, tRatio={} — skipping",
											xAbs, result.m2, result.pressureRatio, result.temperatureRatio);
								}
							} catch (IllegalArgumentException e) {
								log.warn("Oblique shock failed at x={}: {}", xAbs, e.getMessage());
							}
						}
					}

					double qRatio = computeDynamicPressureRatio(localMach, mach, pRatio);
					stations.add(new LocalConditions(xAbs, localMach, pRatio, tRatio, qRatio));

					prevSurfaceAngle = surfAngle;
				}
			} else {
				// Body tube: constant radius, surface angle = 0

				// Flow recovery after detached shock: if the nose shock was detached,
				// the normal shock fallback sets localMach to subsonic, and the surface
				// marching skips all expansion fans (they require localMach >= 1.0).
				// This incorrectly propagates deep-subsonic conditions to downstream
				// components (fins). In reality, the flow re-accelerates around the
				// nose and is near-freestream by the body tube. Reset to freestream.
				if (localMach < 1.0 && mach > 1.0) {
					log.debug("Flow recovery at body tube compX={}: localMach={} reset to freestream M={}",
							compX, localMach, mach);
					localMach = mach;
					pRatio = 1.0;
					tRatio = 1.0;
				}

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
						if (Double.isFinite(newMach) && newMach > 0
								&& Double.isFinite(pExpRatio) && Double.isFinite(tExpRatio)) {
							pRatio *= pExpRatio;
							tRatio *= tExpRatio;
							localMach = newMach;
						} else {
							log.warn("Non-finite shoulder expansion result at compX={}: newMach={}, pExpRatio={}, tExpRatio={} — skipping",
									compX, newMach, pExpRatio, tExpRatio);
						}
					} catch (IllegalArgumentException e) {
						log.warn("Shoulder PM expansion failed at compX={}: {}", compX, e.getMessage());
					}
				} else if (localMach >= 1.0 && turnAngle < -MIN_TURN_ANGLE) {
					// Compression at junction
					try {
						ObliqueShockSolver.ObliqueShockResult result =
								ObliqueShockSolver.solve(localMach,
										Math.abs(turnAngle), GAMMA, true);
						if (Double.isFinite(result.m2) && result.m2 > 0
								&& Double.isFinite(result.pressureRatio)
								&& Double.isFinite(result.temperatureRatio)) {
							pRatio *= result.pressureRatio;
							tRatio *= result.temperatureRatio;
							localMach = result.m2;
						} else {
							log.warn("Non-finite junction shock result at compX={}: m2={}, pRatio={}, tRatio={} — skipping",
									compX, result.m2, result.pressureRatio, result.temperatureRatio);
						}
					} catch (IllegalArgumentException e) {
						log.warn("Junction oblique shock failed at compX={}: {}", compX, e.getMessage());
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
	 * Compute the effective cone half-angle of a transition (nose cone) for shock
	 * calculations.
	 *
	 * <p>Uses the base half-angle {@code arctan(R/L)} rather than sampling the
	 * local surface slope at the tip. Sampling at {@code dx = length * 1e-4}
	 * gives {@code dr/dx → ∞} for Von Karman and ogive shapes (their radius
	 * profile has infinite slope at the mathematical tip where r = 0), which
	 * incorrectly returns angles near 90° and triggers a detached-shock fallback
	 * even for physically slender, attached-shock noses at M 2-4.
	 *
	 * <p>For a cone, {@code arctan(R/L)} is the exact half-angle. For shaped
	 * noses it is the equivalent slant angle that governs whether the shock
	 * attaches, which is the conservative quantity needed here.
	 */
	private static double computeTipHalfAngle(Transition trans) {
		double length = trans.getLength();
		if (length < 1e-6) return 0;

		double r0 = trans.getForeRadius();
		double r1 = trans.getAftRadius();
		// Use the overall base half-angle arctan((r1-r0)/L) as the effective
		// cone half-angle.  For cones this is exact; for ogive/Von Karman it is
		// the physically meaningful slant angle that determines shock attachment.
		return Math.atan2(r1 - r0, length);
	}

	/**
	 * Compute the local surface tangent angle at position x along a transition.
	 * Angle is measured from the axis: positive when the surface flares outward
	 * (radius increasing, e.g. nose cone, outward shoulder), negative when it
	 * tapers inward (radius decreasing, e.g. boattail, reducer transition).
	 * <p>
	 * The negative-angle case is essential: at supersonic speeds, the body
	 * marching loop uses the change in surface angle (turnAngle = prevSurfaceAngle
	 * − surfAngle) to decide between Prandtl–Meyer expansion (turnAngle &gt; 0)
	 * and oblique shock (turnAngle &lt; 0). For a boattail entering after a
	 * body tube, prevSurfaceAngle = 0 and surfAngle is the negative boattail
	 * half-angle, so turnAngle = +|halfAngle| correctly routes the corner to
	 * the expansion branch. Clamping negative angles to 0 here used to suppress
	 * the boattail expansion entirely and — at the next iteration after a tip
	 * angle was set on a non-nose transition — produced a phantom oblique
	 * shock with the full half-angle as the requested compression, exceeding
	 * theta-max near M=1 and spamming "shock detachment" warnings.
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
		return Math.atan2(r2 - r1, x2 - x1);
	}

	/**
	 * Estimate the turbulent boundary layer momentum thickness at axial
	 * position {@code x} from the nose tip.
	 * <p>
	 * Uses the turbulent flat-plate approximation:
	 * <pre>
	 *   theta_BL = 0.036 * x / Re_x^0.2
	 * </pre>
	 * where {@code Re_x = rho * V * x / mu}.
	 * <p>
	 * This is needed by the ESDU 66011 forward-facing step drag model
	 * (Phase 7d) and the protuberance interference penalty (Phase 7c).
	 *
	 * @param x          axial distance from the nose (meters)
	 * @param conditions current flight conditions (for velocity, density, viscosity)
	 * @return estimated momentum thickness (meters), always >= 0
	 */
	public static double getMomentumThicknessAt(double x,
			FlightConditions conditions) {
		if (x <= 0 || conditions.getVelocity() < 1e-6) {
			return 0;
		}
		double nu = conditions.getAtmosphericConditions().getKinematicViscosity();
		if (nu <= 0) {
			return 0;
		}
		double Re_x = conditions.getVelocity() * x / nu;
		if (Re_x < 1.0) {
			return 0;
		}
		return 0.036 * x / Math.pow(Re_x, 0.2);
	}

	// ---- Multi-body shock interference (Phase 7a) ----

	/**
	 * Result of inter-body shock impingement computation.
	 */
	public static class InterBodyImpingement {
		/** Axial position of impingement on the core body (meters from nose). */
		public final double xImpinge;
		/** Interference drag coefficient increment (referenced to S_ref). */
		public final double deltaCd;
		/** Side force coefficient (non-zero for asymmetric configurations). */
		public final double cySide;

		public InterBodyImpingement(double xImpinge, double deltaCd, double cySide) {
			this.xImpinge = xImpinge;
			this.deltaCd = deltaCd;
			this.cySide = cySide;
		}
	}

	/**
	 * Compute the inter-body shock impingement for a strap-on booster
	 * relative to the core stage.
	 * <p>
	 * At supersonic speeds, each body generates a bow shock at its nose.
	 * The Mach cone half-angle is mu = asin(1/M). If the lateral separation
	 * between core and booster is {@code s}, the impingement location on the
	 * core is:
	 * <pre>
	 *   x_impinge = (s - r_core - r_booster) / tan(mu)
	 * </pre>
	 *
	 * @param mach          freestream Mach number (must be > 1)
	 * @param separation    center-to-center distance between core and booster (m)
	 * @param rCore         core body radius (m)
	 * @param rBooster      booster body radius (m)
	 * @param boosterLength length of the booster body (m)
	 * @param sRef          reference area (m^2)
	 * @param gamma         ratio of specific heats
	 * @return impingement result, or null if no impingement
	 */
	public static InterBodyImpingement computeInterBodyImpingement(
			double mach, double separation, double rCore, double rBooster,
			double boosterLength, double sRef, double gamma) {
		if (mach <= 1.0 || separation <= 0 || rCore <= 0 || rBooster <= 0
				|| boosterLength <= 0 || sRef <= 0) {
			return null;
		}

		double gap = separation - rCore - rBooster;
		if (gap <= 0) {
			return null;
		}

		double mu = Math.asin(1.0 / mach);
		double tanMu = Math.tan(mu);
		if (tanMu < 1e-12) {
			return null;
		}

		double xImpinge = gap / tanMu;
		if (xImpinge > boosterLength) {
			return null;
		}

		double deflectionAngle = mu;

		double pressureRatio;
		try {
			ObliqueShockSolver.ObliqueShockResult result =
					ObliqueShockSolver.solve(mach, deflectionAngle, gamma, true);
			pressureRatio = result.pressureRatio;
		} catch (IllegalArgumentException e) {
			try {
				pressureRatio = NormalShockRelations.pressureRatio(mach, gamma);
			} catch (Exception e2) {
				return null;
			}
		}

		double cp = (pressureRatio - 1.0) / (0.5 * gamma * mach * mach);
		double aImpinge = Math.PI * rBooster * rCore;
		double deltaCd = cp * aImpinge / sRef;

		return new InterBodyImpingement(xImpinge, Math.max(0, deltaCd), 0);
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
