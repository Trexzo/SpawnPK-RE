package spk.local;

import java.io.IOException;
import java.util.Locale;

/**
 * Explicit LOCAL DEV fixtures for client-exact application presentation protocols.
 * Values are synthetic test data and never claimed as SpawnPK production content.
 */
final class ApplicationUiFixtureService {
    /**
     * Exact-current client/cache research identifies these as the native Make-X
     * root family. The production rule selecting a particular layout root is
     * still server authority; this LOCAL_DEV fixture deliberately uses the first
     * proven native root instead of inventing a production mapping.
     */
    static final int MAKE_X_FIXTURE_ROOT=55290;
    static final int[] MAKE_X_NATIVE_ROOTS={55290,55291,55292,55293,55333};

    static boolean isKnownMakeXRoot(int root){
        for(int v:MAKE_X_NATIVE_ROOTS)if(v==root)return true;
        return false;
    }

    static String run(String name,ServerPacketWriter w)throws IOException{
        String n=name==null?"help":name.toLowerCase(Locale.ROOT);
        switch(n){
            case "mail":case "mailbox": mailbox(w);return "LOCAL_DEV_FIXTURE mailbox subtype31 root32000 rows=3 attachment=4151x1";
            case "items":case "itemlist": itemList(w);return "LOCAL_DEV_FIXTURE itemlist subtype14 root36000 rows=3 search/actions/tabs";
            case "makex": makeX(w);return "LOCAL_DEV_FIXTURE makex subtype35 root="+MAKE_X_FIXTURE_ROOT+" nativeRoot=true layoutAuthority=CUSTOM_LOCALLAB quantities=1/5/10/X/All";
            case "tasks":case "eventtask": eventTask(w);return "LOCAL_DEV_FIXTURE eventtask subtype10 root57220 synthetic progress";
            case "events": activeEvents(w);return "LOCAL_DEV_FIXTURE active-events subtype13 synthetic timers";
            case "shop": shopTabs(w);return "LOCAL_DEV_FIXTURE shop-tabs subtype17 state-only labels=Main/Void/Utility";
            case "raid": raid(w);return "LOCAL_DEV_FIXTURE raid subtype41 state-only party/progress/timer";
            case "confirm": confirmation(w);return "LOCAL_DEV_FIXTURE confirmation subtype28 client state published";
            case "infobox": infobox(w);return "LOCAL_DEV_FIXTURE infobox subtype34 timer/numeric/static";
            case "boss": boss(w);return "LOCAL_DEV_FIXTURE bossbar subtype7 synthetic state";
            case "metrics": combatMetrics(w);return "LOCAL_DEV_FIXTURE combat-metrics subtype8 synthetic left/right values";
            case "selection": serverSelection(w);return "LOCAL_DEV_FIXTURE server-selection subtype16 root40405 rows=3";
            case "toast": ApplicationUiService.toast(w,"LocalLab fixture","S2C250 subtype 23","presentation only");return "LOCAL_DEV_FIXTURE toast subtype23";
            case "attention": ApplicationUiService.attentionReset(w);ApplicationUiService.attentionCategory(w,2);ApplicationUiService.attentionTarget(w,0,1,510,330,true);return "LOCAL_DEV_FIXTURE attention subtype24 category=INVENTORY rawDirection=1";
            case "chapter": chapter(w);return "LOCAL_DEV_FIXTURE chapter subtype22 synthetic card/claim-state";
            case "effects": ApplicationUiService.timedEffect(w,"event_elixir",45);ApplicationUiService.dynamicTimedEffect(w,"locallab",1,45,"LocalLab dynamic effect[br]fixture only");return "LOCAL_DEV_FIXTURE timed-effects subtype19";
            case "progress": ApplicationUiService.progress457Reset(w);ApplicationUiService.progress457Value(w,0,120);ApplicationUiService.progress457Value(w,1,320);ApplicationUiService.progress457Style(w,0,1);ApplicationUiService.progress457Style(w,1,2);return "LOCAL_DEV_FIXTURE progress457 subtype36";
            default:return "usage: ::appfixture mailbox|itemlist|makex|eventtask|events|shop|raid|confirm|infobox|boss|metrics|selection|toast|attention|chapter|effects|progress ; all data LOCAL_DEV_FIXTURE";
        }
    }
    private static void mailbox(ServerPacketWriter w)throws IOException{
        w.fixed(97,BootstrapPackets.interface97(32000));
        ApplicationUiService.mailboxClear(w);
        ApplicationUiService.mailboxAppend(w,0,"LocalLab unread fixture");
        ApplicationUiService.mailboxAppend(w,1,"LocalLab read fixture");
        ApplicationUiService.mailboxAppend(w,0,"Attachment protocol fixture");
        ApplicationUiService.mailboxSelect(w,2);ApplicationUiService.mailboxDetailMode(w,1);ApplicationUiService.mailboxClaimState(w,1);ApplicationUiService.mailboxFinalize(w);
        ApplicationBus126Publisher.send(w,32168,"LocalLab attachment fixture");
        ApplicationBus126Publisher.send(w,32169,"Synthetic local message - no production mail authority");
        w.varShort(53,BootstrapPackets.itemContainer53(32175,new int[]{4151},new int[]{1}));
    }
    private static void itemList(ServerPacketWriter w)throws IOException{
        w.fixed(97,BootstrapPackets.interface97(36000));ApplicationUiService.itemListResetLayout(w);ApplicationUiService.itemListLayout(w,0);ApplicationUiService.itemListSearchHidden(w,false);ApplicationUiService.itemListActionsEnabled(w,true);
        ApplicationUiService.itemListAppend(w,4151,1,null);ApplicationUiService.itemListAppend(w,20570,1,null);ApplicationUiService.itemListAppend(w,29999,1,"LocalLab custom content");
        ApplicationUiService.itemListDescription(w,"LocalLab client-exact item-list fixture");ApplicationUiService.itemListSearchDescription(w,"Search is client-side over these synthetic rows");ApplicationUiService.itemListMainActions(w,"Inspect","Select","Preview");ApplicationUiService.itemListTab(w,"Fixture");ApplicationUiService.itemListTab(w,"Custom");ApplicationUiService.itemListSelectTab(w,0);ApplicationUiService.itemListFinalize(w);
    }
    private static void makeX(ServerPacketWriter w)throws IOException{
        // 55300 was never a recovered native Make-X root and could disconnect the
        // exact client when the fixture attempted to open it. Use a proven native
        // root, but keep the layout selection explicitly LOCAL/CUSTOM until the
        // production root-selection rule is recovered.
        w.fixed(97,BootstrapPackets.interface97(MAKE_X_FIXTURE_ROOT));
        ApplicationUiService.makeXReset(w);
        ApplicationUiService.makeXLayout(w,"LocalLab Make-X fixture","No production recipe/cost authority");
        // Preserve the sealed R8.5 fixture payload sequence byte-for-byte apart
        // from the corrected root. These subtype-35 operations are exact-current
        // client grammar and are not implicated in the root disconnect.
        ApplicationUiService.makeXPreview(w,0,4151);
        ApplicationUiService.makeXPreview(w,1,20570);
        ApplicationUiService.makeXRowAction(w,0,"Local fixture row");
        ApplicationUiService.makeXRowResource(w,0,"npc_1");
        ApplicationUiService.makeXPreset(w,2);
    }
    private static void eventTask(ServerPacketWriter w)throws IOException{
        w.fixed(97,BootstrapPackets.interface97(57220));ApplicationUiService.eventTaskReset(w);ApplicationUiService.eventTaskAppend(w,"{LABEL}LocalLab fixture task{line}Client parser test");ApplicationUiService.eventTaskPercent(w,42);ApplicationUiService.eventTaskHidden(w,false);ApplicationUiService.eventTaskAction(w,false,"Inspect local fixture");ApplicationUiService.eventTaskFinalize(w);
    }
    private static void activeEvents(ServerPacketWriter w)throws IOException{long d=60_000L;ApplicationUiService.eventHotspot(w,d,"LocalLab hotspot");ApplicationUiService.eventCard(w,d,4151,1,"Fixture card");ApplicationUiService.eventGlobalBoss(w,"Local Boss Fixture",d);ApplicationUiService.eventWildyBoss(w,"Local Wildy Fixture",d);ApplicationUiService.eventBrawl(w,d);}
    private static void shopTabs(ServerPacketWriter w)throws IOException{ApplicationUiService.shopTabsReset(w);ApplicationUiService.shopTabsDefine(w,0,"Main stock","Void","Utility");ApplicationUiService.shopTabsSelect(w,1);}
    private static void raid(ServerPacketWriter w)throws IOException{ApplicationUiService.raidMembersReset(w);ApplicationUiService.raidMember(w,0,"LocalLeader","ready");ApplicationUiService.raidMember(w,1,"LocalInvite","invited");ApplicationUiService.raidAction(w,0);ApplicationUiService.raidProgress(w,3);ApplicationUiService.raidPoints(w,1234);ApplicationUiService.raidTime(w,83);ApplicationUiService.raidTimerCountdown(w,90);ApplicationUiService.raidDifficultyNoun(w,"fixtures");}
    private static void confirmation(ServerPacketWriter w)throws IOException{ApplicationUiService.confirmationReset(w);ApplicationUiService.confirmationLayout(w,1);ApplicationUiService.confirmationPrimary(w,4151,0,false);ApplicationUiService.confirmationSecondary(w,20570,0,true);}
    private static void infobox(ServerPacketWriter w)throws IOException{ApplicationUiService.infoboxTimer(w,"Local timer",30,null,"Synthetic timer");ApplicationUiService.infoboxNumeric(w,"Local count",42,null,"Synthetic number");ApplicationUiService.infoboxStatic(w,"Local static",null,"Presentation-only fixture");}
    private static void boss(ServerPacketWriter w)throws IOException{ApplicationUiService.bossBarReset(w,true);ApplicationUiService.bossBarPair(w,123,456);ApplicationUiService.bossBarProgressText(w,73,100,"LocalLab Boss Fixture");ApplicationUiService.bossBarLabel(w,"LOCAL DEV ONLY");ApplicationUiService.bossBarValue(w,9001);}
    private static void combatMetrics(ServerPacketWriter w)throws IOException{ApplicationUiService.combatMetricNames(w,"Local A","Local B");ApplicationUiService.combatMetricVisibility(w,2,true);ApplicationUiService.combatMetricVisibility(w,3,true);ApplicationUiService.combatMetricVisibility(w,4,true);for(int side=1;side<=2;side++){ApplicationUiService.combatMetricPrimary(w,side,10*side,20);ApplicationUiService.combatMetricCorrectF3(w,side,7*side,10);ApplicationUiService.combatMetricMagic(w,side,5*side,10);ApplicationUiService.combatMetricDamage(w,side,100*side);ApplicationUiService.combatMetricPercentBadge(w,side,50+side,true);}ApplicationUiService.combatMetricRefresh(w);}
    private static void serverSelection(ServerPacketWriter w)throws IOException{w.fixed(97,BootstrapPackets.interface97(40405));ApplicationUiService.serverSelectionReset(w);ApplicationUiService.serverSelectionAppend(w,0,true,"Local Alpha");ApplicationUiService.serverSelectionAppend(w,0,true,"Local Beta");ApplicationUiService.serverSelectionAppend(w,1,false,"Offline fixture row");}
    private static void chapter(ServerPacketWriter w)throws IOException{ApplicationUiService.chapterReset(w);ApplicationUiService.chapterCard(w,0,4151,"LocalLab chapter fixture","Synthetic presentation",new int[]{4151,995},new int[]{1,1000},10,20,true);ApplicationUiService.chapterClaimState(w,1);ApplicationUiService.chapterScalars(w,5,10);}
    private ApplicationUiFixtureService(){}
}
