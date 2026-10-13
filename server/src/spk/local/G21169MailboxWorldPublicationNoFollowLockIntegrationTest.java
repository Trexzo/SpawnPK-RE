package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.169: the REAL G21.39 World/marker cooperator must never
 * follow a symbolic publication-lock leaf or symlink ancestor before
 * executing any account publication. Ordinary raw save remains outside.
 */
public final class G21169MailboxWorldPublicationNoFollowLockIntegrationTest {
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.169 "+reason);
    }

    @FunctionalInterface private interface Checked{
        void run()throws Exception;
    }

    private static void veto(Checked callback,String text)throws Exception{
        try{
            callback.run();
        }catch(IOException refused){
            check(String.valueOf(refused.getMessage()).contains(text),
                "unexpected veto "+refused+" expected "+text);
            return;
        }
        throw new AssertionError(
            "G21.169 unsafe publisher operation returned: "+text);
    }

    private static PlayerSnapshot snapshot(String account){
        WorldPlayer player=new WorldPlayer();
        player.markRegistered(account);
        return PlayerSnapshotCodec.capture(account,player);
    }

    private static void run()throws Exception{
        Path root=Files.createTempDirectory("g21169-real-world-nofollow-");
        try{
            FilePlayerRepository.PathResolver resolver=
                account->root.resolve(account+".properties");
            FilePlayerRepository repository=new FilePlayerRepository(resolver);
            String normal="g21169-normal";
            String symlinked="g21169-leaf";
            String nonregular="g21169-directory";
            Path normalFile=resolver.resolve(normal);
            Path leafFile=resolver.resolve(symlinked);
            Path dirFile=resolver.resolve(nonregular);

            // Real World guarded save: same publication coordinator as
            // durable review-marker writers. This is ordinary legacy
            // snapshot data and does not grant mailbox rewards.
            repository.saveForWorld(snapshot(normal));
            check(Files.isRegularFile(normalFile),
                "ordinary guarded World file was not published");
            byte[] normalBytes=Files.readAllBytes(normalFile);
            check(normalBytes.length>0,"empty World publication");

            // Existing directory cannot be used as the lock leaf.
            Path directoryLeaf=MailboxAccountPublicationCoordinator
                .lockPath(dirFile);
            Files.createDirectory(directoryLeaf);
            AtomicBoolean unexpected=new AtomicBoolean(false);
            veto(()->MailboxAccountPublicationCoordinator
                .withExclusivePublication(dirFile,()->{
                    unexpected.set(true);
                    return null;
                }),"RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT");
            check(!unexpected.get()&&Files.isDirectory(directoryLeaf),
                "directory lock invoked callback or was replaced");

            // A symlink final component can point to an unrelated
            // victim. The original FileChannel.open(CREATE,WRITE)
            // would FOLLOW it, permitting a guarded World overwrite
            // despite the lock protocol being redirected.
            Path victim=root.resolve("g21169-victim-lock");
            byte[] sentinel="G21169_LOCK_VICTIM_DO_NOT_TOUCH\n"
                .getBytes(StandardCharsets.US_ASCII);
            Files.write(victim,sentinel);
            Path maliciousLock=MailboxAccountPublicationCoordinator
                .lockPath(leafFile);
            boolean symlinksSupported=true;
            try{
                Files.createSymbolicLink(maliciousLock,victim);
            }catch(UnsupportedOperationException|
                    java.nio.file.AccessDeniedException|
                    SecurityException unsupported){
                symlinksSupported=false;
            }
            if(symlinksSupported){
                veto(()->repository.saveForWorld(snapshot(symlinked)),
                    "RECOVERY_LOCK_FILE_UNSAFE_NO_GRANT");
                check(Files.isSymbolicLink(maliciousLock)&&
                    !Files.exists(leafFile,LinkOption.NOFOLLOW_LINKS)&&
                    Arrays.equals(sentinel,Files.readAllBytes(victim)),
                    "actual World writer bypassed unsafe lock leaf");
                Path temp=leafFile.resolveSibling(
                    leafFile.getFileName().toString()+".tmp");
                check(!Files.exists(temp,LinkOption.NOFOLLOW_LINKS),
                    "guarded World refusal left account tmp");
            }else{
                check(!Files.exists(leafFile,LinkOption.NOFOLLOW_LINKS),
                    "unsupported symlink path unexpectedly saved account");
            }

            // Even a valid lock leaf under a symlink directory
            // redirects the account publication root. The ancestry
            // must be refused before caller-owned publication executes.
            Path physical=root.resolve("g21169-physical");
            Files.createDirectories(physical);
            Path alias=root.resolve("g21169-alias");
            boolean aliasSupported=true;
            try{
                Files.createSymbolicLink(alias,physical);
            }catch(UnsupportedOperationException|
                    java.nio.file.AccessDeniedException|
                    SecurityException unsupported){
                aliasSupported=false;
            }
            AtomicBoolean aliasCalled=new AtomicBoolean(false);
            Path aliasFile=alias.resolve("escaping.properties");
            if(aliasSupported){
                veto(()->MailboxAccountPublicationCoordinator
                    .withExclusivePublication(aliasFile,()->{
                        aliasCalled.set(true);
                        Files.write(aliasFile,sentinel);
                        return null;
                    }),"RECOVERY_LOCK_ANCESTRY_UNSAFE_NO_GRANT");
                check(!aliasCalled.get()&&
                    !Files.exists(physical.resolve("escaping.properties")),
                    "redirected ancestor invoked the publisher");
            }

            // Different healthy accounts must continue to work after
            // unsafe-path vetoes, without leaked JVM scopes.
            repository.saveForWorld(snapshot("g21169-independent"));
            check(Arrays.equals(normalBytes,Files.readAllBytes(normalFile)),
                "unsafe account affected independent World snapshot");
            check(MailboxAccountPublicationCoordinator.activeJvmLeaseCount()==0&&
                MailboxLocalWriterAttemptWatchV1.activeLocalWatchCount()==0,
                "cooperating publisher leaked JVM lease/watch");

            System.out.println("G21169_WORLD_NOFOLLOW_DIAGNOSTICS"+
                " realWorldNormalPublishes=2"+
                " directoryLeafVetoes=1"+
                " symlinkLeafVetoes="+(symlinksSupported?1:0)+
                " symlinkAncestorVetoes="+(aliasSupported?1:0)+
                " symlinkSupport="+symlinksSupported+
                " aliasSupport="+aliasSupported+
                " externalVictimPreserved="+
                    Arrays.equals(sentinel,Files.readAllBytes(victim))+
                " originalWorldSnapshotUnchanged=true"+
                " zeroJvmLeases=true");
            System.out.println("G21169_WORLD_NOFOLLOW_PASS"+
                " productionCooperatorUsesNoFollow=true"+
                " prepublishLeafAndAncestryCheck=true"+
                " rawDefaultWriterFenced=false"+
                " futureUncooperativeWritersExcluded=false"+
                " descriptorBindingAtomic=false"+
                " independentProvenance=false crashEpoch=false"+
                " cleanup=false rollover=false pruning=false"+
                " unlink=false grant=false replay=false"+
                " session=false release=false ack=false");
        }finally{
            try(Stream<Path> files=Files.walk(root)){
                for(Path path:files.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))
                    Files.deleteIfExists(path);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        run();
    }
}
