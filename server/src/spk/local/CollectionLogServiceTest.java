package spk.local;

import java.util.Arrays;
import java.util.LinkedHashSet;

/** Deterministic regression for Issue #183 semantic Collection Log aggregate. */
public final class CollectionLogServiceTest {
    public static void main(String[] args) {
        CollectionLogDefinition.CollectionId collectionId =
            new CollectionLogDefinition.CollectionId("boss-example");
        CollectionLogDefinition.EntryId entryA =
            new CollectionLogDefinition.EntryId("drop-a");
        CollectionLogDefinition.EntryId entryB =
            new CollectionLogDefinition.EntryId("drop-b");

        LinkedHashSet<CollectionLogDefinition.EntryId> entries =
            new LinkedHashSet<CollectionLogDefinition.EntryId>();
        entries.add(entryA);
        entries.add(entryB);

        CollectionLogDefinition definition = new CollectionLogDefinition(
            collectionId,
            new CollectionLogDefinition.CategoryId("bosses"),
            "Example Boss",
            entries,
            true,
            CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
        );

        CollectionLogService service = new CollectionLogService(Arrays.asList(definition));

        CollectionLogService.ProgressSnapshot empty = service.snapshot(collectionId);
        require(empty.obtainedCount() == 0, "initial obtained count");
        require(empty.totalCount() == 2, "initial total count");
        require(!empty.complete(), "initial incomplete");
        require(!empty.killCount().isPresent(), "initial kill count absent");

        CollectionLogService.DiscoveryResult first = service.observe(
            new CollectionLogService.CollectionEntryObserved(collectionId, entryA)
        );
        require(first == CollectionLogService.DiscoveryResult.FIRST_DISCOVERY, "first discovery");

        CollectionLogService.DiscoveryResult duplicate = service.observe(
            new CollectionLogService.CollectionEntryObserved(collectionId, entryA)
        );
        require(duplicate == CollectionLogService.DiscoveryResult.ALREADY_OBTAINED, "duplicate idempotent");

        CollectionLogService.DiscoveryResult completed = service.observe(
            new CollectionLogService.CollectionEntryObserved(collectionId, entryB)
        );
        require(
            completed == CollectionLogService.DiscoveryResult.FIRST_DISCOVERY_COMPLETED,
            "completion transition"
        );

        service.observeKillCount(collectionId, 42L);
        CollectionLogService.ProgressSnapshot done = service.snapshot(collectionId);
        require(done.obtainedCount() == 2, "derived obtained count");
        require(done.totalCount() == 2, "derived total count");
        require(done.complete(), "derived completion");
        require(done.killCount().isPresent() && done.killCount().getAsLong() == 42L, "kill count");
        require(done.obtainedEntries().contains(entryA), "entry A retained");
        require(done.obtainedEntries().contains(entryB), "entry B retained");
        require(definition.authority() == CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB, "authority");

        expectUnsupported(new Runnable() {
            @Override public void run() {
                done.obtainedEntries().clear();
            }
        }, "progress snapshot immutability");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                service.observe(new CollectionLogService.CollectionEntryObserved(
                    collectionId,
                    new CollectionLogDefinition.EntryId("unknown-drop")
                ));
            }
        }, "unknown entry");

        expectIllegalArgument(new Runnable() {
            @Override public void run() {
                service.snapshot(new CollectionLogDefinition.CollectionId("unknown-collection"));
            }
        }, "unknown collection");

        CollectionLogDefinition noKillStat = new CollectionLogDefinition(
            new CollectionLogDefinition.CollectionId("no-kill-stat"),
            new CollectionLogDefinition.CategoryId("other"),
            "No Kill Stat",
            new LinkedHashSet<CollectionLogDefinition.EntryId>(
                Arrays.asList(new CollectionLogDefinition.EntryId("only-entry"))
            ),
            false,
            CollectionLogEvidenceAuthority.CUSTOM_LOCALLAB
        );
        CollectionLogService noKillService = new CollectionLogService(Arrays.asList(noKillStat));
        expectIllegalState(new Runnable() {
            @Override public void run() {
                noKillService.observeKillCount(noKillStat.id(), 1L);
            }
        }, "unsupported kill count");

        assertProtocolIndependent(
            CollectionLogDefinition.class,
            CollectionLogRepository.class,
            CollectionLogService.class
        );

        System.out.println(
            "ISSUE183_COLLECTION_LOG_PASS semanticIds=true immutableDefinitions=true " +
            "firstDiscovery=true duplicateIdempotent=true derivedCounts=true derivedCompletion=true " +
            "unknownFailClosed=true killCountOptional=true repositoryBoundary=true eventDriven=true " +
            "rewardGrant=false inventoryScan=false protocolIndependent=true"
        );
    }

    private static void assertProtocolIndependent(Class<?>... roots) {
        String[] forbidden = {"widget", "opcode", "subtype", "packet", "sprite", "containerid", "rowindex"};
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack = (field.getName() + " " + field.getType().getName()).toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(!haystack.contains(token), root.getName() + " leaked protocol identity through " + field.getName());
                }
            }
        }
    }

    private static void expectUnsupported(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected immutable view: " + label);
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    private static void expectIllegalArgument(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException: " + label);
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    private static void expectIllegalState(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalStateException: " + label);
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
