package info.openrocket.core.file.rasaero.importt;

import info.openrocket.core.logging.WarningSet;
import info.openrocket.core.file.rasaero.RASAeroCommonConstants;
import info.openrocket.core.rocketcomponent.ExternalComponent;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.rocketcomponent.RocketComponent;

/**
 * Applies the RASAero surface finish to all components.
 *
 * @author Sibo Van Gool <sibo.vangool@hotmail.com>
 */
public abstract class SurfaceFinishHandler {
    public static void setSurfaceFinishes(Rocket rocket, String finish, WarningSet warnings) {
        ExternalComponent.Finish surfaceFinish = RASAeroCommonConstants.RASAERO_TO_OPENROCKET_SURFACE(finish, warnings);
        for (RocketComponent component : rocket) {
            if (component instanceof ExternalComponent) {
                ((ExternalComponent) component).setFinish(surfaceFinish);
            }
        }

        // Plumb the RASAero surface finish into Rocket.setPerfectFinish() so the
        // boundary-layer transition model (laminar fraction) uses the right cap.
        // Without this, every CDX1 import defaulted to perfectFinish=false and
        // hit the 5 %-laminar cap in BarrowmanDragCalculator — systematically
        // over-predicting friction drag on clean / competition rockets that
        // RASAero labels "Smooth (Zero Roughness)" or "Polished".
        //
        // Mapping: only the three smoothest RASAero finishes count as "perfect"
        // in the OR sense. "Smooth Paint" is a real painted airframe that will
        // trip the boundary layer within inches, so it stays non-perfect and
        // keeps the turbulent cap.
        boolean perfect = RASAeroCommonConstants.FINISH_SMOOTH.equals(finish)
                || RASAeroCommonConstants.FINISH_POLISHED.equals(finish)
                || RASAeroCommonConstants.FINISH_SHEET_METAL.equals(finish);
        rocket.setPerfectFinish(perfect);
    }
}
