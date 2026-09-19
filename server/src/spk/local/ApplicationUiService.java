package spk.local;

import java.io.IOException;

/**
 * Typed LocalLab publishers for exact-current S2C250 client presentation/state contracts.
 * These methods publish client UI state only. They do not supply production rewards,
 * prices, ownership, RNG, matchmaking, eligibility, recipes, or other server rules.
 */
final class ApplicationUiService {
    private static ApplicationPacket250Writer.Payload op(int operation){return ApplicationPacket250Writer.payload().u8(operation);}
    private static void send(ServerPacketWriter w,int subtype,ApplicationPacket250Writer.Payload p)throws IOException{ApplicationPacket250Writer.send(w,subtype,p);}

    // subtype 17 - native shop tabs
    static void shopTabsReset(ServerPacketWriter w)throws IOException{send(w,17,op(0));}
    static void shopTabsDefine(ServerPacketWriter w,int group,String...labels)throws IOException{
        if(labels==null)labels=new String[0]; if(labels.length>255)throw new IllegalArgumentException("labels");
        ApplicationPacket250Writer.Payload p=op(1).u8(group).u8(labels.length);for(String s:labels)p.stringNl(s);send(w,17,p);
    }
    static void shopTabsSelect(ServerPacketWriter w,int tab)throws IOException{send(w,17,op(2).u8(tab));}

    // subtype 31 - mailbox presentation
    static void mailboxClear(ServerPacketWriter w)throws IOException{send(w,31,op(0));}
    static void mailboxAppend(ServerPacketWriter w,int readState,String subject)throws IOException{send(w,31,op(1).u8(readState).stringNl(subject));}
    static void mailboxReadState(ServerPacketWriter w,int row,int readState)throws IOException{send(w,31,op(2).u8(row).u8(readState));}
    static void mailboxDetailMode(ServerPacketWriter w,int mode)throws IOException{send(w,31,op(3).u8(mode));}
    static void mailboxClaimState(ServerPacketWriter w,int state)throws IOException{send(w,31,op(4).u8(state));}
    static void mailboxFinalize(ServerPacketWriter w)throws IOException{send(w,31,op(5));}
    static void mailboxAttention(ServerPacketWriter w)throws IOException{send(w,31,op(6));}
    static void mailboxSelect(ServerPacketWriter w,int row)throws IOException{send(w,31,op(7).u8(row));}

    // subtype 30 - own Trading Post listings presentation
    static void tradepostListing(ServerPacketWriter w,int itemId,int completedQty,int amount,int price,int currency)throws IOException{
        send(w,30,op(1).i32(itemId).i32(completedQty).i32(amount).i32(price).u8(currency));
    }
    static void tradepostRemove(ServerPacketWriter w,int itemId)throws IOException{send(w,30,op(2).i32(itemId));}

    // subtype 28 - confirmation dialog
    static void confirmationLayout(ServerPacketWriter w,int flag)throws IOException{send(w,28,op(0).u8(flag));}
    static void confirmationReset(ServerPacketWriter w)throws IOException{send(w,28,op(1));}
    static void confirmationPrimary(ServerPacketWriter w,int a,int b,boolean mirror)throws IOException{send(w,28,op(2).i32(a).i32(b).u8(mirror?1:0));}
    static void confirmationSecondary(ServerPacketWriter w,int a,int b,boolean mirror)throws IOException{send(w,28,op(3).i32(a).i32(b).u8(mirror?1:0));}

    // subtype 35 - Make-X
    static void makeXPreview(ServerPacketWriter w,int slot,int itemId)throws IOException{send(w,35,op(0).u8(slot).i32(itemId));}
    static void makeXPreset(ServerPacketWriter w,int preset)throws IOException{send(w,35,op(1).u8(preset));}
    static void makeXReset(ServerPacketWriter w)throws IOException{send(w,35,op(2).u8(0));}
    static void makeXLayout(ServerPacketWriter w,String title,String subtitle)throws IOException{
        ApplicationPacket250Writer.Payload p=op(2).u8(1).stringNl(title).u8(subtitle==null?0:1);if(subtitle!=null)p.stringNl(subtitle);send(w,35,p);
    }
    static void makeXRowAction(ServerPacketWriter w,int row,String action)throws IOException{send(w,35,op(3).u8(row).stringNl(action));}
    static void makeXRowResource(ServerPacketWriter w,int row,String resourceOrNpc)throws IOException{send(w,35,op(4).u8(row).stringNl(resourceOrNpc));}

