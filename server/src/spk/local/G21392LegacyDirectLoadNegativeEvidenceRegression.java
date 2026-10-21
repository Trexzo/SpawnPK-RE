package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * R64: legacy direct primary/named loaders (outside the guarded World
 * session repository path) must refuse negative evidence before any
 * default-return or mutation and recheck after decode, before apply.
 */
public final class G21392LegacyDirectLoadNegativeEvidenceRegression {
    private G21392LegacyDirectLoadNegativeEvidenceRegression(){}
    @FunctionalInterface interface Load{
        String run(WorldPlayer destination)throws IOException;
    }
    @FunctionalInterface interface Save{
        void run(WorldPlayer source)throws IOException;
    }
    @FunctionalInterface interface HookLoad{
        String run(WorldPlayer dest,
            FilePlayerRepository.BeforeWorldReplace cutpoint)throws IOException;
    }
    private static void check(boolean truth,String why){
        if(!truth)throw new AssertionError("R64_DIRECT_LOAD "+why);
    }
    private static boolean has(Throwable t,String code){
        for(Throwable c=t;c!=null;c=c.getCause())
            if(String.valueOf(c.getMessage()).contains(code))return true;
        return false;
    }
    private static SortedMap<String,String> state(WorldPlayer p){
        return PlayerSnapshotSchemaV1.capture(
            p.bank(),p.equipment(),p.movement(),p.petState(),
            p.playerState(),0);
    }
    private static void veto(
        String name,WorldPlayer dest,Load load,String reason)throws Exception{
        SortedMap<String,String> original=state(dest);
        boolean refused=false;
        try{load.run(dest);}
        catch(IOException ex){refused=has(ex,reason);}
        check(refused,name+" did not veto "+reason);
        check(original.equals(state(dest)),
            name+" applied data despite negative evidence");
    }
    private static void fixture(
        String label,Path account,Save save,Load load,HookLoad hooked
    )throws Exception{
        // Distinct identity and state from the load destination:
        // a successful apply must be observable in the destination.
        WorldPlayer persisted=new WorldPlayer();
        persisted.movement().setPersistentRun(true);
        persisted.movement().setRunEnergy(19);
        save.run(persisted);
        check(Files.isRegularFile(account,LinkOption.NOFOLLOW_LINKS),
            label+" baseline account missing");
        WorldPlayer destination=new WorldPlayer();
        destination.movement().setPersistentRun(false);
        destination.movement().setRunEnergy(92);
        check(!state(persisted).equals(state(destination)),
            label+" fixture has identical source/destination states");
        String healthy=load.run(destination);
        check(healthy.contains("ACCOUNT_LOADED"),
            label+" healthy load unexpectedly failed");
        check(destination.movement().persistentRun()&&
                destination.movement().runEnergy()==19,
            label+" healthy legacy loader failed to apply");
        Path lab=account.resolveSibling(
            account.getFileName()+".g21386-intent-orphan");
        Files.write(lab,"R64_ORPHAN_LAB".getBytes(StandardCharsets.US_ASCII),
            StandardOpenOption.CREATE_NEW);
        veto(label+" orphan",new WorldPlayer(),load,
            "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
        check(Files.exists(lab,LinkOption.NOFOLLOW_LINKS),
            label+" evidence auto-retired");
        Files.delete(lab); // disposable fixture only, not recovery
        // Late marker AFTER properties decoding: mutation is still
        // prohibited before WorldPlayer state apply.
        AtomicBoolean called=new AtomicBoolean();
        WorldPlayer untouched=new WorldPlayer();
        untouched.movement().setRunEnergy(85);
        SortedMap<String,String> before=state(untouched);
        boolean denied=false;
        try{
            hooked.run(untouched,user->{
                check(!called.getAndSet(true),"hook called more than once");
                Files.write(lab,new byte[]{1},StandardOpenOption.CREATE_NEW);
            });
        }catch(IOException expected){
            denied=has(expected,
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
        }
        check(called.get()&&denied,
            label+" late marker allowed decoded state to apply");
        check(before.equals(state(untouched)),
            label+" late marker changed gameplay state");
        check(Files.exists(lab,LinkOption.NOFOLLOW_LINKS),
            label+" late marker was removed by loader");
        Files.delete(lab);
        // Retained R50 negative HEAD also refuses the direct route
        // with NO experimental feature switches.
        LocalNegativeAccountHeadPrototype.publishNegative(account,
            label,64L,"R64_NEGATIVE".getBytes(StandardCharsets.US_ASCII));
        veto(label+" negative HEAD",new WorldPlayer(),load,
            "R53_NEGATIVE_ACCOUNT_HEAD_PRESENT_NO_AUTO_RECOVERY");
        check(Files.isRegularFile(
            LocalNegativeAccountHeadPrototype.headPath(account),
            LinkOption.NOFOLLOW_LINKS),label+" permanent HEAD retired");
    }
    public static void main(String[] args)throws Exception{
        check(args.length==0,"unexpected args");
        Path root=Files.createTempDirectory("r64-direct-loader-");
        String[] props={
            "spk.local.accountFile",
            LocalJvmAccountSessionLease.PROPERTY,
            LocalJvmAccountSessionLease.RAW_WRITE_GATE_PROPERTY,
            LocalCrossJvmAccountSessionLock.PROPERTY,
            LocalCrossJvmAccountSessionLock.CONTINUITY_PROPERTY
        };
        String[] original=new String[props.length];
        for(int i=0;i<props.length;i++){
            original[i]=System.getProperty(props[i]);
            System.clearProperty(props[i]);
        }
        System.setProperty(props[0],
            root.resolve("opensrc.properties").toString());
        try{
            Path primary=AccountStore.accountFile();
            fixture("opensrc",primary,
                p->AccountStore.save(p.bank(),p.equipment(),
                    p.movement(),p.petState(),p.playerState()),
                p->AccountStore.load(p.bank(),p.equipment(),
                    p.movement(),p.petState(),p.playerState()),
                (p,h)->AccountStore.loadWithPostReadHookForTest(
                    p.bank(),p.equipment(),p.movement(),p.petState(),
                    p.playerState(),h));
            String named="r64-hashed";
            Path namedPath=LocalAccountProfiles.accountFile(named);
            fixture(named,namedPath,
                p->LocalAccountProfiles.save(named,p.bank(),p.equipment(),
                    p.movement(),p.petState(),p.playerState()),
                p->LocalAccountProfiles.load(named,p.bank(),p.equipment(),
                    p.movement(),p.petState(),p.playerState()),
                (p,h)->LocalAccountProfiles.loadWithPostReadHookForTest(
                    named,p.bank(),p.equipment(),p.movement(),p.petState(),
                    p.playerState(),h));
            // Missing legacy file with an orphan LAB artifact must NOT
            // silently return a fresh account with default state.
            String missing="r64-missing";
            Path missingFile=LocalAccountProfiles.accountFile(missing);
            Files.createDirectories(missingFile.getParent());
            Path orphan=missingFile.resolveSibling(
                missingFile.getFileName()+".g21385-image-stranded");
            Files.write(orphan,new byte[]{1},StandardOpenOption.CREATE_NEW);
            veto("missing named orphan",new WorldPlayer(),
                p->LocalAccountProfiles.load(missing,p.bank(),p.equipment(),
                    p.movement(),p.petState(),p.playerState()),
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
            // The direct PRIMARY route must also treat a missing file
            // as negative if LAB evidence for its path remains.
            Path missingPrimary=root.resolve("missing-primary.properties");
            System.setProperty(props[0],missingPrimary.toString());
            Path marker=missingPrimary.resolveSibling(
                missingPrimary.getFileName()+".g21387-head-stranded");
            Files.write(marker,new byte[]{1},StandardOpenOption.CREATE_NEW);
            veto("missing primary orphan",new WorldPlayer(),
                p->AccountStore.load(p.bank(),p.equipment(),p.movement(),
                    p.petState(),p.playerState()),
                "R63_REUSABLE_LAB_EVIDENCE_PRESENT_NO_LEGACY_ADMISSION");
            check(LocalJvmAccountSessionLease.activeCountForTest()==0,
                "live World token leaked");
            check(LocalJvmAccountSessionLease.rawWriteReservationsForTest()==0,
                "RAW reservation leaked");
            System.out.println("R64_LEGACY_DIRECT_LOAD_EVIDENCE_PASS"+
                " primaryAndNamed=true"+
                " healthyDecodedLoad=true"+
                " orphanBeforeReadDenied=true"+
                " orphanAfterDecodeBeforeApplyDenied=true"+
                " missingFileWithOrphanDenied=true"+
                " permanentR50HeadDenied=true"+
                " stateUnchangedOnVeto=true"+
                " defaultOffPreservedAbsentEvidence=true"+
                " noPositiveAdmissionOrRecovery=true");
        }finally{
            for(int i=0;i<props.length;i++){
                if(original[i]==null)System.clearProperty(props[i]);
                else System.setProperty(props[i],original[i]);
            }
            try(Stream<Path> all=Files.walk(root)){
                for(Path p:all.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
}
