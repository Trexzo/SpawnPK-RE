package spk.local;

import java.util.*;

/** WORLD-lane facade for production HOME parity data and exact current-client scene/collision policy. */
final class HomeWorldManifest {
    static final int HOME_BASE_X=3032;
    static final int HOME_BASE_Y=3448;
    static final int HOME_PLANE=0;

    static final String CLIENT_SHA256="6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662";
    static final String SOURCE="SpawnPK V9.06 passive area survey";
    static final boolean RAW_PRODUCTION_SCENE_PAYLOADS_PERSISTED=false;
    static final boolean EXACT_PACKET_101_151_SERIALIZER_CERTIFIED=true;
    static final boolean EXACT_CLIENT_COLLISION_POLICY_CERTIFIED=true;

    private HomeWorldManifest(){}

    static List<HomeNpcSpawnRepository.Spawn> npcs(){return HomeNpcSpawnRepository.all();}
    static List<HomeNpcSpawnRepository.Spawn> defaultReplayNpcs(){return HomeNpcSpawnRepository.defaultReplay();}
    static List<HomeNpcSpawnRepository.Spawn> nearbyDefaultReplayNpcs(int playerX,int playerY){return HomeNpcSpawnRepository.nearbyDefaultReplay(playerX,playerY);}
    static List<HomeObjectOverlayRepository.Mutation> objectOverlay(){return HomeObjectOverlayRepository.all();}
    static List<HomeLandmarkRepository.Landmark> landmarks(){return HomeLandmarkRepository.all();}
    static List<HomeCollisionOverlayRepository.Entry> collisionOverlay(){return HomeCollisionOverlayRepository.all();}

    static void requireSceneSerializerCertification(){if(!EXACT_PACKET_101_151_SERIALIZER_CERTIFIED) throw new IllegalStateException("Exact packet 101/151 serializer certification disabled.");}
    static void requireCollisionPolicyCertification(){if(!EXACT_CLIENT_COLLISION_POLICY_CERTIFIED) throw new IllegalStateException("Exact current-client collision policy certification disabled.");}
}