    // subtype 37 - dynamic widget actions
    static void widgetActionsClear(ServerPacketWriter w,int widget)throws IOException{send(w,37,op(0).i32(widget));}
    static void widgetActionSet(ServerPacketWriter w,int widget,int index,String text)throws IOException{send(w,37,op(1).i32(widget).u16(index).stringNl(text));}
    static void widgetActionsFill(ServerPacketWriter w,int widget,String text)throws IOException{send(w,37,op(2).i32(widget).stringNl(text));}
    static void widgetActionsAllocate(ServerPacketWriter w,int widget,boolean enabled)throws IOException{send(w,37,op(3).i32(widget).u8(enabled?1:0));}
    static void widgetScalarPair(ServerPacketWriter w,int widget,int ac,int ap)throws IOException{send(w,37,op(4).i32(widget).u16(ac).u16(ap));}

    // subtype 40 - generic selection dialog
    static void selectionClear(ServerPacketWriter w)throws IOException{send(w,40,op(0));}
    static void selectionReplace(ServerPacketWriter w,int widget,String[] labels,String[] customActions)throws IOException{
        if(labels==null)labels=new String[0];if(labels.length>255)throw new IllegalArgumentException("labels");
        ApplicationPacket250Writer.Payload p=op(1).i32(widget).u8(labels.length);
        for(int i=0;i<labels.length;i++){String action=customActions!=null&&i<customActions.length?customActions[i]:null;p.stringNl(labels[i]).u8(action==null?0:1);if(action!=null)p.stringNl(action);}send(w,40,p);
    }
    static void selectionChoose(ServerPacketWriter w,int widget,int index)throws IOException{send(w,40,op(2).i32(widget).u8(index));}

    // subtype 41 - raid/party UI presentation
    static void raidTab(ServerPacketWriter w,int tab)throws IOException{send(w,41,op(0).u8(tab));}
    static void raidMembersReset(ServerPacketWriter w)throws IOException{send(w,41,op(1));}
    static void raidAction(ServerPacketWriter w,int state)throws IOException{send(w,41,op(2).u8(state));}
    static void raidMember(ServerPacketWriter w,int index,String name,String status)throws IOException{send(w,41,op(3).u8(index).stringNl(name).stringNl(status));}
    static void raidPublicLoading(ServerPacketWriter w)throws IOException{send(w,41,op(4));}
    static void raidPublicEmpty(ServerPacketWriter w)throws IOException{send(w,41,op(5));}
    static void raidProgress(ServerPacketWriter w,int stage)throws IOException{send(w,41,op(6).u8(stage));}
    static void raidVisible(ServerPacketWriter w,boolean visible)throws IOException{send(w,41,op(7).u8(visible?1:0));}
    static void raidOverlayMode(ServerPacketWriter w,int mode)throws IOException{send(w,41,op(15).u8(mode));}
    static void raidOverlayPair(ServerPacketWriter w,int a,int b,boolean flag)throws IOException{send(w,41,op(17).i32(a).i32(b).u8(flag?1:0));}
    static void raidTimerRemove(ServerPacketWriter w)throws IOException{send(w,41,op(18).u8(0));}
    static void raidTimerStartNow(ServerPacketWriter w)throws IOException{send(w,41,op(18).u8(1));}
    static void raidTimerCountdown(ServerPacketWriter w,int seconds)throws IOException{send(w,41,op(18).u8(2).i32(seconds));}
    static void raidPoints(ServerPacketWriter w,int points)throws IOException{send(w,41,op(19).i32(points));}
    static void raidTime(ServerPacketWriter w,int seconds)throws IOException{send(w,41,op(20).i32(seconds));}
    static void raidGlobalState(ServerPacketWriter w,boolean enabled)throws IOException{send(w,41,op(21).u8(enabled?1:0));}
    static void raidDifficultyDefault(ServerPacketWriter w)throws IOException{send(w,41,op(22).u8(0));}
    static void raidDifficultyNoun(ServerPacketWriter w,String noun)throws IOException{send(w,41,op(22).u8(1).stringNl(noun));}

