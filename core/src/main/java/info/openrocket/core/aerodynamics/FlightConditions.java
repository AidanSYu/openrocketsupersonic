package info.openrocket.core.aerodynamics;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.EventObject;
import java.util.List;

import info.openrocket.core.models.atmosphere.AtmosphericConditions;
import info.openrocket.core.rocketcomponent.FlightConfiguration;
import info.openrocket.core.util.BugException;
import info.openrocket.core.util.ChangeSource;
import info.openrocket.core.util.Coordinate;
import info.openrocket.core.util.CoordinateIF;
import info.openrocket.core.util.MathUtil;
import info.openrocket.core.util.Monitorable;
import info.openrocket.core.util.StateChangeListener;
import info.openrocket.core.util.ModID;

/**
 * A class defining the momentary flight conditions of a rocket, including
 * the angle of attack, lateral wind angle, atmospheric conditions etc.
 * 
 * @author Sampo Niskanen <sampo.niskanen@iki.fi>
 */
public class FlightConditions implements Cloneable, ChangeSource, Monitorable {
	/** Lower edge of transonic smoothing band (Mach). */
	private static final double TRANSONIC_LOW = 0.95;
	/** Upper edge of transonic smoothing band (Mach). */
	private static final double TRANSONIC_HIGH = 1.05;

	private List<EventListener> listenerList = new ArrayList<>();
	private EventObject event = new EventObject(this);

	/** Reference length used in calculations. */
	private double refLength = 1.0;

	/** Reference area used in calculations. */
	private double refArea = Math.PI * 0.25;

	/** Angle of attack. */
	private double aoa = 0;

	/** Sine of the angle of attack. */
	private double sinAOA = 0;

	/**
	 * The fraction <code>sin(aoa) / aoa</code>. At an AOA of zero this value
	 * must be one. This value may be used in many cases to avoid checking for
	 * division by zero.
	 */
	private double sincAOA = 1.0;

	/** Lateral wind direction. */
	private double theta = 0;

	/** Current Mach speed. */
	private double mach = 0.3;

	/**
	 * Sqrt(1 - M^2) for M<1
	 * Sqrt(M^2 - 1) for M>1
	 */
	private double beta = calculateBeta(mach);

	/**
	 * Current thrust level as a fraction of maximum thrust [0, 1].
	 * Used by the drag calculator to apply power-on base drag reduction.
	 * A value of 0 means coasting (unpowered); a value > 0 means motor is burning.
	 */
	private double thrustLevel = 0;

	/**
	 * Nozzle exit area to base area ratio [0, 1].
	 * Used for power-on base drag computation. If unavailable, set to NaN
	 * and a default reduction factor will be used.
	 */
	private double nozzleAreaRatio = Double.NaN;

	/**
	 * When true, the skin-friction model forces a fully-turbulent boundary
	 * layer from the nose tip (no laminar run). Mirrors RASAero II
	 * {@code Turbulence=True} semantics and is set by the simulation stepper
	 * from {@link info.openrocket.core.simulation.SimulationConditions}.
	 */
	private boolean forceTurbulentBL = false;

	/** Current roll rate. */
	private double rollRate = 0;

	/** Current pitch rate. */
	private double pitchRate = 0;
	/** Current yaw rate. */
	private double yawRate = 0;

	private CoordinateIF pitchCenter = Coordinate.NUL;

	private AtmosphericConditions atmosphericConditions = new AtmosphericConditions();

	// Plume-induced separation state (Phase 9e)
	// These are set by the simulation stepper when motor is burning at altitude.
	private boolean plumeActive = false;
	private double plumeDiameter = 0;       // Effective plume diameter (m)
	private double baseDiameter = 0;        // Rocket base diameter (m)
	private double separationLength = 0;    // Axial length of separated region (m)

	private ModID modID;

