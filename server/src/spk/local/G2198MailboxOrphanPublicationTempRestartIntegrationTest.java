package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * G21.98: real process death leaves orphan .tmp or linked .tmp alias.
 * No caller may promote these untrusted names to canonical authority.
 */
public final class G2198MailboxOrphanPublicationTempRestartIntegrationTest {
    private enum Cut {
        JOURNAL_BEFORE_LINK(102,Kind.JOURNAL,false,false,false),
        JOURNAL_AFTER_LINK(103,Kind.JOURNAL,true,false,true),
        STRICT_AFTER_TEMP_CREATE(104,Kind.STRICT,true,false,false),
        STRICT_BEFORE_ATOMIC_REPLACE(105,Kind.STRICT,true,false,false),
        COMMIT_BEFORE_LINK(106,Kind.COMMIT,true,true,false),
        COMMIT_AFTER_LINK(107,Kind.COMMIT,true,true,true);

        final int exitCode;
        final Kind kind;
        final boolean journalLeaf;
        final boolean terminalAccount;
        final boolean alias;
        Cut(int code,Kind k,boolean journal,
            boolean terminal,boolean linkAlias){
            exitCode=code;
            kind=k;
            journalLeaf=journal;
            terminalAccount=terminal;
            alias=linkAlias;
        }
        String account(){
            return "g2198-"+name().toLowerCase(Locale.ROOT)
                .replace('_','-');
        }
        boolean commitLeaf(){
            return kind==Kind.COMMIT&&alias;
        }
    }
    private enum Kind { JOURNAL, STRICT, COMMIT }

    private static final class Evidence {
        final Path temp;
        final byte[] tempBytes;
        final String tempFileKey;
        final byte[][] authoritativeLeaves;
        Evidence(Path path,byte[] bytes,String key,byte[][] leaves){
            temp=path;
            tempBytes=bytes;
            tempFileKey=key;
            authoritativeLeaves=leaves;
        }
    }

