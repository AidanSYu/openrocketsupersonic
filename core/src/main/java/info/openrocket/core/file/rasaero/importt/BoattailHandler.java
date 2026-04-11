package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.rocketcomponent.Transition;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * A handler for the boattail element in a RASAero CDX1 file.
 *
 * <p>A boattail is an OpenRocket {@link Transition} appended directly after the
 * last {@link BodyTube} in the parent stage. The previous implementation wrapped
 * the Transition in an inline PodSet child of the BodyTube, ostensibly to support
 * recessed boattails (BoattailOffset ≠ 0). However:
 * <ul>
 *   <li>BoattailOffset is never parsed by any import handler and was always 0 in
 *       practice, so the PodSet wrapper provided no benefit.
 *   <li>The PodSet topology placed the Transition outside the main body chain,
 *       causing {@link info.openrocket.core.aerodynamics.ShockGeometry} to miss
 *       the rear compression, leading to Cm blow-up and trajectory divergence on
 *       rockets with aggressive boat-tails (e.g. Proteus 6, −92% apogee error).
 *   <li>{@code SymmetricComponent.getNextSymmetricComponent()} also did not
 *       traverse into the pod, causing base-drag double-counting on the BodyTube.
 * </ul>
 * The fix: add the Transition as a direct child of the parent stage (sibling of
 * the BodyTube), which is the standard OpenRocket topology for a boat-tail.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public class BoattailHandler extends TransitionHandler {

    /**
     * Constructor
     *
     * @param context  current document loading context
     * @param parent   parent component (sustainer/stage) to add this new component to
     * @param warnings warning set to add import warnings to
     * @throws IllegalArgumentException if the parent component is null or has no children
     */
    public BoattailHandler(DocumentLoadingContext context, RocketComponent parent, WarningSet warnings)
            throws IllegalArgumentException {
        super(context);

        if (parent == null) {
            throw new IllegalArgumentException("The parent component of a boattail may not be null.");
        }
        if (parent.getChildCount() == 0) {
            throw new IllegalArgumentException("The parent component of a boattail must have at least one child.");
        }

        // Add the Transition directly to the parent stage as a sibling after the
        // last body tube. This keeps it in the main body chain so that ShockGeometry,
        // getNextSymmetricComponent(), and base-drag chaining all see it correctly.
        RocketComponent lastChild = parent.getChild(parent.getChildCount() - 1);
        if (lastChild instanceof BodyTube) {
            parent.addChild(this.transition);
        } else if (lastChild instanceof Transition) {
            // Boattail after another transition — still a direct sibling.
            parent.addChild(this.transition);
        } else {
            throw new IllegalArgumentException(
                    "Cannot add boattail after component of type " + lastChild.getClass().getName());
        }

        this.transition.setAftRadiusAutomatic(false);
        this.transition.setShapeType(Transition.Shape.CONICAL); // RASAero only supports conical boattails
        this.transition.setName("Boattail");
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
            throws SAXException {
        if (RASAeroCommonConstants.FIN.equals(element)) {
            return new FinHandler(this.transition, warnings);
        }
        return super.openElement(element, attributes, warnings);
    }
}
