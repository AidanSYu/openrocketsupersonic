package info.openrocket.core.simulation;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Tests for Phase 9b: 6-DOF Simulation State and Quaternion Math.
 *
 * Validates quaternion operations, frame transformations, Euler's equations,
 * state vector management, and angular acceleration computation.
 */
public class SixDOFSimulationTest {

	private static final double EPSILON = 1e-10;

	// --- Quaternion basic operations ---

	@Test
	@DisplayName("Quaternion identity: [1,0,0,0] * q = q")
	void testQuaternionIdentity() {
		double[] identity = {1, 0, 0, 0};
		double[] q = {0.5, 0.5, 0.5, 0.5};

		double[] result = SixDOFState.quaternionMultiply(identity, q);

		assertArrayEquals(q, result, EPSILON, "Identity * q should equal q");
	}

	@Test
	@DisplayName("Quaternion multiply: q * q_conj = identity")
	void testQuaternionMultiplyConjugate() {
		double[] q = {0.5, 0.5, 0.5, 0.5}; // Already unit quaternion
		double[] qConj = SixDOFState.quaternionConjugate(q);
		double[] result = SixDOFState.quaternionMultiply(q, qConj);

		assertEquals(1.0, result[0], EPSILON, "q * q* should have w=1");
		assertEquals(0.0, result[1], EPSILON, "q * q* should have x=0");
		assertEquals(0.0, result[2], EPSILON, "q * q* should have y=0");
		assertEquals(0.0, result[3], EPSILON, "q * q* should have z=0");
	}

	@Test
	@DisplayName("Quaternion normalize: preserves direction, unit length")
	void testQuaternionNormalize() {
		double[] q = {2, 0, 0, 0};
		SixDOFState.quaternionNormalize(q);

		double norm = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
		assertEquals(1.0, norm, EPSILON, "Normalized quaternion should have unit length");
		assertEquals(1.0, q[0], EPSILON, "Direction should be preserved");

		// Non-trivial quaternion
		double[] q2 = {1, 1, 1, 1};
		SixDOFState.quaternionNormalize(q2);
		norm = Math.sqrt(q2[0] * q2[0] + q2[1] * q2[1] + q2[2] * q2[2] + q2[3] * q2[3]);
		assertEquals(1.0, norm, EPSILON);
		assertEquals(0.5, q2[0], EPSILON);
	}

	@Test
	@DisplayName("Quaternion normalize: zero quaternion returns identity")
	void testQuaternionNormalizeZero() {
		double[] q = {0, 0, 0, 0};
		SixDOFState.quaternionNormalize(q);

		assertEquals(1.0, q[0], EPSILON, "Zero quaternion normalized should be identity");
		assertEquals(0.0, q[1], EPSILON);
	}

	// --- Frame transformations ---

	@Test
	@DisplayName("Body to inertial with identity quaternion: no rotation")
	void testBodyToInertialIdentity() {
		double[] qIdentity = {1, 0, 0, 0};
		double[] vBody = {1, 2, 3};

		double[] vInertial = SixDOFState.bodyToInertial(qIdentity, vBody);

		assertArrayEquals(vBody, vInertial, EPSILON,
				"Identity quaternion should not change the vector");
	}

	@Test
	@DisplayName("Inertial to body with identity quaternion: no rotation")
	void testInertialToBodyIdentity() {
		double[] qIdentity = {1, 0, 0, 0};
		double[] vInertial = {1, 2, 3};

		double[] vBody = SixDOFState.inertialToBody(qIdentity, vInertial);

		assertArrayEquals(vInertial, vBody, EPSILON,
				"Identity quaternion should not change the vector");
	}

	@Test
	@DisplayName("Body to inertial followed by inertial to body gives original vector")
	void testRoundTripTransformation() {
		// 90 degree rotation about Z axis: q = [cos(45), 0, 0, sin(45)]
		double angle = Math.PI / 4; // half angle for quaternion
		double[] q = {Math.cos(angle), 0, 0, Math.sin(angle)};

		double[] vOriginal = {1, 0, 0};
		double[] vInertial = SixDOFState.bodyToInertial(q, vOriginal);
		double[] vBack = SixDOFState.inertialToBody(q, vInertial);

		assertArrayEquals(vOriginal, vBack, EPSILON,
				"Round-trip transformation should return original vector");
	}

