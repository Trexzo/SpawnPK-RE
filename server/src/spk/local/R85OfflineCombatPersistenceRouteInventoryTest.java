package spk.local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * R85 READ ONLY SOURCE INVENTORY. Map actual combat producer/consumer and
 * account persistence references in ALL checked-out spk Java sources.
 *
 * Lexical text references are NOT method-call graph edges and not proof
 * a path is live. This audit deliberately distinguishes explicit
 * source-exact anchors from unclassified references, and prints every
 * source candidate so a future reviewer can decide which are active.
 */
public final class R85OfflineCombatPersistenceRouteInventoryTest {
    private static final class Route {
        final String relativePath;
        final String requiredFragment;
        Route(String path,String fragment){
            this.relativePath=path;
            this.requiredFragment=fragment;
        }
    }

    private static final Route[] REQUIRED={
        new Route("local/PlayerCombatResolutionService.java",
            "new CombatOutcome("),
        new Route("local/LocalLabPvmKillOutcomeProducer.java",
            "new CombatOutcome("),
        new Route("local/LocalWorldTickCoordinator.java",
            "this.world.domainEvents().publish(outcome)"),
        new Route("local/LocalSession.java",
            "world.persistence().captureAndSave("),
        new Route("local/WorldPlayerPersistence.java",
            "PlayerSnapshotCodec.capture("),
        new Route("local/PlayerSnapshotCodec.java",
            "PlayerSnapshotSchemaV1.capture("),
        new Route("local/PlayerSnapshotCodec.java",
            "player.snapshotExtensions()"),
        new Route("local/FilePlayerRepository.java",
            "AtomicAccountPublisher.publish("),
        new Route("local/CombatOutcomeObjectiveObserver.java",
            "objectives.advance("),
        new Route("local/CombatOutcomeMatchObserver.java",
            "tryAdjustPresentParticipantScore("),
        new Route("local/CombatOutcomeCollectionLogObserver.java",
            "observeKillCountsAtomically(")
    };

    public static void main(String[] args)throws Exception{
        Path root=Paths.get("src","spk");
        if(!Files.isDirectory(root))
            throw new IllegalStateException(
                "R85_SOURCE_AUDIT_DENIED missing complete checked-out src/spk"
            );

        int anchored=0;
        for(Route route:REQUIRED){
            Path source=root.resolve(route.relativePath);
            if(!Files.isRegularFile(source))
                throw new AssertionError(
                    "R85 required source route missing "+route.relativePath
                );
            String java=new String(
                Files.readAllBytes(source),StandardCharsets.UTF_8
            );
            if(!java.contains(route.requiredFragment))
                throw new AssertionError(
                    "R85 required source boundary changed "+route.relativePath+
                    " fragment="+route.requiredFragment
                );
            anchored++;
        }

        List<Path> javaFiles=new ArrayList<>();
        try(Stream<Path> source=Files.walk(root)){
            source.filter(p->p.getFileName().toString()
                .endsWith(".java"))
                .sorted(Comparator.comparing(Path::toString))
                .forEach(javaFiles::add);
        }

        int combatResolverRefs=0;
        int observerGroupRefs=0;
        int playerSaveRefs=0;
        int extensionRefs=0;
        int testFileRefs=0;
        for(Path path:javaFiles){
            String code=new String(
                Files.readAllBytes(path),StandardCharsets.UTF_8
            );
            String display=root.relativize(path).toString()
                .replace('\\','/');
            boolean hasResolver=code.contains(
                "PlayerCombatResolutionService"
            );
            boolean hasGroup=code.contains(
                "CombatOutcomeObserverGroup"
            );
            boolean hasSave=code.contains(
                "PlayerSnapshotCodec.capture(")
                ||code.contains("world.persistence().captureAndSave(");
            boolean hasExtension=code.contains(
                "replaceNamespace(")
                ||code.contains("snapshotExtensions()");
            if(!hasResolver&&!hasGroup&&!hasSave&&!hasExtension)
                continue;

            if(hasResolver)combatResolverRefs++;
            if(hasGroup)observerGroupRefs++;
            if(hasSave)playerSaveRefs++;
            if(hasExtension)extensionRefs++;
            if(display.endsWith("Test.java"))testFileRefs++;
            System.out.println(
                "R85_SOURCE_ROUTE_CANDIDATE path="+display+
                " resolver="+hasResolver+
                " group="+hasGroup+
                " playerSave="+hasSave+
                " extensions="+hasExtension+
                " declaredTestFile="+display.endsWith("Test.java")
            );
        }

        if(javaFiles.size()<100||
           anchored!=REQUIRED.length||
           combatResolverRefs<1||observerGroupRefs<1||
           playerSaveRefs<1||extensionRefs<1)
            throw new AssertionError(
                "R85 route inventory incomplete"
            );

        System.out.println(
            "R85_REAL_SOURCE_ROUTE_INVENTORY_PASS "+
            "scannedJavaFiles="+javaFiles.size()+
            " exactSourceAnchors="+anchored+
            " combatResolverReferenceFiles="+combatResolverRefs+
            " observerGroupReferenceFiles="+observerGroupRefs+
            " playerSaveReferenceFiles="+playerSaveRefs+
            " extensionReferenceFiles="+extensionRefs+
            " testNamedCandidates="+testFileRefs+
            " lexicalRefsNotCallGraph=true "+
            " noClaimsOfLiveConsumerWiring=true "+
            " noAuthOrDurableEventIdInferred=true "+
            " readOnlyNoRuntimeEffects=true"
        );
    }

    private R85OfflineCombatPersistenceRouteInventoryTest(){}
}
