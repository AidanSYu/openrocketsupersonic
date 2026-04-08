package info.openrocket.core.aerodynamics.barrowman;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.aerodynamics.ShockGeometry;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.util.Transformation;

public abstract class RocketComponentCalc {

	/** Axial position of this component from the rocket nose (meters). */
	protected double componentAbsoluteX = 0;

	/** Pre-computed shock geometry for the current calculation pass. */
	protected ShockGeometry shockGeometry = null;

	public RocketComponentCalc(RocketComponent component) {
		// Store absolute position for shock geometry lookups
		if (component != null) {
			try {
				this.componentAbsoluteX = component.toAbsolute(
						new info.openrocket.core.util.Coordinate(0, 0, 0))[0].getX();
			} catch (Exception e) {
				this.componentAbsoluteX = 0;
			}
		}
	}

	/**
	 * Set the shock geometry for the current calculation pass.
	 * Called by the stability calculator before invoking force/drag methods.
	 *
	 * @param shockGeometry pre-computed shock geometry, or null for subsonic
	 */
	public void setShockGeometry(ShockGeometry shockGeometry) {
		this.shockGeometry = shockGeometry;
	}

	/**
	 * Calculate the non-axial forces produced by the component (normal and side
	 * forces,
	 * pitch, yaw and roll moments and CP position). The values are stored in the
	 * <code>AerodynamicForces</code> object. Additionally the value of CNa is
	 * computed
	 * and stored if possible without large amount of extra calculation, otherwise
	 * NaN is stored. The CP coordinate is stored in local coordinates and moments
	 * are
	 * computed around the local origin.
	 * 
	 * @param conditions the flight conditions.
	 * @param transform  transformation from InstanceMap to get rotations rotations
	 * @param forces     the object in which to store the values.
	 * @param warnings   set in which to store possible warnings.
	 */
	public abstract void calculateNonaxialForces(FlightConditions conditions, Transformation transform,
			AerodynamicForces forces, WarningSet warnings);

	/**
	 * Calculates the friction drag of the component.
	 *
	 * @param conditions  the flight conditions
	 * @param componentCF component coefficient of friction, calculated in
	 *                    BarrowmanCalculator
	 * @param warnings    set in which to to store possible warnings
	 * @return the friction drag coefficient of the component
	 */
	public abstract double calculateFrictionCD(FlightConditions conditions, double componentCf, WarningSet warnings);

	/**
	 * Calculates the pressure drag of the component. This does NOT include
	 * the effect of discontinuities in the rocket body, nor trailing edge
	 * (base) drag — see {@link #calculateComponentBaseCD}.
	 *
	 * @param conditions   the flight conditions.
	 * @param stagnationCD the current stagnation drag coefficient
	 * @param baseCD       the current base drag coefficient
	 * @param warnings     set in which to store possible warnings
	 * @return the pressure drag coefficient of the component
	 */
	public abstract double calculatePressureCD(FlightConditions conditions,
			double stagnationCD, double baseCD, WarningSet warnings);

	/**
	 * Calculates the base (trailing edge) drag of the component.
	 * The default implementation returns 0; subclasses with meaningful
	 * base drag (e.g. fins) should override.
	 *
	 * @param conditions the flight conditions.
	 * @param baseCD     the current base drag coefficient
	 * @param warnings   set in which to store possible warnings
	 * @return the base drag coefficient of the component
	 */
	public double calculateComponentBaseCD(FlightConditions conditions,
			double baseCD, WarningSet warnings) {
		return 0;
	}

	/**
	 * Calculation of Reynolds Number
	 *
	 * @param length     characteristic length
	 * @param conditions Flight conditions taken into account
	 * @return Reynolds Number
	 */
	public double calculateReynoldsNumber(double length, FlightConditions conditions) {
		return conditions.getVelocity() * length /
				conditions.getAtmosphericConditions().getKinematicViscosity();
	}

	/**
	 * Get the local flow conditions at this component's position from the
	 * shock geometry pre-pass. Returns freestream conditions if shockGeometry
	 * is null (subsonic case).
	 *
	 * @param shockGeometry the pre-computed shock geometry (may be null)
	 * @return local conditions at this component
	 */
	protected ShockGeometry.LocalConditions getLocalConditions(ShockGeometry shockGeometry) {
		if (shockGeometry == null || !shockGeometry.isSupersonic()) {
			return null;
		}
		return shockGeometry.getConditionsAt(componentAbsoluteX);
	}

	/**
	 * @return the absolute axial position of this component from the rocket nose
	 */
	public double getComponentAbsoluteX() {
		return componentAbsoluteX;
	}

}
