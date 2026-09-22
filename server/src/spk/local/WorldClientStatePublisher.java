package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * Exact-v308 world/client-state presentation facade.
 *
 * These packets render client state only; they do not own gameplay authority.
 */
final class WorldClientStatePublisher {
    private final ServerPacketWriter packets;

    WorldClientStatePublisher(ServerPacketWriter packets){
        this.packets=Objects.requireNonNull(packets,"packets");
    }

    void resetActorAnimations()throws IOException{
        packets.fixed(1,new byte[0]);
    }

    void cameraShake(int channel,int paramA,int paramB,int paramC)throws IOException{
        if(channel<0||channel>4)throw new IllegalArgumentException("channel="+channel);
        packets.fixed(35,new PacketPayloadWriter()
            .putU8(channel)
            .putU8(paramA)
            .putU8(paramB)
            .putU8(paramC)
            .toByteArray());
    }

    void multicombatState(int state)throws IOException{
        packets.fixed(61,new PacketPayloadWriter().putU8(state).toByteArray());
    }

    void resetVarpsToDefaults()throws IOException{
        packets.fixed(68,new byte[0]);
    }

    void musicTrack(int trackId)throws IOException{
        int encoded=trackId==-1?0xffff:trackId;
        if(encoded<0||encoded>0xffff)throw new IllegalArgumentException("trackId="+trackId);
        packets.fixed(74,new PacketPayloadWriter().putU16LE(encoded).toByteArray());
    }

    void resetDestinationMarker()throws IOException{
        packets.fixed(78,new byte[0]);
    }

    void minimapState(int state)throws IOException{
        packets.fixed(99,new PacketPayloadWriter().putU8(state).toByteArray());
    }

    void resetCamera()throws IOException{
        packets.fixed(107,new byte[0]);
    }

    void systemUpdateSeconds(int seconds)throws IOException{
        packets.fixed(114,new PacketPayloadWriter().putU16LE(seconds).toByteArray());
    }

    void queuedMusic(int trackId,int delayValue)throws IOException{
        packets.fixed(121,new PacketPayloadWriter()
            .putU16LELowAdd128(trackId)
            .putU16BELowAdd128(delayValue)
            .toByteArray());
    }

    void forcedCameraPosition(
        int tileX,int tileY,int heightOffset,int speed,int acceleration
    )throws IOException{
        packets.fixed(166,cameraPoint(tileX,tileY,heightOffset,speed,acceleration));
    }

    void welcomeMetadata(
        int fieldA,int fieldB,int fieldC,int fieldD,int fieldE
    )throws IOException{
        packets.fixed(176,new PacketPayloadWriter()
            .putU8Neg(fieldA)
            .putU16BELowAdd128(fieldB)
            .putU8(fieldC)
            .putI32Y(fieldD)
            .putU16BE(fieldE)
            .toByteArray());
    }

    void forcedCameraLookAt(
        int tileX,int tileY,int heightOffset,int speed,int acceleration
    )throws IOException{
        packets.fixed(177,cameraPoint(tileX,tileY,heightOffset,speed,acceleration));
    }

    void weight(int signedWeight)throws IOException{
        packets.fixed(240,new PacketPayloadWriter()
            .putI16BE(signedWeight)
            .toByteArray());
    }

    void hintNpc(int npcIndex)throws IOException{
        packets.fixed(254,new PacketPayloadWriter()
            .putU8(1)
            .putU16BE(npcIndex)
            .putU8(0).putU8(0).putU8(0)
            .toByteArray());
    }

    void hintLocation(
        int type,int tileX,int tileY,int heightOffset
    )throws IOException{
        if(type<2||type>6)throw new IllegalArgumentException("hint location type="+type);
        packets.fixed(254,new PacketPayloadWriter()
            .putU8(type)
            .putU16BE(tileX)
            .putU16BE(tileY)
            .putU8(heightOffset)
            .toByteArray());
    }

    void hintPlayer(int playerIndex)throws IOException{
        packets.fixed(254,new PacketPayloadWriter()
            .putU8(10)
            .putU16BE(playerIndex)
            .putU8(0).putU8(0).putU8(0)
            .toByteArray());
    }

    private static byte[] cameraPoint(
        int tileX,int tileY,int heightOffset,int speed,int acceleration
    ){
        return new PacketPayloadWriter()
            .putU8(tileX)
            .putU8(tileY)
            .putU16BE(heightOffset)
            .putU8(speed)
            .putU8(acceleration)
            .toByteArray();
    }
}