	@Test
	@DisplayName("90-degree rotation about Z axis transforms X to Y")
	void test90DegRotationZ() {
		// Quaternion for 90 deg about Z: q = [cos(45°), 0, 0, sin(45°)]
		double halfAngle = Math.PI / 4;
		double[] q = {Math.cos(halfAngle), 0, 0, Math.sin(halfAngle)};

		double[] vBody = {1, 0, 0};
		double[] vInertial = SixDOFState.bodyToInertial(q, vBody);

		assertEquals(0.0, vInertial[0], EPSILON, "X should map to ~0");
		assertEquals(1.0, vInertial[1], EPSILON, "Y should map to ~1");
		assertEquals(0.0, vInertial[2], EPSILON, "Z should remain 0");
	}

	@Test
	@DisplayName("Rotation preserves vector length")
	void testRotationPreservesLength() {
		double[] q = {0.5, 0.5, 0.5, 0.5}; // unit quaternion (120 deg about [1,1,1])
		double[] v = {3, 4, 5};

		double[] vRot = SixDOFState.bodyToInertial(q, v);
		double origLen = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
		double rotLen = Math.sqrt(vRot[0] * vRot[0] + vRot[1] * vRot[1] + vRot[2] * vRot[2]);

		assertEquals(origLen, rotLen, EPSILON, "Rotation should preserve vector length");
	}

	// --- Quaternion derivative ---

	@Test
	@DisplayName("Quaternion derivative: zero angular velocity gives zero derivative")
	void testQuaternionDerivativeZeroOmega() {
		double[] q = {1, 0, 0, 0};
		double[] omega = {0, 0, 0};

		double[] dq = SixDOFState.quaternionDerivative(q, omega);

		assertEquals(0, dq[0], EPSILON);
		assertEquals(0, dq[1], EPSILON);
		assertEquals(0, dq[2], EPSILON);
		assertEquals(0, dq[3], EPSILON);
	}

	@Test
	@DisplayName("Quaternion derivative: rotation about Z axis")
	void testQuaternionDerivativeZRotation() {
		double[] q = {1, 0, 0, 0}; // identity
		double omegaZ = 2.0; // rad/s
		double[] omega = {0, 0, omegaZ};

		double[] dq = SixDOFState.quaternionDerivative(q, omega);

		// dq/dt = 0.5 * q * [0, 0, 0, omega_z]
		// For identity q: dq = [0, 0, 0, omega_z/2]
		assertEquals(0, dq[0], EPSILON);
		assertEquals(0, dq[1], EPSILON);
		assertEquals(0, dq[2], EPSILON);
		assertEquals(omegaZ / 2.0, dq[3], EPSILON);
	}

	// --- Euler's equations ---

	@Test
	@DisplayName("Angular acceleration: zero moments and zero omega gives zero alpha")
	void testAngularAccelerationZero() {
		double[] omega = {0, 0, 0};
		double[] moments = {0, 0, 0};
		double[] inertia = {1, 1, 0.5};

		double[] alpha = SixDOFState.computeAngularAcceleration(omega, moments, inertia);

		assertArrayEquals(new double[]{0, 0, 0}, alpha, EPSILON);
	}

	@Test
	@DisplayName("Angular acceleration: pure torque about X axis")
	void testAngularAccelerationPureTorque() {
		double[] omega = {0, 0, 0};
		double Mx = 10.0; // N*m
		double[] moments = {Mx, 0, 0};
		double Ix = 2.0;
		double[] inertia = {Ix, 2.0, 1.0};

		double[] alpha = SixDOFState.computeAngularAcceleration(omega, moments, inertia);

		assertEquals(Mx / Ix, alpha[0], EPSILON, "Angular accel about X should be M/I");
		assertEquals(0, alpha[1], EPSILON);
		assertEquals(0, alpha[2], EPSILON);
	}

