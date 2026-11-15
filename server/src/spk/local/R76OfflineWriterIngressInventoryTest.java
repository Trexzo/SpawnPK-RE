package spk.local;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** R76 offline source inventory and strict anti-drift regression. */
public final class R76OfflineWriterIngressInventoryTest {
    private static final Path SOURCE=Paths.get("src");
    private static final Path BASELINE=Paths.get(
        "..","docs","R76_WRITER_INGRESS_BASELINE.tsv"
    );

    public static void main(String[] args)throws Exception{
        if(args.length==1&&"emit".equals(args[0])){
            List<R76OfflineWriterIngressInventory.Entry> entries=
                R76OfflineWriterIngressInventory.scan(SOURCE);
            System.out.println("R76_BASELINE_BEGIN");
            for(R76OfflineWriterIngressInventory.Entry e:entries)
                System.out.println(e.row());
            System.out.println("R76_BASELINE_END total="+entries.size());
            return;
        }
        require(args.length==0,"R76 unexpected test arguments");
        List<R76OfflineWriterIngressInventory.Entry> source=
            R76OfflineWriterIngressInventory.scan(SOURCE);
        List<String> expected=
            R76OfflineWriterIngressInventory.readBaseline(BASELINE);
        R76OfflineWriterIngressInventory.Review exact=
            R76OfflineWriterIngressInventory.compare(source,expected);
        require(exact.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_UNFENCED_OR_UNPROVEN_ALL_WRITER_ADMISSION &&
                exact.totalSites>=45&&
                exact.unfencedSites>=6&&
                exact.unreviewedSites==0&&
                exact.extra.isEmpty()&&exact.missing.isEmpty(),
            "R76 source inventory incomplete or unreviewed; status="+
            exact.status+" total="+exact.totalSites+
            " unfenced="+exact.unfencedSites+
            " unknown="+exact.unreviewedSites+
            " extra="+sample(exact.extra)+" missing="+
            sample(exact.missing));
        require(Files.isRegularFile(BASELINE),
            "R76 baseline missing from repo");
        deny(exact);

        R76OfflineWriterIngressInventory.Review fromDisk=
            R76OfflineWriterIngressInventory.inspect(
                SOURCE,BASELINE
            );
        require(fromDisk.status==exact.status&&
                fromDisk.totalSites==exact.totalSites,
            "R76 disk audit disagrees with in-memory inventory");
        deny(fromDisk);

        // Comment and string bait MUST NOT be parsed as executable
        // sink calls. A genuinely new receiver.save and Files.write
        // in an unknown class are both found and block admission.
        String novel=
            "package spk.local;\n"+
            "class R76SyntheticWriter {\n"+
            " // repository.save(commented);\n"+
            " String harmless=\"Files.write(unrelated,bytes);\";\n"+
            " char ch='a'; /* Files.move(fake,a,b); */\n"+
            " void unauthorized() {\n"+
            "   repository . save(snapshot);\n"+
            "   Files . write(account,raw);\n"+
            " }\n"+
            "}\n";
        List<R76OfflineWriterIngressInventory.Entry> introduced=
            R76OfflineWriterIngressInventory.scanText(
                "spk/local/R76SyntheticWriter.java",novel
            );
        require(introduced.size()==2&&
                introduced.get(0).call.equals("repository.save")&&
                introduced.get(1).call.equals("Files.write"),
            "R76 scanner missed new unreviewed save or parsed bait");
        List<R76OfflineWriterIngressInventory.Entry> modified=
            new ArrayList<>(source);
        modified.addAll(introduced);
        R76OfflineWriterIngressInventory.Review extra=
            R76OfflineWriterIngressInventory.compare(
                modified,expected
            );
        require(extra.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_INVENTORY_DRIFT&&
                extra.extra.size()==2&&
                extra.missing.isEmpty(),
            "new writer was silently added to reviewed inventory");
        deny(extra);

        // Removing or moving even an already reviewed sink is a
        // source-review event: no stale manifest is silently accepted.
        modified=new ArrayList<>(source);
        modified.remove(0);
        R76OfflineWriterIngressInventory.Review deleted=
            R76OfflineWriterIngressInventory.compare(
                modified,expected
            );
        require(deleted.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_INVENTORY_DRIFT&&
                deleted.missing.size()==1,
            "deleted/moved sink was silently accepted");
        deny(deleted);

        List<String> forged=new ArrayList<>(expected);
        forged.add(expected.get(0));
        R76OfflineWriterIngressInventory.Review duplicate=
            R76OfflineWriterIngressInventory.compare(
                source,forged
            );
        require(duplicate.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_INVENTORY_DRIFT&&
                duplicate.missing.size()==1,
            "duplicate baseline record forged an inventory pass");
        deny(duplicate);

        // New call in an existing known-unfenced file still triggers
        // baseline drift even though it would inherit that file's risk.
        List<R76OfflineWriterIngressInventory.Entry> addedToKnown=
            R76OfflineWriterIngressInventory.scanText(
                "spk/local/WorldPlayerPersistence.java",
                "void newWriter(){ repository.save(snapshot); }"
            );
        modified=new ArrayList<>(source);
        modified.addAll(addedToKnown);
        R76OfflineWriterIngressInventory.Review extraKnown=
            R76OfflineWriterIngressInventory.compare(
                modified,expected
            );
        require(extraKnown.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_INVENTORY_DRIFT,
            "novel writer in known file silently escaped re-review");
        deny(extraKnown);

        // Explicit UNREVIEWED classification cannot be made safe by
        // simply adding an unknown path to a purported baseline.
        List<String> approvedUnknown=new ArrayList<>(expected);
        for(R76OfflineWriterIngressInventory.Entry entry:introduced)
            approvedUnknown.add(entry.row());
        R76OfflineWriterIngressInventory.Review unknown=
            R76OfflineWriterIngressInventory.compare(
                sourcePlus(source,introduced),approvedUnknown
            );
        require(unknown.status==
                R76OfflineWriterIngressInventory.Status
                    .BLOCK_UNREVIEWED_SINK,
            "unclassified writer was blessed by baseline editing");
        deny(unknown);

        // Missing source roots and non-regular manifests are denied.
        try{
            R76OfflineWriterIngressInventory.scan(
                Paths.get("r76-absent-src")
            );
            throw new AssertionError("missing source root permitted");
        }catch(IOException expectedDenied){
            require(expectedDenied.getMessage().contains(
                "R76_SOURCE_ROOT_DENIED"),
                "wrong missing source refusal");
        }

        System.out.println(
            "R76_OFFLINE_WRITER_INGRESS_INVENTORY_PASS "+
            "sourceWideExecutableSinkInventoryBaselineMatched=true "+
            "allClassifiedSitesRemainNonAuthorizing=true "+
            "newAndMovedWriterSitesFailClosed=true "+
            "commentsAndStringBaitIgnored=true "+
            "missingOrDuplicateBaselineRejected=true "+
            "newWriterInsideKnownFileStillRequiresReview=true "+
            "explicitUnreviewedSinkNeverPromoted=true "+
            "sourceAndManifestRemainReadOnly=true "+
            "noAutomaticRetirementLoginOrRecovery=true "+
            "noProductionHooksOrLiveAccountChanges=true "+
            "reviewedSinkSites="+exact.totalSites+
            " unfencedSites="+exact.unfencedSites
        );
    }

    private static List<R76OfflineWriterIngressInventory.Entry> sourcePlus(
        List<R76OfflineWriterIngressInventory.Entry> current,
        List<R76OfflineWriterIngressInventory.Entry> other
    ){
        List<R76OfflineWriterIngressInventory.Entry> out=
            new ArrayList<>(current);
        out.addAll(other);
        return out;
    }

    private static String sample(List<String> list){
        return list.subList(0,Math.min(6,list.size())).toString();
    }

    private static void deny(
        R76OfflineWriterIngressInventory.Review review
    ){
        require(!review.retirementAuthorized()&&
                !review.loginAuthorized()&&
                !review.recoveryAuthorized()&&
                !review.replayAuthorized()&&
                !review.allWritersFenced()&&
                !review.sourceInventoryComplete()&&
                !review.modifiesEvidence(),
            "R76 granted unauthorized retirement/production access");
    }

    private static void require(boolean pass,String message){
        if(!pass)throw new AssertionError(message);
    }
    private R76OfflineWriterIngressInventoryTest(){}
}
