package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.aerodynamics.AerodynamicForces;
import info.openrocket.core.aerodynamics.BarrowmanCalculator;
import info.openrocket.core.aerodynamics.FlightConditions;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.document.OpenRocketDocumentFactory;
import info.openrocket.core.file.DatabaseMotorFinder;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.RocketLoadException;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.rocketcomponent.*;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.CoordinateIF;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test that imports CDX1 rockets and analyzes their stability
 * characteristics (CP, CG, stability margin, CNa, CD) across Mach range.
 */
public class CDX1StabilityDiagnosticTest extends BaseTestCase {

    private OpenRocketDocument importCDX1(String filename) throws IOException, RocketLoadException {
        RASAeroLoader loader = new RASAeroLoader();

        String[] candidates = {
            "simvreal/RasAero Sims/" + filename,
            "c:/Code/OpenRocket Plus/simvreal/RasAero Sims/" + filename,
        };
        File file = null;
        for (String path : candidates) {
            File f = new File(path);
            if (f.exists()) { file = f; break; }
        }
        assertNotNull(file, "CDX1 file not found: " + filename);

        OpenRocketDocument doc = OpenRocketDocumentFactory.createEmptyRocket();
        DocumentLoadingContext context = new DocumentLoadingContext();
        context.setOpenRocketDocument(doc);
        context.setMotorFinder(new DatabaseMotorFinder());

        try (InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
            loader.loadFromStream(context, stream, filename.replace(".CDX1", ""));
        }

        WarningSet warnings = loader.getWarnings();
        if (!warnings.isEmpty()) {
            System.out.println("=== Import warnings for " + filename + " ===");
            for (Warning w : warnings) {
                System.out.println("  " + w.toString());
            }
        }
        return doc;
    }

