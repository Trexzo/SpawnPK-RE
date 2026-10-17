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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/** Actual World admission and G21.84 persistent PREPARED journal. */
public final class G2184MailboxJournalWorldAdmissionIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer p,MailboxSettlementPostimagePlanner.Proposal q){
            player=p;proposal=q;
            terminal=MailboxAtomicTerminalSnapshot.compose(q);
        }
    }

    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2184-world-journal-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository raw=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter writer=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        boolean validPreparedLoads=false,ordinaryPreparedLoads=false;
        boolean legacyLoads=false,rawForensicUnchanged=false;
        boolean divergentBlocked=false,foreignBlocked=false;
        boolean malformedBlocked=false,oversizedBlocked=false;
        boolean terminalBlocked=false,missingBlocked=false;
        boolean symlinkBlocked=false,negativeMarkersWin=true;
        boolean inReadChangedBlocked=false,inReadAppearedBlocked=false;
        boolean inReadRemovedBlocked=false;
        boolean goodJournalUnchanged=false,liveNeverCredited=false;
        boolean noLeakedTempsOrLocks=false;
        int negativeCases=0;

        try(World fixtures=World.isolatedForTest(60000L);
            World restart=World.isolatedForTest(60000L,raw)){
            restart.start();
            Seed healthy=seed(fixtures,"g2184-healthy");
            writer.saveStrict(healthy.proposal.preparedPreimage);
            journal.publishPreparedIntent(healthy.proposal);
            byte[] canonical=Files.readAllBytes(
                journal.journalPath("g2184-healthy"));
            validPreparedLoads=restart.persistence()
                .load("g2184-healthy").isPresent();
            goodJournalUnchanged=Arrays.equals(canonical,
                Files.readAllBytes(journal.journalPath("g2184-healthy")));

            Seed plain=seed(fixtures,"g2184-plain");
            writer.saveStrict(plain.proposal.preparedPreimage);
            ordinaryPreparedLoads=restart.persistence()
                .load("g2184-plain").isPresent()&&
                !Files.exists(journal.journalPath("g2184-plain"),
                    LinkOption.NOFOLLOW_LINKS);
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered("g2184-legacy");
            writer.saveStrict(PlayerSnapshotCodec.capture(
                "g2184-legacy",legacy));
            legacyLoads=restart.persistence().load(
                "g2184-legacy").isPresent();

            Seed diverged=seed(fixtures,"g2184-diverged");
            writer.saveStrict(diverged.proposal.preparedPreimage);
            journal.publishPreparedIntent(diverged.proposal);
            TreeMap<String,String> wrong=new TreeMap<>(
                diverged.proposal.preparedPreimage.values());
            wrong.put("extension.g2184-probe.note","unrelated-change");
            writer.saveStrict(new PlayerSnapshot(
                PlayerSnapshot.CURRENT_VERSION,"g2184-diverged",wrong));
            divergentBlocked=rejectsJournal(restart,"g2184-diverged");

            Seed foreign=seed(fixtures,"g2184-foreign");
            writer.saveStrict(foreign.proposal.preparedPreimage);
            Files.copy(journal.journalPath("g2184-healthy"),
                journal.journalPath("g2184-foreign"));
            foreignBlocked=rejectsJournal(restart,"g2184-foreign");

            Seed malformed=seed(fixtures,"g2184-malformed");
            writer.saveStrict(malformed.proposal.preparedPreimage);
            journal.publishPreparedIntent(malformed.proposal);
            Path malformedPath=journal.journalPath("g2184-malformed");
            byte[] malformedBytes=Files.readAllBytes(malformedPath);
            malformedBytes[0]^=1;
            Files.write(malformedPath,malformedBytes);
            malformedBlocked=rejectsJournal(restart,"g2184-malformed");

            Seed oversized=seed(fixtures,"g2184-oversized");
            writer.saveStrict(oversized.proposal.preparedPreimage);
            journal.publishPreparedIntent(oversized.proposal);
            Files.write(journal.journalPath("g2184-oversized"),
                new byte[1025]);
            oversizedBlocked=rejectsJournal(restart,"g2184-oversized");

            Seed missing=seed(fixtures,"g2184-missing");
            writer.saveStrict(missing.proposal.preparedPreimage);
            journal.publishPreparedIntent(missing.proposal);
            Files.delete(paths.resolve("g2184-missing"));
            missingBlocked=rejectsJournal(restart,"g2184-missing");

            Seed terminal=seed(fixtures,"g2184-terminal");
            writer.saveStrict(terminal.proposal.preparedPreimage);
            journal.publishPreparedIntent(terminal.proposal);
            writer.saveStrict(terminal.terminal);
            terminalBlocked=rejectsJournal(restart,"g2184-terminal")&&
                journal.inspect("g2184-terminal").status==
                    MailboxDurableIdempotencyIntentJournal.Status
                        .TERMINAL_MATCH_NO_COMMIT;

            String[] suffixes={
                ".g2132-mailbox-review",
                MailboxStrictUncertainFence.SUFFIX,
                MailboxStrictWriteIntentFence.SUFFIX
            };
            for(int i=0;i<suffixes.length;i++){
                String account="g2184-negative-"+i;
                Seed marked=seed(fixtures,account);
                writer.saveStrict(marked.proposal.preparedPreimage);
                journal.publishPreparedIntent(marked.proposal);
                Path negative=paths.resolve(account).resolveSibling(
                    account+".properties"+suffixes[i]);
                byte[] content=("G2184_NEGATIVE_"+i).getBytes(
                    StandardCharsets.US_ASCII);
                Files.write(negative,content);
                negativeCases++;
                negativeMarkersWin&=rejectsAny(restart,account)&&
                    Arrays.equals(content,Files.readAllBytes(negative));
            }

            Seed changed=seed(fixtures,"g2184-changed");
            writer.saveStrict(changed.proposal.preparedPreimage);
            journal.publishPreparedIntent(changed.proposal);
            Path changing=journal.journalPath("g2184-changed");
            byte[] before=Files.readAllBytes(changing);
            AtomicBoolean changeTriggered=new AtomicBoolean();
            FilePlayerRepository changedRepo=new FilePlayerRepository(
                paths,a->{},a->{},a->{
                    if(a.equals("g2184-changed")){
                        byte[] alter=before.clone();
                        alter[1]^=1;
                        Files.write(changing,alter);
                        changeTriggered.set(true);
                    }
                });
            try(World hooked=World.isolatedForTest(60000L,changedRepo)){
                hooked.start();
                inReadChangedBlocked=
                    rejectsJournal(hooked,"g2184-changed")&&
                    changeTriggered.get();
            }

            Seed appeared=seed(fixtures,"g2184-appeared");
            writer.saveStrict(appeared.proposal.preparedPreimage);
            Path appear=journal.journalPath("g2184-appeared");
            AtomicBoolean appearTriggered=new AtomicBoolean();
            FilePlayerRepository appearedRepo=new FilePlayerRepository(
                paths,a->{},a->{},a->{
                    if(a.equals("g2184-appeared")){
                        Files.write(appear,canonical);
                        appearTriggered.set(true);
                    }
                });
            try(World hooked=World.isolatedForTest(60000L,appearedRepo)){
                hooked.start();
                inReadAppearedBlocked=
                    rejectsJournal(hooked,"g2184-appeared")&&
                    appearTriggered.get();
            }

            Seed removed=seed(fixtures,"g2184-removed");
            writer.saveStrict(removed.proposal.preparedPreimage);
            journal.publishPreparedIntent(removed.proposal);
            Path remove=journal.journalPath("g2184-removed");
            AtomicBoolean removalTriggered=new AtomicBoolean();
            FilePlayerRepository removedRepo=new FilePlayerRepository(
                paths,a->{},a->{},a->{
                    if(a.equals("g2184-removed")){
                        Files.delete(remove);
                        removalTriggered.set(true);
                    }
                });
            try(World hooked=World.isolatedForTest(60000L,removedRepo)){
                hooked.start();
                inReadRemovedBlocked=
                    rejectsJournal(hooked,"g2184-removed")&&
                    removalTriggered.get();
            }

            Seed symlink=seed(fixtures,"g2184-symlink");
            writer.saveStrict(symlink.proposal.preparedPreimage);
            journal.publishPreparedIntent(symlink.proposal);
            Path link=journal.journalPath("g2184-symlink");
            try{
                Files.delete(link);
                Files.createSymbolicLink(link,
                    journal.journalPath("g2184-healthy"));
                symlinkBlocked=rejectsJournal(restart,"g2184-symlink");
            }catch(java.nio.file.FileSystemException|
                    UnsupportedOperationException notSupported){
                symlinkBlocked=!Files.isSymbolicLink(link);
            }

            rawForensicUnchanged=
                raw.load("g2184-diverged").isPresent()&&
                raw.load("g2184-malformed").isPresent()&&
                raw.load("g2184-terminal").isPresent()&&
                raw.load("g2184-foreign").isPresent();
            liveNeverCredited=
                healthy.player.bank().inventorySlots()==0&&
                healthy.player.mailbox().get(
                    healthy.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                terminal.player.bank().inventorySlots()==0&&
                terminal.player.mailbox().get(
                    terminal.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                changed.player.bank().inventorySlots()==0;

            try(Stream<Path> all=Files.walk(root)){
                noLeakedTempsOrLocks=all.noneMatch(path->
                    path.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path f:all.sorted(Comparator.reverseOrder())
                        .toArray(Path[]::new))Files.deleteIfExists(f);
            }
        }

        System.out.println("G2184_JOURNAL_ADMISSION_DIAGNOSTICS"+
            " validPreparedLoads="+validPreparedLoads+
            " ordinaryPreparedLoads="+ordinaryPreparedLoads+
            " legacyLoads="+legacyLoads+
            " rawForensicUnchanged="+rawForensicUnchanged+
            " divergentBlocked="+divergentBlocked+
            " foreignBlocked="+foreignBlocked+
            " malformedBlocked="+malformedBlocked+
            " oversizedBlocked="+oversizedBlocked+
            " missingBlocked="+missingBlocked+
            " terminalBlocked="+terminalBlocked+
            " symlinkBlocked="+symlinkBlocked+
            " negativeCases="+negativeCases+
            " negativeMarkersWin="+negativeMarkersWin+
            " inReadChangedBlocked="+inReadChangedBlocked+
            " inReadAppearedBlocked="+inReadAppearedBlocked+
            " inReadRemovedBlocked="+inReadRemovedBlocked+
            " goodJournalUnchanged="+goodJournalUnchanged+
            " liveNeverCredited="+liveNeverCredited+
            " noLeakedTempsOrLocks="+noLeakedTempsOrLocks);
        if(!(validPreparedLoads&&ordinaryPreparedLoads&&legacyLoads&&
             rawForensicUnchanged&&divergentBlocked&&foreignBlocked&&
             malformedBlocked&&oversizedBlocked&&missingBlocked&&
             terminalBlocked&&symlinkBlocked&&negativeCases==3&&
             negativeMarkersWin&&inReadChangedBlocked&&
             inReadAppearedBlocked&&inReadRemovedBlocked&&
             goodJournalUnchanged&&liveNeverCredited&&
             noLeakedTempsOrLocks))
            throw new AssertionError(
                "G21.84 real World journal admission regression failed");
        System.out.println("G2184_JOURNAL_WORLD_ADMISSION_PASS"+
            " validPrepared=true negativeJournalVeto=true"+
            " driftVeto=true commit=false replay=false"+
            " terminalAdmission=false grant=false ack=false");
    }

    private static boolean rejectsJournal(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException rejected){
            return rejected.getMessage().contains("G21.84 ");
        }
    }

    private static boolean rejectsAny(World world,String account){
        try{
            world.persistence().load(account);
            return false;
        }catch(IOException denied){
            return true;
        }
    }

    private static Seed seed(World world,String account){
        WorldPlayer p=new WorldPlayer();
        long generation=world.registerPlayer(p,account);
        String id=account+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"World journal fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2184_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot selected=
            p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,selected));
        return new Seed(p,MailboxSettlementPostimagePlanner.plan(
            p,generation,selected));
    }
}
