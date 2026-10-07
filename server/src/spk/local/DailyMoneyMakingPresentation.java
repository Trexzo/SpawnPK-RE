package spk.local;

/**
 * Exact-current Daily Money Making widget identity.
 *
 * This class is presentation/input identity only. It owns no activity catalog,
 * progress, rewards, teleport destination or reset policy.
 */
final class DailyMoneyMakingPresentation {
    static final int TRACK_WIDGET=55002;
    static final int TELEPORT_WIDGET=55012;
    static final int HARD_WIDGET=55015;
    static final int MEDIUM_WIDGET=55018;
    static final int EASY_WIDGET=55021;

    enum InputKind {
        TRACK,
        TELEPORT,
        SELECT_EASY,
        SELECT_MEDIUM,
        SELECT_HARD
    }

    static final class Input {
        final InputKind kind;
        final int widget;

        Input(
            InputKind kind,
            int widget
        ){
            this.kind=java.util.Objects.requireNonNull(
                kind,
                "kind"
            );
            this.widget=widget;
        }

        boolean difficultySelection(){
            return kind==InputKind.SELECT_EASY||
                kind==InputKind.SELECT_MEDIUM||
                kind==InputKind.SELECT_HARD;
        }
    }

    static Input resolveWidget(
        int widget
    ){
        switch(widget){
            case TRACK_WIDGET:
                return new Input(
                    InputKind.TRACK,
                    widget
                );
            case TELEPORT_WIDGET:
                return new Input(
                    InputKind.TELEPORT,
                    widget
                );
            case EASY_WIDGET:
                return new Input(
                    InputKind.SELECT_EASY,
                    widget
                );
            case MEDIUM_WIDGET:
                return new Input(
                    InputKind.SELECT_MEDIUM,
                    widget
                );
            case HARD_WIDGET:
                return new Input(
                    InputKind.SELECT_HARD,
                    widget
                );
            default:
                return null;
        }
    }

    private DailyMoneyMakingPresentation(){}
}
