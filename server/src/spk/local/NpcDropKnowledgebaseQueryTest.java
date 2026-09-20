package spk.local;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

/** Deterministic regression for Issue #175 read-only semantic query services. */
public final class NpcDropKnowledgebaseQueryTest {
    public static void main(String[] args) {
        NpcCatalogQueryService.NpcSummary npc20 =
            new NpcCatalogQueryService.NpcSummary(
                20,
                "Example Beast",
                Integer.valueOf(126),
                Integer.valueOf(2),
                Arrays.asList("Attack", "Examine"),
                CatalogEvidenceAuthority.CUSTOM_LOCALLAB
            );
        NpcCatalogQueryService.NpcSummary npc10 =
            new NpcCatalogQueryService.NpcSummary(
                10,
                "Example Guard",
                Integer.valueOf(50),
                null,
                Arrays.asList("Talk-to", "Attack"),
                CatalogEvidenceAuthority.CUSTOM_LOCALLAB
            );

        NpcCatalogQueryService npcService =
            new NpcCatalogQueryService(Arrays.asList(npc20, npc10));

        require(npcService.findById(20).isPresent(), "npc exact lookup");
        QueryPage<NpcCatalogQueryService.NpcSummary> npcPage =
            npcService.searchByName("EXAMPLE", 0, 1);
        require(npcPage.total() == 2, "npc search total");
        require(npcPage.entries().get(0).npcId() == 10, "npc deterministic order");
        require(npcPage.hasMore(), "npc pagination");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                npc20.actions().add("Raw-cache-action");
            }
        }, "npc actions immutable");

        QueryPage<Integer> hugeLimitPage = QueryPage.slice(
            Arrays.asList(Integer.valueOf(10), Integer.valueOf(20)),
            1,
            Integer.MAX_VALUE
        );
        require(hugeLimitPage.entries().size() == 1, "overflow-safe huge limit page size");
        require(hugeLimitPage.entries().get(0).intValue() == 20, "overflow-safe huge limit row");
        require(!hugeLimitPage.hasMore(), "overflow-safe huge limit hasMore");

        QueryPage<Integer> hugeOffsetPage = QueryPage.slice(
            Arrays.asList(Integer.valueOf(10), Integer.valueOf(20)),
            Integer.MAX_VALUE,
            Integer.MAX_VALUE
        );
        require(hugeOffsetPage.entries().isEmpty(), "overflow-safe huge offset empty page");
        require(hugeOffsetPage.total() == 2, "overflow-safe huge offset total");
        require(!hugeOffsetPage.hasMore(), "overflow-safe huge offset hasMore");

        DropTableCatalogQueryService.DropTableEntry row =
            new DropTableCatalogQueryService.DropTableEntry(
                20,
                1000,
                Long.valueOf(1L),
                Long.valueOf(3L),
                null,
                CatalogEvidenceAuthority.CUSTOM_LOCALLAB
            );

        DropTableCatalogQueryService dropService =
            new DropTableCatalogQueryService(
                Arrays.asList(Integer.valueOf(20), Integer.valueOf(21)),
                Collections.singletonList(row)
            );

        Optional<QueryPage<DropTableCatalogQueryService.DropTableEntry>> known =
            dropService.queryByNpc(20, 0, 10);
        require(known.isPresent(), "known drop source present");
        require(known.get().total() == 1, "known drop row");
        require(!row.rarityOrWeightEvidence().isPresent(), "unknown rarity remains absent");

        Optional<QueryPage<DropTableCatalogQueryService.DropTableEntry>> knownEmpty =
            dropService.queryByNpc(21, 0, 10);
        require(knownEmpty.isPresent() && knownEmpty.get().total() == 0, "known empty table distinct");

        Optional<QueryPage<DropTableCatalogQueryService.DropTableEntry>> unknown =
            dropService.queryByNpc(9999, 0, 10);
        require(!unknown.isPresent(), "unrecovered drop source fails closed");

        QueryPage<DropTableCatalogQueryService.DropTableEntry> byItem =
            dropService.queryByItem(1000, 0, 10);
        require(byItem.total() == 1 && byItem.entries().get(0).sourceNpcId() == 20, "item drop search");

        KnowledgebaseQueryService.Article articleB =
            new KnowledgebaseQueryService.Article(
                "b",
                "Example Bosses",
                Arrays.asList("Recovered evidence only."),
                CatalogEvidenceAuthority.CUSTOM_LOCALLAB
            );
        KnowledgebaseQueryService.Article articleA =
            new KnowledgebaseQueryService.Article(
                "a",
                "Example Basics",
                Arrays.asList("No invented content."),
                CatalogEvidenceAuthority.CUSTOM_LOCALLAB
            );
        KnowledgebaseQueryService knowledge =
            new KnowledgebaseQueryService(Arrays.asList(articleB, articleA));

        require(knowledge.findById("a").isPresent(), "article exact lookup");
        QueryPage<KnowledgebaseQueryService.Article> articles =
            knowledge.searchByTitle("example", 0, 10);
        require(articles.total() == 2, "article search");
        require("a".equals(articles.entries().get(0).articleId()), "article deterministic order");
        expectUnsupported(new Runnable() {
            @Override public void run() {
                articleA.sections().clear();
            }
        }, "article sections immutable");

        assertProtocolIndependent(
            NpcCatalogQueryService.class,
            DropTableCatalogQueryService.class,
            KnowledgebaseQueryService.class,
            QueryPage.class
        );

        System.out.println(
            "ISSUE175_NPC_DROP_KNOWLEDGEBASE_QUERY_PASS npcLookup=true npcSearch=true pagination=true " +
            "paginationOverflowSafe=true npcOrder=true immutableNpc=true dropByNpc=true dropByItem=true " +
            "unknownDropSourceAbsent=true unknownRarityAbsent=true knowledgeLookup=true knowledgeSearch=true " +
            "immutableArticles=true provenance=true mutation=false inventedDropData=false protocolIndependent=true"
        );
    }

    private static void assertProtocolIndependent(Class<?>... roots) {
        String[] forbidden = {
            "widget", "opcode", "subtype", "packet", "sprite",
            "cacheoffset", "cacheindex", "rawdefinition"
        };
        for (Class<?> root : roots) {
            for (java.lang.reflect.Field field : root.getDeclaredFields()) {
                String haystack = (field.getName() + " " + field.getType().getName()).toLowerCase(java.util.Locale.ROOT);
                for (String token : forbidden) {
                    require(!haystack.contains(token), root.getName() + " leaked raw identity through " + field.getName());
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

    private static void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
}
