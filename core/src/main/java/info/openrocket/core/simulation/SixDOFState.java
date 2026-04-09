package info.openrocket.core.simulation;

/**
 * Full 6-DOF state vector for rocket simulation.
 *
 * Contains 13 state variables:
 *   Position:        3 (inertial frame, meters)
 *   Velocity:        3 (inertial frame, m/s)
 *   Quaternion:      4 (body-to-inertial orientation)
 *   Angular velocity: 3 (body frame, rad/s)
 *
 * Quaternion convention: q = [w, x, y, z] where w is the scalar part.
 * The quaternion transforms body-frame vectors to inertial-frame vectors.
 *
 * Euler's equations for rotation:
 *   [I] * d(omega)/dt + omega x ([I]*omega) = M_external
 *
 * Quaternion derivative:
 *   dq/dt = 0.5 * q (x) [0, omega_x, omega_y, omega_z]
 *
 * where (x) denotes quaternion multiplication.
 */
public class SixDOFState implements Cloneable {

	// Position in inertial (world) frame
	private double[] position = new double[3];

	// Velocity in inertial (world) frame
	private double[] velocity = new double[3];

	// Orientation quaternion (body-to-inertial): [w, x, y, z]
	private double[] quaternion = {1.0, 0.0, 0.0, 0.0};

	// Angular velocity in body frame
	private double[] angularVelocity = new double[3];

	// --- Constructors ---

	public SixDOFState() {
	}

	public SixDOFState(double[] pos, double[] vel, double[] quat, double[] omega) {
		setPosition(pos);
		setVelocity(vel);
		setQuaternion(quat);
		setAngularVelocity(omega);
	}

	// --- Accessors ---

	public double[] getPosition() {
		return position.clone();
	}

	public void setPosition(double[] pos) {
		System.arraycopy(pos, 0, this.position, 0, 3);
	}

	public double[] getVelocity() {
		return velocity.clone();
	}

	public void setVelocity(double[] vel) {
		System.arraycopy(vel, 0, this.velocity, 0, 3);
	}

	public double[] getQuaternion() {
		return quaternion.clone();
	}

	public void setQuaternion(double[] quat) {
		System.arraycopy(quat, 0, this.quaternion, 0, 4);
	}

	public double[] getAngularVelocity() {
		return angularVelocity.clone();
	}

	public void setAngularVelocity(double[] omega) {
		System.arraycopy(omega, 0, this.angularVelocity, 0, 3);
	}

	// --- Quaternion operations ---

	/**
	 * Multiply two quaternions: result = q1 * q2
	 * Convention: [w, x, y, z]
	 *
	 * @param q1 left quaternion
	 * @param q2 right quaternion
	 * @return product quaternion
	 */
	public static double[] quaternionMultiply(double[] q1, double[] q2) {
		double w1 = q1[0], x1 = q1[1], y1 = q1[2], z1 = q1[3];
		double w2 = q2[0], x2 = q2[1], y2 = q2[2], z2 = q2[3];

		return new double[]{
			w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
			w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
			w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
			w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2
		};
	}

	/**
	 * Normalize a quaternion to unit length.
	 *
	 * @param q quaternion to normalize (modified in place and returned)
	 * @return the normalized quaternion (same array)
	 */
	public static double[] quaternionNormalize(double[] q) {
		double norm = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
		if (norm < 1e-15) {
			q[0] = 1.0;
			q[1] = q[2] = q[3] = 0.0;
			return q;
		}
		double inv = 1.0 / norm;
		q[0] *= inv;
		q[1] *= inv;
		q[2] *= inv;
		q[3] *= inv;
		return q;
	}

	/**
	 * Conjugate of a quaternion: q* = [w, -x, -y, -z]
	 *
	 * @param q input quaternion
	 * @return conjugate quaternion
	 */
	public static double[] quaternionConjugate(double[] q) {
		return new double[]{q[0], -q[1], -q[2], -q[3]};
	}

	/**
	 * Rotate a body-frame vector to the inertial frame using the orientation quaternion.
	 * v_inertial = q * [0, v_body] * q*
	 *
	 * @param q      orientation quaternion (body-to-inertial)
	 * @param vBody  vector in body frame [x, y, z]
	 * @return vector in inertial frame [x, y, z]
	 */
	public static double[] bodyToInertial(double[] q, double[] vBody) {
		// q * v * q*  where v = [0, vx, vy, vz]
		double[] vQuat = {0.0, vBody[0], vBody[1], vBody[2]};
		double[] qConj = quaternionConjugate(q);
		double[] temp = quaternionMultiply(q, vQuat);
		double[] result = quaternionMultiply(temp, qConj);
		return new double[]{result[1], result[2], result[3]};
	}

