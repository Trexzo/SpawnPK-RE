package spk.local;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Exact-v308 Quick Prayer / Quick Curse selection presentation + widget context.
 *
 * The caller explicitly chooses which already-semantic book is being presented.
 * This adapter does not own the production policy that decides which root to open,
 * prayer activation, drain, persistence, or selected-config value semantics.
 */
final class QuickPrayerSelectionPresentation {
    static final int NORMAL_ROOT=20000;
    static final int CURSES_ROOT=22000;
    static final int QUICK_OFF_WIDGET=4999;
    static final int QUICK_ON_WIDGET=5000;
    static final int SELECT_WIDGET=5001;
    static final int FIRST_SELECTOR=17202;
    static final int LAST_CURSE_SELECTOR=17221;
    static final int LAST_NORMAL_SELECTOR=17230;
    static final int CONFIRM_WIDGET=17241;
    static final String PRESENTATION_AUTHORITY="EXACT_CURRENT_CLIENT";

    private final QuickPrayerSelectionService selections;
    private QuickPrayerSelectionService.Book activeBook;

    QuickPrayerSelectionPresentation(
        QuickPrayerSelectionService selections
    ){
        this.selections=Objects.requireNonNull(selections,"selections");
    }

    void open(
        QuickPrayerSelectionService.Book book,
        ServerPacketWriter packets
    )throws IOException{
        Objects.requireNonNull(book,"book");
        Objects.requireNonNull(packets,"packets");

        QuickPrayerSelectionService.Snapshot before=selections.snapshot();
        if(before.currentBook!=book)
            throw new IllegalStateException(
                "quick selection book/context mismatch current="+
                before.currentBook+" requested="+book
            );
        if(activeBook!=null)
            throw new IllegalStateException(
                "quick selection presentation already open book="+activeBook
            );

        selections.beginEdit();
        activeBook=book;
        packets.fixed(
            97,
            BootstrapPackets.interface97(
                book==QuickPrayerSelectionService.Book.NORMAL
                    ?NORMAL_ROOT
                    :CURSES_ROOT
            )
        );
    }

    String handleWidget(int widget){
        QuickPrayerSelectionService.Book book=activeBook;
        if(book==null)return null;

        if(widget==CONFIRM_WIDGET){
            selections.confirmEdit();
            activeBook=null;
            return "QUICK_SELECTION_CONFIRM book="+book;
        }

        List<QuickPrayerSelectionService.Option> options=
            QuickPrayerSelectionService.available(book);
        int index=widget-FIRST_SELECTOR;
        if(index<0||index>=options.size())
            return null;

        QuickPrayerSelectionService.Option option=options.get(index);
        selections.toggleDraft(option.prayerKey);
        return "QUICK_SELECTION_TOGGLE book="+book+
            " prayerKey="+option.prayerKey+
            " widget="+widget;
    }

    boolean close(){
        if(activeBook==null)return false;
        selections.cancelEdit();
        activeBook=null;
        return true;
    }

    boolean open(){return activeBook!=null;}

    QuickPrayerSelectionService.Book activeBook(){return activeBook;}

    static boolean isSelectionWidget(int widget){
        return widget==CONFIRM_WIDGET||
            (widget>=FIRST_SELECTOR&&
             widget<=LAST_NORMAL_SELECTOR);
    }

    static boolean attachedToBook(
        QuickPrayerSelectionService.Book book,
        int widget
    ){
        Objects.requireNonNull(book,"book");

        if(widget==CONFIRM_WIDGET)
            return true;

        int last=
            book==QuickPrayerSelectionService.Book.NORMAL
                ?LAST_NORMAL_SELECTOR
                :LAST_CURSE_SELECTOR;

        return widget>=FIRST_SELECTOR&&
            widget<=last;
    }

    static void publishActive(
        ServerPacketWriter packets,
        boolean enabled
    )throws IOException{
        ApplicationControl126Service.publish(
            packets,
            ApplicationControl126Command.token(
                enabled
                    ?ApplicationControl126Command.Token.QUICK_PRAYERS_ON
                    :ApplicationControl126Command.Token.QUICK_PRAYERS_OFF
            )
        );
    }

    static void publishDisabled(
        ServerPacketWriter packets
    )throws IOException{
        ApplicationControl126Service.publish(
            packets,
            ApplicationControl126Command.token(
                ApplicationControl126Command.Token.DISABLE_QUICK_PRAYERS
            )
        );
    }
}
