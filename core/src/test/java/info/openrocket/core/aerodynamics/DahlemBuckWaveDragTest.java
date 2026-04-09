package info.openrocket.core.aerodynamics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import info.openrocket.core.aerodynamics.barrowman.DahlemBuckShapeFactors;
import info.openrocket.core.rocketcomponent.Transition;

/**
 * Tests for the Dahlem-Buck semi-empirical wave drag shape correction factors.
 * Validates shape factors, fineness ratio correction, and consistency with
 * the conical reference case.
 */
public class DahlemBuckWaveDragTest {

    private static final double EPSILON = 1e-6;

    @Test
    public void shapeFactorForConeIsOne() {
        // Conical shape is the reference: K_shape must be exactly 1.0
        double k = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.CONICAL, 0, 2.0);
        assertEquals(1.0, k, EPSILON, "Conical K_shape should be 1.0 at any Mach");

        // Also at higher Mach (Mach dependence applies to base factor of 1.0)
        double kHighMach = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.CONICAL, 0, 4.0);
        // With Mach dependence: 1.0 * (1.0 + 0.03 * 2.5) = 1.075
        assertTrue(kHighMach >= 1.0, "Conical K_shape should be >= 1.0 at high Mach");
    }

    @Test
    public void vonKarmanDragLowerThanCone() {
        // Von Karman (HAACK with param=0) is a minimum-drag shape: K_shape < 1.0
        double kVonKarman = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.HAACK, 0, 2.0);
        assertTrue(kVonKarman < 1.0,
                "Von Karman K_shape (" + kVonKarman + ") should be less than 1.0");
        assertTrue(kVonKarman > 0.4,
                "Von Karman K_shape (" + kVonKarman + ") should not be unreasonably low");
    }

    @Test
    public void ogiveDragLowerThanCone() {
        // Ogive produces less wave drag than an equivalent cone
        double kOgive = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.OGIVE, 1.0, 2.0);
        double kCone = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.CONICAL, 0, 2.0);
        assertTrue(kOgive < kCone,
                "Ogive K_shape (" + kOgive + ") should be less than cone (" + kCone + ")");
    }

    @Test
    public void shapeFactorOrdering() {
        // At the same Mach, Von Karman should have lower drag than ogive, which is lower than cone
        double mach = 2.0;
        double kVonKarman = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.HAACK, 0, mach);
        double kOgive = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.OGIVE, 1.0, mach);
        double kCone = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.CONICAL, 0, mach);

        assertTrue(kVonKarman < kOgive,
                "Von Karman (" + kVonKarman + ") should have lower K than ogive (" + kOgive + ")");
        assertTrue(kOgive < kCone,
                "Ogive (" + kOgive + ") should have lower K than cone (" + kCone + ")");
    }

    @Test
    public void finenessRatioCorrectionAtReference() {
        // At the reference fineness ratio of 3, correction should be 1.0
        double corr = DahlemBuckShapeFactors.finenessRatioCorrection(3.0);
        assertEquals(1.0, corr, EPSILON, "Correction at f=3 should be 1.0");
    }

    @Test
    public void finenessRatioCorrectionSlender() {
        // Slender nose (f=5) should have lower drag: correction < 1.0
        double corr = DahlemBuckShapeFactors.finenessRatioCorrection(5.0);
        assertTrue(corr < 1.0,
                "Slender nose (f=5) correction (" + corr + ") should be < 1.0");
        assertTrue(corr > 0.2,
                "Slender nose correction should not be unreasonably small");
    }

    @Test
    public void finenessRatioCorrectionBlunt() {
        // Blunt nose (f=1.5) should have higher drag: correction > 1.0
        double corr = DahlemBuckShapeFactors.finenessRatioCorrection(1.5);
        assertTrue(corr > 1.0,
                "Blunt nose (f=1.5) correction (" + corr + ") should be > 1.0");
    }

    @Test
    public void machDependenceIsSmall() {
        // K_shape should vary by less than 15% from M=1.5 to M=5.0 for any shape
        Transition.Shape[] shapes = {
                Transition.Shape.OGIVE, Transition.Shape.POWER,
                Transition.Shape.PARABOLIC, Transition.Shape.HAACK
        };
        double[] params = { 1.0, 0.5, 0.75, 0.0 };

        for (int i = 0; i < shapes.length; i++) {
            double kLow = DahlemBuckShapeFactors.getShapeFactor(shapes[i], params[i], 1.5);
            double kHigh = DahlemBuckShapeFactors.getShapeFactor(shapes[i], params[i], 5.0);
            double variation = Math.abs(kHigh - kLow) / kLow;
            assertTrue(variation < 0.15,
                    "K_shape variation for " + shapes[i] + " is " + (variation * 100)
                            + "%, should be < 15%");
        }
    }

    @Test
    public void dahlemBuckMatchesConeReferenceForConicalShape() {
        // For a conical shape at the reference fineness ratio of 3, Dahlem-Buck
        // should produce the same result as the cone reference (K=1, fCorr=1)
        double kCone = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.CONICAL, 0, 2.0);
        double fCorr = DahlemBuckShapeFactors.finenessRatioCorrection(3.0);

        // Product should be 1.0: no modification to cone drag
        assertEquals(1.0, kCone * fCorr, EPSILON,
                "Conical shape at f=3 should produce unity correction factor");
    }

    @Test
    public void equivalentConeHalfAngle() {
        // For a nose with length=3, radius=1: atan(1/3) ~ 18.43 degrees
        double angle = DahlemBuckShapeFactors.getEquivalentConeHalfAngle(3.0, 1.0);
        assertEquals(Math.atan(1.0 / 3.0), angle, EPSILON);

        // Flat face (length=0): should return pi/2
        double flatAngle = DahlemBuckShapeFactors.getEquivalentConeHalfAngle(0, 1.0);
        assertEquals(Math.PI / 2, flatAngle, EPSILON);
    }

    @Test
    public void powerShapeFactorVariesWithExponent() {
        // Power-law: exponent 1.0 should give factor close to conical (1.0)
        // exponent 0.5 should give lower factor
        double mach = 2.0;
        double kPow1 = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.POWER, 1.0, mach);
        double kPow05 = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.POWER, 0.5, mach);
        double kPow0 = DahlemBuckShapeFactors.getShapeFactor(Transition.Shape.POWER, 0.0, mach);

        assertTrue(kPow1 > kPow05, "Power n=1.0 should have higher K than n=0.5");
        assertTrue(kPow05 > kPow0, "Power n=0.5 should have higher K than n=0.0");
    }
}