	/**
	 * Sole constructor. The reference length is initialized to the reference length
	 * of the <code>Configuration</code>, and the reference area accordingly.
	 * If <code>config</code> is <code>null</code>, then the reference length is set
	 * to 1 meter.
	 * 
	 * @param config the configuration of which the reference length is taken.
	 */
	public FlightConditions(FlightConfiguration config) {
		if (config != null)
			setRefLength(config.getReferenceLength());
	}

	/**
	 * Set the reference length from the given configuration.
	 * 
	 * @param config the configuration from which to get the reference length.
	 */
	public void setReference(FlightConfiguration config) {
		setRefLength(config.getReferenceLength());
	}

	/**
	 * Set the reference length and area.
	 * fires change event
	 */
	public void setRefLength(double length) {
		if (refLength == length)
			return;
		
		refLength = length;
		refArea = Math.PI * MathUtil.pow2(length / 2);

		fireChangeEvent();
	}

	/**
	 * @return the reference length.
	 */
	public double getRefLength() {
		return refLength;
	}

	/**
	 * Set the reference area and length.
	 * fires change event
	 */
	public void setRefArea(double area) {
		if (refArea == area)
			return;
		
		refArea = area;
		refLength = MathUtil.safeSqrt(area / Math.PI) * 2;

		fireChangeEvent();
	}

	/**
	 * @return the reference area.
	 */
	public double getRefArea() {
		return refArea;
	}

	/**
	 * Sets the angle of attack. It calculates values also for the methods
	 * {@link #getSinAOA()} and {@link #getSincAOA()}.
	 * fires change event if it's different from previous value
	 * 
	 * @param aoa the angle of attack.
	 */
	public void setAOA(double aoa) {
		aoa = MathUtil.clamp(aoa, 0, Math.PI);
		if (MathUtil.equals(this.aoa, aoa))
			return;

		this.aoa = aoa;
		if (aoa < 0.001) {
			this.sinAOA = aoa;
			this.sincAOA = 1.0;
		} else {
			this.sinAOA = Math.sin(aoa);
			this.sincAOA = sinAOA / aoa;
		}

		fireChangeEvent();
	}

	/**
	 * Sets the angle of attack with the sine. The value <code>sinAOA</code> is
	 * assumed
	 * to be the sine of <code>aoa</code> for cases in which this value is known.
	 * The AOA must still be specified, as the sine is not unique in the range
	 * of 0..180 degrees.
	 * fires change event if it's different from previous value
	 * 
	 * @param aoa    the angle of attack in radians.
	 * @param sinAOA the sine of the angle of attack.
	 */
	public void setAOA(double aoa, double sinAOA) {
		aoa = MathUtil.clamp(aoa, 0, Math.PI);
		sinAOA = MathUtil.clamp(sinAOA, 0, 1);
		if (MathUtil.equals(this.aoa, aoa))
			return;

		assert (Math.abs(Math.sin(aoa) - sinAOA) < 0.0001) : "Illegal sine: aoa=" + aoa + " sinAOA=" + sinAOA;

		this.aoa = aoa;
		this.sinAOA = sinAOA;
		if (aoa < 0.001) {
			this.sincAOA = 1.0;
		} else {
			this.sincAOA = sinAOA / aoa;
		}

		fireChangeEvent();
	}

	/**
	 * @return the angle of attack.
	 */
	public double getAOA() {
		return aoa;
	}

	/**
	 * @return the sine of the angle of attack.
	 */
	public double getSinAOA() {
		return sinAOA;
	}

	/**
	 * @return the sinc of the angle of attack (sin(AOA) / AOA). This method returns
	 *         one if the angle of attack is zero.
	 */
	public double getSincAOA() {
		return sincAOA;
	}

	/**
	 * Set the direction of the lateral airflow.
	 * fires change event if it's different from previous value
	 * 
	 */
	public void setTheta(double theta) {
		if (MathUtil.equals(this.theta, theta))
			return;
		this.theta = theta;

		fireChangeEvent();
	}

	/**
	 * @return the direction of the lateral airflow.
	 */
	public double getTheta() {
		return theta;
	}

