package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * A handler for the RASAero {@code <FinCan>} element.
 *
 * <p>A RASAero fin can is a body tube with fins that slides over the aft end of
 * the parent body tube. In the CDX1 geometry this is expressed as an inline
 * sleeve that overlaps the last portion of the parent tube (with {@code Offset}
 * typically equal to {@code -Length}).
 *
 * <p>The previous implementation modeled this by wrapping the fin can tube and
 * its front shoulder in an inline {@link info.openrocket.core.rocketcomponent.PodSet}
 * child of the parent {@link BodyTube}. That topology caused several problems:
 * <ul>
 *   <li>The pod literally overlapped its parent body tube, tripping
 *       {@code Warning.PODSET_OVERLAP} in
 *       {@link info.openrocket.core.aerodynamics.BarrowmanStabilityCalculator}
 *       on every sim step.</li>
 *   <li>{@link info.openrocket.core.aerodynamics.ShockGeometry} did not traverse
 *       into the inline pod, so the shoulder area step was invisible to the
 *       supersonic pre-pass.</li>
 *   <li>{@code SymmetricComponent.getNextSymmetricComponent()} also skipped the
 *       pod, leaving the body tube's base drag miscounted.</li>
 *   <li>Downstream drag accumulation saw the overlapping cross-sections as
 *       duplicated area, producing absurd clamped coefficients at launch
 *       (CD ≈ 10 at M &lt; 0.1 on the MESOS 293K flight, −40% apogee error).</li>
 * </ul>
 *
 * <p>The fix mirrors the one already in {@link BoattailHandler}: we keep the
 * components in the standard linear body chain as siblings of the parent body
 * tube. To make room for the shoulder + fin-can tube without changing the
 * overall aft station, we shorten the parent body tube by
 * {@code (shoulderLength + finCanLength)}. The result is an equivalent outer
 * surface (body tube → shoulder ramp → thicker sleeve with fins) with no
 * overlap and a single continuous symmetric-component chain for the aero
 * calculators to walk.
 *
 * @author Sibo Van Gool &lt;sibo.vangool@hotmail.com&gt;
 */
public class FinCanHandler extends BodyTubeHandler {
    private final RocketComponent parent;
    private final BodyTube parentBodyTube;

    private double insideDiameter;
    private double shoulderLength;

    public FinCanHandler(DocumentLoadingContext context, RocketComponent parent) {
        super(context);
        if (parent == null) {
            throw new IllegalArgumentException("The parent component of a fin can may not be null.");
        }
        if (parent.getChildCount() == 0) {
            throw new IllegalArgumentException("There is no component to attach the fin can to.");
        }
        RocketComponent lastChild = parent.getChild(parent.getChildCount() - 1);
        if (!(lastChild instanceof BodyTube)) {
            throw new IllegalArgumentException("The parent component of a fin can must be a body tube.");
        }

        this.parent = parent;
        this.parentBodyTube = (BodyTube) lastChild;
        this.bodyTube.setName("Fin Can");
        // The fin-can tube is deliberately NOT attached to the component tree
        // yet; we wait until endHandler() so we can shorten the parent body
        // tube and insert the shoulder as a sibling in one consistent step.
        // Fin children added via BodyTubeHandler.openElement() still attach
        // to this.bodyTube correctly — they'll be carried along when we wire
        // the tube into the stage.
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
            throws SAXException {
        // Handle FinCan-specific elements that BodyTubeHandler doesn't know about
        if (RASAeroCommonConstants.INSIDE_DIAMETER.equals(element)
                || RASAeroCommonConstants.SHOULDER_LENGTH.equals(element)) {
            return PlainTextHandler.INSTANCE;
        }
        return super.openElement(element, attributes, warnings);
    }

    @Override
    public void closeElement(String element, HashMap<String, String> attributes, String content, WarningSet warnings)
            throws SAXException {
        super.closeElement(element, attributes, content, warnings);
        try {
            if (RASAeroCommonConstants.INSIDE_DIAMETER.equals(element)) {
                insideDiameter = Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;
            } else if (RASAeroCommonConstants.SHOULDER_LENGTH.equals(element)) {
                shoulderLength = Double.parseDouble(content) / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH;
            }
        } catch (NumberFormatException nfe) {
            warnings.add("Could not convert " + element + " value of " + content + ".  It is expected to be a number.");
        }
    }

    @Override
    public void endHandler(String element, HashMap<String, String> attributes, String content, WarningSet warnings)
            throws SAXException {
        super.endHandler(element, attributes, content, warnings);
        this.bodyTube.setOuterRadiusAutomatic(false);
        // Re-apply the correct outer radius. BodyTubeHandler.endHandler() calls
        // setOuterRadius(correct) then setOuterRadiusAutomatic(true) then setThickness(0.002).
        // setThickness() calls getMaxRadius() → getAftRadius() → getOuterRadius(), which —
        // while autoRadius=true and the tube is still unparented — invokes getAutoOuterRadius()
        // and writes DEFAULT_RADIUS (0.025 m) back into the outerRadius field, corrupting it.
        // Explicitly restoring the correct value here fixes the corruption.
        if (diameter != null && diameter > 0) {
            this.bodyTube.setOuterRadius(diameter / 2.0 / RASAeroCommonConstants.OPENROCKET_TO_RASAERO_LENGTH);
        }

        // Build the front shoulder transition (body OD → fin can OD).
        Transition shoulder = new Transition();
        shoulder.setName("Fin Can Shoulder");
        shoulder.setShapeType(Transition.Shape.CONICAL);
        shoulder.setForeRadiusAutomatic(false);
        shoulder.setAftRadiusAutomatic(false);
        shoulder.setLength(shoulderLength);
        shoulder.setForeRadius(insideDiameter / 2);
        shoulder.setAftRadius(bodyTube.getOuterRadius());
        shoulder.setThickness(bodyTube.getThickness());
        shoulder.setColor(bodyTube.getColor());

        // Shorten the parent body tube to make room for the shoulder + fin-can
        // tube. In the CDX1, RASAero expresses a fin can as an inline sleeve
        // over the aft end of the body tube; in OR's linear body chain we
        // replace that aft portion of the parent tube with the explicit
        // shoulder + fin-can-tube siblings so the aft station is preserved.
        double sleeveLength = shoulderLength + this.bodyTube.getLength();
        double newParentLength = parentBodyTube.getLength() - sleeveLength;
        if (newParentLength < 0) {
            warnings.add("Fin can (length " + sleeveLength
                    + " m) is longer than the parent body tube (length "
                    + parentBodyTube.getLength() + " m); clamping to 0.");
            newParentLength = 0;
        }
        parentBodyTube.setLength(newParentLength);

        // Attach shoulder and fin-can tube as siblings of the parent body tube
        // in the stage. The fins already added to this.bodyTube during parsing
        // ride along in the subtree.
        parent.addChild(shoulder);
        parent.addChild(this.bodyTube);
    }
}
