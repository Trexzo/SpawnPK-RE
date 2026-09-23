package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

/** Exact-v308 constructed/dynamic-region presentation publisher (S2C241). */
final class RegionPresentationPublisher {
    private static final int PLANES=4;
    private static final int CHUNKS=13;
    private static final int MAX_DESCRIPTOR=(1<<26)-1;

    private final ServerPacketWriter packets;

    RegionPresentationPublisher(ServerPacketWriter packets){
        this.packets=Objects.requireNonNull(packets,"packets");
    }

    void constructedRegion(
        int regionX,
        int regionY,
        int[][][] descriptorGrid
    )throws IOException{
        validateGrid(descriptorGrid);

        ByteArrayOutputStream body=new ByteArrayOutputStream();
        byte[] header=new PacketPayloadWriter()
            .putU16BELowAdd128(regionY)
            .toByteArray();
        body.write(header,0,header.length);

        BitWriter bits=new BitWriter();
        for(int plane=0;plane<PLANES;plane++){
            for(int localChunkX=0;localChunkX<CHUNKS;localChunkX++){
                for(int localChunkY=0;localChunkY<CHUNKS;localChunkY++){
                    int descriptor=descriptorGrid[plane][localChunkX][localChunkY];
                    if(descriptor<0){
                        bits.write(0,1);
                    }else{
                        bits.write(1,1);
                        bits.write(descriptor,26);
                    }
                }
            }
        }
        byte[] bitBytes=bits.finish();
        body.write(bitBytes,0,bitBytes.length);

        byte[] trailer=new PacketPayloadWriter()
            .putU16BE(regionX)
            .toByteArray();
        body.write(trailer,0,trailer.length);

        packets.varShort(241,body.toByteArray());
    }

    private static void validateGrid(int[][][] grid){
        Objects.requireNonNull(grid,"descriptorGrid");
        if(grid.length!=PLANES)
            throw new IllegalArgumentException("planes="+grid.length);
        for(int plane=0;plane<PLANES;plane++){
            if(grid[plane]==null||grid[plane].length!=CHUNKS)
                throw new IllegalArgumentException("plane "+plane+" chunkX dimension");
            for(int x=0;x<CHUNKS;x++){
                if(grid[plane][x]==null||grid[plane][x].length!=CHUNKS)
                    throw new IllegalArgumentException(
                        "plane "+plane+" chunkX "+x+" chunkY dimension"
                    );
                for(int y=0;y<CHUNKS;y++){
                    int descriptor=grid[plane][x][y];
                    if(descriptor<-1||descriptor>MAX_DESCRIPTOR)
                        throw new IllegalArgumentException(
                            "descriptor["+plane+"]["+x+"]["+y+"]="+descriptor
                        );
                }
            }
        }
    }
}