	/**
	 * Set the current Mach speed. This should be (but is not required to be) in
	 * reference to the speed of sound of the atmospheric conditions.
	 * 
	 * fires change event if it's different from previous value
	 */
	public void setMach(double mach) {
		mach = Math.max(mach, 0);
		if (MathUtil.equals(this.mach, mach))
			return;

		this.mach = mach;
		this.beta = calculateBeta(mach);

		fireChangeEvent();
	}

	/**
	 * @return the current Mach speed.
	 */
	public double getMach() {
		return mach;
	}

	/**
	 * Returns the current rocket velocity, calculated from the Mach number and the
	 * speed of sound. If either of these parameters are changed, the velocity
	 * changes
	 * as well.
	 * 
	 * @return the velocity of the rocket.
	 */
	public double getVelocity() {
		return mach * atmosphericConditions.getMachSpeed();
	}

	/**
	 * Sets the Mach speed according to the given velocity and the current speed of
	 * sound.
	 * 
	 * @param velocity the current velocity.
	 */
	public void setVelocity(double velocity) {
		if (atmosphericConditions.getMachSpeed() < 1e-6) {
			setMach(0);
		} else {
			setMach(velocity / atmosphericConditions.getMachSpeed());
		}
	}

	/**
	 * @return sqrt(abs(1 - Mach^2)). This is calculated in the setting call and is
	 *         therefore fast.
	 */
	public double getBeta() {
		return beta;
	}

	/**
	 * Calculate the Prandtl-Glauert compressibility factor beta.
	 * <p>
	 * Subsonic: beta = sqrt(1 - M²)  (Prandtl-Glauert)
	 * Supersonic: beta = sqrt(M² - 1) (Ackeret)
	 * Transonic (M 0.95–1.05): cubic Hermite spline giving C1-continuous
	 * blending with a positive floor (no zero crossing).
	 *
	 * @param mach the Mach number
	 * @return the beta value (always > 0)
	 */
	private static double calculateBeta(double mach) {
		if (mach < TRANSONIC_LOW) {
			// Subsonic: exact Prandtl-Glauert
			return Math.sqrt(1.0 - mach * mach);
		} else if (mach > TRANSONIC_HIGH) {
			// Supersonic: exact Ackeret
			return Math.sqrt(mach * mach - 1.0);
		} else {
			// Transonic smoothing via cubic Hermite spline.
			// Endpoint values and slopes from the exact formulas:
			//   f(ML) = sqrt(1 - ML²),  f'(ML) = -ML / sqrt(1 - ML²)
			//   f(MH) = sqrt(MH² - 1),  f'(MH) =  MH / sqrt(MH² - 1)
			double fLo = Math.sqrt(1.0 - TRANSONIC_LOW * TRANSONIC_LOW);
			double fHi = Math.sqrt(TRANSONIC_HIGH * TRANSONIC_HIGH - 1.0);
			double dfLo = -TRANSONIC_LOW / fLo;   // negative slope (beta decreasing)
			double dfHi = TRANSONIC_HIGH / fHi;    // positive slope (beta increasing)

			double dm = TRANSONIC_HIGH - TRANSONIC_LOW;
			double t = (mach - TRANSONIC_LOW) / dm;
			double t2 = t * t;
			double t3 = t2 * t;

			// Hermite basis functions
			double h00 = 2 * t3 - 3 * t2 + 1;
			double h10 = t3 - 2 * t2 + t;
			double h01 = -2 * t3 + 3 * t2;
			double h11 = t3 - t2;

			return h00 * fLo + h10 * dm * dfLo + h01 * fHi + h11 * dm * dfHi;
		}
	}

	/**
	 * @return the current roll rate.
	 */
	public double getRollRate() {
		return rollRate;
	}

	/**
	 * Set the current roll rate.
	 * fires change event if it's different from previous
	 */
	public void setRollRate(double rate) {
		if (MathUtil.equals(this.rollRate, rate))
			return;

		this.rollRate = rate;
		
		fireChangeEvent();
	}

