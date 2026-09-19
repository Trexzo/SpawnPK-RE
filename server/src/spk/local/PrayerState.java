package spk.local;

import java.io.*;
import java.util.*;

final class PrayerState {
    private PrayerDefinitionRepository.Book book=PrayerDefinitionRepository.Book.NORMAL;
    private final HashSet<Integer> activeWidgets=new HashSet<>();
    private int manualHeadIcon=-1;

    PrayerDefinitionRepository.Book book(){return book;}
    int root(){return book.root;}
    boolean activeWidget(int widget){return activeWidgets.contains(widget);}
    int activeCount(){return activeWidgets.size();}
    int manualHeadIcon(){return manualHeadIcon;}

    String switchBook(String token,ServerPacketWriter w)throws IOException{
        PrayerDefinitionRepository.Book next;
        if("normal".equalsIgnoreCase(token)||"prayer".equalsIgnoreCase(token)) next=PrayerDefinitionRepository.Book.NORMAL;
        else if("curses".equalsIgnoreCase(token)||"curse".equalsIgnoreCase(token)) next=PrayerDefinitionRepository.Book.CURSES;
        else return "REJECTED_BOOK expected=normal|curses";
        book=next;
        // Normal Prayer and Curses intentionally reuse many config varps. Re-publish
        // the newly visible book from book-specific widget state on every switch.
        for(PrayerDefinitionRepository.Def d:PrayerDefinitionRepository.all())
            if(d.book==book) w.fixed(36,BootstrapPackets.config36(d.varp,activeWidgets.contains(d.widget)?1:0));
        w.fixed(71,BootstrapPackets.sidebar71(book.root,5));
        w.fixed(106,BootstrapPackets.selectTab106(5));
        return "PRAYER_BOOK_SET book="+book+" root="+book.root+" activeCount="+activeWidgets.size()+" policy=ACTIVE_STATE_NOT_CLEARED_SERVER_RULE_UNKNOWN";
    }

    String click(PrayerDefinitionRepository.Def d,PlayerState player,ServerPacketWriter w)throws IOException{
        if(d==null)return "NOT_PRAYER_WIDGET";
        if(d.book!=book){
            w.fixed(36,BootstrapPackets.config36(d.varp,activeWidgets.contains(d.widget)?1:0));
            return "REJECTED_WRONG_BOOK active="+book+" clicked="+d.book;
        }
        if(player.currentLevel(PlayerState.PRAYER)<=0){
            activeWidgets.remove(d.widget);
            w.fixed(36,BootstrapPackets.config36(d.varp,0));
            return "REJECTED_NO_PRAYER_POINTS name="+d.name;
        }
        if(player.currentLevel(PlayerState.PRAYER)<d.level){
            activeWidgets.remove(d.widget);
            w.fixed(36,BootstrapPackets.config36(d.varp,0));
            return "REJECTED_LEVEL name="+d.name+" required="+d.level+" current="+player.currentLevel(PlayerState.PRAYER);
        }
        boolean next=!activeWidgets.contains(d.widget);
        if(next)activeWidgets.add(d.widget); else activeWidgets.remove(d.widget);
        w.fixed(36,BootstrapPackets.config36(d.varp,next?1:0));
        return "PRAYER_TOGGLE name="+d.name+" enabled="+next+" widget="+d.widget+" varp="+d.varp+
            " clientPredictionConfirmed=true mechanics=ACTIVATION_ONLY_NO_DRAIN_OR_EFFECT_GUESS";
    }

    String deactivateAll(ServerPacketWriter w)throws IOException{
        int n=0;
        for(Integer widget:new ArrayList<>(activeWidgets)){
            PrayerDefinitionRepository.Def d=PrayerDefinitionRepository.byWidget(widget);
            if(d!=null && d.book==book){ w.fixed(36,BootstrapPackets.config36(d.varp,0)); n++; }
        }
        activeWidgets.removeIf(widget->{PrayerDefinitionRepository.Def d=PrayerDefinitionRepository.byWidget(widget);return d!=null&&d.book==book;});
        return "PRAYERS_CLEARED count="+n;
    }

    String publishManualHeadIcon(int value,ServerPacketWriter w)throws IOException{
        if(value<-1||value>20)return "REJECTED_HEADICON_RANGE expected=-1..20";
        manualHeadIcon=value;
        w.varShort(126,BootstrapPackets.widgetText126(32,Integer.toString(value)));
        return "LOCAL_PRAYER_HEADICON_FIXTURE value="+value+" semanticMapping=UNASSIGNED_R25";
    }

    String summary(){return "book="+book+" root="+book.root+" activeCount="+activeWidgets.size()+" manualHeadIcon="+manualHeadIcon;}
}