	@Test
	@DisplayName("Angular acceleration: gyroscopic coupling (symmetric rocket)")
	void testGyroscopicCoupling() {
		// For a symmetric rocket with Ix = Iy != Iz, spinning about Z:
		// If omega = [0, 0, wz], then omega x (I*omega) = 0 for symmetric case
		double[] omega = {0, 0, 10.0};
		double[] moments = {0, 0, 0};
		double[] inertia = {2.0, 2.0, 1.0}; // symmetric about Z

		double[] alpha = SixDOFState.computeAngularAcceleration(omega, moments, inertia);

		// For pure spin about Z with symmetric body, there is no gyroscopic coupling
		assertEquals(0, alpha[0], EPSILON);
		assertEquals(0, alpha[1], EPSILON);
		assertEquals(0, alpha[2], EPSILON);
	}

	@Test
	@DisplayName("Angular acceleration: gyroscopic precession for asymmetric spin")
	void testGyroscopicPrecession() {
		// Spinning about Z with a perturbation in X
		double wz = 10.0;
		double wx = 0.1;
		double[] omega = {wx, 0, wz};
		double[] moments = {0, 0, 0};
		double Ix = 2.0, Iy = 2.0, Iz = 1.0;
		double[] inertia = {Ix, Iy, Iz};

		double[] alpha = SixDOFState.computeAngularAcceleration(omega, moments, inertia);

		// From Euler's eqs: domega_y = (M_y - (Ix - Iz) * wz * wx) / Iy
		// With M_y = 0: domega_y = -(Ix - Iz) * wz * wx / Iy = -(2-1)*10*0.1/2 = -0.5
		assertEquals(0, alpha[0], EPSILON);
		assertEquals(-(Ix - Iz) * wz * wx / Iy, alpha[1], EPSILON,
				"Gyroscopic precession in Y");
		assertEquals(0, alpha[2], EPSILON);
	}

	// --- Full inertia tensor ---

	@Test
	@DisplayName("Full inertia tensor: diagonal case matches simplified version")
	void testFullInertiaDiagonal() {
		double[] omega = {1, 2, 3};
		double[] moments = {10, 20, 30};
		double[] diagInertia = {5, 8, 3};

		// Full tensor (diagonal)
		double[] fullI = {
			5, 0, 0,
			0, 8, 0,
			0, 0, 3
		};

		double[] alphaSimple = SixDOFState.computeAngularAcceleration(omega, moments, diagInertia);
		double[] alphaFull = SixDOFState.computeAngularAccelerationFull(omega, moments, fullI);

		assertArrayEquals(alphaSimple, alphaFull, 1e-8,
				"Full tensor with diagonal values should match simplified computation");
	}

	// --- State vector ---

	@Test
	@DisplayName("State vector round-trip")
	void testStateVectorRoundTrip() {
		SixDOFState state = new SixDOFState();
		state.setPosition(new double[]{1, 2, 3});
		state.setVelocity(new double[]{4, 5, 6});
		state.setQuaternion(new double[]{0.5, 0.5, 0.5, 0.5});
		state.setAngularVelocity(new double[]{0.1, 0.2, 0.3});

		double[] sv = state.toStateVector();
		assertEquals(13, sv.length);

		SixDOFState state2 = new SixDOFState();
		state2.fromStateVector(sv);

		assertArrayEquals(state.getPosition(), state2.getPosition(), EPSILON);
		assertArrayEquals(state.getVelocity(), state2.getVelocity(), EPSILON);
		assertArrayEquals(state.getQuaternion(), state2.getQuaternion(), EPSILON);
		assertArrayEquals(state.getAngularVelocity(), state2.getAngularVelocity(), EPSILON);
	}