    // subtype 14 - item list/search/transfer presentation
    static void itemListResetLayout(ServerPacketWriter w)throws IOException{send(w,14,op(0));}
    static void itemListLayout(ServerPacketWriter w,int mode)throws IOException{send(w,14,op(1).u8(mode));}
    static void itemListSearchHidden(ServerPacketWriter w,boolean hidden)throws IOException{send(w,14,op(2).u8(hidden?1:0));}
    static void itemListActionsEnabled(ServerPacketWriter w,boolean enabled)throws IOException{send(w,14,op(3).u8(enabled?1:0));}
    static void itemListClear(ServerPacketWriter w)throws IOException{send(w,14,op(4).u8(0));}
    static void itemListAppend(ServerPacketWriter w,int itemId,int amount,String rowText)throws IOException{
        ApplicationPacket250Writer.Payload p=op(4).u8(rowText==null?1:2).i32(itemId).i32(amount);if(rowText!=null)p.stringNl(rowText);send(w,14,p);
    }
    static void itemListSearch(ServerPacketWriter w,String text)throws IOException{send(w,14,op(5).stringNl(text));}
    static void itemListFinalize(ServerPacketWriter w)throws IOException{send(w,14,op(7));}
    static void itemListDescription(ServerPacketWriter w,String text)throws IOException{send(w,14,op(9).stringNl(text));}
    static void itemListSmallDescription(ServerPacketWriter w,String text)throws IOException{send(w,14,op(10).stringNl(text));}
    static void itemListSearchDescription(ServerPacketWriter w,String text)throws IOException{send(w,14,op(11).stringNl(text));}
    static void itemListMainActions(ServerPacketWriter w,String...actions)throws IOException{if(actions==null)actions=new String[0];if(actions.length>5)throw new IllegalArgumentException("max5");ApplicationPacket250Writer.Payload p=op(12).u8(actions.length);for(String a:actions)p.stringNl(a);send(w,14,p);}
    static void itemListTab(ServerPacketWriter w,String label)throws IOException{send(w,14,op(13).stringNl(label));}
    static void itemListSelectTab(ServerPacketWriter w,int tab)throws IOException{send(w,14,op(14).u8(tab));}
    static void itemListDirectSlot(ServerPacketWriter w,int slot,int itemId,int amount)throws IOException{send(w,14,op(22).u16(slot).i32(itemId).i32(amount));}
    static void itemListSecondaryActions(ServerPacketWriter w,String...actions)throws IOException{if(actions==null)actions=new String[0];if(actions.length>5)throw new IllegalArgumentException("max5");ApplicationPacket250Writer.Payload p=op(23).u8(actions.length);for(String a:actions)p.stringNl(a);send(w,14,p);}
    static void itemListMainActionCode(ServerPacketWriter w,int code)throws IOException{send(w,14,op(24).u16(code));}
    static void itemListSecondaryActionCode(ServerPacketWriter w,int code)throws IOException{send(w,14,op(25).u16(code));}

    // subtype 10 - event task UI
    static void eventTaskReset(ServerPacketWriter w)throws IOException{send(w,10,op(0));}
    static void eventTaskAppend(ServerPacketWriter w,String markup)throws IOException{send(w,10,op(1).stringNl(markup));}
    static void eventTaskPercent(ServerPacketWriter w,int percent)throws IOException{send(w,10,op(2).i32(percent));}
    static void eventTaskHidden(ServerPacketWriter w,boolean hidden)throws IOException{send(w,10,op(3).u8(hidden?1:0));}
    static void eventTaskRow(ServerPacketWriter w,String markup,int row)throws IOException{send(w,10,op(5).stringNl(markup).i32(row));}
    static void eventTaskSelect(ServerPacketWriter w,String key)throws IOException{send(w,10,op(6).stringNl(key));}
    static void eventTaskFinalize(ServerPacketWriter w)throws IOException{send(w,10,op(7));}
    static void eventTaskScroll(ServerPacketWriter w,int offset)throws IOException{send(w,10,op(8).u16(offset));}
    static void eventTaskAction(ServerPacketWriter w,boolean append,String text)throws IOException{send(w,10,op(10).u8(append?1:0).stringNl(text));}

