package spk.local;

import java.io.IOException;

/**
 * R8.1 publication of the two self-contained bow projectiles directly live-probed
 * in V9.13.  IDs and packet-117 geometry are direct runtime authority.  The
 * captured constructor lifetime is also used instead of a generic RSPS value:
 * Webweaver observed 14/15 cycles (mode 14); Scorching observed 9 cycles at
 * cardinal distance 1 and 14 cycles at distance 3.  Packet start delay is zero
 * so the projectile starts with the already-proven attack animation/GFX; this is
 * explicitly local reconstruction rather than a claim about an unrecorded server
 * delay field.
 */
final class V913LiveProjectilePublisher {
    static final String POLICY="V9.13_LIVE_PROBED_SELF_CONTAINED_BOWS";

    static boolean enabled(V913WeaponRuntimeAuthority.Profile p){
        return p!=null && p.directBasicAttack && p.hasProjectileGeometry() && (p.itemId==25557 || p.itemId==28860);
    }

    static int startDelay(V913WeaponRuntimeAuthority.Profile p){return 0;}

    static int endDelay(V913WeaponRuntimeAuthority.Profile p,int distance){
        if(p==null)return -1;
        if(p.itemId==28860 && distance<=1)return 9; // directly observed Scorching close shot
        return 14; // observed modal lifetime for Webweaver and non-adjacent Scorching
    }

    static String publish(V913WeaponRuntimeAuthority.Profile p,MovementState movement,NpcEntity target,SceneUpdatePublisher scene)throws IOException{
        if(!enabled(p))return "SUPPRESSED_NOT_ALLOWLISTED";
        if(movement==null||target==null||scene==null)return "SUPPRESSED_MISSING_RUNTIME_CONTEXT";
        int dx=target.x-movement.x(),dy=target.y-movement.y();
        if(dx<-128||dx>127||dy<-128||dy>127)return "SUPPRESSED_TARGET_DELTA_RANGE dx="+dx+" dy="+dy;
        int distance=LocalSession.chebyshev(movement.x(),movement.y(),target.x,target.y);
        int start=startDelay(p),end=endDelay(p,distance);
        // Exact 317/current-client lock-on convention: positive NPC target is sceneIndex+1.
        scene.projectile(p.projectileId,new Tile(movement.x(),movement.y(),movement.plane()),dx,dy,target.sceneIndex+1,
            p.projectileStartHeight,p.projectileEndHeight,start,end,p.projectileSlope,p.projectileStartDistance);
        return "PUBLISHED id="+p.projectileId+" dx="+dx+" dy="+dy+" lockon="+(target.sceneIndex+1)+
            " heights="+p.projectileStartHeight+"/"+p.projectileEndHeight+" delay="+start+"/"+end+
            " slope="+p.projectileSlope+" startDistance="+p.projectileStartDistance+" policy="+POLICY;
    }

    private V913LiveProjectilePublisher(){}
}