	/**
	 * 
	 * @return current pitch rate
	 */
	public double getPitchRate() {
		return pitchRate;
	}

	/**
	 * sets the pitch rate
	 * fires change event if it's different from previous
	 * 
	 * @param pitchRate
	 */
	public void setPitchRate(double pitchRate) {
		if (MathUtil.equals(this.pitchRate, pitchRate))
			return;
		this.pitchRate = pitchRate;
		fireChangeEvent();
	}

	/**
	 * 
	 * @return current yaw rate
	 */
	public double getYawRate() {
		return yawRate;
	}

	public void setYawRate(double yawRate) {
		if (MathUtil.equals(this.yawRate, yawRate))
			return;
		this.yawRate = yawRate;
		fireChangeEvent();
	}

	/**
	 * @return the pitchCenter
	 */
	public CoordinateIF getPitchCenter() {
		return pitchCenter;
	}

	/**
	 * @param pitchCenter the pitchCenter to set
	 */
	public void setPitchCenter(CoordinateIF pitchCenter) {
		if (this.pitchCenter.equals(pitchCenter))
			return;
		this.pitchCenter = pitchCenter;
		fireChangeEvent();
	}

	/**
	 * Return the current atmospheric conditions. Note that this method returns a
	 * reference to the {@link AtmosphericConditions} object used by this object.
	 * Changes made to the object will modify the encapsulated object, but will NOT
	 * generate change events.
	 * 
	 * @return the current atmospheric conditions.
	 */
	public AtmosphericConditions getAtmosphericConditions() {
		return atmosphericConditions;
	}

	/**
	 * Set the current atmospheric conditions. This method will fire a change event
	 * if a change occurs.
	 */
	public void setAtmosphericConditions(AtmosphericConditions cond) {
		if (atmosphericConditions.equals(cond))
			return;

		atmosphericConditions = cond;
		fireChangeEvent();
	}

	// --- Power-on base drag accessors (Phase 6b) ---

	/**
	 * Set the current thrust level as a fraction of maximum thrust.
	 *
	 * @param level thrust fraction in [0, 1]; 0 = coasting, 1 = full thrust
	 */
	public void setThrustLevel(double level) {
		level = MathUtil.clamp(level, 0, 1);
		if (MathUtil.equals(this.thrustLevel, level))
			return;
		this.thrustLevel = level;
		fireChangeEvent();
	}

	/**
	 * @return the current thrust level as a fraction of maximum thrust [0, 1].
	 */
	public double getThrustLevel() {
		return thrustLevel;
	}

	/**
	 * @return true if the motor is currently producing thrust.
	 */
	public boolean isPowered() {
		return thrustLevel > 0;
	}

	/**
	 * Set the nozzle exit area to base area ratio.
	 *
	 * @param ratio area ratio in [0, 1], or NaN if unavailable
	 */
	public void setNozzleAreaRatio(double ratio) {
		if (Double.isNaN(ratio) && Double.isNaN(this.nozzleAreaRatio))
			return;
		if (MathUtil.equals(this.nozzleAreaRatio, ratio))
			return;
		this.nozzleAreaRatio = ratio;
		fireChangeEvent();
	}

	/**
	 * @return the nozzle exit area to base area ratio, or NaN if unavailable.
	 */
	public double getNozzleAreaRatio() {
		return nozzleAreaRatio;
	}

	/**
	 * @return whether the skin-friction model should force a fully-turbulent
	 *         boundary layer (RASAero {@code Turbulence=True} semantics).
	 */
	public boolean isForceTurbulentBL() {
		return forceTurbulentBL;
	}

	/**
	 * Set the force-turbulent-BL flag for this flight state. When
	 * {@code true}, the skin-friction calculator bypasses the mixed
	 * laminar/transitional smooth-plate model and treats the boundary layer as
	 * fully turbulent from x=0.
	 */
	public void setForceTurbulentBL(boolean forceTurbulentBL) {
		if (this.forceTurbulentBL == forceTurbulentBL)
			return;
		this.forceTurbulentBL = forceTurbulentBL;
		fireChangeEvent();
	}