	/**
	 * Rotate an inertial-frame vector to the body frame.
	 * v_body = q* * [0, v_inertial] * q
	 *
	 * @param q         orientation quaternion (body-to-inertial)
	 * @param vInertial vector in inertial frame [x, y, z]
	 * @return vector in body frame [x, y, z]
	 */
	public static double[] inertialToBody(double[] q, double[] vInertial) {
		double[] vQuat = {0.0, vInertial[0], vInertial[1], vInertial[2]};
		double[] qConj = quaternionConjugate(q);
		double[] temp = quaternionMultiply(qConj, vQuat);
		double[] result = quaternionMultiply(temp, q);
		return new double[]{result[1], result[2], result[3]};
	}

	/**
	 * Compute the quaternion time derivative given angular velocity in body frame.
	 *
	 * dq/dt = 0.5 * q * [0, omega_x, omega_y, omega_z]
	 *
	 * @param q     current orientation quaternion
	 * @param omega angular velocity in body frame [wx, wy, wz]
	 * @return quaternion derivative [dw, dx, dy, dz]
	 */
	public static double[] quaternionDerivative(double[] q, double[] omega) {
		double[] omegaQuat = {0.0, omega[0], omega[1], omega[2]};
		double[] product = quaternionMultiply(q, omegaQuat);
		return new double[]{
			0.5 * product[0],
			0.5 * product[1],
			0.5 * product[2],
			0.5 * product[3]
		};
	}

	/**
	 * Compute the angular acceleration from Euler's equations.
	 *
	 * [I] * domega/dt = M - omega x ([I]*omega)
	 *
	 * For diagonal inertia tensor [Ix, Iy, Iz]:
	 *   domega_x/dt = (M_x - (Iz - Iy) * wy * wz) / Ix
	 *   domega_y/dt = (M_y - (Ix - Iz) * wz * wx) / Iy
	 *   domega_z/dt = (M_z - (Iy - Ix) * wx * wy) / Iz
	 *
	 * @param omega    angular velocity in body frame [wx, wy, wz]
	 * @param moments  external moments in body frame [Mx, My, Mz]
	 * @param inertia  diagonal inertia tensor [Ix, Iy, Iz]
	 * @return angular acceleration in body frame [dw_x, dw_y, dw_z]
	 */
	public static double[] computeAngularAcceleration(double[] omega, double[] moments,
			double[] inertia) {
		double wx = omega[0], wy = omega[1], wz = omega[2];
		double Ix = inertia[0], Iy = inertia[1], Iz = inertia[2];

		if (Ix <= 0 || Iy <= 0 || Iz <= 0) {
			return new double[]{0, 0, 0};
		}

		double domegaX = (moments[0] - (Iz - Iy) * wy * wz) / Ix;
		double domegaY = (moments[1] - (Ix - Iz) * wz * wx) / Iy;
		double domegaZ = (moments[2] - (Iy - Ix) * wx * wy) / Iz;

		return new double[]{domegaX, domegaY, domegaZ};
	}

