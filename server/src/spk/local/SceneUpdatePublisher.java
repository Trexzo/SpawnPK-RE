package spk.local;

import java.io.IOException;
import java.util.Objects;

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

    void groundSpawn(GroundItem g)throws IOException{
        groundSpawn(
            g.itemId,
            g.amount,
            g.tile
        );
    }
    void groundSpawn(
        int itemId,
        int amount,
        Tile tile
    )throws IOException{
        int c=packed(tile);
        packets.fixed(
            44,
            SceneUpdateEncoding.groundSpawn(
                c,
                itemId,
                amount
            )
        );
    }
    void groundAmount(GroundItem g,int oldAmount)throws IOException{
        groundAmount(
            g.itemId,
            oldAmount,
            g.amount,
            g.tile
        );
    }
    void groundAmount(
        int itemId,
        int oldAmount,
        int newAmount,
        Tile tile
    )throws IOException{
        int c=packed(tile);
        packets.fixed(
            84,
            SceneUpdateEncoding.groundAmount(
                c,
                itemId,
                oldAmount,
                newAmount
            )
        );
    }
    void groundRemove(GroundItem g)throws IOException{
        groundRemove(
            g.itemId,
            g.tile
        );
    }
    void groundRemove(
        int itemId,
        Tile tile
    )throws IOException{
        int c=packed(tile);
        packets.fixed(
            156,
            SceneUpdateEncoding.groundRemove(
                c,
                itemId
            )
        );
    }
    void objectAdd(int objectId,Tile t,int shape,int rotation)throws IOException{
        int c=packed(t);
        packets.fixed(151,SceneUpdateEncoding.objectAdd(c,objectId,shape,rotation));
    }
    void objectRemove(Tile t,int shape,int rotation)throws IOException{
        int c=packed(t);
        packets.fixed(101,SceneUpdateEncoding.objectRemove(c,shape,rotation));
    }
    void objectAnimation(int animationId,Tile t,int shape,int rotation)throws IOException{
        int c=packed(t);
        packets.fixed(160,SceneUpdateEncoding.objectAnimation(c,animationId,shape,rotation));
    }
    void spotGraphic(int gfxId,Tile t,int height,int delay)throws IOException{
        int c=packed(t);
        packets.fixed(4,SceneUpdateEncoding.spotGraphic(c,gfxId,height,delay));
    }
    void positionalSound(int soundId,Tile t,int radius,int volume)throws IOException{
        int c=packed(t);
        packets.fixed(105,SceneUpdateEncoding.positionalSound(c,soundId,radius,volume));
    }
    void soundEffect(int soundId,int delay,int loops)throws IOException{
        packets.fixed(174,new PacketPayloadWriter().putU16BE(soundId).putU16BE(delay).putU16BE(loops).toByteArray());
    }
    void projectile(int projectileId,Tile source,int dx,int dy,int rawTarget,int startHeight,int endHeight,int startCycle,int endCycle,int slope,int startDistance)throws IOException{
        int c=packed(source);
        packets.fixed(117,SceneUpdateEncoding.projectile(
            c,projectileId,dx,dy,rawTarget,startHeight,endHeight,
            startCycle,endCycle,slope,startDistance
        ));
    }

    void attachTemporaryObjectToPlayer(
        WorldPlayer target,Tile tile,int objectDefinitionId,int shape,int rotation,
        int startDelayTicks,int endDelayTicks,
        int xOffsetA,int xOffsetB,int yOffsetA,int yOffsetB
    )throws IOException{
        Objects.requireNonNull(target,"target");
        if(endDelayTicks<startDelayTicks)
            throw new IllegalArgumentException("endDelayTicks < startDelayTicks");
        int playerIndex=Player81WorldSync.clientIndexFor(packets,target);
        if(playerIndex<0)
            throw new IllegalStateException("target has no client index for viewer: "+target.id());
        int c=packed(tile);
        packets.fixed(147,SceneUpdateEncoding.attachedTemporaryObject(
            c,playerIndex,xOffsetA,startDelayTicks,yOffsetA,endDelayTicks,
            shape,rotation,xOffsetB,objectDefinitionId,yOffsetB
        ));
    }

    void groundSpawnExcept(GroundItem g,WorldPlayer excludedPlayer)throws IOException{
        Objects.requireNonNull(excludedPlayer,"excludedPlayer");
        int playerIndex=Player81WorldSync.clientIndexFor(packets,excludedPlayer);
        if(playerIndex<0){
            groundSpawn(g);
            return;
        }
        int c=packed(g.tile);
        packets.fixed(215,SceneUpdateEncoding.groundSpawnExcept(
            c,g.itemId,playerIndex,g.amount
        ));
    }

    void clear8x8(Tile anyTileInChunk)throws IOException{
        int cx=ctx.chunkXFor(anyTileInChunk.x),cy=ctx.chunkYFor(anyTileInChunk.y);
        packets.fixed(64,new PacketPayloadWriter().putU8Neg(cy).putU8_128Minus(cx).toByteArray());
        ctx.invalidate();
    }
    SceneCoordinateContext context(){return ctx;}
}