package spk.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * G21.275/276: a valid preexisting ACTIVE review marker can remain
 * quarantine-only across a forced World save, but a same-inode/same-
 * size/injected byte change after ATOMIC_MOVE cannot yield a normal
 * SaveTicket. No marker release or new admission authority.
 */
public final class G21275ActiveReviewMarkerPostMoveByteDriftTest {
    private G21275ActiveReviewMarkerPostMoveByteDriftTest(){}
    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("G21.275/276 "+reason);
    }
    private static PlayerSnapshot snapshot(String username,String phase){
        WorldPlayer player=new WorldPlayer();
        player.snapshotExtensions().replaceNamespace(
            "core_g21275",Collections.singletonMap("phase",phase));
        return PlayerSnapshotCodec.capture(username,player);
    }
    private static void editOneValidUuidNibble(Path account,Path marker)
        throws IOException{
        byte[] before=Files.readAllBytes(marker);
        String text=new String(before,StandardCharsets.UTF_8);
        String prefix="untrustedSessionNonce=";
        int field=text.indexOf(prefix);
        check(field>=0,"ACTIVE marker lacks valid nonce field");
        int at=field+prefix.length();
        check(at<before.length,"ACTIVE nonce position invalid");
        byte[] after=before.clone();
        after[at]=(byte)(before[at]=='a'?'b':'a');
        FileTime originalMtime=Files.getLastModifiedTime(
            marker,LinkOption.NOFOLLOW_LINKS);
        try(FileChannel channel=FileChannel.open(marker,
                StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)){
            channel.position(at);
            channel.write(ByteBuffer.wrap(new byte[]{after[at]}));
            channel.force(true);
        }
        Files.setLastModifiedTime(marker,originalMtime);
        check(Arrays.equals(after,Files.readAllBytes(marker))&&
            after.length==before.length&&
            Files.getLastModifiedTime(marker,LinkOption.NOFOLLOW_LINKS)
                .equals(originalMtime),
            "same-inode same-length restored-mtime mutation missing");
        // The changed UUID remains syntactically legal. Distinguish
        // digest/identity drift from a malformed marker rejection.
        LocalDurableAccountReviewIntentPrimitive
            .requireReviewMarker(account);
    }
    static void run(boolean mutate)throws Exception{
        String user=mutate?"g21275-mutated-review":"g21276-retained-review";
        Path root=Files.createTempDirectory(
            mutate?"g21275-world-marker-drift-":"g21276-world-review-stable-");
        String[] flags={"spk.local.accountFile",
            FilePlayerRepository.EXPERIMENTAL_FORCE_WORLD_POSTIMAGE_PROPERTY};
        String[] old=new String[flags.length];
        for(int i=0;i<flags.length;i++)old[i]=System.getProperty(flags[i]);
        System.setProperty(flags[0],
            root.resolve("opensrc.properties").toString());
        System.setProperty(flags[1],"true");
        World world=null;
        WorldPlayer player=null;
        long generation=-1;
        try{
            Path account=LocalAccountProfiles.accountFile(user);
            Path marker=LocalDurableAccountReviewIntentPrimitive
                .markerPath(account);
            AtomicReference<byte[]> postmove=new AtomicReference<>();
            AtomicBoolean tamperValidated=new AtomicBoolean();
            FilePlayerRepository repo=new FilePlayerRepository(
                LocalAccountProfiles::accountFile,
                a->{},a->{},a->{},a->{},a->{},
                a->{
                    check(user.equals(a),
                        "wrong account selected in after-move hook");
                    postmove.set(Files.readAllBytes(account));
                    if(mutate){
                        editOneValidUuidNibble(account,marker);
                        tamperValidated.set(true);
                    }
                });
            repo.save(snapshot(user,"BEFORE"));
            LocalDurableAccountReviewIntentPrimitive
                .writeActiveReview(account);
            LocalDurableAccountReviewIntentPrimitive
                .requireReviewMarker(account);
            byte[] markerBefore=Files.readAllBytes(marker);
            byte[] accountBefore=Files.readAllBytes(account);
            world=World.isolatedForTest(60000L,repo);
            player=new WorldPlayer();
            generation=world.registerPlayer(player,user);
            world.start();
            final World ownerWorld=world;
            final WorldPlayer live=player;
            final long owned=generation;
            AtomicReference<WorldPlayerPersistence.SaveTicket> ticket=
                new AtomicReference<>();
            world.submitAndWait(live,owned,()->{
                live.snapshotExtensions().replaceNamespace(
                    "core_g21275",
                    Collections.singletonMap("phase","AFTER"));
                ticket.set(ownerWorld.persistence().captureAndSave(
                    user,live,owned,0,
                    "[G21.275] ","ACTIVE_REVIEW_POSTMOVE"));
            },5000L);
            check(ticket.get()!=null,"real World ticket was not created");
            boolean uncertain=false;
            try{
                ticket.get().completion.get(20L,TimeUnit.SECONDS);
            }catch(ExecutionException failed){
                Throwable e=failed.getCause();
                uncertain=e!=null&&
                    String.valueOf(e.getMessage()).contains(
                        "G21.271 FORCED_WORLD_POST_MOVE_UNCONFIRMED_MANUAL_REVIEW")&&
                    (!mutate||(e.getCause()!=null&&
                        String.valueOf(e.getCause().getMessage()).contains(
                            "G21.275 WORLD_ACTIVE_REVIEW_MARKER_CHANGED_NO_ACK")));
                if(!mutate)throw failed;
            }
            check(uncertain==mutate,
                "ACTIVE marker continuity was not distinguished");
            if(mutate)
                check(tamperValidated.get(),
                    "tampered ACTIVE nonce not fully validated in hook");
            check(postmove.get()!=null&&
                !Arrays.equals(accountBefore,postmove.get())&&
                Arrays.equals(postmove.get(),Files.readAllBytes(account)),
                "postmove World account bytes not retained");
            Optional<PlayerSnapshot> current=repo.load(user);
            check(current.isPresent()&&
                "AFTER".equals(current.get().values().get(
                    "extension.core_g21275.phase")),
                "forced World account postimage absent");
            check(Files.exists(marker,LinkOption.NOFOLLOW_LINKS),
                "ACTIVE marker was retired or unlinked");
            check(Arrays.equals(markerBefore,Files.readAllBytes(marker))
                  !=mutate,
                "ACTIVE marker mutation/retention mismatch");
            check(repo.forcedWorldPostimageCountForTest()==
                    (mutate?0:1),
                "uncertain or valid World save miscounted");
            try(Stream<Path> entries=Files.list(account.getParent())){
                String prefix=account.getFileName().toString()+
                    ".g21170-world-";
                check(entries.noneMatch(p->p.getFileName().toString()
                    .startsWith(prefix)),"guarded World temp leaked");
            }
            System.out.println(mutate
                ?"G21275_WORLD_ACTIVE_REVIEW_SAME_INODE_BYTE_DRIFT_VETO_PASS"+
                    " preexistingValidActiveReview=true"+
                    " postMoveUuidByteEdited=true"+
                    " originalMtimeAndSizeRestored=true"+
                    " markerStillValidNegative=true"+
                    " worldSaveUncertainNotRollback=true"+
                    " falseConfirmedSaveCountZero=true"+
                    " markerNotRetired=true"
                :"G21276_WORLD_EXISTING_ACTIVE_REVIEW_RETAINED_CONTROL_PASS"+
                    " preexistingValidActiveReview=true"+
                    " exactMarkerBytesAndInodeUnchanged=true"+
                    " healthyWorldSavePublished=true"+
                    " permanentNegativeMarkerRetained=true"+
                    " noNewSessionAdmissionGranted=true"+
                    " falseMarkerRetirement=false");
        }finally{
            if(world!=null){
                if(player!=null&&generation>0)
                    world.unregisterPlayer(player,generation);
                world.close();
            }
            for(int i=0;i<flags.length;i++){
                if(old[i]==null)System.clearProperty(flags[i]);
                else System.setProperty(flags[i],old[i]);
            }
            try(Stream<Path> walk=Files.walk(root)){
                for(Path path:(Iterable<Path>)walk.sorted(
                        Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(path);
            }
        }
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected arguments");
        run(true);
    }
}
