package spk.local;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Exact-current packet81 encoders added from V9.12 runtime + client decoder authority. */
final class Player81MeasuredSync {
    private Player81MeasuredSync(){}

    static byte[] walkStepAndInteraction(int dir,int target)throws IOException{
        if(dir<0||dir>7)throw new IllegalArgumentException("direction");
        BitWriter bits=new BitWriter();
        bits.write(1,1);      // local player has movement/update
        bits.write(1,2);      // walk
        bits.write(dir,3);
        bits.write(1,1);      // local update mask follows
        bits.write(0,8);      // no existing other players
        bits.write(2047,11);  // end new-player list
        byte[] prefix=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(prefix.length+3);
        out.write(prefix);out.write(0x01);putLE16(out,target<0?65535:target);return out.toByteArray();
    }

    static byte[] runStepsAndInteraction(int dir1,int dir2,int target)throws IOException{
        if(dir1<0||dir1>7||dir2<0||dir2>7)throw new IllegalArgumentException("direction");
        BitWriter bits=new BitWriter();
        bits.write(1,1);bits.write(2,2);bits.write(dir1,3);bits.write(dir2,3);bits.write(1,1);
        bits.write(0,8);bits.write(2047,11);
        byte[] prefix=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(prefix.length+3);
        out.write(prefix);out.write(0x01);putLE16(out,target<0?65535:target);return out.toByteArray();
    }

    /**
     * Production pickup authority from V9.12:
     * mask 0x008 animation is decoded before mask 0x002 turn-to-tile.
     * Q decoder U() = LE16 with +128 on low byte; R decoder S() = plain LE16.
     * Runtime Q/R are odd half-tile world coordinates: 2*world+1.
     */
    static byte[] animationAndTurnToTile(int animationId,int worldX,int worldY)throws IOException{
        if(animationId<0||animationId>65535)throw new IllegalArgumentException("animationId");
        int q=worldX*2+1,r=worldY*2+1;
        if(q<0||q>65535||r<0||r>65535)throw new IllegalArgumentException("turn tile");
        BitWriter bits=new BitWriter();
        bits.write(1,1);bits.write(0,2); // no movement, mask follows
        bits.write(0,8);bits.write(2047,11);
        byte[] prefix=bits.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream(prefix.length+9);
        out.write(prefix);out.write(0x0A); // animation + turn-to-tile
        putLE16(out,animationId);
        out.write(0);out.write(0);         // animation O(), O() => decoded zeroes
        putLE16LowAdd128(out,q);           // Client rs/x/e.U()
        putLE16(out,r);                    // Client rs/x/e.S()
        return out.toByteArray();
    }

    private static void putLE16(ByteArrayOutputStream out,int v){out.write(v&255);out.write((v>>>8)&255);}
    private static void putLE16LowAdd128(ByteArrayOutputStream out,int v){out.write(((v&255)+128)&255);out.write((v>>>8)&255);}
}
