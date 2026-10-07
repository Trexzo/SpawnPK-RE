package spk.local;

import java.io.IOException;
import java.util.Objects;

/**
 * CUSTOM_LOCALLAB projection of the certified G16 Adventure PvM objective onto
 * the exact-current Adventure book transport.
 *
 * Rewards and chapter-claim state are deliberately omitted.
 */
final class LocalLabAdventureBookPresentation {
    static final int SUBJECT_NPC_HEAD=2;
    static final String PRIMARY_TEXT=
        "Defeat 3 LocalLab Monster Spawner targets.";

    static void open(
        ServerPacketWriter writer,
        ObjectiveProgressService.Snapshot objective
    )throws IOException{
        ServerPacketWriter checkedWriter=
            Objects.requireNonNull(
                writer,
                "writer"
            );
        ObjectiveProgressService.Snapshot checked=
            validate(
                objective
            );

        ApplicationControl126Service.adventureBook(
            checkedWriter
        );
        ApplicationUiService.chapterReset(
            checkedWriter
        );
        ApplicationUiService.chapterSecondaryReset(
            checkedWriter
        );
        ApplicationUiService.chapterCard(
            checkedWriter,
            SUBJECT_NPC_HEAD,
            LocalLabMonsterSpawnerProvisioning
                .NPC_DEFINITION_ID,
            PRIMARY_TEXT,
            null,
            new int[0],
            new int[0],
            (int)checked.progress,
            (int)checked.goal,
            checked.claimed
        );
        ApplicationUiService.chapterScalars(
            checkedWriter,
            checked.complete?1:0,
            1
        );
    }

    private static ObjectiveProgressService.Snapshot
        validate(
            ObjectiveProgressService.Snapshot objective
        )
    {
        ObjectiveProgressService.Snapshot checked=
            Objects.requireNonNull(
                objective,
                "objective"
            );

        if(!LocalLabAdventureRuntime
                .OBJECTIVE_KEY
                .equals(
                    checked.key
                )||
           checked.goal!=
                LocalLabAdventureRuntime.GOAL||
           !LocalLabAdventureRuntime
                .AUTHORITY
                .equals(
                    checked.sourceAuthority
                )||
           checked.progress<0L||
           checked.progress>
                LocalLabAdventureRuntime.GOAL||
           checked.claimed)
            throw new IllegalArgumentException(
                "unsupported LocalLab Adventure objective "+
                checked
            );

        return checked;
    }

    private LocalLabAdventureBookPresentation(){}
}