	@Test
	@DisplayName("State clone produces independent copy")
	void testStateClone() {
		SixDOFState state = new SixDOFState();
		state.setPosition(new double[]{1, 2, 3});
		state.setVelocity(new double[]{4, 5, 6});
		state.setQuaternion(new double[]{1, 0, 0, 0});
		state.setAngularVelocity(new double[]{0.1, 0.2, 0.3});

		SixDOFState copy = state.clone();

		// Modify original
		state.setPosition(new double[]{10, 20, 30});

		// Copy should be unaffected
		assertArrayEquals(new double[]{1, 2, 3}, copy.getPosition(), EPSILON,
				"Clone should be independent of original");
	}

	@Test
	@DisplayName("Compute derivative: stationary state gives zero translational derivative")
	void testDerivativeStationary() {
		SixDOFState state = new SixDOFState();
		state.setQuaternion(new double[]{1, 0, 0, 0});

		double[] forces = {0, 0, 0};
		double[] moments = {0, 0, 0};
		double mass = 1.0;
		double[] inertia = {1, 1, 0.5};

		double[] deriv = state.computeDerivative(forces, moments, mass, inertia);

		// All zeros for stationary state
		for (int i = 0; i < 13; i++) {
			assertEquals(0.0, deriv[i], EPSILON,
					"Stationary state derivative should be zero at index " + i);
		}
	}

	@Test
	@DisplayName("Compute derivative: constant velocity propagates position")
	void testDerivativeConstantVelocity() {
		SixDOFState state = new SixDOFState();
		state.setVelocity(new double[]{10, 0, 0});
		state.setQuaternion(new double[]{1, 0, 0, 0});

		double[] forces = {0, 0, 0};
		double[] moments = {0, 0, 0};
		double mass = 1.0;
		double[] inertia = {1, 1, 0.5};

		double[] deriv = state.computeDerivative(forces, moments, mass, inertia);

		// dpos/dt = velocity
		assertEquals(10.0, deriv[0], EPSILON, "dx/dt should equal vx");
		assertEquals(0.0, deriv[1], EPSILON);
		assertEquals(0.0, deriv[2], EPSILON);

		// dvel/dt = 0 (no forces)
		assertEquals(0.0, deriv[3], EPSILON);
		assertEquals(0.0, deriv[4], EPSILON);
		assertEquals(0.0, deriv[5], EPSILON);
	}

	@Test
	@DisplayName("Compute derivative: force produces acceleration F/m")
	void testDerivativeForceAcceleration() {
		SixDOFState state = new SixDOFState();
		state.setQuaternion(new double[]{1, 0, 0, 0});

		double Fx = 100.0; // N
		double mass = 10.0; // kg
		double[] forces = {Fx, 0, 0};
		double[] moments = {0, 0, 0};
		double[] inertia = {1, 1, 0.5};

		double[] deriv = state.computeDerivative(forces, moments, mass, inertia);

		assertEquals(Fx / mass, deriv[3], EPSILON, "dvx/dt should equal Fx/m");
	}

	@Test
	@DisplayName("Angular momentum conserved in coast phase (zero torques)")
	void testAngularMomentumConservation() {
		// Simulate a few Euler steps with no external torques
		// For a symmetric body spinning about Z, angular momentum should be conserved
		double[] omega = {0, 0, 5.0}; // 5 rad/s about Z
		double[] inertia = {2.0, 2.0, 1.0};

		// L = I * omega
		double Lz_initial = inertia[2] * omega[2];

		// Integrate for 100 small steps
		double dt = 0.001;
		double[] currentOmega = omega.clone();
		for (int i = 0; i < 100; i++) {
			double[] alpha = SixDOFState.computeAngularAcceleration(
					currentOmega, new double[]{0, 0, 0}, inertia);
			currentOmega[0] += alpha[0] * dt;
			currentOmega[1] += alpha[1] * dt;
			currentOmega[2] += alpha[2] * dt;
		}

		double Lz_final = inertia[2] * currentOmega[2];
		assertEquals(Lz_initial, Lz_final, 1e-6,
				"Angular momentum should be conserved with zero torques");
	}
}
