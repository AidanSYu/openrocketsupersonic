package info.openrocket.core.aerodynamics;

import static info.openrocket.core.util.MathUtil.pow2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import info.openrocket.core.aerodynamics.barrowman.FinSetCalc;
import info.openrocket.core.masscalc.MassCalculator;
import info.openrocket.core.aerodynamics.barrowman.RocketComponentCalc;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.ComponentAssembly;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.rocketcomponent.InstanceContext;
import info.openrocket.core.rocketcomponent.InstanceMap;
import info.openrocket.core.rocketcomponent.ParallelStage;
import info.openrocket.core.rocketcomponent.PodSet;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.SymmetricComponent;
import info.openrocket.core.unit.UnitGroup;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Reflection;

/**
 * Stability portion of the extended Barrowman aerodynamic calculator.
 */
public class BarrowmanStabilityCalculator implements StabilityCalculator {

	private static final double STALL_ANGLE = 17.5 * Math.PI / 180;
	private static final String BARROWMAN_PACKAGE = "info.openrocket.core.aerodynamics.barrowman";
	private static final String BARROWMAN_SUFFIX = "Calc";

	/**
	 * Global damping multiplier applied to the legacy getDampingMultiplier() result.
	 * The original OpenRocket code used 1x; this fork uses 3x for more realistic
	 * apogee-turn behavior. Package-visible for sensitivity testing only.
	 */
	static double DAMPING_MULTIPLIER = 3.0;

	/**
	 * Transonic Cmq augmentation peak factor. The augmentation is:
	 *   k_transonic = 1.0 + TRANSONIC_CMQ_PEAK * exp(-((M-1)/TRANSONIC_CMQ_SIGMA)^2)
	 * At M=1.0, k_transonic = 1 + TRANSONIC_CMQ_PEAK = 3.5 by default.
	 * Package-visible for sensitivity testing only.
	 */
	static double TRANSONIC_CMQ_PEAK = 2.5;
	static double TRANSONIC_CMQ_SIGMA = 0.15;

	private final WarningSet ignoreWarningSet = new WarningSet();

	private Map<RocketComponent, RocketComponentCalc> calcMap = null;
	private double cacheDiameter = -1;
	private double cacheLength = -1;

	/** Current shock geometry for the calculation pass (Phase 3b). */
	private ShockGeometry shockGeometry = null;

	@Override
	public StabilityCalculator newInstance() {
		return new BarrowmanStabilityCalculator();
	}

	@Override
	public double getStallAngle() {
		return STALL_ANGLE;
	}

	@Override
	public CoordinateIF getCP(FlightConfiguration configuration, FlightConditions conditions, WarningSet warnings) {
		return calculateNonAxialForces(configuration, conditions, warnings).getCP();
	}

	@Override
	public AerodynamicForces calculateNonAxialForces(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		ensureCalcMap(configuration);

		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		InstanceMap imap = configuration.getActiveInstances();
		AerodynamicForces assemblyForces = new AerodynamicForces().zero();

		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent comp = entry.getKey();
			List<InstanceContext> contextList = entry.getValue();

			RocketComponentCalc calcObj = calcMap.get(comp);
			if (calcObj == null) {
				continue;
			}

			// Pass shock geometry to component calc (Phase 3b)
			calcObj.setShockGeometry(shockGeometry);

			AerodynamicForces componentForces = calculateComponentNonAxialForces(conditions, comp, calcObj,
					contextList, actualWarnings);
			assemblyForces.merge(componentForces);
		}