    // subtype 13 - active event/hotspot timers
    static void eventHotspot(ServerPacketWriter w,long delayMs,String name)throws IOException{send(w,13,op(1).i64(delayMs).stringNl(name));}
    static void eventCard(ServerPacketWriter w,long delayMs,int itemId,int amount,String label)throws IOException{send(w,13,op(2).i64(delayMs).i32(itemId).i32(amount).stringNl(label));}
    static void eventBloodLms(ServerPacketWriter w,long delayMs)throws IOException{send(w,13,op(3).i64(delayMs));}
    static void eventGoldenHg(ServerPacketWriter w,long delayMs)throws IOException{send(w,13,op(4).i64(delayMs));}
    static void eventGlobalBoss(ServerPacketWriter w,String name,long delayMs)throws IOException{send(w,13,op(5).stringNl(name).i64(delayMs));}
    static void eventWildyBoss(ServerPacketWriter w,String name,long delayMs)throws IOException{send(w,13,op(6).stringNl(name).i64(delayMs));}
    static void eventBrawl(ServerPacketWriter w,long delayMs)throws IOException{send(w,13,op(7).i64(delayMs));}

    // subtype 2 - dynamic scene object override
    static void sceneOverrideRemove(ServerPacketWriter w,int x,int y,int plane,int ignored)throws IOException{send(w,2,op(0).u16(x).u16(y).u8(plane).u8(ignored));}
    static void sceneOverrideAdd(ServerPacketWriter w,int objectId,int x,int y,int plane,int type,int orientation)throws IOException{send(w,2,op(1).i32(objectId).u16(x).u16(y).u8(plane).u8(type).u8(orientation));}
    static void sceneOverrideRemoveObjectId(ServerPacketWriter w,int objectId)throws IOException{send(w,2,op(2).i32(objectId));}

    // subtype 12 - world polygon/highlight
    static void polygonAdd(ServerPacketWriter w,int fr,int fg,int fb,int fa,int or,int og,int ob,int oa,int x,int y,String label)throws IOException{
        ApplicationPacket250Writer.Payload p=op(0).u8(fr).u8(fg).u8(fb).u8(fa).u8(or).u8(og).u8(ob).u8(oa).u16(x).u16(y).u8(label==null?0:1);if(label!=null)p.stringNl(label);send(w,12,p);
    }
    static void polygonRemove(ServerPacketWriter w,int x,int y)throws IOException{send(w,12,op(1).u16(x).u16(y));}


    // subtype 1 - Hunger Games lobby/live presentation command streams (nested stream ends with 0)
    static void hungerGamesLobby(ServerPacketWriter w,int players,String countdown,int needed,String stats)throws IOException{
        ApplicationPacket250Writer.Payload p=op(1).u8(1).u16(players);
        if(countdown!=null)p.u8(2).stringNl(countdown);
        p.u8(3).u8(needed);
        if(stats!=null)p.u8(5).stringNl(stats);
        p.u8(0);send(w,1,p);
    }
    static void hungerGamesStarting(ServerPacketWriter w)throws IOException{send(w,1,op(1).u8(4).u8(0));}
    static void hungerGamesLive(ServerPacketWriter w,int survivors,int kills,int nextEventSec,int nextPowerupSec,int stage,int stageSec,String style,boolean fog)throws IOException{
        ApplicationPacket250Writer.Payload p=op(2).u8(1).u8(survivors).u8(2).u8(kills).u8(3).u16(nextEventSec).u8(4).u16(nextPowerupSec).u8(5).u8(stage).u16(stageSec);
        if(style!=null)p.u8(7).stringNl(style);p.u8(8).u8(fog?1:0).u8(0);send(w,1,p);
    }
    static void hungerGamesDisableLive(ServerPacketWriter w)throws IOException{send(w,1,op(2).u8(6).u8(0));}

