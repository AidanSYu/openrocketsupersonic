package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.document.OpenRocketDocument;
import info.openrocket.core.file.DocumentLoadingContext;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.file.simplesax.AbstractElementHandler;
import info.openrocket.core.file.simplesax.ElementHandler;
import info.openrocket.core.file.simplesax.PlainTextHandler;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;
import info.openrocket.core.simulation.SimulationOptions;
import org.xml.sax.SAXException;

import java.util.HashMap;

/**
 * A SAX handler for a RASAeroDocument document.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public class RASAeroHandler extends AbstractElementHandler {
    /**
     * The main content handler.
     */
    private RocketDocumentHandler handler = null;

    private final DocumentLoadingContext context;
    private final String rocketName;

    public RASAeroHandler(DocumentLoadingContext context, String rocketName) {
        super();
        this.context = context;
        this.rocketName = rocketName;
    }

    /**
     * Return the OpenRocketDocument read from the file, or <code>null</code> if a
     * document
     * has not been read yet.
     *
     * @return the document read, or null.
     */
    public OpenRocketDocument getDocument() {
        return context.getOpenRocketDocument();
    }

    @Override
    public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
            throws SAXException {
        // Check for unknown elements
        if (!RASAeroCommonConstants.RASAERO_DOCUMENT.equals(element)) {
            warnings.add("Unknown element " + element + " in RASAeroDocument, ignoring.");
            return null;
        }

        // Check for first call
        if (handler != null) {
            warnings.add("Multiple document elements found, ignoring later ones.");
            return null;
        }

        handler = new RocketDocumentHandler(context, rocketName);
        return handler;
    }

    /**
     * A SAX handler for the RASAeroDocument element.
     */
    private static class RocketDocumentHandler extends AbstractElementHandler {
        /**
         * The DocumentLoadingContext
         */
        private final DocumentLoadingContext context;

        /**
         * The top-level component, from which all child components are added.
         */
        private final Rocket rocket;

        /**
         * The RASAero launch site settings to be used for all the OpenRocket
         * simulations.
         */
        private final SimulationOptions launchSiteSettings = new SimulationOptions();

        public RocketDocumentHandler(DocumentLoadingContext context, String rocketName) {
            super();
            this.context = context;
            this.rocket = context.getOpenRocketDocument().getRocket();
            this.rocket.setName(rocketName);
            final AxialStage stage = new AxialStage(); // The first stage in RASAero is not explicitly defined, so add
                                                       // it here
            stage.setName("Sustainer");
            this.rocket.addChild(stage);
        }

        @Override
        public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
                throws SAXException {
            // File version
            if (RASAeroCommonConstants.FILE_VERSION.equals(element)) {
                return PlainTextHandler.INSTANCE;
            }
            // Rocket design
            else if (RASAeroCommonConstants.ROCKET_DESIGN.equals(element)) {
                return new RocketDesignHandler(context, rocket.getChild(0), launchSiteSettings);
            }
            // LaunchSite
            else if (RASAeroCommonConstants.LAUNCH_SITE.equals(element)) {
                return new LaunchSiteHandler(launchSiteSettings);
            }
            // Recovery
            else if (RASAeroCommonConstants.RECOVERY.equals(element)) {
                return new RecoveryHandler(rocket);
            }
            // SimulationList
            else if (RASAeroCommonConstants.SIMULATION_LIST.equals(element)) {
                return new SimulationListHandler(context, rocket, launchSiteSettings);
            }

            return null;
        }

        @Override
        public void closeElement(String element, HashMap<String, String> attributes, String content,
                WarningSet warnings) throws SAXException {
        }
    }

    /**
     * A SAX handler for the RocketDesign element.
     */
    private static class RocketDesignHandler extends AbstractElementHandler {
        /**
         * The DocumentLoadingContext
         */
        private final DocumentLoadingContext context;

        /**
         * The top-level component, from which all child components are added.
         */
        private final RocketComponent component;

        /**
         * Shared simulation options used as the template for every imported
         * RASAero simulation. RocketDesign-level flags (e.g. Turbulence) apply
         * to all simulations from this file, so we set them here and let
         * {@link SimulationHandler} copy them into each per-simulation
         * {@link SimulationOptions} instance.
         */
        private final SimulationOptions launchSiteSettings;

        public RocketDesignHandler(DocumentLoadingContext context, RocketComponent component,
                SimulationOptions launchSiteSettings) {
            super();
            this.context = context;
            this.component = component;
            this.launchSiteSettings = launchSiteSettings;
        }

        @Override
        public ElementHandler openElement(String element, HashMap<String, String> attributes, WarningSet warnings)
                throws SAXException {
            // Nose cone
            if (RASAeroCommonConstants.NOSE_CONE.equals(element)) {
                return new NoseConeHandler(context, component, warnings);
            }
            // Body tube
            else if (RASAeroCommonConstants.BODY_TUBE.equals(element)) {
                return new BodyTubeHandler(context, component, warnings);
            }
            // Transition
            else if (RASAeroCommonConstants.TRANSITION.equals(element)) {
                return new TransitionHandler(context, component, warnings);
            }
            // Fin can
            else if (RASAeroCommonConstants.FIN_CAN.equals(element)) {
                return new FinCanHandler(context, component);
            }
            // Booster
            else if (RASAeroCommonConstants.BOOSTER.equals(element)) {
                return new BoosterHandler(context, component);
            }
            // BoatTail
            else if (RASAeroCommonConstants.BOATTAIL.equals(element)) {
                return new BoattailHandler(context, component, warnings);
            }

            // Surface finish
            else if (RASAeroCommonConstants.SURFACE_FINISH.equals(element)) {
                return PlainTextHandler.INSTANCE;
            }

            // Imported silently before; keep parsing text so we can emit explicit
            // warnings when the file requests unsupported behavior.
            else if (RASAeroCommonConstants.MODIFIED_BARROWMAN.equals(element)
                    || RASAeroCommonConstants.TURBULENCE.equals(element)
                    || RASAeroCommonConstants.SUSTAINER_NOZZLE.equals(element)
                    || RASAeroCommonConstants.BOOSTER1_NOZZLE.equals(element)
                    || RASAeroCommonConstants.BOOSTER2_NOZZLE.equals(element)) {
                return PlainTextHandler.INSTANCE;
            }

            // Comments
            else if (RASAeroCommonConstants.COMMENTS.equals(element)) {
                return PlainTextHandler.INSTANCE;
            }

            // warnings.add("Unknown element " + element + " in RocketDesign, ignoring.");
            return null;
        }

        @Override
        public void closeElement(String element, HashMap<String, String> attributes, String content,
                WarningSet warnings) throws SAXException {
            // Surface finish
            if (RASAeroCommonConstants.SURFACE_FINISH.equals(element)) {
                SurfaceFinishHandler.setSurfaceFinishes(component.getRocket(), content, warnings);
            }
            else if (RASAeroCommonConstants.MODIFIED_BARROWMAN.equals(element)) {
                warnIfTrue(warnings, element, content);
            }
            else if (RASAeroCommonConstants.TURBULENCE.equals(element)) {
                if (Boolean.parseBoolean(content)) {
                    // Honor RASAero's Turbulence=True by forcing a fully-turbulent
                    // boundary layer in the ORP skin-friction model. Applies to
                    // every simulation generated from this CDX1 via
                    // launchSiteSettings, which SimulationHandler copies into
                    // each sim's own SimulationOptions.
                    if (launchSiteSettings != null) {
                        launchSiteSettings.setForceTurbulentBL(true);
                    }
                    warnings.add("RASAero " + element + "=True honored: forcing fully-turbulent boundary layer"
                            + " in skin-friction model.");
                }
            }
            else if (RASAeroCommonConstants.SUSTAINER_NOZZLE.equals(element)
                    || RASAeroCommonConstants.BOOSTER1_NOZZLE.equals(element)
                    || RASAeroCommonConstants.BOOSTER2_NOZZLE.equals(element)) {
                noteDesignLevelNozzle(warnings, element, content);
            }
            // Comments
            else if (RASAeroCommonConstants.COMMENTS.equals(element)) {
                component.getRocket().setComment(content);
            }
        }

        private void warnIfTrue(WarningSet warnings, String element, String content) {
            if (Boolean.parseBoolean(content)) {
                warnings.add("Ignoring unsupported RASAero setting " + element + "=" + content + ".");
            }
        }

        private void noteDesignLevelNozzle(WarningSet warnings, String element, String content) {
            try {
                if (Math.abs(Double.parseDouble(content)) > 1.0e-12) {
                    warnings.add("RASAero design-level " + element + "=" + content
                            + " noted; per-simulation nozzle diameter fields are imported when present.");
                }
            } catch (NumberFormatException ignored) {
                // Numeric-format validation is handled elsewhere if the value is consumed.
            }
        }
    }
}
