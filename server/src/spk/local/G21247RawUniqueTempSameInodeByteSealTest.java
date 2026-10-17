package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.247 same-inode, same-length bytes tampering before RAW publication. */
public final class G21247RawUniqueTempSameInodeByteSealTest {
    private static final String NAMED="g21247-named";
    private static final String RAW="g21247-repository-raw";
    private G21247RawUniqueTempSameInodeByteSealTest(){}
    private static void check(boolean yes,String reason){
        if(!yes)throw new AssertionError("G21.247 "+reason);
    }

    private static void corruptUniqueTemp(Path account)throws IOException{
        Path temp;
        String prefix=account.getFileName().toString()+".g21246-raw-";
        try(Stream<Path> files=Files.list(account.getParent())){
            Path[] match=files.filter(path->path.getFileName().toString()
                .startsWith(prefix)).toArray(Path[]::new);
            check(match.length==1,"expected exactly one unique RAW temp");
            temp=match[0];
        }
        check(!Files.isSymbolicLink(temp),"selected temp was symlink");
        Object beforeKey=Files.readAttributes(
            temp,java.nio.file.attribute.BasicFileAttributes.class,
            LinkOption.NOFOLLOW_LINKS).fileKey();
        FileTime beforeTime=Files.getLastModifiedTime(
            temp,LinkOption.NOFOLLOW_LINKS);
        byte[] original=Files.readAllBytes(temp);
        check(original.length>0,"serialized RAW temp unexpectedly empty");
        byte[] corrupt=original.clone();
        corrupt[0]=(byte)(original[0]=='#'?'!':'#');
        Files.write(temp,corrupt,StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS);
        Files.setLastModifiedTime(temp,beforeTime);
        check(original.length==Files.size(temp),
            "tamper changed temp length, test invalid");
        check(beforeKey.equals(Files.readAttributes(
                temp,java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).fileKey()),
            "tamper accidentally replaced inode, test invalid");
        check(beforeTime.equals(Files.getLastModifiedTime(
                temp,LinkOption.NOFOLLOW_LINKS)),
            "tamper did not restore mtime");
    }

    private static void ensureNoUniqueTemp(Path account)throws IOException{
        Path parent=account.getParent();
        String prefix=account.getFileName().toString()+".g21246-raw-";
        try(Stream<Path> files=Files.list(parent)){
            check(files.noneMatch(x->x.getFileName().toString()
                .startsWith(prefix)),
                "failed RAW temp was not cleaned after denial");
        }
    }

    private static boolean bytesVeto(Throwable t){
        return t instanceof IOException&&
            String.valueOf(t.getMessage()).contains(
                "SAFE_RAW_TEMP_BYTES_CHANGED");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("g21247-raw-byte-seal-");
        String[] keys={"spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY,
            LocalOptInRawTemp.PROPERTY};
        String[] prior=new String[keys.length];
        for(int i=0;i<keys.length;i++)prior[i]=System.getProperty(keys[i]);
        System.setProperty(keys[0],
            root.resolve("opensrc.properties").toString());
        for(int i=1;i<keys.length;i++)System.setProperty(keys[i],"true");
        try{
            WorldPlayer p=new WorldPlayer();
            Path primary=AccountStore.accountFile();
            AccountStore.save(p.bank(),p.equipment(),p.movement(),
                p.petState(),p.playerState());
            byte[] primaryBytes=Files.readAllBytes(primary);
            boolean veto=false;
            try{
                AccountStore.saveWithPreMoveHookForTest(
                    p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState(),user->corruptUniqueTemp(primary));
            }catch(IOException expected){veto=bytesVeto(expected);}
            check(veto,"primary did not detect in-place RAW temp tampering");
            check(Arrays.equals(primaryBytes,Files.readAllBytes(primary)),
                "primary overwrite after tamper");
            ensureNoUniqueTemp(primary);

            Path named=LocalAccountProfiles.accountFile(NAMED);
            LocalAccountProfiles.save(NAMED,p.bank(),p.equipment(),
                p.movement(),p.petState(),p.playerState());
            byte[] namedBytes=Files.readAllBytes(named);
            veto=false;
            try{
                LocalAccountProfiles.saveWithPreMoveHookForTest(
                    NAMED,p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState(),user->corruptUniqueTemp(named));
            }catch(IOException expected){veto=bytesVeto(expected);}
            check(veto,"named writer did not detect in-place RAW temp tampering");
            check(Arrays.equals(namedBytes,Files.readAllBytes(named)),
                "named account overwritten after tamper");
            ensureNoUniqueTemp(named);

            Path raw=LocalAccountProfiles.accountFile(RAW);
            FilePlayerRepository clean=new FilePlayerRepository(
                LocalAccountProfiles::accountFile);
            p.snapshotExtensions().replaceNamespace(
                "core_g21247",Collections.singletonMap("phase","BEFORE"));
            clean.save(PlayerSnapshotCodec.capture(RAW,p));
            byte[] rawBytes=Files.readAllBytes(raw);
            FilePlayerRepository attacked=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                user->{},user->{},user->{},user->{},
                user->corruptUniqueTemp(raw));
            p.snapshotExtensions().replaceNamespace(
                "core_g21247",Collections.singletonMap("phase","UNSAFE"));
            veto=false;
            try{attacked.save(PlayerSnapshotCodec.capture(RAW,p));}
            catch(IOException expected){veto=bytesVeto(expected);}
            check(veto,"repository RAW did not detect in-place temp tampering");
            check(Arrays.equals(rawBytes,Files.readAllBytes(raw)),
                "repository RAW overwrote old postimage");
            ensureNoUniqueTemp(raw);
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked after failed byte seal");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "test left JVM owner active");

            System.out.println(
                "G21247_RAW_TEMP_SAME_INODE_BYTE_SEAL_PASS"+
                " primaryNamedRepositoryDenied=true"+
                " lengthAndFileKeyUnchanged=true"+
                " mtimeRestored=true"+
                " originalAccountBytesPreserved=true"+
                " abandonedOwnedTempRemoved=true"+
                " boundedSha256=true"+
                " hostilePostcheckRaceNotClosed=true"+
                " durableEpoch=false");
        }finally{
            for(int i=0;i<keys.length;i++){
                if(prior[i]==null)System.clearProperty(keys[i]);
                else System.setProperty(keys[i],prior[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(file);
            }
        }
    }
}
