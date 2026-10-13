package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.172: actual World save must not publish a raw actor's substituted
 * symlink or same-inode changed bytes as an account postimage.
 */
public final class G21172MailboxWorldTempPublicationIdentityIntegrationTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.172 "+reason);
    }

    private static PlayerSnapshot state(String account,String value){
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(account);
        PlayerSnapshot seed=PlayerSnapshotCodec.capture(account,player);
        TreeMap<String,String> values=new TreeMap<>(seed.values());
        values.put("extension.g21172.test",value);
        return new PlayerSnapshot(
            PlayerSnapshot.CURRENT_VERSION,account,values);
    }

    private static Path oneWorldTemp(Path root,String account)
        throws IOException{
        try(Stream<Path> entries=Files.list(root)){
            Path[] found=entries.filter(p->{
                String name=p.getFileName().toString();
                return name.startsWith(
                    account+".properties.g21170-world-")&&
                    name.endsWith(".tmp");
            }).toArray(Path[]::new);
            check(found.length==1,"expected exactly one pending World temp");
            return found[0];
        }
    }

    private static long tempCount(Path root)throws IOException{
        try(Stream<Path> files=Files.list(root)){
            return files.filter(p->p.getFileName().toString()
                .contains(".g21170-world-")).count();
        }
    }

    private static void veto(
        FilePlayerRepository repository,PlayerSnapshot wanted,
        String expected
    )throws Exception{
        try{
            repository.saveForWorld(wanted);
        }catch(IOException denied){
            check(String.valueOf(denied.getMessage()).contains(expected),
                "unexpected World refusal "+denied+" expected "+expected);
            return;
        }
        throw new AssertionError("G21.172 World improperly published");
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21172-world-temp-owner-");
        try{
            FilePlayerRepository.PathResolver resolver=
                name->root.resolve(name+".properties");
            FilePlayerRepository ordinary=new FilePlayerRepository(resolver);
            String account="g21172-account";
            Path accountPath=resolver.resolve(account);
            ordinary.saveForWorld(state(account,"PREIMAGE"));
            byte[] original=Files.readAllBytes(accountPath);
            byte[] victimBytes="G21172_OUTSIDE_VICTIM_DO_NOT_WRITE\n"
                .getBytes(StandardCharsets.US_ASCII);
            Path victim=root.resolve("victim.txt");
            Files.write(victim,victimBytes);

            // The source has serialized the World temp but G21.39
            // publication has not yet been acquired. Raw attack
            // replaces its pathname with a symlink to another file.
            AtomicBoolean symlinkSubstituted=new AtomicBoolean();
            FilePlayerRepository symlinkAttack=new FilePlayerRepository(
                resolver,name->{
                    Path temp=oneWorldTemp(root,name);
                    Files.delete(temp);
                    Files.createSymbolicLink(temp,victim);
                    symlinkSubstituted.set(true);
                });
            boolean symlinksSupported=true;
            try{
                veto(symlinkAttack,state(account,"SYMLINK_ATTACK"),
                    "G21.172 WORLD_TEMP_IDENTITY_UNSAFE_NO_AUTHORITY");
            }catch(UnsupportedOperationException|
                    java.nio.file.AccessDeniedException|
                    SecurityException unsupported){
                symlinksSupported=false;
            }
            check(Arrays.equals(victimBytes,Files.readAllBytes(victim)),
                "uncooperative symlink attack changed external victim");
            check(Arrays.equals(original,Files.readAllBytes(accountPath)),
                "World published substituted temp as account");
            if(symlinkSubstituted.get()){
                Path attackerLeaf=oneWorldTemp(root,account);
                check(Files.isSymbolicLink(attackerLeaf),
                    "World denial removed attacker-owned symlink");
                // Test fixture cleanup, NEVER production cleanup.
                Files.delete(attackerLeaf);
            }else{
                check(!symlinksSupported,
                    "symlink fixture unexpectedly produced no attack");
            }

            // Even without unlink or symlink, a raw actor can alter
            // the SAME inode after serialization. Compare SHA-256
            // inside the production World lock, not just fileKey.
            AtomicBoolean sameInodeTouched=new AtomicBoolean();
            FilePlayerRepository rawBytesAttack=new FilePlayerRepository(
                resolver,name->{
                    Path temp=oneWorldTemp(root,name);
                    byte[] bytes=Files.readAllBytes(temp);
                    check(bytes.length>0,"empty World temp fixture");
                    bytes[0]=(byte)(bytes[0]=='#'?'!':'#');
                    Files.write(temp,bytes,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                    sameInodeTouched.set(true);
                });
            veto(rawBytesAttack,state(account,"RAW_SAME_INODE"),
                "G21.172 WORLD_TEMP_BYTES_CHANGED_UNSAFE_NO_AUTHORITY");
            check(sameInodeTouched.get()&&tempCount(root)==0,
                "World bytes refusal leaked owned temp");
            check(Arrays.equals(original,Files.readAllBytes(accountPath)),
                "World published corrupted temp bytes");

            // Verify an unmodified World temp still publishes and
            // its freshly allocated name does not leak.
            ordinary.saveForWorld(state(account,"VALID_NEW_WORLD"));
            check(ordinary.load(account).isPresent()&&
                ordinary.load(account).get().values().equals(
                    state(account,"VALID_NEW_WORLD").values())&&
                tempCount(root)==0,
                "ordinary guarded World publication failed");
            ordinary.saveForWorld(state("g21172-other","INDEPENDENT"));
            check(ordinary.load("g21172-other").isPresent(),
                "independent World save stopped working");
            boolean noLeaks=
                MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0;
            check(noLeaks,"JVM publication lease/watch leak");
            System.out.println("G21172_WORLD_TEMP_IDENTITY_DIAGNOSTICS"+
                " rawSymlinkReplacementVetoes="+
                    (symlinkSubstituted.get()?1:0)+
                " symlinksSupported="+symlinksSupported+
                " sameInodeRawByteVetoes=1"+
                " victimUnchanged=true originalAccountUnchanged=true"+
                " legitimateWorldSaves=3"+
                " noOwnedWorldTempLeaks=true zeroJvmLeases="+noLeaks);
            System.out.println("G21172_WORLD_TEMP_IDENTITY_PASS"+
                " noFollowWorldTempStream=true"+
                " lockedPreMoveShaAndFileKeyChecked=true"+
                " attackerSymlinkNotUnlinkedByWorld=true"+
                " completeRawWriterExclusion=false"+
                " betweenWitnessAndMoveRaceExcluded=false"+
                " independentProvenance=false atomicCrashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
