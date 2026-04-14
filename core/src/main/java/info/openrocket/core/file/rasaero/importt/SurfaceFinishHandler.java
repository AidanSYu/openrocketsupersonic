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

        // RASAero II's "Smooth (Zero Roughness)" / "Polished" / "Sheet Metal"
        // labels mean the *roughness model* is at its baseline value — i.e. use
        // the standard turbulent friction correlation without an additional
        // surface-roughness penalty. They do NOT mean "extended laminar flow":
        // RASAero treats the boundary layer as fully turbulent for every entry.
        //
        // Setting Rocket.perfectFinish=true here previously enabled OpenRocket's
        // Blasius (laminar) Cf branch and lifted the laminarFraction cap from
        // 0.05 to whatever the Michel transition predicts (~0.10–0.15 on a
        // typical 4 in HPR airframe at sub-Mach-0.8). That artificially shaved
        // 10–15 % off body friction drag on every benchmark rocket imported
        // from a CDX1 with a "Smooth" finish — visible in the SimVReal
        // overshoot cluster (EZI-65, Thunder & Lightning, the Caliber Isp set,
        // Raven), where ORP apogee was consistently +15–25 % above real flight
        // while RASAero II was within ±5 %.
        //
        // Real high-power airframes — even competition-grade ones — trip the
        // boundary layer within an inch: fillets, shoulder steps, vent holes,
        // rail buttons, and paint joints all sit well above the natural
        // transition Reynolds number. Treating any imported CDX1 as "perfect
        // finish" is therefore unphysical regardless of the RASAero label.
        // Keep the OPTIMUM (5 μm) ExternalComponent finish from
        // RASAERO_TO_OPENROCKET_SURFACE — that already provides RASAero's
        // baseline friction — and leave Rocket.perfectFinish at false so the
        // turbulent cap stays in effect.
        rocket.setPerfectFinish(false);
    }
}
