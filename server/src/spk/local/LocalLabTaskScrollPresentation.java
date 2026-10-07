package spk.local;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * CUSTOM_LOCALLAB presentation adapter for the one G15 Task Scroll definition.
 *
 * Exact client widgets/containers are owned by TaskScrollPresentation. This
 * adapter supplies only LocalLab-authored title/progress text and deliberately
 * keeps the potential-reward container empty until reward authority exists.
 */
final class LocalLabTaskScrollPresentation {
    static final String TITLE=
        "LocalLab PvM Task Scroll";
    static final String AUTHORITY=
        "CUSTOM_LOCALLAB_G153_TASK_SCROLL_PRESENTATION";

    static void open(
        ServerPacketWriter packets,
        TaskScrollService.Snapshot task
    )throws IOException{
        ServerPacketWriter writer=
            Objects.requireNonNull(
                packets,
                "packets"
            );
        TaskScrollService.Snapshot checked=
            Objects.requireNonNull(
                task,
                "task"
            );

        if(!LocalLabTaskScrollRuntime
                .TASK_KEY
                .equals(
                    checked.definition.taskKey
                ))
            throw new IllegalArgumentException(
                "Unowned LocalLab Task Scroll "+
                checked.definition.taskKey
            );

        TaskScrollPresentation.open(
            writer
        );

        int[] rewardItemIds=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];
        int[] rewardQuantities=
            new int[
                TaskScrollPresentation.REWARD_SLOTS
            ];

        Arrays.fill(
            rewardItemIds,
            -1
        );

        TaskScrollPresentation.publishTask(
            writer,
            checked,
            TITLE,
            progressText(checked),
            rewardItemIds,
            rewardQuantities
        );
    }

    static String progressText(
        TaskScrollService.Snapshot task
    ){
        TaskScrollService.Snapshot checked=
            Objects.requireNonNull(
                task,
                "task"
            );

        String ratio=
            checked.objective.progress+
            "/"+
            checked.objective.goal;

        return checked.objective.complete
            ?"Complete ("+ratio+")"
            :ratio;
    }

    private LocalLabTaskScrollPresentation(){}
}