		return assemblyForces;
	}

	@Override
	public StabilityForceBreakdown getForceAnalysis(FlightConfiguration configuration,
			FlightConditions conditions, WarningSet warnings) {
		ensureCalcMap(configuration);

		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;
		InstanceMap instances = configuration.getActiveInstances();

		Map<RocketComponent, AerodynamicForces> eachMap = new LinkedHashMap<>();
		Map<RocketComponent, AerodynamicForces> assemblyMap = new LinkedHashMap<>();

		calculateForceAnalysis(configuration, conditions, configuration.getRocket(), instances, eachMap,
				assemblyMap, actualWarnings);

		return new StabilityForceBreakdown(eachMap, assemblyMap);
	}

	@Override
	public void calculateDampingMoments(FlightConfiguration configuration, FlightConditions conditions,
			AerodynamicForces total) {
		ensureCalcMap(configuration);

		double mul = getDampingMultiplier(configuration, conditions, conditions.getPitchCenter().getX());
		double pitchRate = conditions.getPitchRate();
		double yawRate = conditions.getYawRate();
		double velocity = conditions.getVelocity();

		mul *= DAMPING_MULTIPLIER; // Higher damping yields much more realistic apogee turn

		double pitchDampingMomentMagnitude = MathUtil.min(mul * pow2(pitchRate / velocity), total.getCm());
		double yawDampingMomentMagnitude = MathUtil.min(mul * pow2(yawRate / velocity), total.getCyaw());

		total.setPitchDampingMoment(MathUtil.sign(pitchRate) * pitchDampingMomentMagnitude);
		total.setYawDampingMoment(MathUtil.sign(yawRate) * yawDampingMomentMagnitude);

		// Phase 11a: Magnus force & moment derivatives
		// Slender body approximation: Cy_pa = -(2/3) * CNa_body
		double cnaTotal = total.getCP().getWeight();
		double xCP = total.getCP().getX();
		double xCG = conditions.getPitchCenter().getX();
		double refLen = conditions.getRefLength();

		// Use total CNa as approximation for body CNa contribution
		// (conservative: body typically contributes 20-40% of total)
		double cnaBody = cnaTotal * 0.3; // approximate body fraction
		double cyPa = -(2.0 / 3.0) * cnaBody;
		double cnPa = cyPa * (xCP - xCG) / refLen;

		total.setCyPa(cyPa);
		total.setCnPa(cnPa);

		// Phase 8a: Pitch damping derivative Cmq
		double cmqTotal = 0;

		InstanceMap imap = configuration.getActiveInstances();
		for (Map.Entry<RocketComponent, ArrayList<InstanceContext>> entry : imap.entrySet()) {
			RocketComponent comp = entry.getKey();
			RocketComponentCalc calcObj = calcMap.get(comp);
			if (calcObj == null) continue;

			AerodynamicForces compForces = calculateComponentNonAxialForces(conditions, comp, calcObj,
					entry.getValue(), ignoreWarningSet);
			CoordinateIF cp = compForces.getCP();
			if (cp != null && !Double.isNaN(cp.getX()) && cp.getWeight() > 0) {
				double arm = cp.getX() - xCG;
				cmqTotal += -2.0 * cp.getWeight() * arm * arm / (refLen * refLen);
			}
		}

		double mach = conditions.getMach();
		double k_transonic = 1.0 + TRANSONIC_CMQ_PEAK * Math.exp(-Math.pow((mach - 1.0) / TRANSONIC_CMQ_SIGMA, 2));
		cmqTotal *= k_transonic;

		total.setCmq(cmqTotal);
		total.setCmAlphaDot(0.4 * cmqTotal);
	}

	@Override
	public void checkGeometry(FlightConfiguration configuration, RocketComponent component, WarningSet warnings) {
		WarningSet actualWarnings = (warnings != null) ? warnings : ignoreWarningSet;

		Queue<RocketComponent> queue = new LinkedList<>();
		addDirectChildStagesToQueue(configuration, queue, component);

		SymmetricComponent prevComp = null;
		if ((component instanceof ComponentAssembly) &&
				(!(component instanceof Rocket)) &&
				(component.getChildCount() > 0)) {
			prevComp = ((SymmetricComponent) (component.getChild(0))).getPreviousSymmetricComponent();
		}

		while (queue.peek() != null) {
			RocketComponent comp = queue.poll();
			if ((comp instanceof SymmetricComponent) ||
					((comp instanceof AxialStage) &&
							!(comp instanceof ParallelStage))) {
				addDirectChildStagesToQueue(configuration, queue, comp);

				if (comp instanceof SymmetricComponent) {
					SymmetricComponent sym = (SymmetricComponent) comp;
					if (prevComp == null) {
						if (sym.getForeRadius() - sym.getThickness() > MathUtil.EPSILON) {

							// only record open airframe warning if it's the sustainer or it has a recovery device
							boolean sustainer = configuration.isStageActive(0);
							boolean hasRecoveryDevice = configuration.getBottomStage().hasRecoveryDevice();

							if (sustainer || hasRecoveryDevice) {
								actualWarnings.add(Warning.OPEN_AIRFRAME_FORWARD, sym);
							}
						}
					} else {
						if (!UnitGroup.UNITS_LENGTH.getDefaultUnit().toStringUnit(2.0 * sym.getForeRadius())
								.equals(UnitGroup.UNITS_LENGTH.getDefaultUnit()
										.toStringUnit(2.0 * prevComp.getAftRadius()))) {
							actualWarnings.add(Warning.DIAMETER_DISCONTINUITY, prevComp, sym);
						}

						if ((sym.getLength() < MathUtil.EPSILON) ||
							(sym.getAftRadius() < MathUtil.EPSILON && sym.getForeRadius() < MathUtil.EPSILON)) {
							actualWarnings.add(Warning.ZERO_VOLUME_BODY, sym);
						}

						double symXfore = sym.toAbsolute(Coordinate.NUL)[0].getX();
						double prevXfore = prevComp.toAbsolute(Coordinate.NUL)[0].getX();

						double symXaft = sym.toAbsolute(new Coordinate(comp.getLength(), 0, 0, 0))[0].getX();
						double prevXaft = prevComp.toAbsolute(new Coordinate(prevComp.getLength(), 0, 0, 0))[0].getX();

						if (!UnitGroup.UNITS_LENGTH.getDefaultUnit().toStringUnit(symXfore)
								.equals(UnitGroup.UNITS_LENGTH.getDefaultUnit().toStringUnit(prevXaft))) {
							if (symXfore > prevXaft) {
								actualWarnings.add(Warning.AIRFRAME_GAP, prevComp, sym);
							} else {
								if ((symXfore >= prevXfore) &&
										((symXaft >= prevXaft) || (sym.getNextSymmetricComponent() == null))) {
									actualWarnings.add(Warning.AIRFRAME_OVERLAP, prevComp, sym);
								} else {
									SymmetricComponent firstComp = prevComp;
									SymmetricComponent scout = prevComp;
									while (scout != null) {
										firstComp = scout;
										scout = scout.getPreviousSymmetricComponent();
									}
									double firstCompXfore = firstComp.toAbsolute(Coordinate.NUL)[0].getX();

									SymmetricComponent lastComp = sym;
									scout = sym;
									while (scout != null) {
										lastComp = scout;
										scout = scout.getNextSymmetricComponent();
									}
									double lastCompXaft = lastComp
											.toAbsolute(new Coordinate(lastComp.getLength(), 0, 0, 0))[0].getX();

									if (lastCompXaft <= firstCompXfore) {
										actualWarnings.add(Warning.PODSET_FORWARD, comp.getParent());
									} else {
										actualWarnings.add(Warning.PODSET_OVERLAP, comp.getParent());
									}
								}

							}
						} else {
							RocketComponent prevCompParent = prevComp.getParent();
							RocketComponent compParent = comp.getParent();
							int prevCompPos = prevCompParent.getChildPosition(prevComp);
							RocketComponent nextComp = prevCompPos + 1 >= prevCompParent.getChildCount() ? null
									: prevCompParent.getChild(prevCompPos + 1);
							if ((compParent instanceof PodSet || compParent instanceof ParallelStage) &&
									MathUtil.equals(symXfore, prevXaft) && (compParent.getParent() == nextComp)) {
								actualWarnings.add(Warning.PODSET_OVERLAP, comp.getParent());
							}
						}
					}
					prevComp = sym;
				}
			} else if ((comp instanceof PodSet) || (comp instanceof ParallelStage)) {
				checkGeometry(configuration, comp, actualWarnings);
			}
		}
	}

	/**
	 * Set the shock geometry for the current calculation pass.
	 * Called by BarrowmanCalculator before each aero computation.
	 *
	 * @param shockGeometry pre-computed shock geometry, or null for subsonic
	 */
	public void setShockGeometry(ShockGeometry shockGeometry) {
		this.shockGeometry = shockGeometry;
	}

	@Override
	public void voidAerodynamicCache() {
		calcMap = null;
		cacheDiameter = -1;
		cacheLength = -1;
		shockGeometry = null;
	}

	private AerodynamicForces calculateComponentNonAxialForces(FlightConditions conditions, RocketComponent comp,
			RocketComponentCalc calcObj, List<InstanceContext> contextList, WarningSet warnings) {
		AerodynamicForces componentForces = new AerodynamicForces().zero();

		for (InstanceContext context : contextList) {
			AerodynamicForces instanceForces = new AerodynamicForces().zero();
			calcObj.calculateNonaxialForces(conditions, context.transform, instanceForces, warnings);

			CoordinateIF cpInst = instanceForces.getCP();
			CoordinateIF cpAbs = context.transform.transform(cpInst);
			cpAbs = cpAbs.setY(0.0).setZ(0.0);

			instanceForces.setCP(cpAbs);
			double cNInst = instanceForces.getCN();
			instanceForces.setCm(cNInst * instanceForces.getCP().getX() / conditions.getRefLength());

			componentForces.merge(instanceForces);
		}

		componentForces.setComponent(comp);

		return componentForces;
	}

	private AerodynamicForces calculateForceAnalysis(FlightConfiguration configuration, FlightConditions conds,
			RocketComponent comp,
			InstanceMap instances,
			Map<RocketComponent, AerodynamicForces> eachForces,
			Map<RocketComponent, AerodynamicForces> assemblyForces,
			WarningSet warnings) {
		AerodynamicForces aggregateForces = new AerodynamicForces().zero();
		aggregateForces.setComponent(comp);

		if (comp.isAerodynamic() || comp instanceof ComponentAssembly) {
			RocketComponentCalc calcObj = calcMap.get(comp);
			if (calcObj == null) {
				throw new NullPointerException("Could not find a CalculationObject for aerodynamic Component!: "
						+ comp.getComponentName());
			} else {
				// Pass shock geometry to component calc (Phase 3b)
				calcObj.setShockGeometry(shockGeometry);

				List<InstanceContext> contextList = instances.get(comp);
				AerodynamicForces compForces = calculateComponentNonAxialForces(conds, comp, calcObj, contextList,
						warnings);
				eachForces.put(comp, compForces);
				aggregateForces.merge(compForces);
			}
		}

		for (RocketComponent child : comp.getChildren()) {
			if (child instanceof AxialStage && !configuration.isStageActive(child.getStageNumber())) {
				for (AxialStage childStage : child.getTopLevelChildStages()) {
					if (configuration.isStageActive(childStage.getStageNumber())) {
						AerodynamicForces childForces = calculateForceAnalysis(configuration, conds, childStage, instances,
								eachForces, assemblyForces, warnings);
						if (childForces != null) {
							aggregateForces.merge(childForces);
						}
					}
				}
				continue;
			}

			AerodynamicForces childForces = calculateForceAnalysis(configuration, conds, child, instances, eachForces,
					assemblyForces, warnings);

			if (childForces != null) {
				aggregateForces.merge(childForces);
			}
		}

		assemblyForces.put(comp, aggregateForces);

		return assemblyForces.get(comp);
	}

	private void addDirectChildStagesToQueue(FlightConfiguration configuration, Queue<RocketComponent> queue,
			RocketComponent comp) {
		for (RocketComponent child : comp.getChildren()) {
			if (child instanceof AxialStage && !configuration.isStageActive(child.getStageNumber())) {
				for (AxialStage childStage : child.getTopLevelChildStages()) {
					if (configuration.isStageActive(childStage.getStageNumber())) {
						queue.add(childStage);
					}
				}
				continue;
			}
			queue.add(child);
		}
	}

	private double getDampingMultiplier(FlightConfiguration configuration, FlightConditions conditions, double cgx) {
		if (cacheDiameter < 0) {
			double area = 0;
			cacheLength = 0;
			cacheDiameter = 0;

			for (RocketComponent c : configuration.getActiveComponents()) {
				if (c instanceof SymmetricComponent) {
					SymmetricComponent s = (SymmetricComponent) c;
					area += s.getComponentPlanformArea();
					cacheLength += s.getLength();
				}
			}
			if (cacheLength > 0) {
				cacheDiameter = area / cacheLength;
			}
		}

		double mul = 0.275 * cacheDiameter / (conditions.getRefArea() * conditions.getRefLength());
		mul *= (MathUtil.pow4(cgx) + MathUtil.pow4(cacheLength - cgx));

		for (RocketComponent c : configuration.getActiveComponents()) {
			if (c instanceof FinSet) {
				FinSet f = (FinSet) c;
				mul += 0.6 * Math.min(f.getFinCount(), 4) * f.getPlanformArea() *
						MathUtil.pow3(Math.abs(f.toAbsolute(new Coordinate(
								((FinSetCalc) calcMap.get(f)).getMidchordPos()))[0].getX()
								- cgx)) /
						(conditions.getRefArea() * conditions.getRefLength());
			}
		}

		return mul;
	}

	private void ensureCalcMap(FlightConfiguration configuration) {
		if (calcMap == null) {
			buildCalcMap(configuration);
		}
	}

	private void buildCalcMap(FlightConfiguration configuration) {
		calcMap = new HashMap<>();

		for (RocketComponent comp : configuration.getAllComponents()) {
			if (!comp.isAerodynamic() && !(comp instanceof ComponentAssembly)) {
				continue;
			}

			RocketComponentCalc calcObj = (RocketComponentCalc) Reflection.construct(
					BARROWMAN_PACKAGE,
					comp,
					BARROWMAN_SUFFIX,
					comp);

			calcMap.put(comp, calcObj);
		}
	}
}
