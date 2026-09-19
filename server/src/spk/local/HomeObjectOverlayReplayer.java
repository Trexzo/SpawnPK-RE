package spk.local;

import java.io.*;
import java.util.*;

/**
 * WORLD-lane runtime adapter for the V9.06 production HOME object overlay.
 *
 * V9.06 captured production-local coordinates against production scene base
 * 3032,3448. LocalLab v5.4 currently loads the same world against runtime
 * region base 3032,3440. Packet 85/101/151 fields are client-local, so a
 * production local coordinate must never be replayed verbatim into a runtime
 * using a different scene base. WORLD-R6 therefore reprojects every semantic
 * world tile against the caller's active runtime base before serializing.
 */
final class HomeObjectOverlayReplayer {
    static final class Stats {
        final int basePackets, removePackets, addPackets;
        final int runtimeBaseX,runtimeBaseY;
        Stats(int basePackets, int removePackets, int addPackets,int runtimeBaseX,int runtimeBaseY) {
            this.basePackets=basePackets; this.removePackets=removePackets; this.addPackets=addPackets;
            this.runtimeBaseX=runtimeBaseX; this.runtimeBaseY=runtimeBaseY;
        }
        int sceneMutationPackets(){ return removePackets + addPackets; }
        int totalPackets(){ return basePackets + sceneMutationPackets(); }
        @Override public String toString(){
            return "Stats{base85="+basePackets+",remove101="+removePackets+",add151="+addPackets+
                ",total="+totalPackets()+",runtimeBase="+runtimeBaseX+","+runtimeBaseY+"}";
        }
    }

    private HomeObjectOverlayReplayer() {}

    /** Replay against LocalLab's currently loaded 104x104 runtime base. */
    static Stats replayHome(ServerPacketWriter w) throws IOException {
        return replayHome(w, MovementState.REGION_BASE_X, MovementState.REGION_BASE_Y);
    }

    static Stats replayHome(ServerPacketWriter w,int runtimeBaseX,int runtimeBaseY) throws IOException {
        return replay(w, HomeObjectOverlayRepository.all(), runtimeBaseX, runtimeBaseY);
    }

    static Stats replay(ServerPacketWriter w, List<HomeObjectOverlayRepository.Mutation> mutations,
                        int runtimeBaseX,int runtimeBaseY) throws IOException {
        if (w == null) throw new NullPointerException("writer");
        int activeBaseX=-1, activeBaseY=-1;
        int bases=0, removes=0, adds=0;

        for (HomeObjectOverlayRepository.Mutation m : mutations) {
            if (m.plane != HomeWorldManifest.HOME_PLANE)
                throw new IllegalArgumentException("WORLD-R6 currently replays HOME plane 0 only: "+m);

            // Preserve a hard provenance check on V9.06's captured production-local
            // coordinates, but never use those coordinates directly for runtime IO.
            int capturedLocalX=m.worldX-HomeWorldManifest.HOME_BASE_X;
            int capturedLocalY=m.worldY-HomeWorldManifest.HOME_BASE_Y;
            if (m.localX!=capturedLocalX || m.localY!=capturedLocalY)
                throw new IllegalStateException("V9.06 local/world mismatch seq="+m.seq+" local="+m.localX+","+m.localY+
                    " expectedCaptured="+capturedLocalX+","+capturedLocalY);

            int localX=m.worldX-runtimeBaseX;
            int localY=m.worldY-runtimeBaseY;
            if(localX<0 || localX>=104 || localY<0 || localY>=104)
                throw new IllegalStateException("HOME mutation outside active runtime scene seq="+m.seq+" world="+m.worldX+","+m.worldY+
                    " runtimeBase="+runtimeBaseX+","+runtimeBaseY+" local="+localX+","+localY);

            int bx=SceneObjectPacketCodec.chunkBase(localX);
            int by=SceneObjectPacketCodec.chunkBase(localY);
            if (bx!=activeBaseX || by!=activeBaseY) {
                w.fixed(SceneObjectPacketCodec.OPCODE_SCENE_BASE, SceneObjectPacketCodec.sceneBase85(bx,by));
                activeBaseX=bx; activeBaseY=by; bases++;
            }

            if (m.isRemove()) {
                w.fixed(SceneObjectPacketCodec.OPCODE_OBJECT_REMOVE,
                    SceneObjectPacketCodec.objectRemove101(localX,localY,bx,by,m.shape,m.rotation));
                removes++;
            } else if (m.isAdd()) {
                w.fixed(SceneObjectPacketCodec.OPCODE_OBJECT_ADD,
                    SceneObjectPacketCodec.objectAdd151(m.wireObjectId,localX,localY,bx,by,m.shape,m.rotation));
                adds++;
            } else {
                throw new IllegalStateException("unknown mutation operation "+m.operation);
            }
        }
        return new Stats(bases,removes,adds,runtimeBaseX,runtimeBaseY);
    }
}
