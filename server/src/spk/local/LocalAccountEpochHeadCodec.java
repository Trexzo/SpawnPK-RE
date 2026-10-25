package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * R152: UNWIRED, NON-ADMITTING epoch HEAD byte codec. Decoding a HEAD
 * does NOT prove account ownership, durable publication, epoch ancestry,
 * filesystem identity, final-save ACK, or recovery authority. No I/O.
 */
final class LocalAccountEpochHeadCodec {
    static final String MAGIC = "G21377_ACCOUNT_EPOCH_HEAD_V1";
    static final int MAX_HEAD_BYTES = 1024;
    static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    static final String ZERO_SHA =
        "0000000000000000000000000000000000000000000000000000000000000000";
    enum State { COMMITTED, ACTIVE, FINAL_COMMITTED, REVIEW }

    static final class Record {
        final String account, previousHeadSha256, snapshotSha256;
        final long epoch, sequence, worldGeneration;
        final State state;
        Record(String account, long epoch, long sequence, long worldGeneration,
               State state, String previousHeadSha256, String snapshotSha256) {
            this.account=account;
            this.epoch=epoch;
            this.sequence=sequence;
            this.worldGeneration=worldGeneration;
            this.state=state;
            this.previousHeadSha256=previousHeadSha256;
            this.snapshotSha256=snapshotSha256;
        }
    }
    private LocalAccountEpochHeadCodec(){}

    static byte[] encode(Record r)throws IOException{
        if(r==null || r.account==null ||
           !r.account.matches("[a-z0-9_-]{1,64}") ||
           r.epoch<=0 || r.sequence<=0 || r.worldGeneration<=0 ||
           r.state==null || !validSha(r.previousHeadSha256) ||
           !validSha(r.snapshotSha256))
            throw new IOException("R152_INVALID_HEAD_FIELDS_NO_AUTHORITY");
        String body = MAGIC+"\n"+
            "account="+r.account+"\n"+
            "epoch="+r.epoch+"\n"+
            "sequence="+r.sequence+"\n"+
            "worldGeneration="+r.worldGeneration+"\n"+
            "state="+r.state.name()+"\n"+
            "previousHeadSha256="+r.previousHeadSha256+"\n"+
            "snapshotSha256="+r.snapshotSha256+"\n";
        byte[] bytes=body.getBytes(StandardCharsets.US_ASCII);
        if(bytes.length>MAX_HEAD_BYTES)
            throw new IOException("R152_HEAD_OVERSIZE_NO_AUTHORITY");
        return bytes;
    }

    static Record decode(byte[] bytes)throws IOException{
        if(bytes==null || bytes.length==0 || bytes.length>MAX_HEAD_BYTES)
            throw new IOException("R152_HEAD_SIZE_NO_AUTHORITY");
        for(byte b:bytes){
            int c=b&255;
            if(c!='\n' && (c<32||c>126))
                throw new IOException("R152_NONASCII_HEAD_NO_AUTHORITY");
        }
        String[] lines=new String(bytes,StandardCharsets.US_ASCII)
            .split("\n",-1);
        if(lines.length!=9 || !MAGIC.equals(lines[0]) ||
           !lines[8].isEmpty())
            throw new IOException("R152_NONCANONICAL_HEAD_STRUCTURE");
        State state;
        try{
            state=State.valueOf(field(lines[5],"state="));
        }catch(IllegalArgumentException invalid){
            throw new IOException("R152_UNKNOWN_HEAD_STATE",invalid);
        }
        Record r=new Record(
            field(lines[1],"account="),
            positive(field(lines[2],"epoch=")),
            positive(field(lines[3],"sequence=")),
            positive(field(lines[4],"worldGeneration=")),
            state,field(lines[6],"previousHeadSha256="),
            field(lines[7],"snapshotSha256="));
        if(!Arrays.equals(bytes,encode(r)))
            throw new IOException("R152_ALTERNATE_HEAD_ENCODING");
        return r;
    }

    private static String field(String line,String key)throws IOException{
        if(!line.startsWith(key))
            throw new IOException("R152_HEAD_FIELD_ORDER_INVALID");
        return line.substring(key.length());
    }
    private static long positive(String value)throws IOException{
        if(value.length()==0||value.length()>19||
           !value.matches("[1-9][0-9]*"))
            throw new IOException("R152_HEAD_NUMBER_NONCANONICAL");
        try{return Long.parseLong(value);}
        catch(NumberFormatException bad){
            throw new IOException("R152_HEAD_NUMBER_OVERFLOW",bad);
        }
    }
    private static boolean validSha(String value){
        return value!=null&&value.matches("[0-9a-f]{64}");
    }

    static String digestSnapshot(byte[] bytes)throws IOException{
        if(bytes==null || bytes.length==0 ||
           bytes.length>MAX_SNAPSHOT_BYTES)
            throw new IOException("R152_SNAPSHOT_SIZE_NO_AUTHORITY");
        final byte[] digest;
        try{digest=MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){
            throw new IOException("R152_SHA256_UNAVAILABLE",impossible);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest){
            int value=b&255;
            hex.append(Character.forDigit(value>>>4,16));
            hex.append(Character.forDigit(value&15,16));
        }
        return hex.toString();
    }

    /** Equality of raw bytes does not establish provenance or durability. */
    static void requireSnapshotDigest(Record r,byte[] bytes)
        throws IOException{
        if(r==null||!validSha(r.snapshotSha256))
            throw new IOException("R152_HEAD_UNTRUSTED");
        String observed=digestSnapshot(bytes);
        if(!MessageDigest.isEqual(
            observed.getBytes(StandardCharsets.US_ASCII),
            r.snapshotSha256.getBytes(StandardCharsets.US_ASCII)))
            throw new IOException("R152_SNAPSHOT_DIGEST_MISMATCH");
    }
}