    // subtype 3 - coffer/mail top-level overlay controls (no nested operation byte)
    static void cofferOverlay(ServerPacketWriter w,int selector,boolean visible)throws IOException{send(w,3,ApplicationPacket250Writer.payload().u16(selector).u8(visible?1:0));}

    // subtype 4 - token-roll/reward presentation
    static void tokenRollAllowedIndices(ServerPacketWriter w,int...zeroBasedIndices)throws IOException{ApplicationPacket250Writer.Payload p=op(1);if(zeroBasedIndices!=null)for(int x:zeroBasedIndices)p.u16(x+1);p.u16(0);send(w,4,p);}
    static void tokenRollProgress(ServerPacketWriter w,boolean preserveBase,int valueA,int valueB,int multiplier)throws IOException{send(w,4,op(2).u8(preserveBase?1:0).u16(valueA).u16(valueB).i32(multiplier));}
    static void tokenRollMode(ServerPacketWriter w,int mode,int spriteState)throws IOException{send(w,4,op(3).u8(mode).u8(spriteState));}
    static void tokenRollToggle(ServerPacketWriter w,boolean enabled)throws IOException{send(w,4,op(4).u8(enabled?1:0));}

    // subtype 5 - direct runtime widget scalar fields
    static void widgetRuntimeV(ServerPacketWriter w,int widget,int value)throws IOException{send(w,5,op(1).i32(widget).u16(value));}
    static void widgetRuntimeAH(ServerPacketWriter w,int widget,int value)throws IOException{send(w,5,op(2).i32(widget).u16(value));}

    // subtype 6 - Event Activity/token-limit rows
    static void eventActivityClear(ServerPacketWriter w)throws IOException{send(w,6,op(0));}
    static void eventActivityAppend(ServerPacketWriter w,String title,String[] extras,int current,int limit,long timestamp)throws IOException{if(extras==null)extras=new String[0];if(extras.length>255)throw new IllegalArgumentException("extras");ApplicationPacket250Writer.Payload p=op(1).stringNl(title).u8(extras.length);for(String x:extras)p.stringNl(x);p.i32(current).i32(limit).i64(timestamp);send(w,6,p);}
    static void eventActivityFinalize(ServerPacketWriter w)throws IOException{send(w,6,op(2));}

    // subtype 7 - boss bar overlay
    static void bossBarReset(ServerPacketWriter w,boolean enabled)throws IOException{send(w,7,op(1).u8(enabled?1:0));}
    static void bossBarPair(ServerPacketWriter w,int a,int b)throws IOException{send(w,7,op(2).i32(a).i32(b));}
    static void bossBarProgress(ServerPacketWriter w,int current,int max)throws IOException{send(w,7,op(4).i32(current).i32(max));}
    static void bossBarLabel(ServerPacketWriter w,String label)throws IOException{send(w,7,op(5).stringNl(label));}
    static void bossBarValue(ServerPacketWriter w,int value)throws IOException{send(w,7,op(6).i32(value));}
    static void bossBarProgressText(ServerPacketWriter w,int current,int max,String text)throws IOException{send(w,7,op(7).i32(current).i32(max).stringNl(text));}

    // subtype 8 - combat metric overlay; names beyond client-rendered labels are intentionally structural
    static void combatMetricVisibility(ServerPacketWriter w,int selector,boolean enabled)throws IOException{send(w,8,op(1).u8(selector).u8(enabled?1:0));}
    static void combatMetricNames(ServerPacketWriter w,String left,String right)throws IOException{send(w,8,op(2).stringNl(left).stringNl(right));}
    static void combatMetricPrimary(ServerPacketWriter w,int side,int a,int b)throws IOException{send(w,8,op(3).u8(side).u16(a).u16(b));}
    static void combatMetricCorrectF3(ServerPacketWriter w,int side,int a,int b)throws IOException{send(w,8,op(4).u8(side).u16(a).u16(b));}
    static void combatMetricMagic(ServerPacketWriter w,int side,int a,int b)throws IOException{send(w,8,op(5).u8(side).u16(a).u16(b));}
    static void combatMetricDamage(ServerPacketWriter w,int side,int value)throws IOException{send(w,8,op(6).u8(side).u16(value));}
    static void combatMetricPercentBadge(ServerPacketWriter w,int side,int value,boolean green)throws IOException{send(w,8,op(7).u8(side).u8(value).u8(green?1:0));}
    static void combatMetricRefresh(ServerPacketWriter w)throws IOException{send(w,8,op(8));}

