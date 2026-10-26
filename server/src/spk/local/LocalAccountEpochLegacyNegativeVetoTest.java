package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * R160: the newer R153/R154 G21.377 negative-only HEAD and its orphan
 * snapshot object must block existing legacy account access without
 * enabling any positive owner epoch, replay or final-save authority.
 * Source-only invocation; not an original client or crash power-loss test.
 */
public final class LocalAccountEpochLegacyNegativeVetoTest {
    private static int scenarios;
    @FunctionalInterface interface Operation{
        void run()throws IOException;
    }
    enum Evidence { HEAD_AND_OBJECT, ORPHAN_OBJECT_ONLY }

    private LocalAccountEpochLegacyNegativeVetoTest(){}

    private static void check(boolean ok,String reason){
        if(!ok)throw new AssertionError("R160 "+reason);
    }

    private static byte[] image(String name){
        return ("R160 untrusted negative-only account snapshot "+name)
            .getBytes(StandardCharsets.US_ASCII);
    }

    private static String accountName(Path account){
        String leaf=account.getFileName().toString();
        check(leaf.endsWith(".properties"),"invalid account filename");
        return leaf.substring(0,leaf.length()-".properties".length());
    }

    private static LocalAccountEpochHeadCodec.Record marker(
        Path account,byte[] bytes)throws IOException{
        return new LocalAccountEpochHeadCodec.Record(
            accountName(account),1,1,160,
            LocalAccountEpochHeadCodec.State.REVIEW,
            LocalAccountEpochHeadCodec.ZERO_SHA,
            LocalAccountEpochHeadCodec.digestSnapshot(bytes));
    }

    private static void publish(Path account,Evidence evidence)
        throws IOException{
        byte[] bytes=image(accountName(account));
        if(evidence==Evidence.HEAD_AND_OBJECT){
            String leaf=accountName(account);
            if(leaf.matches("[a-z0-9_-]{1,64}")){
                LocalAccountEpochNegativeHeadPublication.publish(
                    account,marker(account,bytes),bytes);
            }else{
                // A real hashed named-profile leaf starts with "profile-"
                // and has 64 SHA-256 characters (72 total). R152's
                // prototype codec rejects that valid legacy account
                // filename; NEVER relax its production encoding simply
                // to make the fixture pass. The negative veto must also
                // reject an unparseable HEAD for this selected account.
                LocalAccountEpochSnapshotObjects.publish(account,bytes);
                Files.write(
                    LocalAccountEpochNegativeHeadPublication.headPath(
                        account),
                    "R160_UNTRUSTED_UNENCODABLE_PROFILE_HEAD"
                        .getBytes(StandardCharsets.US_ASCII),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            }
        }else{
            LocalAccountEpochSnapshotObjects.publish(account,bytes);
            check(!Files.exists(
                    LocalAccountEpochNegativeHeadPublication.headPath(
                        account),LinkOption.NOFOLLOW_LINKS),
                "orphan case unexpectedly got HEAD");
        }
    }

    private static void refused(String label,String expected,Path account,
        Operation operation)throws Exception{
        boolean veto=false;
        try{
            operation.run();
        }catch(IOException error){
            for(Throwable cause=error;cause!=null;cause=cause.getCause()){
                if(String.valueOf(cause.getMessage()).contains(expected)){
                    veto=true;
                    break;
                }
            }
        }
        check(veto,label+" did not refuse with "+expected);
        check(!Files.exists(account.resolveSibling(
                account.getFileName().toString()+".tmp"),
                LinkOption.NOFOLLOW_LINKS),
            label+" allocated a fixed raw temp on denial");
        check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
            label+" leaked a RAW reservation");
    }

