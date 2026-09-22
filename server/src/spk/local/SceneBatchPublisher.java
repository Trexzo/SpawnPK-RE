package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 S2C60 local-region batch envelope over the same semantic scene
 * operations used by SceneUpdatePublisher.
 */
final class SceneBatchPublisher {
    private final ServerPacketWriter packets;
    private final SceneCoordinateContext ctx;

    SceneBatchPublisher(ServerPacketWriter packets,SceneCoordinateContext ctx){
        this.packets=Objects.requireNonNull(packets,"packets");
        this.ctx=Objects.requireNonNull(ctx,"ctx");
    }

    Batch begin(Tile anchor){
        Objects.requireNonNull(anchor,"anchor");
        if(anchor.plane!=ctx.plane)
            throw new IllegalArgumentException("plane="+anchor.plane+" loaded="+ctx.plane);
        int localX=ctx.localX(anchor.x);
        int localY=ctx.localY(anchor.y);
        return new Batch(
            SceneObjectPacketCodec.chunkBase(localX),
            SceneObjectPacketCodec.chunkBase(localY)
        );
    }

    final class Batch {
        private final int baseX;
        private final int baseY;
        private final ByteArrayOutputStream body=new ByteArrayOutputStream();
        private boolean sent;

        Batch(int baseX,int baseY){
            this.baseX=baseX;
            this.baseY=baseY;
            byte[] header=new PacketPayloadWriter()
                .putU8(baseY)
                .putU8Neg(baseX)
                .toByteArray();
            body.write(header,0,header.length);
        }

        Batch groundSpawn(GroundItem item){
            int c=packed(item.tile);
            return child(44,SceneUpdateEncoding.groundSpawn(c,item.itemId,item.amount));
        }

        Batch groundAmount(GroundItem item,int oldAmount){
            int c=packed(item.tile);
            return child(84,SceneUpdateEncoding.groundAmount(
                c,item.itemId,oldAmount,item.amount
            ));
        }

        Batch groundRemove(GroundItem item){
            int c=packed(item.tile);
            return child(156,SceneUpdateEncoding.groundRemove(c,item.itemId));
        }

        Batch objectAdd(int objectId,Tile tile,int shape,int rotation){
            int c=packed(tile);
            return child(151,SceneUpdateEncoding.objectAdd(
                c,objectId,shape,rotation
            ));
        }

        Batch objectRemove(Tile tile,int shape,int rotation){
            int c=packed(tile);
            return child(101,SceneUpdateEncoding.objectRemove(
                c,shape,rotation
            ));
        }

        Batch objectAnimation(int animationId,Tile tile,int shape,int rotation){
            int c=packed(tile);
            return child(160,SceneUpdateEncoding.objectAnimation(
                c,animationId,shape,rotation
            ));
        }

        Batch spotGraphic(int gfxId,Tile tile,int height,int delay){
            int c=packed(tile);
            return child(4,SceneUpdateEncoding.spotGraphic(
                c,gfxId,height,delay
            ));
        }

        Batch positionalSound(int soundId,Tile tile,int radius,int volume){
            int c=packed(tile);
            return child(105,SceneUpdateEncoding.positionalSound(
                c,soundId,radius,volume
            ));
        }

        Batch projectile(
            int projectileId,Tile source,int dx,int dy,int rawTarget,
            int startHeight,int endHeight,int startCycle,int endCycle,
            int slope,int startDistance
        ){
            int c=packed(source);
            return child(117,SceneUpdateEncoding.projectile(
                c,projectileId,dx,dy,rawTarget,startHeight,endHeight,
                startCycle,endCycle,slope,startDistance
            ));
        }

        Batch attachTemporaryObjectToPlayer(
            WorldPlayer target,Tile tile,int objectDefinitionId,
            int shape,int rotation,int startDelayTicks,int endDelayTicks,
            int xOffsetA,int xOffsetB,int yOffsetA,int yOffsetB
        ){
            Objects.requireNonNull(target,"target");
            if(endDelayTicks<startDelayTicks)
                throw new IllegalArgumentException("endDelayTicks < startDelayTicks");
            int playerIndex=Player81WorldSync.clientIndexFor(packets,target);
            if(playerIndex<0)
                throw new IllegalStateException(
                    "target has no client index for viewer: "+target.id()
                );
            int c=packed(tile);
            return child(147,SceneUpdateEncoding.attachedTemporaryObject(
                c,playerIndex,xOffsetA,startDelayTicks,yOffsetA,endDelayTicks,
                shape,rotation,xOffsetB,objectDefinitionId,yOffsetB
            ));
        }

        Batch groundSpawnExcept(GroundItem item,WorldPlayer excludedPlayer){
            Objects.requireNonNull(excludedPlayer,"excludedPlayer");
            int playerIndex=Player81WorldSync.clientIndexFor(
                packets,excludedPlayer
            );
            int c=packed(item.tile);
            if(playerIndex<0)
                return child(44,SceneUpdateEncoding.groundSpawn(
                    c,item.itemId,item.amount
                ));
            return child(215,SceneUpdateEncoding.groundSpawnExcept(
                c,item.itemId,playerIndex,item.amount
            ));
        }

        void send()throws IOException{
            if(sent)throw new IllegalStateException("batch already sent");
            sent=true;
            packets.varShort(60,body.toByteArray());
            ctx.setCurrent(baseX,baseY);
        }

        private Batch child(int opcode,byte[] payload){
            if(sent)throw new IllegalStateException("batch already sent");
            body.write(opcode);
            body.write(payload,0,payload.length);
            return this;
        }

        private int packed(Tile tile){
            Objects.requireNonNull(tile,"tile");
            if(tile.plane!=ctx.plane)
                throw new IllegalArgumentException(
                    "plane="+tile.plane+" loaded="+ctx.plane
                );
            int localX=ctx.localX(tile.x);
            int localY=ctx.localY(tile.y);
            int cx=SceneObjectPacketCodec.chunkBase(localX);
            int cy=SceneObjectPacketCodec.chunkBase(localY);
            if(cx!=baseX||cy!=baseY)
                throw new IllegalArgumentException(
                    "scene batch crosses 8x8 base: "+
                    "tile="+tile+" base="+baseX+","+baseY
                );
            return SceneObjectPacketCodec.packedCoord(
                localX,localY,baseX,baseY
            );
        }
    }
}
