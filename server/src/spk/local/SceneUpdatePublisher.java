package spk.local;

import java.io.IOException;

final class SceneUpdatePublisher {
    private final ServerPacketWriter packets;
    private final SceneCoordinateContext ctx;
    SceneUpdatePublisher(ServerPacketWriter packets,SceneCoordinateContext ctx){this.packets=packets;this.ctx=ctx;}

    private int packed(Tile t)throws IOException{
        if(t.plane!=ctx.plane)throw new IllegalArgumentException("plane="+t.plane+" loaded="+ctx.plane);
        int lx=ctx.localX(t.x),ly=ctx.localY(t.y),cx=SceneObjectPacketCodec.chunkBase(lx),cy=SceneObjectPacketCodec.chunkBase(ly);
        ensureBase(cx,cy);
        return SceneObjectPacketCodec.packedCoord(lx,ly,cx,cy);
    }
    private void ensureBase(int cx,int cy)throws IOException{ if(!ctx.isCurrent(cx,cy)){ packets.fixed(85,SceneObjectPacketCodec.sceneBase85(cx,cy)); ctx.setCurrent(cx,cy); } }

    void groundSpawn(GroundItem g)throws IOException{ int c=packed(g.tile); packets.fixed(44,new PacketPayloadWriter().putU16LELowAdd128(g.itemId).putU16BE(g.amount).putU8(c).toByteArray()); }
    void groundAmount(GroundItem g,int oldAmount)throws IOException{ int c=packed(g.tile); packets.fixed(84,new PacketPayloadWriter().putU8(c).putU16BE(g.itemId).putU16BE(oldAmount).putU16BE(g.amount).toByteArray()); }
    void groundRemove(GroundItem g)throws IOException{ int c=packed(g.tile); packets.fixed(156,new PacketPayloadWriter().putU8Add128(c).putU16BE(g.itemId).toByteArray()); }
    void objectAdd(int objectId,Tile t,int shape,int rotation)throws IOException{ int lx=ctx.localX(t.x),ly=ctx.localY(t.y),cx=SceneObjectPacketCodec.chunkBase(lx),cy=SceneObjectPacketCodec.chunkBase(ly);ensureBase(cx,cy);packets.fixed(151,SceneObjectPacketCodec.objectAdd151(objectId,lx,ly,cx,cy,shape,rotation)); }
    void objectRemove(Tile t,int shape,int rotation)throws IOException{ int lx=ctx.localX(t.x),ly=ctx.localY(t.y),cx=SceneObjectPacketCodec.chunkBase(lx),cy=SceneObjectPacketCodec.chunkBase(ly);ensureBase(cx,cy);packets.fixed(101,SceneObjectPacketCodec.objectRemove101(lx,ly,cx,cy,shape,rotation)); }
    void objectAnimation(int animationId,Tile t,int shape,int rotation)throws IOException{ int c=packed(t),sr=SceneObjectPacketCodec.packedShapeRotation(shape,rotation); packets.fixed(160,new PacketPayloadWriter().putU8_128Minus(c).putU8_128Minus(sr).putU16BELowAdd128(animationId).toByteArray()); }
    void spotGraphic(int gfxId,Tile t,int height,int delay)throws IOException{ int c=packed(t); packets.fixed(4,new PacketPayloadWriter().putU8(c).putU16BE(gfxId).putU8(height).putU16BE(delay).toByteArray()); }
    void positionalSound(int soundId,Tile t,int radius,int volume)throws IOException{ int c=packed(t); if(radius<0||radius>15||volume<0||volume>7)throw new IllegalArgumentException("radius/volume"); packets.fixed(105,new PacketPayloadWriter().putU8(c).putU16BE(soundId).putU8((radius<<4)|volume).toByteArray()); }
    void soundEffect(int soundId,int delay,int loops)throws IOException{ packets.fixed(174,new PacketPayloadWriter().putU16BE(soundId).putU16BE(delay).putU16BE(loops).toByteArray()); }
    void projectile(int projectileId,Tile source,int dx,int dy,int rawTarget,int startHeight,int endHeight,int startCycle,int endCycle,int slope,int startDistance)throws IOException{
        int c=packed(source); PacketPayloadWriter p=new PacketPayloadWriter().putU8(c).putI8(dx).putI8(dy).putI16BE(rawTarget).putU16BE(projectileId).putU8(startHeight).putU8(endHeight).putU16BE(startCycle).putU16BE(endCycle).putU8(slope).putU8(startDistance); packets.fixed(117,p.toByteArray());
    }
    void clear8x8(Tile anyTileInChunk)throws IOException{ int cx=ctx.chunkXFor(anyTileInChunk.x),cy=ctx.chunkYFor(anyTileInChunk.y); packets.fixed(64,new PacketPayloadWriter().putU8Neg(cy).putU8_128Minus(cx).toByteArray()); ctx.invalidate(); }
    SceneCoordinateContext context(){return ctx;}
}