    // subtype 9 - Items Kept on Death auto-keep ids
    static void autoKeepClear(ServerPacketWriter w)throws IOException{send(w,9,op(0));}
    static void autoKeepAdd(ServerPacketWriter w,int itemId)throws IOException{send(w,9,op(1).u16(itemId));}

    // subtypes 11 / 18 - structurally identical essence/ticket bonus overlays
    private static void bonusOverlay(ServerPacketWriter w,int subtype,int opId,int value)throws IOException{ApplicationPacket250Writer.Payload p=op(opId);if(opId==1)p.u8(value);else p.u16(value);send(w,subtype,p);}
    static void essenceBonusEnabled(ServerPacketWriter w,boolean enabled)throws IOException{bonusOverlay(w,11,1,enabled?1:0);}static void essenceBonusA(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,11,2,v);}static void essenceBonusB(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,11,3,v);}static void essenceBonusC(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,11,4,v);}
    static void ticketBonusEnabled(ServerPacketWriter w,boolean enabled)throws IOException{bonusOverlay(w,18,1,enabled?1:0);}static void ticketBonusA(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,18,2,v);}static void ticketBonusB(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,18,3,v);}static void ticketBonusC(ServerPacketWriter w,int v)throws IOException{bonusOverlay(w,18,4,v);}

    // subtype 15 - gambling session state
    static void gamblingSession(ServerPacketWriter w,boolean active)throws IOException{send(w,15,op(1).u8(active?1:0));}

    // subtype 16 - server selection root40405 rows
    static void serverSelectionReset(ServerPacketWriter w)throws IOException{send(w,16,op(0));}
    static void serverSelectionAppend(ServerPacketWriter w,int rowMode,boolean selectable,String label)throws IOException{send(w,16,op(1).u8(rowMode).u8(selectable?1:0).stringNl(label));}
    static void serverSelectionUpdate(ServerPacketWriter w,int row,String label)throws IOException{send(w,16,op(2).u16(row).stringNl(label));}

    // subtype 19 - timed effects; dynamic and enum-name variants
    static void timedEffect(ServerPacketWriter w,String effectName,int duration)throws IOException{send(w,19,ApplicationPacket250Writer.payload().stringNl(effectName).i32(duration));}
    static void dynamicTimedEffect(ServerPacketWriter w,String key,int valueA,int valueB,String description)throws IOException{send(w,19,ApplicationPacket250Writer.payload().stringNl("dyn_"+key).i32(valueA).i32(valueB).stringNl(description));}

    // subtype 21 - clear actor-attached effect list
    static void actorEffectsClear(ServerPacketWriter w,int actorIndex)throws IOException{send(w,21,op(1).i32(actorIndex));}

    // subtype 22 - chapter rewards presentation
    static void chapterReset(ServerPacketWriter w)throws IOException{send(w,22,op(0));}
    static void chapterSecondaryReset(ServerPacketWriter w)throws IOException{send(w,22,op(2));}
    static void chapterCard(ServerPacketWriter w,int rewardType,int definitionId,String primary,String secondary,int[] pairIds,int[] pairAmounts,int valueA,int valueB,boolean flag)throws IOException{if(pairIds==null)pairIds=new int[0];if(pairAmounts==null)pairAmounts=new int[0];if(pairIds.length!=pairAmounts.length||pairIds.length>255)throw new IllegalArgumentException("pairs");int mode=secondary==null?1:2;ApplicationPacket250Writer.Payload p=op(3).u8(rewardType).i32(definitionId).u8(mode).stringNl(primary);if(secondary!=null)p.stringNl(secondary);p.u8(pairIds.length);for(int i=0;i<pairIds.length;i++)p.i32(pairIds[i]).i32(pairAmounts[i]);p.u16(valueA).u16(valueB).u8(flag?1:0);send(w,22,p);}
    static void chapterAttentionA(ServerPacketWriter w)throws IOException{send(w,22,op(5));}static void chapterAttentionB(ServerPacketWriter w)throws IOException{send(w,22,op(6));}
    static void chapterClaimState(ServerPacketWriter w,int state)throws IOException{send(w,22,op(7).u8(state));}
    static void chapterScalars(ServerPacketWriter w,int a,int b)throws IOException{send(w,22,op(8).u16(a).u16(b));}

    // subtype 23 - three-line toast
    static void toast(ServerPacketWriter w,String a,String b,String c)throws IOException{send(w,23,ApplicationPacket250Writer.payload().stringNl(a).stringNl(b).stringNl(c));}

    // subtype 24 - flashing attention hint; raw direction/category selectors are preserved exactly
    static void attentionReset(ServerPacketWriter w)throws IOException{send(w,24,op(0));}
    static void attentionTarget(ServerPacketWriter w,int target,int direction,int x,int y,boolean flag)throws IOException{send(w,24,op(1).i32(target).u8(direction).u16(x).u16(y).u8(flag?1:0));}
    static void attentionCategory(ServerPacketWriter w,int category)throws IOException{send(w,24,op(3).u8(category));}
    static void attentionScalar(ServerPacketWriter w,int value)throws IOException{send(w,24,op(4).i32(value));}

    // subtypes 25/26/27 - exact structural overlay state
    static void overlayCounterPair(ServerPacketWriter w,int primary,int secondary)throws IOException{ApplicationPacket250Writer.Payload p=ApplicationPacket250Writer.payload().u16(primary);if(primary>0)p.u16(secondary);send(w,25,p);}
    static void overlayPairState(ServerPacketWriter w,int first,int second)throws IOException{send(w,26,ApplicationPacket250Writer.payload().u8(first).u8(second));}
    static void overlayTextClear(ServerPacketWriter w)throws IOException{send(w,27,op(0));}
    static void overlayTextField(ServerPacketWriter w,int group,int field,String text)throws IOException{if(group<1||group>4||field<1||field>3)throw new IllegalArgumentException("group/field");send(w,27,op(group).u8(field).stringNl(text));}

    // subtype 29 - archive2 client resource request
    static void archive2Request(ServerPacketWriter w,int resourceId)throws IOException{send(w,29,ApplicationPacket250Writer.payload().u16(resourceId));}

    // subtype 32 - custom magic presentation
    static void customMagicScalarCT(ServerPacketWriter w,int v)throws IOException{send(w,32,op(1).u16(v));}
    static void customMagicInfernalMode(ServerPacketWriter w,boolean infernal)throws IOException{send(w,32,op(2).u8(infernal?1:0));}
    static void customMagicScalarDX(ServerPacketWriter w,int v)throws IOException{send(w,32,op(3).u16(v));}
    static void customMagicPrompt(ServerPacketWriter w,String prompt)throws IOException{send(w,32,op(4).stringNl(prompt));}
    static void customMagicNoop5(ServerPacketWriter w,int a,int b,int c)throws IOException{send(w,32,op(5).u8(a).u8(b).u8(c));}
    static void customMagicFlag6(ServerPacketWriter w,boolean b)throws IOException{send(w,32,op(6).u8(b?1:0));}
    static void customMagicFive(ServerPacketWriter w,int a,int b,int c,int d,int e)throws IOException{send(w,32,op(7).u16(a).u16(b).u16(c).u16(d).u16(e));}
    static void customMagicFlag8(ServerPacketWriter w,boolean b)throws IOException{send(w,32,op(8).u8(b?1:0));}

    // subtype 33 - screen/status panel controller 30700
    static void statusPanelReset(ServerPacketWriter w)throws IOException{send(w,33,op(0));}
    static void statusPanelModel(ServerPacketWriter w,int a,int b,boolean mirror)throws IOException{send(w,33,op(1).i32(a).i32(b).u8(mirror?1:0));}
    static void statusPanelLines(ServerPacketWriter w,String...lines)throws IOException{if(lines==null||lines.length<1||lines.length>4)throw new IllegalArgumentException("1..4 lines");ApplicationPacket250Writer.Payload p=op(2).u8(lines.length);for(String line:lines)p.stringNl(line);send(w,33,p);}
    static void statusPanelColor(ServerPacketWriter w,int color)throws IOException{send(w,33,op(3).i32(color));}
    static void statusPanelRefresh(ServerPacketWriter w,int ignored)throws IOException{send(w,33,op(4).i32(ignored));}

    // subtype 34 - infobox overlay controller
    static void infoboxRemove(ServerPacketWriter w,int kind,String key)throws IOException{send(w,34,ApplicationPacket250Writer.payload().u8(kind).u8(0).stringNl(key));}
    static void infoboxNumericUpdate(ServerPacketWriter w,String key,int value)throws IOException{send(w,34,ApplicationPacket250Writer.payload().u8(2).u8(2).stringNl(key).i32(value));}
    private static void infoboxCreate(ServerPacketWriter w,int kind,String key,String display,int value,String namedResource,Integer spriteId,String description)throws IOException{ApplicationPacket250Writer.Payload p=ApplicationPacket250Writer.payload().u8(kind).u8(1).stringNl(key).stringNl(display==null?"def":display);if(kind!=3)p.i32(value);if(namedResource!=null)p.u8(1).stringNl(namedResource);else if(spriteId!=null)p.u8(2).i32(spriteId);else p.u8(0);p.u8(description==null?0:1);if(description!=null)p.stringNl(description);send(w,34,p);}
    static void infoboxTimer(ServerPacketWriter w,String key,int seconds,String resource,String description)throws IOException{infoboxCreate(w,1,key,"def",seconds,resource,null,description);}
    static void infoboxNumeric(ServerPacketWriter w,String key,int value,String resource,String description)throws IOException{infoboxCreate(w,2,key,"def",value,resource,null,description);}
    static void infoboxStatic(ServerPacketWriter w,String key,String resource,String description)throws IOException{infoboxCreate(w,3,key,"def",0,resource,null,description);}

    // subtype 36 - two 0..457 progress values plus optional style enum (1 GOLD_SLOW / 2 PURPLE_EXOTIC)
    static void progress457Reset(ServerPacketWriter w)throws IOException{send(w,36,op(0));}
    static void progress457Value(ServerPacketWriter w,int slot,int value)throws IOException{send(w,36,op(1).u8(slot).u16(value));}
    static void progress457Style(ServerPacketWriter w,int slot,int stylePlusOne)throws IOException{send(w,36,op(2).u8(slot).u8(stylePlusOne));}

    // subtype 38 - named chat type 6
    static void namedChat6(ServerPacketWriter w,long senderIdentity,String message)throws IOException{send(w,38,op(1).i64(senderIdentity).stringNl(message));}

    // subtype 39 - int state map
    static void intStateMap(ServerPacketWriter w,int key,int value)throws IOException{send(w,39,ApplicationPacket250Writer.payload().i32(key).u16(value));}

    // subtype 42 - runtime NPC-definition override channels
    static void npcOverrideMembership(ServerPacketWriter w,boolean remove,int npcId)throws IOException{send(w,42,op(0).u8(remove?1:0).i32(npcId));}
    static void npcOverrideP(ServerPacketWriter w,int npcId,int state)throws IOException{send(w,42,op(1).i32(npcId).u8(state));}
    static void npcOverrideQR(ServerPacketWriter w,int npcId,int q,int r)throws IOException{send(w,42,op(2).i32(npcId).i32(q).i32(r));}

    // subtype 43 - exact int -> u8 flag map (client's -1 removal sentinel is unreachable)
    static void intFlagMap(ServerPacketWriter w,int key,int value)throws IOException{send(w,43,ApplicationPacket250Writer.payload().i32(key).u8(value));}

    // Subtype 20 intentionally has NO LocalLab convenience emitter: the exact client operation starts/stops
    // an external TCP receiver. Authority is documented, but LocalLab's loopback-only safety boundary does not invoke it.

    private ApplicationUiService(){}
}