    private static void scenario(String name,Path account,
        Operation raw,Operation world,Operation session,
        Evidence evidence)throws Exception{
        raw.run();
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            name+" initial account missing");
        byte[] old=Files.readAllBytes(account);
        Object oldKey=Files.readAttributes(account,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        publish(account,evidence);
        Path head=LocalAccountEpochNegativeHeadPublication.headPath(account);
        boolean headPresent=evidence==Evidence.HEAD_AND_OBJECT;
        byte[] headBytes=headPresent?Files.readAllBytes(head):null;
        byte[] rawObject=image(accountName(account));
        String digest=LocalAccountEpochHeadCodec.digestSnapshot(rawObject);
        Path object=LocalAccountEpochSnapshotObjects.objectPath(
            account,digest);
        Object objectKey=Files.readAttributes(object,
            BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).fileKey();
        String expected=headPresent?
            "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY":
            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION";
        refused(name+" RAW "+evidence,expected,account,raw);
        refused(name+" World "+evidence,expected,account,world);
        refused(name+" session "+evidence,expected,account,session);
        check(Arrays.equals(old,Files.readAllBytes(account))&&
            oldKey.equals(Files.readAttributes(account,
                BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS)
                .fileKey()),name+" legacy postimage drifted");
        check(Files.exists(head,LinkOption.NOFOLLOW_LINKS)==headPresent&&
            (!headPresent||Arrays.equals(headBytes,Files.readAllBytes(head))),
            name+" negative HEAD was altered");
        LocalAccountEpochSnapshotObjects.Observation preserved=
            LocalAccountEpochSnapshotObjects.verify(account,digest,objectKey);
        check(preserved.fileKey.equals(objectKey),
            name+" immutable object replaced or changed");
        scenarios++;
        System.out.println("R160_G21377_LEGACY_NEGATIVE_VETO_PASS"+
            " scenario="+name+" evidence="+evidence+
            " rawWorldSessionDenied=true"+
            " originalAccountAndObjectPreserved=true"+
            " noPositiveEpochOrFinalAck=true");
    }

    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected CLI arguments");
        Path root=Files.createTempDirectory("r160-g21377-legacy-veto-");
        String[] properties={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] originals=new String[properties.length];
        for(int i=0;i<properties.length;i++){
            originals[i]=System.getProperty(properties[i]);
            System.clearProperty(properties[i]);
        }
        System.setProperty("spk.local.accountFile",
            root.resolve("opensrc.properties").toString());
        try{
            WorldPlayer player=new WorldPlayer();
            FilePlayerRepository repo=new FilePlayerRepository(
                account->root.resolve(account+".properties"));
            for(Evidence evidence:Evidence.values()){
                String suffix=evidence==Evidence.HEAD_AND_OBJECT?
                    "head":"orphan";
                String ordinary="r160-"+suffix;
                Path ordinaryPath=root.resolve(ordinary+".properties");
                scenario(ordinary,ordinaryPath,
                    ()->repo.save(PlayerSnapshotCodec.capture(
                        ordinary,new WorldPlayer())),
                    ()->repo.saveForWorld(PlayerSnapshotCodec.capture(
                        ordinary,new WorldPlayer())),
                    ()->repo.loadForWorldSession(ordinary),evidence);

                String primary="opensrc";
                if(evidence==Evidence.ORPHAN_OBJECT_ONLY){
                    // Distinct account file because primary is permanently
                    // quarantined after the first case.
                    System.setProperty("spk.local.accountFile",
                        root.resolve("opensrc-orphan.properties").toString());
                }
                Path primaryPath=AccountStore.accountFile();
                FilePlayerRepository primaryRepo=new FilePlayerRepository(
                    ignored->primaryPath);
                scenario(primary+"-"+suffix,primaryPath,
                    ()->AccountStore.save(player.bank(),player.equipment(),
                        player.movement(),player.petState(),
                        player.playerState()),
                    ()->primaryRepo.saveForWorld(PlayerSnapshotCodec.capture(
                        primary,new WorldPlayer())),
                    ()->primaryRepo.loadForWorldSession(primary),evidence);

                String named="r160-named-"+suffix;
                Path namedPath=LocalAccountProfiles.accountFile(named);
                FilePlayerRepository namedRepo=new FilePlayerRepository(
                    LocalAccountProfiles::accountFile);
                scenario(named,namedPath,
                    ()->LocalAccountProfiles.save(named,player.bank(),
                        player.equipment(),player.movement(),
                        player.petState(),player.playerState()),
                    ()->namedRepo.saveForWorld(PlayerSnapshotCodec.capture(
                        named,new WorldPlayer())),
                    ()->namedRepo.loadForWorldSession(named),evidence);

                String strict="r160-strict-"+suffix;
                Path strictPath=root.resolve(strict+".properties");
                StrictDurablePlayerSnapshotWriter strictWriter=
                    new StrictDurablePlayerSnapshotWriter(
                        u->root.resolve(u+".properties"));
                FilePlayerRepository strictRepo=new FilePlayerRepository(
                    u->root.resolve(u+".properties"));
                scenario(strict,strictPath,
                    ()->strictWriter.saveStrict(PlayerSnapshotCodec.capture(
                        strict,new WorldPlayer())),
                    ()->strictRepo.saveForWorld(PlayerSnapshotCodec.capture(
                        strict,new WorldPlayer())),
                    ()->strictRepo.loadForWorldSession(strict),evidence);
            }
            check(scenarios==8,"expected eight writer/admission modes");

            String missing="r160-absent";
            Path absent=root.resolve(missing+".properties");
            publish(absent,Evidence.HEAD_AND_OBJECT);
            refused("missing-file session",
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY",
                absent,()->repo.loadForWorldSession(missing));
            refused("missing-file RAW",
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY",
                absent,()->repo.save(PlayerSnapshotCodec.capture(
                    missing,new WorldPlayer())));
            check(!Files.exists(absent,LinkOption.NOFOLLOW_LINKS),
                "negative HEAD bootstrapped missing account");

            String malformed="r160-malformed";
            Path malformedAccount=root.resolve(malformed+".properties");
            repo.save(PlayerSnapshotCodec.capture(
                malformed,new WorldPlayer()));
            Path malformedHead=LocalAccountEpochNegativeHeadPublication
                .headPath(malformedAccount);
            Files.write(malformedHead,"damaged".getBytes(
                StandardCharsets.US_ASCII),StandardOpenOption.CREATE_NEW);
            refused("malformed newer HEAD",
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY",
                malformedAccount,()->repo.loadForWorldSession(malformed));
            refused("malformed newer HEAD raw",
                "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY",
                malformedAccount,()->repo.save(PlayerSnapshotCodec.capture(
                    malformed,new WorldPlayer())));

            String healthy="r160-healthy";
            repo.save(PlayerSnapshotCodec.capture(
                healthy,new WorldPlayer()));
            check(repo.loadForWorldSession(healthy).isPresent(),
                "unrelated sibling negative evidence blocked clean account");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "session owner leak");
            System.out.println("R160_G21377_LEGACY_ADMISSION_VETO_MATRIX_PASS"+
                " scenarios=8 rawWriters=4 worldWriters=4 sessions=4"+
                " newerHeadAndOrphanBothNegative=true"+
                " absentAccountAndMalformedHeadDenied=true"+
                " foreignAccountRemainsAvailable=true"+
                " defaultOffProductionWriterChecks=true"+
                " noOriginalClientOrPowerLossProof=true");
        }finally{
            for(int i=0;i<properties.length;i++){
                if(originals[i]==null)System.clearProperty(properties[i]);
                else System.setProperty(properties[i],originals[i]);
            }
            // Only remove the disposable source-regression fixture tree.
            try(Stream<Path> walk=Files.walk(root)){
                for(Path file:walk.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(file);
            }
        }
    }
}
