package spk.local;

/**
 * Exact current-client codec for the scene-object packet trio used by the
 * SpawnPK home overlay.
 *
 * Recovered statically from the pinned current client
 * SHA-256 6232bae206846a4ba8d09766a2dee886b69016066a3f50f83b201bf705f93662.
 *
 * Client decode authority:
 *   opcode 85  : oF = Buffer.O(); oE = Buffer.O();
 *   opcode 101 : shapeRot = Buffer.O(); coord = Buffer.y();
 *   opcode 151 : coord = Buffer.N(); objectId = Buffer.S(); shapeRot = Buffer.P();
 *
 * Buffer readers in rs.x.e:
 *   y() = raw unsigned byte
 *   N() = (byte - 128) & 0xff
 *   O() = (-byte) & 0xff
 *   P() = (128 - byte) & 0xff
 *   S() = little-endian unsigned short
 *
 * This class implements the exact inverse writers.
 */
final class SceneObjectPacketCodec {
    private SceneObjectPacketCodec() {}

    static final int OPCODE_SCENE_BASE = 85;
    static final int OPCODE_OBJECT_REMOVE = 101;
    static final int OPCODE_OBJECT_ADD = 151;

    static byte[] sceneBase85(int chunkBaseLocalX, int chunkBaseLocalY) {
        requireLocal(chunkBaseLocalX, "chunkBaseLocalX");
        requireLocal(chunkBaseLocalY, "chunkBaseLocalY");
        // Client reads Y first, then X, both with O() == (-byte) & 0xff.
        return new byte[] { encO(chunkBaseLocalY), encO(chunkBaseLocalX) };
    }

    static byte[] objectRemove101(int localX, int localY, int chunkBaseLocalX, int chunkBaseLocalY,
                                  int shape, int rotation) {
        int coord = packedCoord(localX, localY, chunkBaseLocalX, chunkBaseLocalY);
        int shapeRot = packedShapeRotation(shape, rotation);
        // Client: shapeRot = O(); coord = y().
        return new byte[] { encO(shapeRot), (byte) coord };
    }

    static byte[] objectAdd151(int wireObjectId, int localX, int localY, int chunkBaseLocalX, int chunkBaseLocalY,
                               int shape, int rotation) {
        if (wireObjectId < 0 || wireObjectId > 0xffff) throw new IllegalArgumentException("wireObjectId");
        int coord = packedCoord(localX, localY, chunkBaseLocalX, chunkBaseLocalY);
        int shapeRot = packedShapeRotation(shape, rotation);
        // Client: coord=N(); objectId=S(); shapeRot=P().
        return new byte[] {
            encN(coord),
            (byte) (wireObjectId & 0xff),
            (byte) ((wireObjectId >>> 8) & 0xff),
            encP(shapeRot)
        };
    }

    static int chunkBase(int localCoord) {
        requireLocal(localCoord, "localCoord");
        return localCoord & ~7;
    }

    static int packedCoord(int localX, int localY, int chunkBaseLocalX, int chunkBaseLocalY) {
        requireLocal(localX, "localX"); requireLocal(localY, "localY");
        requireLocal(chunkBaseLocalX, "chunkBaseLocalX"); requireLocal(chunkBaseLocalY, "chunkBaseLocalY");
        int dx = localX - chunkBaseLocalX;
        int dy = localY - chunkBaseLocalY;
        if (dx < 0 || dx > 7 || dy < 0 || dy > 7)
            throw new IllegalArgumentException("object not inside selected 8x8 scene chunk: local="+localX+","+localY+" base="+chunkBaseLocalX+","+chunkBaseLocalY);
        return (dx << 4) | dy;
    }

    static int packedShapeRotation(int shape, int rotation) {
        if (shape < 0 || shape > 31) throw new IllegalArgumentException("shape");
        if (rotation < 0 || rotation > 3) throw new IllegalArgumentException("rotation");
        return (shape << 2) | rotation;
    }

    // Exact inverse of current-client readers.
    private static byte encN(int v) { return (byte) ((v + 128) & 0xff); }
    private static byte encO(int v) { return (byte) ((-v) & 0xff); }
    private static byte encP(int v) { return (byte) ((128 - v) & 0xff); }

    private static void requireLocal(int v, String name) {
        if (v < 0 || v >= 104) throw new IllegalArgumentException(name+"="+v);
    }

    // Decoder mirrors retained for deterministic offline certification only.
    static int decY(byte b) { return b & 0xff; }
    static int decN(byte b) { return ((b & 0xff) - 128) & 0xff; }
    static int decO(byte b) { return (-(b & 0xff)) & 0xff; }
    static int decP(byte b) { return (128 - (b & 0xff)) & 0xff; }
    static int decS(byte lo, byte hi) { return ((hi & 0xff) << 8) | (lo & 0xff); }
}