    private void analyzeStability(OpenRocketDocument doc, String rocketName, double expectedCgIn) {
        Rocket rocket = doc.getRocket();
        FlightConfiguration config = rocket.getSelectedConfiguration();
        BarrowmanCalculator calc = new BarrowmanCalculator();

        // Print component tree
        System.out.println("\n=== COMPONENT TREE: " + rocketName + " ===");
        printTree(rocket, 0);

        // Find body diameter for caliber calculation
        double bodyDiameter = 0;
        for (RocketComponent comp : rocket) {
            if (comp instanceof BodyTube) {
                bodyDiameter = ((BodyTube) comp).getOuterRadius() * 2;
                break;
            }
        }

        // Get CG from stage override
        double cgX = expectedCgIn * 0.0254; // Use expected CG from CDX1 since override should match
        AxialStage sustainer = rocket.getStage(0);
        if (sustainer.isCGOverridden()) {
            cgX = sustainer.getOverrideCGX();
            System.out.println("CG override: " + String.format("%.4f", cgX) + " m from nose");
        }

        System.out.println("Body diameter: " + String.format("%.4f", bodyDiameter) + " m");
        System.out.println("CG position: " + String.format("%.4f", cgX) + " m");

        // Sweep Mach numbers
        double[] machNumbers = {0.3, 0.5, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.5, 2.0, 2.5, 3.0, 4.0};

        System.out.println("\nMach  | CP (m)   | CP (cal) | Margin(cal)| CNa     | CD      | Stable?");
        System.out.println("------|----------|----------|------------|---------|---------|--------");

        for (double mach : machNumbers) {
            FlightConditions conditions = new FlightConditions(config);
            conditions.setMach(mach);
            conditions.setAOA(Math.toRadians(2.0));

            WarningSet warnings = new WarningSet();

            try {
                CoordinateIF cp = calc.getCP(config, conditions, warnings);
                double cpX = cp.getX();
                double cna = cp.getWeight();

                AerodynamicForces forces = calc.getAerodynamicForces(config, conditions, warnings);
                double cd = forces.getCD();

                double marginCalibers = (cpX - cgX) / bodyDiameter;
                boolean stable = marginCalibers > 1.0;

                System.out.println(String.format("%.2f  | %.4f   | %.2f     | %+.2f       | %.4f  | %.4f  | %s",
                        mach, cpX, cpX / bodyDiameter, marginCalibers, cna, cd,
                        stable ? "YES" : "**NO**"));

                if (!warnings.isEmpty()) {
                    for (Warning w : warnings) {
                        System.out.println("  ^^ WARNING at M=" + mach + ": " + w.toString());
                    }
                }

                if (Double.isNaN(cpX) || Double.isInfinite(cpX) ||
                    Double.isNaN(cna) || Double.isInfinite(cna) ||
                    Double.isNaN(cd) || Double.isInfinite(cd)) {
                    System.out.println("  ^^ ERROR: NaN/Inf detected at Mach " + mach);
                }
            } catch (Exception e) {
                System.out.println(String.format("%.2f  | EXCEPTION: %s", mach, e.getMessage()));
                e.printStackTrace(System.out);
            }
        }

        // Per-component breakdown at Mach 2.0
        System.out.println("\n=== PER-COMPONENT at Mach 2.0 ===");
        FlightConditions conditions = new FlightConditions(config);
        conditions.setMach(2.0);
        conditions.setAOA(Math.toRadians(2.0));
        WarningSet warnings = new WarningSet();

        try {
            Map<RocketComponent, AerodynamicForces> forceMap =
                calc.getForceAnalysis(config, conditions, warnings);

            for (Map.Entry<RocketComponent, AerodynamicForces> entry : forceMap.entrySet()) {
                RocketComponent comp = entry.getKey();
                AerodynamicForces forces = entry.getValue();
                CoordinateIF cp = forces.getCP();
                if (cp != null) {
                    System.out.println(String.format("  %-40s CP=%.4f m  CNa=%+.4f  CD=%.4f",
                            comp.getClass().getSimpleName() + " \"" + comp.getName() + "\"",
                            cp.getX(), cp.getWeight(), forces.getCD()));
                }
            }
        } catch (Exception e) {
            System.out.println("  Force analysis failed: " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }

    private void printTree(RocketComponent comp, int depth) {
        String indent = "  ".repeat(depth);
        String details = "";
        if (comp instanceof NoseCone) {
            NoseCone nc = (NoseCone) comp;
            details = String.format(" [%s, L=%.3fm, R=%.4fm]",
                nc.getShapeType(), nc.getLength(), nc.getBaseRadius());
        } else if (comp instanceof BodyTube) {
            BodyTube bt = (BodyTube) comp;
            details = String.format(" [L=%.3fm, R=%.4fm]", bt.getLength(), bt.getOuterRadius());
        } else if (comp instanceof TrapezoidFinSet) {
            TrapezoidFinSet fs = (TrapezoidFinSet) comp;
            details = String.format(" [n=%d, root=%.3fm, span=%.3fm, thick=%.4fm, xsec=%s]",
                fs.getFinCount(), fs.getRootChord(), fs.getHeight(), fs.getThickness(),
                fs.getCrossSection());
        } else if (comp instanceof Transition) {
            Transition t = (Transition) comp;
            details = String.format(" [L=%.4fm, foreR=%.4fm, aftR=%.4fm]",
                t.getLength(), t.getForeRadius(), t.getAftRadius());
        } else if (comp instanceof PodSet) {
            PodSet ps = (PodSet) comp;
            details = String.format(" [instances=%d]", ps.getInstanceCount());
        }
        System.out.println(indent + comp.getClass().getSimpleName() + " \"" + comp.getName() + "\"" + details);
        for (int i = 0; i < comp.getChildCount(); i++) {
            printTree(comp.getChild(i), depth + 1);
        }
    }

    @Test
    public void testN5800Stability() throws Exception {
        OpenRocketDocument doc = importCDX1("DontDebateThisN5800MinDia.CDX1");
        analyzeStability(doc, "N5800 MinDia", 64.69);
    }

    @Test
    public void testEZI65Stability() throws Exception {
        OpenRocketDocument doc = importCDX1("EZI65-1.CDX1");
        analyzeStability(doc, "EZI65", 59.0);
    }

    @Test
    public void testTorrentStability() throws Exception {
        OpenRocketDocument doc = importCDX1("Torrent.CDX1");
        analyzeStability(doc, "Torrent", 66.5);
    }
}
