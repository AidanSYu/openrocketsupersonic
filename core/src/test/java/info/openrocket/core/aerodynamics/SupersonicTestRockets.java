package info.openrocket.core.aerodynamics;

import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FinSet;
import info.openrocket.core.rocketcomponent.NoseCone;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.Transition;
import info.openrocket.core.rocketcomponent.TrapezoidFinSet;
import info.openrocket.core.rocketcomponent.position.AxialMethod;

/**
 * Standard rocket geometries for supersonic aerodynamic validation.
 * <p>
 * Each geometry is chosen to isolate specific aerodynamic phenomena and to
 * enable comparison with RASAero II and analytical solutions. Dimensions are
 * representative of high-power rockets that reach supersonic speeds.
 * <p>
 * All rockets use metric units (meters, radians).
 */
public class SupersonicTestRockets {

	/**
	 * Simple conical nose cone + cylindrical body tube. No fins.
	 * <p>
	 * Isolates: nose cone wave drag, body friction drag, base drag.
	 * Cone half-angle ~9.5 deg (fineness ratio ~3).
	 *
	 * @return the rocket
	 */
	public static Rocket makeConeCylinder() {
		Rocket rocket = new Rocket();
		rocket.setName("Cone-Cylinder");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// Conical nose: length 0.15m, radius 0.025m (50mm diameter), fineness ~3
		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		// Body tube: length 0.60m, same radius as nose base
		BodyTube body = new BodyTube(0.60, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Tangent ogive nose cone + cylindrical body tube. No fins.
	 * <p>
	 * Isolates: ogive wave drag vs cone wave drag, body friction drag, base drag.
	 * Same overall dimensions as cone-cylinder for direct comparison.
	 *
	 * @return the rocket
	 */
	public static Rocket makeOgiveCylinder() {
		Rocket rocket = new Rocket();
		rocket.setName("Ogive-Cylinder");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// Tangent ogive nose: length 0.15m, radius 0.025m, shape param 1.0 = tangent ogive
		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.15, 0.025);
		nose.setShapeParameter(1.0);
		nose.setName("Ogive Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		// Body tube: same dimensions as cone-cylinder
		BodyTube body = new BodyTube(0.60, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Conical nose + cylindrical body + 4 trapezoidal fins.
	 * <p>
	 * Isolates: fin effects on drag and stability at supersonic speeds.
	 * Same body as cone-cylinder to isolate fin contribution by subtraction.
	 *
	 * @return the rocket
	 */
	public static Rocket makeConeCylinderFins() {
		Rocket rocket = new Rocket();
		rocket.setName("Cone-Cylinder-Fins");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// Same conical nose
		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 0.15, 0.025);
		nose.setName("Conical Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		// Same body tube
		BodyTube body = new BodyTube(0.60, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		// 4 trapezoidal fins at the aft end
		TrapezoidFinSet fins = new TrapezoidFinSet(4, 0.05, 0.025, 0.02, 0.04);
		fins.setThickness(0.003);
		fins.setCrossSection(FinSet.CrossSection.SQUARE);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		fins.setName("4 Fin Set");
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Tangent ogive nose + cylindrical body + boattail transition + 4 trapezoidal fins.
	 * <p>
	 * The most representative high-power rocket geometry. Tests:
	 * - Ogive wave drag
	 * - Boattail effect on base drag
	 * - Fin aerodynamics
	 * - Component interactions
	 *
	 * @return the rocket
	 */
	public static Rocket makeOgiveBoattailFins() {
		Rocket rocket = new Rocket();
		rocket.setName("Ogive-Boattail-Fins");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// Tangent ogive nose
		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 0.15, 0.025);
		nose.setShapeParameter(1.0);
		nose.setName("Ogive Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		// Main body tube (shorter to make room for boattail)
		BodyTube body = new BodyTube(0.50, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		// 4 trapezoidal fins on the body tube
		TrapezoidFinSet fins = new TrapezoidFinSet(4, 0.05, 0.025, 0.02, 0.04);
		fins.setThickness(0.003);
		fins.setCrossSection(FinSet.CrossSection.SQUARE);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		fins.setName("4 Fin Set");
		body.addChild(fins);

		// Boattail: tapers from body radius to smaller aft radius
		Transition boattail = new Transition();
		boattail.setShapeType(Transition.Shape.CONICAL);
		boattail.setForeRadiusAutomatic(true);
		boattail.setAftRadius(0.018);
		boattail.setLength(0.06);
		boattail.setThickness(0.001);
		boattail.setName("Boattail");
		stage.addChild(boattail);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Von Karman nose + cylindrical body + 3 fins.
	 * <p>
	 * Von Karman (Sears-Haack) is the minimum-drag body of revolution at
	 * supersonic speeds. Useful as a theoretical best-case comparison.
	 *
	 * @return the rocket
	 */
	public static Rocket makeVonKarmanFins() {
		Rocket rocket = new Rocket();
		rocket.setName("VonKarman-Cylinder-Fins");

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		// Von Karman (Haack series) nose
		NoseCone nose = new NoseCone(Transition.Shape.HAACK, 0.18, 0.025);
		nose.setShapeParameter(0.0); // LD-Haack (Von Karman)
		nose.setName("Von Karman Nose");
		nose.setThickness(0.002);
		stage.addChild(nose);

		// Body tube
		BodyTube body = new BodyTube(0.55, 0.025, 0.001);
		body.setName("Body Tube");
		stage.addChild(body);

		// 3 swept fins — airfoil cross-section
		TrapezoidFinSet fins = new TrapezoidFinSet(3, 0.06, 0.03, 0.025, 0.045);
		fins.setThickness(0.003);
		fins.setCrossSection(FinSet.CrossSection.AIRFOIL);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		fins.setName("3 Fin Set");
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}
}
