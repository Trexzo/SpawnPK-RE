package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class EffectiveNpcCatalogAdapterTest {
    public static void main(String[] args) {
        EffectiveNpcCatalogAdapter.Result result =
            EffectiveNpcCatalogAdapter.build();

        require(
            result.sourceDefinitionCount() ==
                EffectiveNpcDefinitionRepository.count(),
            "source definition count mismatch"
        );

        require(
            result.cataloguedCount() +
                result.skippedUnnamedCount() ==
                result.sourceDefinitionCount(),
            "source accounting mismatch"
        );

        require(
            result.authority() ==
                CatalogEvidenceAuthority
                    .EXACT_CURRENT_CLIENT_OR_CACHE,
            "adapter authority"
        );

        NpcCatalogQueryService query =
            result.query();

        NpcCatalogQueryService.NpcSummary hans =
            requireNpc(
                query,
                0,
                "Hans"
            );

        require(
            hans.actions().equals(
                Arrays.asList(
                    "Talk-to"
                )
            ),
            "Hans exact actions " +
            hans.actions()
        );

        require(
            !hans.combatLevel().isPresent() &&
            !hans.size().isPresent(),
            "adapter claimed combat level/size"
        );

        NpcCatalogQueryService.NpcSummary man =
            requireNpc(
                query,
                1,
                "Man"
            );

        require(
            man.actions().equals(
                Arrays.asList(
                    "Talk-to",
                    "Attack",
                    "Pickpocket"
                )
            ),
            "Man exact actions " +
            man.actions()
        );

        NpcCatalogQueryService.NpcSummary pet =
            requireNpc(
                query,
                8456,
                "Forest stallion pet"
            );

        require(
            pet.actions().equals(
                Arrays.asList(
                    "Pick-up"
                )
            ),
            "pet exact actions " +
            pet.actions()
        );

        require(
            pet.authority() ==
                CatalogEvidenceAuthority
                    .EXACT_CURRENT_CLIENT_OR_CACHE,
            "pet authority"
        );

        Optional<NpcCatalogQueryService.NpcSummary>
            unnamed =
                query.findById(
                    8469
                );

        require(
            !unnamed.isPresent(),
            "unnamed NPC received fabricated semantic name"
        );

        require(
            result.skippedUnnamedCount() > 0,
            "unnamed corpus rows not accounted"
        );

        QueryPage<NpcCatalogQueryService.NpcSummary>
            stallionSearch =
                query.searchByName(
                    "FOREST STALLION PET",
                    0,
                    10
                );

        require(
            stallionSearch.total() >= 1 &&
            stallionSearch.entries()
                .get(0)
                .npcId() == 8456,
            "case-insensitive exact-backed name query"
        );

        expectUnsupported(
            () -> pet.actions().add(
                "Invented"
            ),
            "semantic NPC actions mutable"
        );

        List<String> exactPetActions =
            EffectiveNpcCatalogAdapter
                .exactActions(
                    8456
                );

        require(
            exactPetActions.equals(
                Arrays.asList(
                    "Pick-up"
                )
            ),
            "exact action helper"
        );

        expectUnsupported(
            () -> exactPetActions.add(
                "Invented"
            ),
            "exact action helper mutable"
        );

        require(
            EffectiveNpcCatalogAdapter
                .exactActions(
                    16383
                )
                .isEmpty(),
            "unknown NPC action helper"
        );

        assertRawDefinitionHidden();
        assertProtocolIndependent();

        System.out.println(
            "EXACT_NPC_CATALOG_ADAPTER_PASS " +
            "sourceCountMatched=true " +
            "exactAuthority=true " +
            "hans=true " +
            "manActions=true " +
            "petPickup=true " +
            "unnamedSkipped=true " +
            "combatLevelUnclaimed=true " +
            "sizeUnclaimed=true " +
            "rawDefinitionHidden=true " +
            "protocolIndependent=true " +
            "mutation=false"
        );
    }

    private static NpcCatalogQueryService.NpcSummary
        requireNpc(
            NpcCatalogQueryService query,
            int npcId,
            String expectedName
        ) {

        Optional<NpcCatalogQueryService.NpcSummary>
            found =
                query.findById(
                    npcId
                );

        if (!found.isPresent()) {
            throw new AssertionError(
                "missing exact NPC " +
                npcId
            );
        }

        NpcCatalogQueryService.NpcSummary summary =
            found.get();

        require(
            expectedName.equals(
                summary.name()
            ),
            "NPC name mismatch id=" +
            npcId +
            " actual=" +
            summary.name()
        );

        require(
            summary.authority() ==
                CatalogEvidenceAuthority
                    .EXACT_CURRENT_CLIENT_OR_CACHE,
            "NPC authority id=" +
            npcId
        );

        return summary;
    }

    private static void assertRawDefinitionHidden() {
        String rawType =
            EffectiveNpcDefinitionRepository.Def.class
                .getName();

        for (Field field :
                EffectiveNpcCatalogAdapter.Result.class
                    .getDeclaredFields()) {

            require(
                !field.getType()
                    .getName()
                    .equals(
                        rawType
                    ),
                "raw definition leaked through result field " +
                field.getName()
            );
        }

        for (Method method :
                EffectiveNpcCatalogAdapter.class
                    .getDeclaredMethods()) {

            require(
                !method.getReturnType()
                    .getName()
                    .equals(
                        rawType
                    ),
                "raw definition leaked through adapter method " +
                method.getName()
            );
        }
    }

    private static void assertProtocolIndependent() {
        for (Class<?> type :
                new Class<?>[]{
                    EffectiveNpcCatalogAdapter.class,
                    EffectiveNpcCatalogAdapter
                        .Result.class
                }) {

            for (Field field :
                    type.getDeclaredFields()) {

                String haystack =
                    (
                        field.getName() +
                        " " +
                        field.getType().getName()
                    ).toLowerCase(
                        Locale.ROOT
                    );

                for (String forbidden :
                        new String[]{
                            "packet",
                            "opcode",
                            "widget",
                            "sceneindex",
                            "menuaction",
                            "wireschema",
                            "cacheoffset",
                            "msgpack"
                        }) {

                    require(
                        !haystack.contains(
                            forbidden
                        ),
                        "protocol/raw identity leaked through " +
                        type.getSimpleName() +
                        "." +
                        field.getName()
                    );
                }
            }
        }
    }

    private static void expectUnsupported(
        Runnable action,
        String label
    ) {
        try {
            action.run();
            throw new AssertionError(
                "Expected immutable view: " +
                label
            );
        } catch (
            UnsupportedOperationException expected
        ) {
            // expected
        }
    }

    private static void require(
        boolean condition,
        String label
    ) {
        if (!condition) {
            throw new AssertionError(
                label
            );
        }
    }

    private EffectiveNpcCatalogAdapterTest() {}
}
