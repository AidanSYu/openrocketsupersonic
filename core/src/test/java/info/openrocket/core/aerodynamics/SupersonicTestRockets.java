package info.openrocket.core.aerodynamics;

import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.ExternalComponent;
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

	/**
	 * AGARD Calibration Model B.
	 * <p>
	 * Standard geometric reference for wind tunnel calibration.
	 * Body: Tangent ogive nose (L=3D) + cylinder (L=5.5D).
	 * Wing: Delta wing, span 4D, trailing edge unswept, 
	 * leading edge swept. (Exposed root chord = 2.598D, span = 1.5D).
	 * 
	 * @return the rocket
	 */
	public static Rocket makeAgardB() {
		Rocket rocket = new Rocket();
		rocket.setName("AGARD-B");
		// AEDC-TR-70-100 used a polished metal calibration article with natural
		// transition, not a painted model-rocket finish.
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		double D = 0.05; // 50mm diameter
		
		// Nose: Tangent ogive, length 3D
		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 3.0 * D, D / 2.0);
		nose.setShapeParameter(1.0);
		nose.setName("Ogive Nose");
		nose.setThickness(0.002);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		// Body: Cylinder, length 5.5D
		BodyTube body = new BodyTube(5.5 * D, D / 2.0, 0.001);
		body.setName("Body Tube");
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		// Wing: Delta planform with 2 fins
		// Exposed root chord = 2.598D, exposed semispan = 1.5D.
		// Per the AGARD model-B geometry, the wing root leading edge starts 0.507D
		// aft of the ogive-cylinder shoulder, not at the very tail.
		double rootChord = 2.598 * D;
		double span = 1.5 * D;
		double tipChord = 0.001 * D; // Near zero to approximate delta tip
		double sweepLength = rootChord - tipChord; // Unswept trailing edge

		TrapezoidFinSet fins = new TrapezoidFinSet(2, rootChord, tipChord, sweepLength, span);
		fins.setThickness(0.002); // 4% thickness based on D=0.05
		fins.setCrossSection(FinSet.CrossSection.AIRFOIL);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.TOP);
		fins.setAxialOffset(0.507 * D);
		fins.setName("Delta Wing");
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

	private static Rocket buildNacaModel(String shapeName, Transition.Shape shapeType, double lOverD, double shapeParameter) {
		Rocket rocket = new Rocket();
		rocket.setName("NACA A52H28 " + shapeName);
		// Treat the NACA calibration bodies as carefully prepared tunnel models rather
		// than ordinary HPR airframes so the benchmark can exercise the smooth-finish
		// transition path instead of the rough-airframe cap.
		rocket.setPerfectFinish(true);
		
		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		double D = 0.04445; // 1.75 inches
		NoseCone nose = new NoseCone(shapeType, lOverD * D, D / 2.0);
		if (!Double.isNaN(shapeParameter)) {
			nose.setShapeParameter(shapeParameter);
		}
		nose.setName(shapeName);
		nose.setThickness(0.002);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		rocket.enableEvents();
		return rocket;
	}
	
	public static Rocket makeNacaCone() { return buildNacaModel("Cone", Transition.Shape.POWER, 3.0, 1.0); }
	public static Rocket makeNacaParaboloid() { return buildNacaModel("Paraboloid", Transition.Shape.POWER, 3.0, 0.5); }
	public static Rocket makeNacaPowerQuarter() { return buildNacaModel("Power-Quarter", Transition.Shape.POWER, 3.0, 0.25); }
	public static Rocket makeNacaLdHaack() { return buildNacaModel("LD-Haack", Transition.Shape.HAACK, 3.0, 0.0); }
	public static Rocket makeNacaOgive() { return buildNacaModel("LV-Ogive", Transition.Shape.OGIVE, 2.93, 1.0); }

	public static Rocket makeNacaTn3393OgiveCylinder(boolean perfectFinish) {
		Rocket rocket = new Rocket();
		rocket.setName("NACA TN 3393 Ogive-Cylinder l/d=5");
		rocket.setPerfectFinish(perfectFinish);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		double d = 0.05;
		NoseCone nose = new NoseCone(Transition.Shape.OGIVE, 3.12 * d, d / 2.0);
		nose.setShapeParameter(1.0);
		nose.setName("Ogive Nose");
		nose.setThickness(0.001);
		nose.setFinish(perfectFinish ? ExternalComponent.Finish.POLISHED : ExternalComponent.Finish.NORMAL);
		stage.addChild(nose);

		BodyTube body = new BodyTube((5.0 - 3.12) * d, d / 2.0, 0.001);
		body.setName("Cylinder Afterbody");
		body.setFinish(perfectFinish ? ExternalComponent.Finish.POLISHED : ExternalComponent.Finish.NORMAL);
		stage.addChild(body);

		rocket.enableEvents();
		return rocket;
	}

	/**
	 * Basic Finner reference projectile (Army-Navy Basic Finner, ANF).
	 * <p>
	 * THE standard calibration body for missile/projectile aerodynamics.
	 * Geometry from AEDC-TR-76-58 (Jenke 1976) and ADA636861 (Dupuis &amp; Hathaway 1997):
	 * <ul>
	 *   <li>10° half-angle cone, 2.84 calibers long</li>
	 *   <li>Cylinder afterbody, 7.16 calibers long</li>
	 *   <li>Total L/D = 10.0</li>
	 *   <li>4 rectangular fins: 1.0 cal chord, 1.0 cal exposed span, 0.08 cal thick</li>
	 *   <li>Total fin span = 3.0 cal (tip-to-tip)</li>
	 *   <li>Model diameter = 30 mm (standard aeroballistic range model)</li>
	 * </ul>
	 * Fin cross-section is a flat slab (sharp LE and TE).
	 * Fins are flush with the base (trailing edge at body aft end).
	 *
	 * @return the rocket
	 */
	public static Rocket makeBasicFinner() {
		Rocket rocket = new Rocket();
		rocket.setName("Basic Finner (ADA636861)");
		// Aeroballistic range models are precision-machined stainless steel
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		double d = 0.030; // 30 mm diameter (1 caliber)

		// Cone: 10 deg half-angle, 2.84 calibers long
		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 2.84 * d, d / 2.0);
		nose.setName("10-deg Cone");
		nose.setThickness(0.001);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		// Cylinder: 7.16 calibers long
		BodyTube body = new BodyTube(7.16 * d, d / 2.0, 0.001);
		body.setName("Cylinder Afterbody");
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		// 4 rectangular fins at the tail
		// Root chord = tip chord = 1.0 cal (rectangular), exposed span = 1.0 cal
		// Sweep = 0 (rectangular planform), thickness = 0.08 cal
		// Trailing edge flush with body base
		// Cross-section: HEXAGONAL (double-wedge / sharp LE).
		// AEDC-TR-76-58 p.5: "sharp leading edges and thicknesses of 0.08 calibers."
		// Precision-machined steel fins with beveled/sharp LE → Ackeret wave drag
		// model, not the blunt-face stagnation model (SQUARE).
		double finChord = 1.0 * d;
		double finSpan = 1.0 * d;  // exposed height per fin
		double finThickness = 0.08 * d;

		TrapezoidFinSet fins = new TrapezoidFinSet(4, finChord, finChord, 0.0, finSpan);
		fins.setName("Rectangular Fins");
		fins.setCrossSection(FinSet.CrossSection.HEXAGONAL);
		fins.setThickness(finThickness);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.BOTTOM);
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

	public static Rocket makeNasaTmX653SharpConeCylinderBluntFins() {
		Rocket rocket = new Rocket();
		rocket.setName("NASA TM X-653 NSCFB");
		rocket.setPerfectFinish(true);

		AxialStage stage = new AxialStage();
		stage.setName("Sustainer");
		rocket.addChild(stage);

		double d = 0.03175; // 1.25 in

		NoseCone nose = new NoseCone(Transition.Shape.CONICAL, 2.0 * d, d / 2.0);
		nose.setName("Sharp Cone");
		nose.setThickness(0.001);
		nose.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(nose);

		BodyTube body = new BodyTube(3.37 * d, d / 2.0, 0.001);
		body.setName("Cylinder Body");
		body.setFinish(ExternalComponent.Finish.POLISHED);
		stage.addChild(body);

		TrapezoidFinSet fins = new TrapezoidFinSet(4, 1.37 * d, 0.0, 1.37 * d, 0.5 * d);
		fins.setName("Blunt Fins");
		fins.setCrossSection(FinSet.CrossSection.ROUNDED);
		fins.setThickness(0.05 * d);
		fins.setFinish(ExternalComponent.Finish.POLISHED);
		fins.setAxialMethod(AxialMethod.TOP);
		fins.setAxialOffset(body.getLength() - 1.37 * d);
		body.addChild(fins);

		rocket.enableEvents();
		return rocket;
	}

}