    private static void require(boolean ok,String why){
        if(!ok)throw new AssertionError("G21.98 "+why);
    }
    private static FilePlayerRepository.PathResolver paths(Path root){
        return account->root.resolve(account+".properties");
    }
    private static boolean present(Path p){
        return Files.exists(p,LinkOption.NOFOLLOW_LINKS);
    }
    private static byte[][] canonical(Path root,String account)
        throws IOException{
        FilePlayerRepository.PathResolver resolver=paths(root);
        Path file=resolver.resolve(account);
        Path journal=new MailboxDurableIdempotencyIntentJournal(resolver)
            .journalPath(account);
        Path commit=new MailboxGuardedDiskCommitRecord(resolver)
            .recordPath(account);
        return new byte[][]{
            present(file)?Files.readAllBytes(file):null,
            present(journal)?Files.readAllBytes(journal):null,
            present(commit)?Files.readAllBytes(commit):null
        };
    }
    private static boolean sameLeaves(byte[][] a,byte[][] b){
        for(int i=0;i<3;i++)
            if(!Arrays.equals(a[i],b[i]))return false;
        return true;
    }
    private static String key(Path file)throws IOException{
        BasicFileAttributes attrs=Files.readAttributes(
            file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        require(attrs.isRegularFile(),"orphan temp not a regular leaf");
        require(attrs.size()<=1024*1024,
            "orphan temp unbounded metadata size");
        require(attrs.fileKey()!=null,
            "test environment lacks physical file identity witness");
        return attrs.fileKey().toString();
    }
    private static String prefix(Path root,Cut cut){
        String filename=cut.account()+".properties";
        if(cut.kind==Kind.STRICT)
            return filename+".g2123-";
        if(cut.kind==Kind.JOURNAL)
            return filename+
                MailboxDurableIdempotencyIntentJournal.SUFFIX+
                ".write-";
        return filename+MailboxGuardedDiskCommitRecord.SUFFIX+
            ".write-";
    }
    private static Evidence observe(
        Path root,Cut cut
    )throws IOException{
        final String starts=prefix(root,cut);
        final Path temp;
        try(Stream<Path> files=Files.list(root)){
            Path[] found=files.filter(x->
                x.getFileName().toString().startsWith(starts)&&
                x.getFileName().toString().endsWith(".tmp"))
                .toArray(Path[]::new);
            require(found.length==1,
                "expected exactly one crashed publication temp"+
                " cut="+cut+" found="+found.length);
            temp=found[0];
        }
        byte[] orphan=Files.readAllBytes(temp);
        return new Evidence(temp,orphan,key(temp),
            canonical(root,cut.account()));
    }

    private static void hardHalt(Cut cut,String stage){
        System.out.println("G2198_CHILD_ORPHAN_CUT"+
            " cut="+cut.name()+" stage="+stage+
            " noGrant=true noCleanup=true");
        System.out.flush();
        Runtime.getRuntime().halt(cut.exitCode);
        throw new AssertionError("G21.98 Runtime.halt returned");
    }

    private static void crashWriter(Path root,Cut cut)
        throws Exception{
        String account=cut.account();
        FilePlayerRepository.PathResolver resolver=paths(root);
        try(World source=World.isolatedForTest(60000L)){
            WorldPlayer owner=new WorldPlayer();
            long generation=source.registerPlayer(owner,account);
            owner.mailbox().deliver(new RewardDeliveryMessage(
                account+":gift","No Replay","NO_GRANT",
                Collections.singletonList(
                    new RewardDeliveryMessage.Attachment(995,25)),
                "CUSTOM_LOCALLAB_G2198_FIXTURE"));
            MailboxRewardDeliveryService.Snapshot row=
                owner.mailbox().get(account+":gift");
            MailboxPreparedClaimJournal.stageOnly(owner,
                MailboxPreparedClaimJournal.prepare(owner,row));
            MailboxSettlementPostimagePlanner.Proposal plan=
                MailboxSettlementPostimagePlanner.plan(
                    owner,generation,row);
            require(row.claimState==
                MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                owner.bank().inventorySlots()==0,
                "seed must never issue live reward");

            StrictDurablePlayerSnapshotWriter strict=
                new StrictDurablePlayerSnapshotWriter(resolver);
            strict.saveStrict(plan.preparedPreimage);
            MailboxDurableIdempotencyIntentJournal journal=
                new MailboxDurableIdempotencyIntentJournal(resolver);
            if(cut.kind==Kind.JOURNAL){
                new MailboxDurableIdempotencyIntentJournal(resolver,
                    phase->{
                        if((cut==Cut.JOURNAL_BEFORE_LINK&&
                            phase==MailboxDurableIdempotencyIntentJournal
                                .Phase.BEFORE_LINK)||
                           (cut==Cut.JOURNAL_AFTER_LINK&&
                            phase==MailboxDurableIdempotencyIntentJournal
                                .Phase.AFTER_LINK))
                            hardHalt(cut,"journal_"+phase);
                    }).publishPreparedIntent(plan);
                throw new AssertionError("G21.98 journal did not halt");
            }
            journal.publishPreparedIntent(plan);

            PlayerSnapshot terminal=
                MailboxAtomicTerminalSnapshot.compose(plan);
            String sha=StrictDurablePlayerSnapshotWriter
                .canonicalSnapshotSha256(plan.preparedPreimage);
            if(cut.kind==Kind.STRICT){
                new StrictDurablePlayerSnapshotWriter(resolver,
                    phase->{
                        if((cut==Cut.STRICT_AFTER_TEMP_CREATE&&
                            phase==StrictDurablePlayerSnapshotWriter
                                .Phase.AFTER_TEMP_CREATE)||
                           (cut==Cut.STRICT_BEFORE_ATOMIC_REPLACE&&
                            phase==StrictDurablePlayerSnapshotWriter
                                .Phase.BEFORE_ATOMIC_REPLACE))
                            hardHalt(cut,"strict_"+phase);
                    }).saveStrictTerminalForWorld(
                        terminal,resolver.resolve(account),
                        sha,()->{},()->{});
                throw new AssertionError("G21.98 strict did not halt");
            }
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                strict.saveStrictTerminalForWorld(
                    terminal,resolver.resolve(account),
                    sha,()->{},()->{});
            require(receipt.matchesSnapshot(terminal),
                "strict terminal receipt mismatched");
            new MailboxGuardedDiskCommitRecord(resolver,
                phase->{
                    if((cut==Cut.COMMIT_BEFORE_LINK&&
                        phase==MailboxGuardedDiskCommitRecord
                            .Phase.BEFORE_LINK)||
                       (cut==Cut.COMMIT_AFTER_LINK&&
                        phase==MailboxGuardedDiskCommitRecord
                            .Phase.AFTER_LINK))
                        hardHalt(cut,"commit_"+phase);
                }).recordConfirmedDiskTerminal(plan,receipt);
            throw new AssertionError("G21.98 COMMIT did not halt");
        }
    }

    private static void restartReadOnly(Path root,Cut cut)
        throws Exception{
        final String account=cut.account();
        final FilePlayerRepository.PathResolver resolver=paths(root);
        final FilePlayerRepository repository=
            new FilePlayerRepository(resolver);
        final Evidence before=observe(root,cut);
        require((before.authoritativeLeaves[1]!=null)==cut.journalLeaf,
            "actual canonical PREPARED journal unexpectedly present/absent");
        require((before.authoritativeLeaves[2]!=null)==cut.commitLeaf(),
            "orphan COMMIT temp was treated as canonical COMMIT");
        if(cut==Cut.STRICT_AFTER_TEMP_CREATE)
            require(before.tempBytes.length==0,
                "strict orphan after create should have zero bytes");
        else
            require(before.tempBytes.length>0,
                "serialized orphan publication temp unexpectedly empty");

        Path canonicalAlias=cut.kind==Kind.JOURNAL
            ?new MailboxDurableIdempotencyIntentJournal(resolver)
                .journalPath(account)
            :cut.kind==Kind.COMMIT
                ?new MailboxGuardedDiskCommitRecord(resolver)
                    .recordPath(account)
                :resolver.resolve(account);
        if(cut.alias)
            require(before.tempFileKey.equals(key(canonicalAlias)),
                "expected published hard-link to share orphan file identity");
        else if(present(canonicalAlias))
            require(!before.tempFileKey.equals(key(canonicalAlias)),
                "unpublished orphan unexpectedly IS canonical file object");

        require(MailboxAccountPublicationCoordinator
            .withExclusivePublicationBounded(
                resolver.resolve(account),1500L,()->true),
            "fresh process unable to reacquire account lock");

        MailboxGuardedDiskCommitRecord.Observation commit=
            new MailboxGuardedDiskCommitRecord(resolver).inspect(account);
        require(commit.status==
            (cut.commitLeaf()
                ?MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY
                :MailboxGuardedDiskCommitRecord.Status.ABSENT),
            "canonical COMMIT state inferred incorrectly from temp");
        require(!commit.transactionCommitted&&
                !commit.liveApplied&&!commit.grantAuthorized&&
                !commit.replayAuthorized&&
                !commit.restartAdmissionAuthorized&&
                !commit.releaseAuthorized&&!commit.clientAckAuthorized,
            "orphan temp was promoted to positive COMMIT authority");

        MailboxDurableIdempotencyIntentJournal.Observation intent=
            new MailboxDurableIdempotencyIntentJournal(resolver)
                .inspect(account);
        MailboxDurableIdempotencyIntentJournal.Status expectedJournal=
            !cut.journalLeaf
                ?MailboxDurableIdempotencyIntentJournal.Status.ABSENT
                :cut.terminalAccount
                    ?MailboxDurableIdempotencyIntentJournal.Status
                        .TERMINAL_MATCH_NO_COMMIT
                    :MailboxDurableIdempotencyIntentJournal.Status
                        .PREPARED_MATCH_NO_REPLAY;
        require(intent.status==expectedJournal,
            "canonical journal inferred incorrectly from orphan temp "+
            cut+" status="+intent.status);
        require(!intent.grantAuthorized&&!intent.replayAuthorized&&
                !intent.restartAdmissionAuthorized&&
                !intent.clientAckAuthorized,
            "orphan journal minted positive authority");

        PlayerSnapshot raw=repository.load(account).orElseThrow(
            ()->new AssertionError("authoritative account file disappeared"));
        require(MailboxAtomicTerminalSnapshot.inspect(raw).state==
                (cut.terminalAccount
                    ?MailboxAtomicTerminalSnapshot.State
                        .COHERENT_TERMINAL_NO_GRANT
                    :MailboxAtomicTerminalSnapshot.State.ABSENT),
            "temporary terminal account was mistaken for canonical disk");
        boolean preparedUnclaimed=false;
        boolean sessionDenied=false,workerDenied=false,saveDenied=false;
        if(!cut.terminalAccount){
            PlayerSnapshot admissible=repository.loadForWorldSession(
                account).orElseThrow(
                    ()->new AssertionError("prepared account absent"));
            require(MailboxPreparedRestartAdmission.inspect(admissible)
                .state==MailboxPreparedRestartAdmission.State
                    .VALID_PREPARED_UNCLAIMED,
                "original prepared account changed");
            WorldPlayer detached=new WorldPlayer();
            PlayerSnapshotCodec.applyValidated(admissible,detached);
            MailboxRewardDeliveryService.Snapshot row=
                detached.mailbox().get(account+":gift");
            preparedUnclaimed=detached.bank().inventorySlots()==0&&
                row!=null&&row.claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            require(preparedUnclaimed,"orphan temp credited item");
            try(World w=World.isolatedForTest(60000L,repository)){
                require(w.persistence().load(account).isPresent()&&
                    w.players().snapshot().isEmpty(),
                    "intact PREPARED read unexpectedly became live player");
            }
        }else{
            try{
                repository.loadForWorldSession(account);
            }catch(IOException e){sessionDenied=true;}
            try(World w=World.isolatedForTest(60000L,repository)){
                try{w.persistence().load(account);}
                catch(IOException e){workerDenied=true;}
                require(w.players().snapshot().isEmpty(),
                    "terminal read registered a live World player");
            }
            try{
                repository.saveForWorld(PlayerSnapshotCodec.capture(
                    account,new WorldPlayer()));
            }catch(IOException e){saveDenied=true;}
            require(sessionDenied&&workerDenied&&saveDenied,
                "terminal/COMMIT orphan allowed World session or save");
        }

        boolean detachedDenied=false,detachedNoGrant=false;
        try{
            MailboxCommittedDetachedRestartRecovery.Result forensic=
                new MailboxCommittedDetachedRestartRecovery(resolver)
                    .recoverDetached(account);
            detachedNoGrant=cut.commitLeaf()&&
                forensic.detachedRoundTrip&&
                forensic.claimedBeforeAnyReplay&&
                !forensic.transactionCommitted&&
                !forensic.liveApplied&&!forensic.grantAuthorized&&
                !forensic.replayAuthorized&&
                !forensic.restartAdmissionAuthorized&&
                !forensic.releaseAuthorized&&!forensic.clientAckAuthorized;
        }catch(IOException refused){detachedDenied=true;}
        require(cut.commitLeaf()?detachedNoGrant:detachedDenied,
            "forensic COMMIT recovery wrongly trusted orphan temp");

        Evidence after=observe(root,cut);
        require(before.temp.equals(after.temp)&&
                before.tempFileKey.equals(after.tempFileKey)&&
                Arrays.equals(before.tempBytes,after.tempBytes)&&
                sameLeaves(before.authoritativeLeaves,
                    after.authoritativeLeaves),
            "read-only restarted JVM promoted/altered orphan or canonical");
        require(MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0,
            "publication lock leaked in fresh restart");
        System.out.println("G2198_ORPHAN_RESTART_CHILD_PASS"+
            " cut="+cut.name()+
            " physicalTempRetained=true canonicalUnchanged=true"+
            " trueHardLinkAlias="+cut.alias+
            " canonicalCommit="+cut.commitLeaf()+
            " preparedUnclaimed="+preparedUnclaimed+
            " terminalSessionDenied="+sessionDenied+
            " terminalSaveDenied="+saveDenied+
            " detachedSafe=true"+
            " grant=false liveApply=false replay=false ack=false");
    }

    private static void fork(Path root,Cut cut,boolean writer)
        throws Exception{
        String name=writer?"write":"restart";
        Path log=root.resolve("g2198-"+cut.name()+"-"+name+".log");
        String java=Paths.get(System.getProperty("java.home"),
            "bin",System.getProperty("os.name")
                .toLowerCase(Locale.ROOT).contains("win")
                    ?"java.exe":"java").toString();
        ProcessBuilder child=new ProcessBuilder(java,"-cp",
            System.getProperty("java.class.path"),
            G2198MailboxOrphanPublicationTempRestartIntegrationTest
                .class.getName(),"--child",name,
            root.toString(),cut.name());
        child.redirectErrorStream(true);
        child.redirectOutput(log.toFile());
        Process process=child.start();
        try{
            require(process.waitFor(25,TimeUnit.SECONDS),
                "child timed out "+cut+" stage="+name);
        }finally{
            if(process.isAlive()){
                process.destroyForcibly();
                process.waitFor(5,TimeUnit.SECONDS);
            }
        }
        String text=new String(Files.readAllBytes(log),
            StandardCharsets.UTF_8);
        int expected=writer?cut.exitCode:0;
        require(process.exitValue()==expected&&
            text.contains(writer
                ?"G2198_CHILD_ORPHAN_CUT"
                :"G2198_ORPHAN_RESTART_CHILD_PASS")&&
            text.contains("cut="+cut.name()),
            "child mismatch cut="+cut+" stage="+name+
            " exit="+process.exitValue()+" expected="+expected+
            " output="+text);
    }

    private static void parent()throws Exception{
        Path root=Files.createTempDirectory("g2198-orphan-real-jvm-");
        int hardHalts=0,independentRestarts=0,retained=0;
        boolean allUnchanged=true,zeroLeases=false;
        try{
            for(Cut cut:Cut.values()){
                fork(root,cut,true);
                hardHalts++;
                Evidence before=observe(root,cut);
                fork(root,cut,false);
                independentRestarts++;
                Evidence after=observe(root,cut);
                boolean same=before.temp.equals(after.temp)&&
                    before.tempFileKey.equals(after.tempFileKey)&&
                    Arrays.equals(before.tempBytes,after.tempBytes)&&
                    sameLeaves(before.authoritativeLeaves,
                        after.authoritativeLeaves);
                allUnchanged&=same;
                if(same)retained++;
            }
            zeroLeases=MailboxAccountPublicationCoordinator
                .activeJvmLeaseCount()==0;
            System.out.println("G2198_ORPHAN_PUBLICATION_DIAGNOSTICS"+
                " distinctHardJvmHalts="+hardHalts+
                " independentJvmRestarts="+independentRestarts+
                " orphanTempLeavesRetained="+retained+
                " noCanonicalPromotion="+allUnchanged+
                " parentPublicationLeasesZero="+zeroLeases);
            require(hardHalts==Cut.values().length&&
                independentRestarts==Cut.values().length&&
                retained==Cut.values().length&&
                allUnchanged&&zeroLeases,
                "orphan publication restart matrix");
            System.out.println("G2198_ORPHAN_PUBLICATION_RESTART_PASS"+
                " cuts=6 independentJvms=12"+
                " orphanTempNeverCanonical=true"+
                " hardLinkAliasesPreserved=true"+
                " terminalSavesAndSessionsVetoed=true"+
                " preparedUnclaimed=true"+
                " grant=false replay=false release=false ack=false");
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))
                    Files.deleteIfExists(p);
            }
        }
    }

    public static void main(String[] args)throws Exception{
        if(args.length==4&&"--child".equals(args[0])){
            Path root=Paths.get(args[2]).toAbsolutePath().normalize();
            Cut cut=Cut.valueOf(args[3]);
            if("write".equals(args[1])){
                crashWriter(root,cut);
                throw new AssertionError("G21.98 writer unexpectedly returned");
            }
            require("restart".equals(args[1]),"invalid child mode");
            restartReadOnly(root,cut);
            return;
        }
        require(args.length==0,"invalid parent arguments");
        parent();
    }
}
