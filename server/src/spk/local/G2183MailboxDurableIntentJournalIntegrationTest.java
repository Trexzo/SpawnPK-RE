package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Real-file G21.83 write-once journal/restart and fault matrix. */
public final class G2183MailboxDurableIntentJournalIntegrationTest {
    private static final MailboxDurableIdempotencyIntentJournal.Phase[] CUTS={
        MailboxDurableIdempotencyIntentJournal.Phase.BEFORE_TEMP_CREATE,
        MailboxDurableIdempotencyIntentJournal.Phase.BEFORE_LINK,
        MailboxDurableIdempotencyIntentJournal.Phase.AFTER_LINK,
        MailboxDurableIdempotencyIntentJournal.Phase.BEFORE_DIRECTORY_FORCE,
        MailboxDurableIdempotencyIntentJournal.Phase.AFTER_DIRECTORY_FORCE
    };
    private static final class Seed {
        final WorldPlayer owner;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer owner,MailboxSettlementPostimagePlanner.Proposal p){
            this.owner=owner;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }

    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("g2183-journal-");
        FilePlayerRepository.PathResolver paths=
            a->dir.resolve(a+".properties");
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        boolean prelinkAbsent=true,postlinkUnconfirmed=true;
        boolean cleanPreparedRecognized=false;
        boolean stableFreshInstance=false;
        boolean terminalVisibleNotCommitted=false;
        boolean duplicateNeverClobbers=false;
        boolean divergedQuarantined=false;
        boolean missingQuarantined=false;
        boolean tamperedQuarantined=false;
        boolean oversizedQuarantined=false;
        boolean nonregularQuarantined=false;
        boolean negativeMarkerDominates=false;
        boolean foreignAccountRejected=false;
        boolean noFileOrGrantMutation=true;
        boolean worldTerminalAdmissionDenied=false;
        boolean noLeaks=false;
        boolean allNoAuthority=true;
        int prelink=0,postlink=0;
        try(World world=World.isolatedForTest(60000L);
            World restarted=World.isolatedForTest(
                60000L,new FilePlayerRepository(paths))){
            restarted.start();
            for(int i=0;i<=CUTS.length;i++){
                String account="g2183-window-"+i;
                Seed s=seed(world,account);
                strict.saveStrict(s.proposal.preparedPreimage);
                final MailboxDurableIdempotencyIntentJournal.Phase stop=
                    i==CUTS.length?null:CUTS[i];
                AtomicInteger fired=new AtomicInteger();
                MailboxDurableIdempotencyIntentJournal faulted=
                    new MailboxDurableIdempotencyIntentJournal(paths,p->{
                        if(p==stop){fired.incrementAndGet();
                            throw new IOException("G21.83 injected "+p);}
                    });
                boolean ordinaryFailure=false,uncertain=false;
                try{
                    faulted.publishPreparedIntent(s.proposal);
                }catch(
                    MailboxDurableIdempotencyIntentJournal
                        .UnconfirmedJournalException e
                ){
                    uncertain=true;
                }catch(IOException e){
                    ordinaryFailure=true;
                }
                if(stop!=null&&fired.get()!=1)
                    throw new AssertionError(
                        "G21.83 unhit fault point "+stop);
                Path marker=journal.journalPath(account);
                boolean visible=Files.exists(
                    marker,LinkOption.NOFOLLOW_LINKS);
                MailboxDurableIdempotencyIntentJournal.Observation observed=
                    new MailboxDurableIdempotencyIntentJournal(paths)
                        .inspect(account);
                allNoAuthority&=noAuthority(observed);
                if(i<2){
                    prelink++;
                    prelinkAbsent&=ordinaryFailure&&!uncertain&&
                        !visible&&observed.status==
                            MailboxDurableIdempotencyIntentJournal.Status
                                .ABSENT;
                }else if(i<CUTS.length){
                    postlink++;
                    postlinkUnconfirmed&=uncertain&&!ordinaryFailure&&
                        visible&&observed.status==
                            MailboxDurableIdempotencyIntentJournal.Status
                                .PREPARED_MATCH_NO_REPLAY;
                }else{
                    cleanPreparedRecognized=!ordinaryFailure&&!uncertain&&
                        visible&&observed.recordValidated&&
                        observed.status==
                            MailboxDurableIdempotencyIntentJournal.Status
                                .PREPARED_MATCH_NO_REPLAY&&
                        observed.intentKey.equals(
                            s.proposal.idempotencyKey)&&
                        observed.messageId.equals(s.proposal.messageId)&&
                        observed.preparedSha256.equals(
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(
                                    s.proposal.preparedPreimage))&&
                        observed.terminalSha256.equals(
                            StrictDurablePlayerSnapshotWriter
                                .canonicalSnapshotSha256(s.terminal));
                    byte[] originally=Files.readAllBytes(marker);
                    MailboxDurableIdempotencyIntentJournal.Observation again=
                        new MailboxDurableIdempotencyIntentJournal(paths)
                            .inspect(account);
                    stableFreshInstance=
                        again.status==observed.status&&
                        observed.intentKey.equals(again.intentKey)&&
                        noAuthority(again);
                    boolean refused=false;
                    try{
                        journal.publishPreparedIntent(s.proposal);
                    }catch(IOException e){
                        refused=e.getMessage().contains(
                            "JOURNAL_ALREADY_EXISTS");
                    }
                    duplicateNeverClobbers=refused&&Arrays.equals(
                        originally,Files.readAllBytes(marker));
                    // Detached strict file write only; never grants to
                    // registered World owner or releases its PREPARED.
                    strict.saveStrict(s.terminal);
                    MailboxDurableIdempotencyIntentJournal.Observation term=
                        journal.inspect(account);
                    terminalVisibleNotCommitted=term.status==
                        MailboxDurableIdempotencyIntentJournal.Status
                            .TERMINAL_MATCH_NO_COMMIT&&
                        term.intentKey.equals(s.proposal.idempotencyKey)&&
                        noAuthority(term);
                    worldTerminalAdmissionDenied=
                        rejectsAdmission(restarted,account);
                    allNoAuthority&=noAuthority(term);
                    noFileOrGrantMutation&=Arrays.equals(
                        originally,Files.readAllBytes(marker));

                    // Subsequent unrelated raw bytes never count as
                    // the PREPARED or coherent TERMINAL image.
                    strict.saveStrict(s.proposal.preparedPreimage);
                    TreeMap<String,String> changed=new TreeMap<>(
                        s.proposal.preparedPreimage.values());
                    changed.put("extension.g2183-unrelated.note","changed");
                    strict.saveStrict(new PlayerSnapshot(
                        PlayerSnapshot.CURRENT_VERSION,account,changed));
                    MailboxDurableIdempotencyIntentJournal.Observation div=
                        journal.inspect(account);
                    divergedQuarantined=div.status==
                        MailboxDurableIdempotencyIntentJournal.Status
                            .ACCOUNT_DIVERGED_QUARANTINE&&
                        noAuthority(div);
                }
                noFileOrGrantMutation&=
                    s.owner.bank().inventorySlots()==0&&
                    s.owner.mailbox().get(s.proposal.messageId).claimState==
                        MailboxRewardDeliveryService.ClaimState.UNCLAIMED;
            }

            String missing="g2183-missing";
            Seed deleted=seed(world,missing);
            strict.saveStrict(deleted.proposal.preparedPreimage);
            journal.publishPreparedIntent(deleted.proposal);
            Files.delete(paths.resolve(missing));
            MailboxDurableIdempotencyIntentJournal.Observation gone=
                journal.inspect(missing);
            missingQuarantined=gone.status==
                MailboxDurableIdempotencyIntentJournal.Status
                    .MISSING_ACCOUNT_QUARANTINE&&noAuthority(gone);
            allNoAuthority&=noAuthority(gone);

            String tampered="g2183-tamper";
            Seed altered=seed(world,tampered);
            strict.saveStrict(altered.proposal.preparedPreimage);
            journal.publishPreparedIntent(altered.proposal);
            Path damaged=journal.journalPath(tampered);
            byte[] tamperBytes=Files.readAllBytes(damaged);
            tamperBytes[0]=(byte)(tamperBytes[0]^1);
            Files.write(damaged,tamperBytes);
            MailboxDurableIdempotencyIntentJournal.Observation bad=
                journal.inspect(tampered);
            tamperedQuarantined=bad.status==
                MailboxDurableIdempotencyIntentJournal.Status
                    .INVALID_RECORD_QUARANTINE&&noAuthority(bad);

            String oversize="g2183-oversize";
            Seed huge=seed(world,oversize);
            strict.saveStrict(huge.proposal.preparedPreimage);
            journal.publishPreparedIntent(huge.proposal);
            Files.write(journal.journalPath(oversize),new byte[1025]);
            MailboxDurableIdempotencyIntentJournal.Observation large=
                journal.inspect(oversize);
            oversizedQuarantined=large.status==
                MailboxDurableIdempotencyIntentJournal.Status
                    .INVALID_RECORD_QUARANTINE&&noAuthority(large);

            String negative="g2183-negative";
            Seed blocked=seed(world,negative);
            strict.saveStrict(blocked.proposal.preparedPreimage);
            journal.publishPreparedIntent(blocked.proposal);
            Path review=paths.resolve(negative).resolveSibling(
                negative+".properties"+
                MailboxStrictUncertainFence.SUFFIX);
            byte[] negativeBytes="G2183_NEGATIVE_HOLD".getBytes(
                StandardCharsets.US_ASCII);
            Files.write(review,negativeBytes);
            MailboxDurableIdempotencyIntentJournal.Observation veto=
                journal.inspect(negative);
            negativeMarkerDominates=veto.status==
                MailboxDurableIdempotencyIntentJournal.Status
                    .NEGATIVE_MARKER_MANUAL_HOLD&&
                noAuthority(veto)&&
                Arrays.equals(negativeBytes,Files.readAllBytes(review))&&
                rejectsAdmission(restarted,negative);
            allNoAuthority&=noAuthority(veto);

            String symlink="g2183-symlink";
            Seed linked=seed(world,symlink);
            strict.saveStrict(linked.proposal.preparedPreimage);
            journal.publishPreparedIntent(linked.proposal);
            Path link=journal.journalPath(symlink);
            Path target=journal.journalPath(negative);
            try{
                Files.delete(link);
                Files.createSymbolicLink(link,target);
                MailboxDurableIdempotencyIntentJournal.Observation nonregular=
                    journal.inspect(symlink);
                nonregularQuarantined=nonregular.status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .INVALID_RECORD_QUARANTINE&&
                    noAuthority(nonregular);
            }catch(UnsupportedOperationException|
                    java.nio.file.FileSystemException unsupported){
                // Unsupported symlink filesystem: no bypass is claimed.
                nonregularQuarantined=!Files.isSymbolicLink(link);
            }

            try{
                journal.inspect("G2183_BAD_ACCOUNT");
            }catch(IllegalArgumentException expected){
                foreignAccountRejected=true;
            }
            try(Stream<Path> all=Files.walk(dir)){
                noLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(dir)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2183_JOURNAL_DIAGNOSTICS"+
            " prelink="+prelink+" postlink="+postlink+
            " prelinkAbsent="+prelinkAbsent+
            " postlinkUnconfirmed="+postlinkUnconfirmed+
            " cleanPreparedRecognized="+cleanPreparedRecognized+
            " stableFreshInstance="+stableFreshInstance+
            " terminalVisibleNotCommitted="+terminalVisibleNotCommitted+
            " duplicateNeverClobbers="+duplicateNeverClobbers+
            " divergedQuarantined="+divergedQuarantined+
            " missingQuarantined="+missingQuarantined+
            " tamperedQuarantined="+tamperedQuarantined+
            " oversizedQuarantined="+oversizedQuarantined+
            " nonregularQuarantined="+nonregularQuarantined+
            " negativeMarkerDominates="+negativeMarkerDominates+
            " foreignAccountRejected="+foreignAccountRejected+
            " worldTerminalAdmissionDenied="+worldTerminalAdmissionDenied+
            " noFileOrGrantMutation="+noFileOrGrantMutation+
            " noLeaks="+noLeaks+
            " allNoAuthority="+allNoAuthority);
        if(!(prelink==2&&postlink==3&&prelinkAbsent&&
             postlinkUnconfirmed&&cleanPreparedRecognized&&
             stableFreshInstance&&terminalVisibleNotCommitted&&
             duplicateNeverClobbers&&divergedQuarantined&&
             missingQuarantined&&tamperedQuarantined&&
             oversizedQuarantined&&nonregularQuarantined&&
             negativeMarkerDominates&&foreignAccountRejected&&
             worldTerminalAdmissionDenied&&noFileOrGrantMutation&&
             noLeaks&&allNoAuthority))
            throw new AssertionError(
                "G21.83 durable PREPARED intent journal regression failed");
        System.out.println("G2183_INTENT_JOURNAL_PASS"+
            " writeOnce=true restartReadable=true faultClosed=true"+
            " positiveCommit=false replay=false grant=false"+
            " admission=false liveApply=false ack=false");
    }

    private static boolean rejectsAdmission(World world,String a){
        try{
            world.persistence().load(a);
            return false;
        }catch(IOException denied){
            return true;
        }
    }

    private static boolean noAuthority(
        MailboxDurableIdempotencyIntentJournal.Observation result
    ){
        return !result.durabilityConfirmed&&
            !result.transactionCommitted&&!result.liveApplied&&
            !result.grantAuthorized&&!result.replayAuthorized&&
            !result.rollbackAuthorized&&
            !result.restartAdmissionAuthorized&&
            !result.releaseAuthorized&&!result.clientAckAuthorized;
    }

    private static Seed seed(World world,String account){
        WorldPlayer owner=new WorldPlayer();
        long generation=world.registerPlayer(owner,account);
        String messageId=account+":gift";
        owner.mailbox().deliver(new RewardDeliveryMessage(
            messageId,"Idempotency journal","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2183_TEST_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            owner.mailbox().get(messageId);
        MailboxPreparedClaimJournal.stageOnly(owner,
            MailboxPreparedClaimJournal.prepare(owner,selected));
        return new Seed(owner,MailboxSettlementPostimagePlanner.plan(
            owner,generation,selected));
    }
}
