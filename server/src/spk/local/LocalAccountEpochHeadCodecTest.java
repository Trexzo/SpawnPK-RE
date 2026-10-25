package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/** R152: bounded input and deterministic rejection; no filesystem/World. */
public final class LocalAccountEpochHeadCodecTest {
    private static int refused;
    @FunctionalInterface interface Action { void invoke()throws Exception; }
    private static void check(boolean good,String msg){
        if(!good)throw new AssertionError("R152 "+msg);
    }
    private static void deny(String label,Action action)throws Exception{
        try{action.invoke();}
        catch(IOException expected){refused++;return;}
        throw new AssertionError("R152 incorrectly accepted "+label);
    }
    private static byte[] bad(String base,String find,String replacement){
        check(base.contains(find),"missing mutation "+find);
        return base.replace(find,replacement)
            .getBytes(StandardCharsets.US_ASCII);
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        byte[] snapshot="exact raw World bytes".getBytes(
            StandardCharsets.US_ASCII);
        String sha=LocalAccountEpochHeadCodec.digestSnapshot(snapshot);
        LocalAccountEpochHeadCodec.Record head=
            new LocalAccountEpochHeadCodec.Record(
                "account_1",1,7,23,LocalAccountEpochHeadCodec.State.ACTIVE,
                LocalAccountEpochHeadCodec.ZERO_SHA,sha);
        byte[] canonical=LocalAccountEpochHeadCodec.encode(head);
        String body=new String(canonical,StandardCharsets.US_ASCII);
        for(LocalAccountEpochHeadCodec.State state:
                LocalAccountEpochHeadCodec.State.values()){
            LocalAccountEpochHeadCodec.Record original=
                new LocalAccountEpochHeadCodec.Record(
                    head.account,head.epoch,head.sequence,
                    head.worldGeneration,state,
                    head.previousHeadSha256,head.snapshotSha256);
            byte[] bytes=LocalAccountEpochHeadCodec.encode(original);
            LocalAccountEpochHeadCodec.Record parsed=
                LocalAccountEpochHeadCodec.decode(bytes);
            check(parsed.state==state && parsed.epoch==1 &&
                  parsed.sequence==7 && parsed.worldGeneration==23 &&
                  parsed.account.equals("account_1") &&
                  Arrays.equals(bytes,LocalAccountEpochHeadCodec.encode(parsed)),
                  "canonical roundtrip "+state);
            LocalAccountEpochHeadCodec.requireSnapshotDigest(parsed,snapshot);
        }
        deny("null",()->LocalAccountEpochHeadCodec.decode(null));
        deny("empty",()->LocalAccountEpochHeadCodec.decode(new byte[0]));
        deny("oversize HEAD",()->LocalAccountEpochHeadCodec.decode(
            new byte[LocalAccountEpochHeadCodec.MAX_HEAD_BYTES+1]));
        deny("truncated newline",()->LocalAccountEpochHeadCodec.decode(
            Arrays.copyOf(canonical,canonical.length-1)));
        deny("unknown version",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"_HEAD_V1","_HEAD_V2")));
        deny("invalid account",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"account=account_1","account=../bad")));
        deny("extra field",()->LocalAccountEpochHeadCodec.decode(
            (body+"grant=true\n").getBytes(StandardCharsets.US_ASCII)));
        deny("duplicate state",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"state=ACTIVE\n","state=ACTIVE\nstate=ACTIVE\n")));
        deny("field reordered",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"epoch=1\nsequence=7\n","sequence=7\nepoch=1\n")));
        deny("unknown state",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"state=ACTIVE","state=RELEASED")));
        deny("zero epoch",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"epoch=1","epoch=0")));
        deny("leading-zero epoch",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"epoch=1","epoch=01")));
        deny("negative sequence",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"sequence=7","sequence=-7")));
        deny("overflow generation",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"worldGeneration=23",
                "worldGeneration=9223372036854775808")));
        deny("noncanonical SHA uppercase",()->
            LocalAccountEpochHeadCodec.decode(
                bad(body,"snapshotSha256="+sha,
                    "snapshotSha256="+sha.toUpperCase(Locale.ROOT))));
        deny("short predecessor",()->LocalAccountEpochHeadCodec.decode(
            bad(body,"previousHeadSha256="+LocalAccountEpochHeadCodec.ZERO_SHA,
                "previousHeadSha256=abcd")));
        deny("CRLF",()->LocalAccountEpochHeadCodec.decode(
            body.replace("\n","\r\n").getBytes(StandardCharsets.US_ASCII)));
        deny("extra newline",()->LocalAccountEpochHeadCodec.decode(
            (body+"\n").getBytes(StandardCharsets.US_ASCII)));
        byte[] control=Arrays.copyOf(canonical,canonical.length);
        control[4]=0;
        deny("embedded NUL",()->LocalAccountEpochHeadCodec.decode(control));
        byte[] unicode=Arrays.copyOf(canonical,canonical.length);
        unicode[4]=(byte)0xff;
        deny("nonASCII",()->LocalAccountEpochHeadCodec.decode(unicode));
        deny("changed snapshot",()->
            LocalAccountEpochHeadCodec.requireSnapshotDigest(
                head,"tampered".getBytes(StandardCharsets.US_ASCII)));
        deny("missing snapshot",()->
            LocalAccountEpochHeadCodec.digestSnapshot(null));
        deny("empty snapshot",()->
            LocalAccountEpochHeadCodec.digestSnapshot(new byte[0]));
        deny("oversize snapshot",()->
            LocalAccountEpochHeadCodec.digestSnapshot(
                new byte[LocalAccountEpochHeadCodec.MAX_SNAPSHOT_BYTES+1]));
        deny("invalid record",()->LocalAccountEpochHeadCodec.encode(
            new LocalAccountEpochHeadCodec.Record(
                "Bad",1,1,1,LocalAccountEpochHeadCodec.State.ACTIVE,
                LocalAccountEpochHeadCodec.ZERO_SHA,sha)));
        deny("missing state",()->LocalAccountEpochHeadCodec.encode(
            new LocalAccountEpochHeadCodec.Record(
                "account",1,1,1,null,
                LocalAccountEpochHeadCodec.ZERO_SHA,sha)));
        deny("null record",()->LocalAccountEpochHeadCodec.encode(null));
        check(refused==27,"negative-case total "+refused);
        System.out.println("R152_NONADMITTING_HEAD_CODEC_PASS"+
            " states=4 rejectedMalformedInputs="+refused+
            " canonicalRoundtrip=true snapshotDigestBounded=true"+
            " noFilesystemOrWorldIO=true noAdmitOrFinalACK=true");
    }
}