	// --- Plume-induced separation accessors (Phase 9e) ---

	/** Whether plume-induced flow separation is active. */
	public boolean isPlumeActive() {
		return plumeActive;
	}

	/** Set plume separation state. */
	public void setPlumeState(boolean active, double plumeDiam, double baseDiam, double sepLength) {
		this.plumeActive = active;
		this.plumeDiameter = plumeDiam;
		this.baseDiameter = baseDiam;
		this.separationLength = sepLength;
	}

	/** Clear plume state (no plume effects). */
	public void clearPlumeState() {
		this.plumeActive = false;
		this.plumeDiameter = 0;
		this.baseDiameter = 0;
		this.separationLength = 0;
	}

	public double getPlumeDiameter() {
		return plumeDiameter;
	}

	public double getPlumeBaseDiameter() {
		return baseDiameter;
	}

	public double getSeparationLength() {
		return separationLength;
	}

	/**
	 * Retrieve the modification count of this object.
	 *
	 * @return modification ID
	 */
	@Override
	public ModID getModID() {
		return modID;
	}

	@Override
	public String toString() {
		return String.format("FlightConditions[" +
				"aoa=%.2f\u00b0," +
				"theta=%.2f\u00b0," +
				"mach=%.3f," +
				"rollRate=%.2f," +
				"pitchRate=%.2f," +
				"yawRate=%.2f," +
				"refLength=%.3f," +
				"pitchCenter=" + pitchCenter.toString() + "," +
				"atmosphericConditions=" + atmosphericConditions.toString() +
				"]",
				aoa * 180 / Math.PI, theta * 180 / Math.PI, mach, rollRate, pitchRate, yawRate, refLength);
	}

	/**
	 * @return a copy of the flight conditions. The copy has no listeners. The
	 *         atmospheric conditions is also cloned.
	 */
	@Override
	public FlightConditions clone() {
		try {
			FlightConditions cond = (FlightConditions) super.clone();
			cond.listenerList = new ArrayList<>();
			cond.event = new EventObject(cond);
			cond.atmosphericConditions = atmosphericConditions.clone();
			return cond;
		} catch (CloneNotSupportedException e) {
			throw new BugException("clone not supported!", e);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!(obj instanceof FlightConditions))
			return false;

		FlightConditions other = (FlightConditions) obj;

		return (MathUtil.equals(this.refLength, other.refLength) &&
				MathUtil.equals(this.aoa, other.aoa) &&
				MathUtil.equals(this.theta, other.theta) &&
				MathUtil.equals(this.mach, other.mach) &&
				MathUtil.equals(this.rollRate, other.rollRate) &&
				MathUtil.equals(this.pitchRate, other.pitchRate) &&
				MathUtil.equals(this.yawRate, other.yawRate) &&
				MathUtil.equals(this.thrustLevel, other.thrustLevel) &&
				this.pitchCenter.equals(other.pitchCenter)
				&& this.atmosphericConditions.equals(other.atmosphericConditions));
	}

	@Override
	public int hashCode() {
		return (int) (1000 * (refLength + aoa + theta + mach + rollRate + pitchRate + yawRate));
	}

	@Override
	public void addChangeListener(StateChangeListener listener) {
		listenerList.add(0, listener);
	}

	@Override
	public void removeChangeListener(StateChangeListener listener) {
		listenerList.remove(listener);
	}

	/**
	 * wake up call to listeners
	 */
	protected void fireChangeEvent() {
		modID = new ModID();
		
		// Copy the list before iterating to prevent concurrent modification exceptions.
		EventListener[] listeners = listenerList.toArray(new EventListener[0]);
		for (EventListener l : listeners) {
			if (l instanceof StateChangeListener) {
				((StateChangeListener) l).stateChanged(event);
			}
		}
	}
}
