package spk.local;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * R77 read-only source inventory. Enumerates every checked-out Java file
 * rather than claiming that naming heuristics prove ownership.
 *
 * An untagged lambda cannot be automatically labeled player-owned:
 * each discovered caller needs semantic review before migration.
 */
public final class R77OfflineEventProducerSourceAuditTest {
    private static final Pattern UNTAGGED_SCHEDULE=Pattern.compile(
        "(?:\\.events\\(\\)|\\bevents\\(\\)|\\bevents)\\s*\\.\\s*schedule\\s*\\("
    );
    private static final Pattern OWNER_TAGGED=Pattern.compile(
        "\\.scheduleOwnedForTest\\s*\\("
    );
    private static final Pattern DOMAIN_PUBLICATION=Pattern.compile(
        "(?:\\.domainEvents\\(\\)|\\bdomainEvents)\\s*\\.\\s*publish\\s*\\("
    );

    public static void main(String[] args)throws Exception{
        Path source=Paths.get("src","spk");
        if(!Files.isDirectory(source))
            throw new IllegalStateException(
                "R77_SOURCE_AUDIT_DENIED missing checked-out server source"
            );

        List<Path> javaFiles=new ArrayList<>();
        try(Stream<Path> all=Files.walk(source)){
            all.filter(p->p.getFileName().toString()
                    .endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(javaFiles::add);
        }

        int productionUntagged=0;
        int testUntagged=0;
        int ownerTagged=0;
        int domainPublications=0;
        boolean contextFixtureFound=false;

        for(Path path:javaFiles){
            String s=new String(
                Files.readAllBytes(path),StandardCharsets.UTF_8
            );
            String display=source.relativize(path).toString()
                .replace('\\','/');
            boolean test=path.getFileName().toString()
                .endsWith("Test.java");
            if(display.endsWith(
                    "local/DomainEventBusWorldContextTest.java"))
                contextFixtureFound=true;
            int untagged=count(UNTAGGED_SCHEDULE,s);
            int tagged=count(OWNER_TAGGED,s);
            int domain=count(DOMAIN_PUBLICATION,s);
            if(untagged>0){
                if(test)testUntagged+=untagged;
                else productionUntagged+=untagged;
                // Discovery is not attribution. A source match may
                // refer to a global event or to a player closure.
                System.out.println(
                    "R77_SOURCE_EVENT_SCHEDULE_CANDIDATE "+
                    "kind="+(test?"fixture":"unclassified-production")+
                    " path="+display+" count="+untagged
                );
            }
            if(tagged>0)ownerTagged+=tagged;
            if(domain>0)domainPublications+=domain;
        }
        if(javaFiles.size()<1||!contextFixtureFound)
            throw new AssertionError(
                "R77 source inventory incomplete; world context missing"
            );

        System.out.println(
            "R77_READ_ONLY_EVENT_SOURCE_INVENTORY_PASS "+
            "scannedJavaFiles="+javaFiles.size()+
            " unclassifiedProductionScheduleMatches="+
                productionUntagged+
            " testScheduleMatches="+testUntagged+
            " ownerTaggedMatches="+ownerTagged+
            " domainPublicationMatches="+domainPublications+
            " untaggedCallerOwnershipNotInferred=true "+
            "globalEventServiceNotReclassified=true "+
            "sourceOnlyNoRuntimeSideEffects=true"
        );
    }

    private static int count(Pattern pattern,String source){
        Matcher matcher=pattern.matcher(source);
        int n=0;
        while(matcher.find())n++;
        return n;
    }

    private R77OfflineEventProducerSourceAuditTest(){}
}
