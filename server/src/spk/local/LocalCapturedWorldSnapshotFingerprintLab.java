package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * R67 NONADMITTING read-only bounded canonical fingerprint of the
 * captured immutable domain PlayerSnapshot (NOT a byte-identical
 * legacy Properties file nor a durable World owner/final ACK).
 *
 * String components are UTF-8 length-prefixed. A schema/version/user
 * change changes the digest even when gameplay values coincide.
 */
final class LocalCapturedWorldSnapshotFingerprintLab {
    private static final int MAX_CANONICAL_BYTES=8*1024*1024;
    private static final int MAX_FIELDS=32768;
    private static final byte[] MAGIC=
        "G21395_WORLD_CAPTURE_CONTENT_ONLY_V1".getBytes(
            StandardCharsets.US_ASCII);
    private LocalCapturedWorldSnapshotFingerprintLab(){}

    static final class Observation {
        final Path exactAccount;
        final String username;
        final long worldGeneration;
        final int snapshotVersion;
        final int fieldCount;
        final int canonicalBytes;
        final String canonicalSha256;
        final boolean admissionAuthorized=false;
        final boolean finalSaveAcknowledged=false;
        final boolean releaseAuthorized=false;
        private Observation(Path path,PlayerSnapshot captured,
                            long generation,int count,int size,String sha){
            exactAccount=path;
            username=captured.username();
            worldGeneration=generation;
            snapshotVersion=captured.version();
            fieldCount=count;
            canonicalBytes=size;
            canonicalSha256=sha;
        }
    }

    private static final class Hasher {
        private final MessageDigest digest;
        private final java.io.ByteArrayOutputStream encoded=
            new java.io.ByteArrayOutputStream();
        private int bytes;
        private Hasher()throws IOException{
            try{digest=MessageDigest.getInstance("SHA-256");}
            catch(NoSuchAlgorithmException unavailable){
                throw new IOException("R67_SHA256_UNAVAILABLE",unavailable);
            }
        }
        void bytes(byte[] src)throws IOException{
            if(src==null||bytes>MAX_CANONICAL_BYTES-src.length)
                throw new IOException(
                    "R67_SNAPSHOT_CANONICAL_BOUNDS_EXCEEDED");
            digest.update(src);
            encoded.write(src,0,src.length);
            bytes+=src.length;
        }
        void integer(int n)throws IOException{
            bytes(new byte[]{
                (byte)(n>>>24),(byte)(n>>>16),
                (byte)(n>>>8),(byte)n
            });
        }
        void string(String value)throws IOException{
            if(value==null)
                throw new IOException("R67_NULL_CANONICAL_COMPONENT");
            byte[] b=value.getBytes(StandardCharsets.UTF_8);
            integer(b.length);
            bytes(b);
        }
        byte[] exactBytes(){return encoded.toByteArray();}
        String hex(){
            StringBuilder result=new StringBuilder(64);
            for(byte b:digest.digest()){
                String n=Integer.toHexString(b&255);
                if(n.length()==1)result.append('0');
                result.append(n);
            }
            return result.toString();
        }
    }

    private static Hasher canonical(
        Path selected,PlayerSnapshot snapshot,long generation
    )throws IOException{
        if(selected==null||snapshot==null||
           selected.getFileName()==null||generation<=0)
            throw new IOException("R67_CAPTURE_INPUT_INVALID");
        if(snapshot.values().size()>MAX_FIELDS)
            throw new IOException("R67_CAPTURE_TOO_MANY_FIELDS");
        Hasher canonical=new Hasher();
        canonical.integer(MAGIC.length);
        canonical.bytes(MAGIC);
        canonical.integer(snapshot.version());
        canonical.string(snapshot.username());
        canonical.integer(snapshot.values().size());
        String prior=null;
        for(Map.Entry<String,String> entry:snapshot.values().entrySet()){
            if(prior!=null&&prior.compareTo(entry.getKey())>=0)
                throw new IOException(
                    "R67_SNAPSHOT_NONCANONICAL_FIELD_ORDER");
            canonical.string(entry.getKey());
            canonical.string(entry.getValue());
            prior=entry.getKey();
        }
        return canonical;
    }

    static Observation fingerprint(
        Path selected,PlayerSnapshot snapshot,long generation
    )throws IOException{
        Hasher canonical=canonical(selected,snapshot,generation);
        int exactBytes=canonical.bytes;
        String sha=canonical.hex();
        return new Observation(
            selected,snapshot,generation,snapshot.values().size(),
            exactBytes,sha);
    }

    /**
     * R68: exactly the bytes whose SHA-256 R67 fingerprints. Still
     * LAB-only domain encoding, NOT a byte-identical legacy .properties
     * image, a live final save, or a durable positive receipt.
     */
    static byte[] canonicalBytes(
        Path selected,PlayerSnapshot snapshot,long generation
    )throws IOException{
        return canonical(selected,snapshot,generation).exactBytes();
    }
}
