package spk.local;

/** Shared exact-current body encoders for standalone and S2C60 scene updates. */
final class SceneUpdateEncoding {
    private SceneUpdateEncoding(){}

    static byte[] groundSpawn(int packedCoord,int itemId,int amount){
        return new PacketPayloadWriter()
            .putU16LELowAdd128(itemId)
            .putU16BE(amount)
            .putU8(packedCoord)
            .toByteArray();
    }

    static byte[] groundAmount(int packedCoord,int itemId,int oldAmount,int newAmount){
        return new PacketPayloadWriter()
            .putU8(packedCoord)
            .putU16BE(itemId)
            .putU16BE(oldAmount)
            .putU16BE(newAmount)
            .toByteArray();
    }

    static byte[] groundRemove(int packedCoord,int itemId){
        return new PacketPayloadWriter()
            .putU8Add128(packedCoord)
            .putU16BE(itemId)
            .toByteArray();
    }

    static byte[] objectAdd(int packedCoord,int objectId,int shape,int rotation){
        int shapeRotation=SceneObjectPacketCodec.packedShapeRotation(shape,rotation);
        return new PacketPayloadWriter()
            .putU8Add128(packedCoord)
            .putU16LE(objectId)
            .putU8_128Minus(shapeRotation)
            .toByteArray();
    }

    static byte[] objectRemove(int packedCoord,int shape,int rotation){
        int shapeRotation=SceneObjectPacketCodec.packedShapeRotation(shape,rotation);
        return new PacketPayloadWriter()
            .putU8Neg(shapeRotation)
            .putU8(packedCoord)
            .toByteArray();
    }

    static byte[] objectAnimation(int packedCoord,int animationId,int shape,int rotation){
        int shapeRotation=SceneObjectPacketCodec.packedShapeRotation(shape,rotation);
        return new PacketPayloadWriter()
            .putU8_128Minus(packedCoord)
            .putU8_128Minus(shapeRotation)
            .putU16BELowAdd128(animationId)
            .toByteArray();
    }

    static byte[] spotGraphic(int packedCoord,int gfxId,int height,int delay){
        return new PacketPayloadWriter()
            .putU8(packedCoord)
            .putU16BE(gfxId)
            .putU8(height)
            .putU16BE(delay)
            .toByteArray();
    }

    static byte[] positionalSound(int packedCoord,int soundId,int radius,int volume){
        if(radius<0||radius>15||volume<0||volume>7)
            throw new IllegalArgumentException("radius/volume");
        return new PacketPayloadWriter()
            .putU8(packedCoord)
            .putU16BE(soundId)
            .putU8((radius<<4)|volume)
            .toByteArray();
    }

    static byte[] projectile(
        int packedCoord,int projectileId,int dx,int dy,int rawTarget,
        int startHeight,int endHeight,int startCycle,int endCycle,
        int slope,int startDistance
    ){
        return new PacketPayloadWriter()
            .putU8(packedCoord)
            .putI8(dx)
            .putI8(dy)
            .putI16BE(rawTarget)
            .putU16BE(projectileId)
            .putU8(startHeight)
            .putU8(endHeight)
            .putU16BE(startCycle)
            .putU16BE(endCycle)
            .putU8(slope)
            .putU8(startDistance)
            .toByteArray();
    }

    static byte[] attachedTemporaryObject(
        int packedCoord,int playerIndex,
        int xOffsetA,int startDelayTicks,int yOffsetA,
        int endDelayTicks,int shape,int rotation,int xOffsetB,
        int objectDefinitionId,int yOffsetB
    ){
        int shapeRotation=SceneObjectPacketCodec.packedShapeRotation(shape,rotation);
        return new PacketPayloadWriter()
            .putU8_128Minus(packedCoord)
            .putU16BE(playerIndex)
            .putI8_128Minus(xOffsetA)
            .putU16LE(startDelayTicks)
            .putI8Neg(yOffsetA)
            .putU16BE(endDelayTicks)
            .putU8_128Minus(shapeRotation)
            .putI8(xOffsetB)
            .putU16BE(objectDefinitionId)
            .putI8Neg(yOffsetB)
            .toByteArray();
    }

    static byte[] groundSpawnExcept(
        int packedCoord,int itemId,int excludedPlayerIndex,int amount
    ){
        return new PacketPayloadWriter()
            .putU16BELowAdd128(itemId)
            .putU8_128Minus(packedCoord)
            .putU16BELowAdd128(excludedPlayerIndex)
            .putU16BE(amount)
            .toByteArray();
    }
}
