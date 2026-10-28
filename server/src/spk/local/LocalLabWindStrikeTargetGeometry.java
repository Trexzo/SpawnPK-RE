package spk.local;

import java.util.Objects;

/**
 * P5.23 conservative CUSTOM_LOCALLAB-only spell geometry admission.
 *
 * This is NOT a verified SpawnPK spell range or line-of-sight rule.
 * The canonical World NPC location is the authority, never the viewer's
 * potentially stale scene x/y. The caller must already own the player
 * mutation lock and exact canonical WorldNpcRegistry mutation monitor.
 */
final class LocalLabWindStrikeTargetGeometry {
    // Explicit source fixture policy, NOT original-game combat evidence.
    static final int MAX_CHEBYSHEV_TILES=8;

    private LocalLabWindStrikeTargetGeometry(){}

    /** Null means bounded geometric admission, NOT confirmed clear LOS. */
    static String rejection(MovementState caster,WorldNpc target){
        Objects.requireNonNull(caster,"caster");
        Objects.requireNonNull(target,"target");

        // The registry monitor held by the caller prevents WorldNpc
        // moves that use the canonical registry. Player movement comes
        // from the current WorldPlayer while its mutation lock is held.
        int x=target.x();
        int y=target.y();

        if(caster.plane()!=target.plane())
            return "REJECTED_TARGET_DIFFERENT_PLANE";

        if(!caster.insideCurrentLoadedRegion(x,y))
            return "REJECTED_TARGET_OUTSIDE_LOADED_REGION";

        // Promote before subtracting to avoid integer overflow for any
        // source-only synthetic world coordinate.
        long dx=Math.abs((long)x-caster.x());
        long dy=Math.abs((long)y-caster.y());
        if(Math.max(dx,dy)>MAX_CHEBYSHEV_TILES)
            return "REJECTED_TARGET_OUT_OF_LAB_RANGE";

        return null;
    }
}