	/**
	 * Compute angular acceleration with full (non-diagonal) inertia tensor.
	 *
	 * [I] * domega/dt = M - omega x ([I]*omega)
	 *
	 * The inertia tensor is stored as a 3x3 symmetric matrix in row-major order.
	 *
	 * @param omega    angular velocity in body frame [wx, wy, wz]
	 * @param moments  external moments in body frame [Mx, My, Mz]
	 * @param I        3x3 inertia tensor in row-major order (9 elements)
	 * @return angular acceleration in body frame [dw_x, dw_y, dw_z]
	 */
	public static double[] computeAngularAccelerationFull(double[] omega, double[] moments,
			double[] I) {
		double wx = omega[0], wy = omega[1], wz = omega[2];

		// I * omega
		double Iw0 = I[0] * wx + I[1] * wy + I[2] * wz;
		double Iw1 = I[3] * wx + I[4] * wy + I[5] * wz;
		double Iw2 = I[6] * wx + I[7] * wy + I[8] * wz;

		// omega x (I * omega)
		double cx = wy * Iw2 - wz * Iw1;
		double cy = wz * Iw0 - wx * Iw2;
		double cz = wx * Iw1 - wy * Iw0;

		// RHS = M - omega x (I * omega)
		double rhs0 = moments[0] - cx;
		double rhs1 = moments[1] - cy;
		double rhs2 = moments[2] - cz;

		// Solve [I] * domega = rhs using Cramer's rule for 3x3 system
		double det = I[0] * (I[4] * I[8] - I[5] * I[7])
				   - I[1] * (I[3] * I[8] - I[5] * I[6])
				   + I[2] * (I[3] * I[7] - I[4] * I[6]);

		if (Math.abs(det) < 1e-30) {
			return new double[]{0, 0, 0};
		}

		double invDet = 1.0 / det;

		double domegaX = invDet * (rhs0 * (I[4] * I[8] - I[5] * I[7])
								 - I[1] * (rhs1 * I[8] - I[5] * rhs2)
								 + I[2] * (rhs1 * I[7] - I[4] * rhs2));

		double domegaY = invDet * (I[0] * (rhs1 * I[8] - I[5] * rhs2)
								 - rhs0 * (I[3] * I[8] - I[5] * I[6])
								 + I[2] * (I[3] * rhs2 - rhs1 * I[6]));

		double domegaZ = invDet * (I[0] * (I[4] * rhs2 - rhs1 * I[7])
								 - I[1] * (I[3] * rhs2 - rhs1 * I[6])
								 + rhs0 * (I[3] * I[7] - I[4] * I[6]));

		return new double[]{domegaX, domegaY, domegaZ};
	}

	/**
	 * Get the full 13-element state vector: [pos(3), vel(3), quat(4), omega(3)]
	 *
	 * @return state vector of length 13
	 */
	public double[] toStateVector() {
		double[] state = new double[13];
		System.arraycopy(position, 0, state, 0, 3);
		System.arraycopy(velocity, 0, state, 3, 3);
		System.arraycopy(quaternion, 0, state, 6, 4);
		System.arraycopy(angularVelocity, 0, state, 10, 3);
		return state;
	}

	/**
	 * Set state from a 13-element state vector.
	 *
	 * @param state state vector of length 13
	 */
	public void fromStateVector(double[] state) {
		System.arraycopy(state, 0, position, 0, 3);
		System.arraycopy(state, 3, velocity, 0, 3);
		System.arraycopy(state, 6, quaternion, 0, 4);
		System.arraycopy(state, 10, angularVelocity, 0, 3);
	}

	/**
	 * Compute the state derivative given external forces and moments.
	 *
	 * @param forces         external forces in inertial frame [Fx, Fy, Fz] (N)
	 * @param moments        external moments in body frame [Mx, My, Mz] (N*m)
	 * @param mass           total mass (kg)
	 * @param inertia        diagonal inertia tensor [Ix, Iy, Iz] (kg*m^2)
	 * @return state derivative as 13-element array
	 */
	public double[] computeDerivative(double[] forces, double[] moments,
			double mass, double[] inertia) {
		double[] deriv = new double[13];

		// dpos/dt = velocity
		deriv[0] = velocity[0];
		deriv[1] = velocity[1];
		deriv[2] = velocity[2];

		// dvel/dt = forces / mass
		if (mass > 0) {
			deriv[3] = forces[0] / mass;
			deriv[4] = forces[1] / mass;
			deriv[5] = forces[2] / mass;
		}

		// dquat/dt = 0.5 * q * omega_quat
		double[] dq = quaternionDerivative(quaternion, angularVelocity);
		deriv[6] = dq[0];
		deriv[7] = dq[1];
		deriv[8] = dq[2];
		deriv[9] = dq[3];

		// domega/dt = Euler's equations
		double[] domega = computeAngularAcceleration(angularVelocity, moments, inertia);
		deriv[10] = domega[0];
		deriv[11] = domega[1];
		deriv[12] = domega[2];

		return deriv;
	}

	@Override
	public SixDOFState clone() {
		try {
			SixDOFState copy = (SixDOFState) super.clone();
			// Deep-copy the arrays so the clone is independent
			copy.position = this.position.clone();
			copy.velocity = this.velocity.clone();
			copy.quaternion = this.quaternion.clone();
			copy.angularVelocity = this.angularVelocity.clone();
			return copy;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("clone not supported", e);
		}
	}
}
