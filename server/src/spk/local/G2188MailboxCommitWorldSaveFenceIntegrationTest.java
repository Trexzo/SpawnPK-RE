package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.88: never allow World writers to erase recorded disk COMMIT. */
public final class G2188MailboxCommitWorldSaveFenceIntegrationTest {
    private static final class Seed {
        final WorldPlayer player;
        final MailboxSettlementPostimagePlanner.Proposal proposal;
        final PlayerSnapshot terminal;
        Seed(WorldPlayer player,MailboxSettlementPostimagePlanner.Proposal p){
            this.player=player;proposal=p;
            terminal=MailboxAtomicTerminalSnapshot.compose(p);
        }
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("g2188-save-fence-");
        FilePlayerRepository.PathResolver paths=
            account->root.resolve(account+".properties");
        FilePlayerRepository repository=new FilePlayerRepository(paths);
        StrictDurablePlayerSnapshotWriter strict=
            new StrictDurablePlayerSnapshotWriter(paths);
        MailboxDurableIdempotencyIntentJournal journal=
            new MailboxDurableIdempotencyIntentJournal(paths);
        MailboxGuardedDiskCommitRecord disk=
            new MailboxGuardedDiskCommitRecord(paths);
        boolean diskRecordValid=false;
        boolean ordinaryWorldSaveVeto=false;
        boolean strictWorldSaveVeto=false;
        boolean strictTerminalRepublishVeto=false;
        boolean rawRollbackSimulated=false;
        boolean recordNeverRemoved=false;
        boolean symlinkPresenceVeto=false;
        boolean corruptPresenceVeto=false;
        boolean ordinaryLegacyUnaffected=false;
        boolean ordinaryPreparedUnaffected=false;
        boolean unrelatedStrictUnaffected=false;
        boolean noLiveCredit=true,noLeaks=false;
        try(World world=World.isolatedForTest(60000L)){
            String account="g2188-committed";
            Seed claimed=seed(world,account);
            strict.saveStrict(claimed.proposal.preparedPreimage);
            journal.publishPreparedIntent(claimed.proposal);
            StrictDurablePlayerSnapshotWriter.Receipt receipt=
                confirmed(strict,paths,claimed);
            disk.recordConfirmedDiskTerminal(claimed.proposal,receipt);
            byte[] record=Files.readAllBytes(disk.recordPath(account));
            diskRecordValid=disk.inspect(account).status==
                MailboxGuardedDiskCommitRecord.Status
                    .DISK_COMMIT_MATCH_NO_LIVE_APPLY;
            // Test-only RAW bypass simulates an uncooperative rollback.
            // The actual World save must still refuse the stale PREPARED.
            repository.save(claimed.proposal.preparedPreimage);
            rawRollbackSimulated=repository.load(account).get()
                .values().equals(
                    claimed.proposal.preparedPreimage.values());
            ordinaryWorldSaveVeto=worldSaveRefused(
                repository,claimed.proposal.preparedPreimage);
            strictWorldSaveVeto=strictSaveRefused(
                strict,paths,claimed.proposal.preparedPreimage);
            strictTerminalRepublishVeto=terminalSaveRefused(
                strict,paths,claimed);
            recordNeverRemoved=Arrays.equals(record,
                Files.readAllBytes(disk.recordPath(account)))&&
                repository.load(account).get().values().equals(
                    claimed.proposal.preparedPreimage.values());

            String corrupt="g2188-corrupt";
            Seed damaged=seed(world,corrupt);
            strict.saveStrict(damaged.proposal.preparedPreimage);
            journal.publishPreparedIntent(damaged.proposal);
            Path fake=disk.recordPath(corrupt);
            Files.write(fake,new byte[]{1,2,3});
            corruptPresenceVeto=worldSaveRefused(repository,
                damaged.proposal.preparedPreimage)&&
                strictSaveRefused(strict,paths,
                    damaged.proposal.preparedPreimage)&&
                Arrays.equals(new byte[]{1,2,3},Files.readAllBytes(fake));

            String linkAccount="g2188-link";
            Seed linked=seed(world,linkAccount);
            strict.saveStrict(linked.proposal.preparedPreimage);
            journal.publishPreparedIntent(linked.proposal);
            Path link=disk.recordPath(linkAccount);
            try{
                Files.createSymbolicLink(link,disk.recordPath(account));
                symlinkPresenceVeto=
                    worldSaveRefused(repository,
                        linked.proposal.preparedPreimage)&&
                    strictSaveRefused(strict,paths,
                        linked.proposal.preparedPreimage)&&
                    Files.isSymbolicLink(link);
            }catch(UnsupportedOperationException|
                    java.nio.file.FileSystemException unsupported){
                symlinkPresenceVeto=!Files.exists(
                    link,LinkOption.NOFOLLOW_LINKS);
            }

            String preparedAccount="g2188-plain-prepared";
            Seed plain=seed(world,preparedAccount);
            strict.saveStrict(plain.proposal.preparedPreimage);
            repository.saveForWorld(plain.proposal.preparedPreimage);
            ordinaryPreparedUnaffected=repository.load(preparedAccount)
                .get().values().equals(
                    plain.proposal.preparedPreimage.values());
            StrictDurablePlayerSnapshotWriter.Receipt plainReceipt=
                strict.saveStrictForWorld(
                    plain.proposal.preparedPreimage,
                    paths.resolve(preparedAccount));
            unrelatedStrictUnaffected=plainReceipt.matchesSnapshot(
                plain.proposal.preparedPreimage);

            String legacyAccount="g2188-legacy";
            WorldPlayer legacy=new WorldPlayer();
            legacy.markRegistered(legacyAccount);
            PlayerSnapshot previous=PlayerSnapshotCodec.capture(
                legacyAccount,legacy);
            repository.saveForWorld(previous);
            ordinaryLegacyUnaffected=repository.load(legacyAccount)
                .get().values().equals(previous.values());

            noLiveCredit=claimed.player.bank().inventorySlots()==0&&
                claimed.player.mailbox().get(
                    claimed.proposal.messageId).claimState==
                    MailboxRewardDeliveryService.ClaimState.UNCLAIMED&&
                damaged.player.bank().inventorySlots()==0;
            try(Stream<Path> all=Files.walk(root)){
                noLeaks=all.noneMatch(p->
                    p.getFileName().toString().endsWith(".tmp"))&&
                    MailboxAccountPublicationCoordinator
                        .activeJvmLeaseCount()==0;
            }
        }finally{
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
        System.out.println("G2188_COMMIT_SAVE_FENCE_DIAGNOSTICS"+
            " diskRecordValid="+diskRecordValid+
            " rawRollbackSimulated="+rawRollbackSimulated+
            " ordinaryWorldSaveVeto="+ordinaryWorldSaveVeto+
            " strictWorldSaveVeto="+strictWorldSaveVeto+
            " strictTerminalRepublishVeto="+strictTerminalRepublishVeto+
            " recordNeverRemoved="+recordNeverRemoved+
            " corruptPresenceVeto="+corruptPresenceVeto+
            " symlinkPresenceVeto="+symlinkPresenceVeto+
            " ordinaryPreparedUnaffected="+ordinaryPreparedUnaffected+
            " unrelatedStrictUnaffected="+unrelatedStrictUnaffected+
            " ordinaryLegacyUnaffected="+ordinaryLegacyUnaffected+
            " noLiveCredit="+noLiveCredit+" noLeaks="+noLeaks);
        if(!(diskRecordValid&&rawRollbackSimulated&&
             ordinaryWorldSaveVeto&&strictWorldSaveVeto&&
             strictTerminalRepublishVeto&&recordNeverRemoved&&
             corruptPresenceVeto&&symlinkPresenceVeto&&
             ordinaryPreparedUnaffected&&unrelatedStrictUnaffected&&
             ordinaryLegacyUnaffected&&noLiveCredit&&noLeaks))
            throw new AssertionError(
                "G21.88 COMMIT World-save fence regression failed");
        System.out.println("G2188_COMMIT_WORLD_SAVE_FENCE_PASS"+
            " staleRollbackWorldVeto=true strictVeto=true"+
            " recordPreserved=true grant=false replay=false"+
            " release=false ack=false");
    }
    private static boolean worldSaveRefused(
        FilePlayerRepository repo,PlayerSnapshot p
    )throws IOException{
        try{repo.saveForWorld(p);return false;}
        catch(IOException rejected){
            return rejected.getMessage().contains(
                "G21.88 DISK_COMMIT_WORLD_SAVE_VETO");
        }
    }
    private static boolean strictSaveRefused(
        StrictDurablePlayerSnapshotWriter writer,
        FilePlayerRepository.PathResolver paths,PlayerSnapshot p
    )throws IOException{
        try{writer.saveStrictForWorld(p,paths.resolve(p.username()));
            return false;}
        catch(IOException rejected){
            return rejected.getMessage().contains(
                "G21.88 DISK_COMMIT_STRICT_SAVE_VETO");
        }
    }
    private static boolean terminalSaveRefused(
        StrictDurablePlayerSnapshotWriter writer,
        FilePlayerRepository.PathResolver paths,Seed seed
    )throws IOException{
        try{confirmed(writer,paths,seed);return false;}
        catch(IOException rejected){
            return rejected.getMessage().contains(
                "G21.88 DISK_COMMIT_STRICT_SAVE_VETO");
        }
    }
    private static StrictDurablePlayerSnapshotWriter.Receipt confirmed(
        StrictDurablePlayerSnapshotWriter writer,
        FilePlayerRepository.PathResolver paths,Seed s
    )throws IOException{
        return writer.saveStrictTerminalForWorld(
            s.terminal,paths.resolve(s.proposal.account),
            StrictDurablePlayerSnapshotWriter.canonicalSnapshotSha256(
                s.proposal.preparedPreimage),
            ()->{},()->{}
        );
    }
    private static Seed seed(World world,String account){
        WorldPlayer p=new WorldPlayer();
        long generation=world.registerPlayer(p,account);
        String id=account+":gift";
        p.mailbox().deliver(new RewardDeliveryMessage(
            id,"COMMIT fence fixture","NO_GRANT",
            Collections.singletonList(
                new RewardDeliveryMessage.Attachment(995,25)),
            "CUSTOM_LOCALLAB_G2188_FIXTURE"));
        MailboxRewardDeliveryService.Snapshot row=p.mailbox().get(id);
        MailboxPreparedClaimJournal.stageOnly(p,
            MailboxPreparedClaimJournal.prepare(p,row));
        return new Seed(p,MailboxSettlementPostimagePlanner.plan(
            p,generation,row));
    }
}
