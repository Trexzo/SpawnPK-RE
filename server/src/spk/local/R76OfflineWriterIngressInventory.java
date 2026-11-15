package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * R76 offline source-level inventory. Negative-only, NO runtime hooks.
 *
 * Enumerates all Java sources under server/src/spk excluding *Test.java;
 * removes comments, string and char literal contents before scanning
 * receiver.method(...) call sites. An exact human-reviewed baseline
 * must match BEFORE a new writer path can be considered inventoried.
 *
 * This is NOT a Java AST or complete transitive call graph. It cannot
 * discover reflection, method references, nested receiver expressions,
 * JNI, dynamically dispatched aliases or calls outside the source
 * root. Therefore even the best status ALWAYS denies retirement,
 * authentication, recovery, intent cleanup and live account access.
 */
final class R76OfflineWriterIngressInventory {
    private static final Pattern CALL=Pattern.compile(
        "(?<![A-Za-z0-9_$])([A-Za-z_$][A-Za-z0-9_$]*)"+
        "\\s*\\.\\s*([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\("
    );
    private static final Pattern CTOR=Pattern.compile(
        "\\bnew\\s+(FilePlayerRepository|FileOutputStream|"+
        "FileWriter|RandomAccessFile|PrintWriter)\\s*\\("
    );
    private static final Set<String> FILE_SINKS=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "write","writeString","move","copy","newOutputStream",
            "newBufferedWriter","newByteChannel","delete",
            "deleteIfExists","createTempFile","createFile"
        )));
    private static final Set<String> TRANSITIONS=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "save","saveQuiet","saveIfCurrent",
            "saveIfCurrentUnderLock","saveOwned",
            "saveOwnedUnderLock","replaceOwner",
            "replaceOwnerUnderLock","releaseOwner",
            "releaseOwnerUnderLock"
        )));
    private static final Set<String> UNFENCED_FILES=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "AccountStore.java","LocalAccountProfiles.java",
            "PetAccessoryPersistence.java","FilePlayerRepository.java",
            "WorldPlayerPersistence.java","LocalAccountLifecycle.java",
            "World.java","AtomicAccountPublisher.java"
        )));
    private static final Set<String> COOPERATIVE_BUT_NO_R73=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "R57TradePairJournal.java","R59TradePairIntentIndex.java",
            "R61TradePairReplay.java","R62TradePairCompletionCertificate.java",
            "R63AccountSaveAdmission.java","R64OfflineSessionOwnership.java"
        )));
    private static final Set<String> TEST_EVIDENCE_FILES=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "R65DeferredSessionSaveDispatch.java",
            "R69OfflineIoCommitGate.java",
            "R70OfflineCheckpointAuthority.java",
            "R71OfflineFinalSaveCheckpointFence.java",
            "R72OfflineInFlightCommitEvidence.java",
            "R73OfflinePendingWriteAdmission.java",
            "R51InterruptedAccountPublisherChild.java"
        )));
    private static final Set<String> OTHER_REVIEWED_FILES=
        Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "R60TradePairCoordinator.java","ConfigHoverPatchTool.java",
            "CustomAssetAuthoringRepository.java",
            "HomeCollisionOverlayRepository.java",
            "HomeLandmarkRepository.java",
            "HomeNpcWanderRepository.java","HomeNpcSpawnRepository.java",
            "HomeObjectOverlayRepository.java",
            "HomeObjectCollisionDefinitionRepository.java",
            "LocalDevVisualOverrideStore.java",
            "LocalDialogNumberKeyState.java",
            "MarketplaceService.java","PetDefinitionRepository.java",
            "VoidglassR2ConfigPatchTool.java",
            "LocalPetInventoryDialogHandler.java"
        )));

    enum Status {
        BLOCK_INVENTORY_DRIFT,
        BLOCK_UNREVIEWED_SINK,
        BLOCK_UNFENCED_OR_UNPROVEN_ALL_WRITER_ADMISSION
    }

    static final class Entry implements Comparable<Entry> {
        final String source;
        final int line;
        final String call;
        final String category;
        Entry(String source,int line,String call,String category){
            this.source=source;this.line=line;
            this.call=call;this.category=category;
        }
        String row(){
            return source+"|"+line+"|"+call+"|"+category;
        }
        @Override public int compareTo(Entry e){
            return row().compareTo(e.row());
        }
    }

    static final class Review {
        final Status status;
        final String reason;
        final List<String> extra;
        final List<String> missing;
        final int totalSites,unfencedSites,unreviewedSites;
        Review(Status status,String reason,List<String> extra,
            List<String> missing,List<Entry> found){
            this.status=status;this.reason=reason;
            this.extra=Collections.unmodifiableList(
                new ArrayList<>(extra));
            this.missing=Collections.unmodifiableList(
                new ArrayList<>(missing));
            totalSites=found.size();
            int unfenced=0,unknown=0;
            for(Entry e:found){
                if("UNFENCED_ACCOUNT".equals(e.category))
                    unfenced++;
                if("UNREVIEWED".equals(e.category))
                    unknown++;
            }
            unfencedSites=unfenced;unreviewedSites=unknown;
        }
        boolean retirementAuthorized(){return false;}
        boolean loginAuthorized(){return false;}
        boolean recoveryAuthorized(){return false;}
        boolean replayAuthorized(){return false;}
        boolean allWritersFenced(){return false;}
        boolean sourceInventoryComplete(){return false;}
        boolean modifiesEvidence(){return false;}
    }

    static Review inspect(Path javaRoot,Path baseline)throws IOException{
        List<Entry> actual=scan(javaRoot);
        List<String> expected=readBaseline(baseline);
        return compare(actual,expected);
    }

    static Review compare(
        List<Entry> actual,List<String> expectedRows
    ){
        List<String> observed=new ArrayList<>();
        for(Entry e:actual)observed.add(e.row());
        Collections.sort(observed);
        List<String> expected=new ArrayList<>(expectedRows);
        Collections.sort(expected);
        List<String> extra=new ArrayList<>();
        List<String> missing=new ArrayList<>();
        // Multiset rather than set: duplicate call sites or forged
        // duplicate baseline entries must be reported as drift.
        Map<String,Integer> counts=new HashMap<>();
        for(String row:expected)
            counts.put(row,counts.getOrDefault(row,0)+1);
        for(String row:observed){
            int current=counts.getOrDefault(row,0);
            if(current>0)counts.put(row,current-1);
            else extra.add(row);
        }
        for(Map.Entry<String,Integer> e:counts.entrySet())
            for(int i=0;i<e.getValue();i++)
                missing.add(e.getKey());
        Collections.sort(extra);
        Collections.sort(missing);
        if(!extra.isEmpty()||!missing.isEmpty())
            return new Review(
                Status.BLOCK_INVENTORY_DRIFT,
                "R76_UNREVIEWED_SOURCE_OR_BASELINE_DRIFT",
                extra,missing,actual
            );
        for(Entry e:actual)
            if("UNREVIEWED".equals(e.category))
                return new Review(
                    Status.BLOCK_UNREVIEWED_SINK,
                    "R76_UNCLASSIFIED_WRITER_SOURCE",extra,missing,actual
                );
        return new Review(
            Status.BLOCK_UNFENCED_OR_UNPROVEN_ALL_WRITER_ADMISSION,
            "R76_EXACT_INVENTORY_MATCH_BUT_NO_RETIREMENT_AUTHORITY",
            extra,missing,actual
        );
    }

    static List<String> readBaseline(Path path)throws IOException{
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||
           Files.isSymbolicLink(path))
            throw new IOException("R76_BASELINE_DENIED missing/nonregular");
        List<String> rows=new ArrayList<>();
        for(String line:Files.readAllLines(
                path,StandardCharsets.UTF_8)){
            if(line.isEmpty()||line.startsWith("#"))continue;
            if(!line.matches(
                    "spk/[A-Za-z0-9_./-]+\\.java\\|[1-9][0-9]*\\|"+
                    "[A-Za-z_$][A-Za-z0-9_$.]*\\|"+
                    "(UNFENCED_ACCOUNT|COOPERATIVE_NO_R73|"+
                    "TEST_EVIDENCE_ONLY|NONACCOUNT_REVIEWED|UNREVIEWED)"
            ))
                throw new IOException("R76_BASELINE_DENIED invalid row");
            rows.add(line);
        }
        if(rows.isEmpty()||rows.size()>2500)
            throw new IOException("R76_BASELINE_DENIED row limit");
        return rows;
    }

    static List<Entry> scan(Path javaRoot)throws IOException{
        Path root=Objects.requireNonNull(
            javaRoot,"javaRoot").toAbsolutePath().normalize();
        if(Files.isSymbolicLink(root)||
           !Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("R76_SOURCE_ROOT_DENIED");
        List<Entry> entries=new ArrayList<>();
        int files=0;
        try(Stream<Path> paths=Files.walk(root)){
            List<Path> sourcePaths=new ArrayList<>();
            paths.filter(p->p.toString().endsWith(".java"))
                .forEach(sourcePaths::add);
            Collections.sort(sourcePaths);
            for(Path p:sourcePaths){
                String filename=p.getFileName().toString();
                if(filename.endsWith("Test.java"))continue;
                if(++files>2500)
                    throw new IOException("R76_SOURCE_FILE_LIMIT");
                BasicFileAttributes attr=Files.readAttributes(
                    p,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!attr.isRegularFile()||
                   attr.size()>2_000_000L)
                    throw new IOException("R76_SOURCE_FILE_DENIED "+p);
                String relative=root.relativize(p).toString()
                    .replace('\\','/');
                entries.addAll(scanText(relative,
                    Files.readString(p,StandardCharsets.UTF_8)));
                if(entries.size()>2500)
                    throw new IOException("R76_SINK_LIMIT");
            }
        }
        Collections.sort(entries);
        return Collections.unmodifiableList(entries);
    }

    static List<Entry> scanText(String relative,String source){
        Objects.requireNonNull(relative,"relative");
        Objects.requireNonNull(source,"source");
        String masked=maskNonCode(source);
        List<Entry> out=new ArrayList<>();
        Matcher calls=CALL.matcher(masked);
        while(calls.find()){
            String receiver=calls.group(1);
            String method=calls.group(2);
            boolean tracked=
                ("Files".equals(receiver)&&
                    FILE_SINKS.contains(method))||
                ("AtomicAccountPublisher".equals(receiver)&&
                    "publish".equals(method))||
                TRANSITIONS.contains(method);
            if(tracked)
                out.add(new Entry(
                    relative,lineNumber(masked,calls.start()),
                    receiver+"."+method,category(relative)
                ));
        }
        Matcher constructors=CTOR.matcher(masked);
        while(constructors.find())
            out.add(new Entry(
                relative,lineNumber(masked,constructors.start()),
                "new."+constructors.group(1),category(relative)
            ));
        Collections.sort(out);
        return out;
    }

    private static int lineNumber(String code,int index){
        int line=1;
        for(int i=0;i<index;i++)
            if(code.charAt(i)=='\n')line++;
        return line;
    }

    private static String category(String relative){
        String file=relative.substring(relative.lastIndexOf('/')+1);
        if(UNFENCED_FILES.contains(file))return "UNFENCED_ACCOUNT";
        if(COOPERATIVE_BUT_NO_R73.contains(file))
            return "COOPERATIVE_NO_R73";
        if(TEST_EVIDENCE_FILES.contains(file))
            return "TEST_EVIDENCE_ONLY";
        if(OTHER_REVIEWED_FILES.contains(file))
            return "NONACCOUNT_REVIEWED";
        return "UNREVIEWED";
    }

    /**
     * Preserves source offsets and newline boundaries. The Java11
     * repository uses ordinary literals (no Java text blocks). This
     * lightweight lexer is still not a complete language parser.
     */
    private static String maskNonCode(String text){
        char[] a=text.toCharArray();
        char[] masked=text.toCharArray();
        final int CODE=0,LINE=1,BLOCK=2,STRING=3,CHAR=4;
        int state=CODE;
        boolean escaped=false;
        for(int i=0;i<a.length;i++){
            char c=a[i];
            char next=i+1<a.length?a[i+1]:'\0';
            if(state==CODE){
                if(c=='/'&&next=='/'){
                    masked[i]=' ';masked[++i]=' ';state=LINE;
                }else if(c=='/'&&next=='*'){
                    masked[i]=' ';masked[++i]=' ';state=BLOCK;
                }else if(c=='"'){
                    masked[i]=' ';state=STRING;escaped=false;
                }else if(c=='\''){
                    masked[i]=' ';state=CHAR;escaped=false;
                }
            }else{
                if(c!='\n'&&c!='\r')masked[i]=' ';
                if(state==LINE){
                    if(c=='\n')state=CODE;
                }else if(state==BLOCK){
                    if(c=='*'&&next=='/'){
                        masked[++i]=' ';state=CODE;
                    }
                }else if(state==STRING||state==CHAR){
                    if(escaped){escaped=false;continue;}
                    if(c=='\\'){escaped=true;continue;}
                    if((state==STRING&&c=='"')||
                       (state==CHAR&&c=='\''))state=CODE;
                }
            }
        }
        return new String(masked);
    }

    private R76OfflineWriterIngressInventory(){}
}
