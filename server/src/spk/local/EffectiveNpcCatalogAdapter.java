package spk.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only adapter from the existing exact-current interaction definition
 * corpus into the semantic NPC query boundary.
 *
 * This class deliberately consumes only fields already exposed by
 * EffectiveNpcDefinitionRepository. It does not reparse raw cache/config
 * payloads or infer missing server mechanics.
 */
final class EffectiveNpcCatalogAdapter {
    static final int MAX_DEFINITION_ID_EXCLUSIVE = 16384;

    static final class Result {
        private final NpcCatalogQueryService query;
        private final int sourceDefinitionCount;
        private final int cataloguedCount;
        private final int skippedUnnamedCount;
        private final CatalogEvidenceAuthority authority;

        private Result(
            NpcCatalogQueryService query,
            int sourceDefinitionCount,
            int cataloguedCount,
            int skippedUnnamedCount,
            CatalogEvidenceAuthority authority
        ) {
            this.query = query;
            this.sourceDefinitionCount = sourceDefinitionCount;
            this.cataloguedCount = cataloguedCount;
            this.skippedUnnamedCount = skippedUnnamedCount;
            this.authority = authority;
        }

        NpcCatalogQueryService query() {
            return query;
        }

        int sourceDefinitionCount() {
            return sourceDefinitionCount;
        }

        int cataloguedCount() {
            return cataloguedCount;
        }

        int skippedUnnamedCount() {
            return skippedUnnamedCount;
        }

        CatalogEvidenceAuthority authority() {
            return authority;
        }
    }

    static Result build() {
        final int sourceCount =
            EffectiveNpcDefinitionRepository.count();

        ArrayList<NpcCatalogQueryService.NpcSummary> summaries =
            new ArrayList<NpcCatalogQueryService.NpcSummary>();

        int visited = 0;
        int skippedUnnamed = 0;

        for (int npcId = 0;
             npcId < MAX_DEFINITION_ID_EXCLUSIVE;
             npcId++) {

            EffectiveNpcDefinitionRepository.Def definition =
                EffectiveNpcDefinitionRepository.get(npcId);

            if (definition == null) {
                continue;
            }

            visited++;

            String name =
                trimToNull(
                    definition.name
                );

            if (name == null) {
                skippedUnnamed++;
                continue;
            }

            ArrayList<String> actions =
                new ArrayList<String>();

            for (int option = 1;
                 option <= 5;
                 option++) {

                String action =
                    trimToNull(
                        definition.action(option)
                    );

                if (action != null) {
                    actions.add(action);
                }
            }

            summaries.add(
                new NpcCatalogQueryService.NpcSummary(
                    definition.npcId,
                    name,
                    null,
                    null,
                    actions,
                    CatalogEvidenceAuthority
                        .EXACT_CURRENT_CLIENT_OR_CACHE
                )
            );
        }

        if (visited != sourceCount) {
            throw new IllegalStateException(
                "NPC corpus id range mismatch visited=" +
                visited +
                " repositoryCount=" +
                sourceCount
            );
        }

        if (summaries.size() +
                skippedUnnamed != sourceCount) {
            throw new IllegalStateException(
                "NPC catalog accounting mismatch catalogued=" +
                summaries.size() +
                " skippedUnnamed=" +
                skippedUnnamed +
                " source=" +
                sourceCount
            );
        }

        return new Result(
            new NpcCatalogQueryService(
                summaries
            ),
            sourceCount,
            summaries.size(),
            skippedUnnamed,
            CatalogEvidenceAuthority
                .EXACT_CURRENT_CLIENT_OR_CACHE
        );
    }

    static List<String> exactActions(
        int npcId
    ) {
        EffectiveNpcDefinitionRepository.Def definition =
            EffectiveNpcDefinitionRepository.get(
                npcId
            );

        if (definition == null) {
            return Collections.emptyList();
        }

        ArrayList<String> actions =
            new ArrayList<String>();

        for (int option = 1;
             option <= 5;
             option++) {

            String action =
                trimToNull(
                    definition.action(option)
                );

            if (action != null) {
                actions.add(action);
            }
        }

        return Collections.unmodifiableList(
            actions
        );
    }

    private static String trimToNull(
        String value
    ) {
        if (value == null) {
            return null;
        }

        String clean =
            value.trim();

        return clean.isEmpty()
            ? null
            : clean;
    }

    private EffectiveNpcCatalogAdapter() {}
}
