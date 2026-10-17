package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class BloodcoreLotteryAdapterTest {
    private static final String AUTHORITY=
        "LOCAL_LAB_POLICY_TEST";

    public static void main(String[] args)
        throws Exception{
        exactPresentationReuse();
        enterIntentDoesNotSettle();
        bloodcoreHistoryProjection();
        callerFormattedPublication();
        opaqueValuesStayEvidenceOnly();
        semanticServiceHasNoProtocolIdentity();
        policyStillExternal();

        System.out.println(
            "BLOODCORE_LOTTERY_ADAPTER_PASS "+
            "root61150=true "+
            "enter61196=true "+
            "history35=true "+
            "channelBloodcore=true "+
            "existingPresentationReused=true "+
            "callerFormattedHistory=true "+
            "opaqueItem22844=true "+
            "opaque250=true "+
            "opaque10000=true "+
            "opaqueSemantics=false "+
            "entrySettlementExternal=true "+
            "rngExternal=true "+
            "rawProtocolInService=false"
        );
    }

    private static void exactPresentationReuse(){
        require(
            LotteryPresentation
                .BLOODCORE_ROOT==61150&&
            LotteryPresentation
                .BLOODCORE_ENTRY_WIDGET==61196&&
            LotteryPresentation
                .BLOODCORE_HISTORY_FIRST==61161&&
            LotteryPresentation
                .BLOODCORE_HISTORY_LAST==61195,
            "existing exact Bloodcore presentation"
        );

        require(
            LotteryPresentation
                .resolveEntryWidget(
                    61196
                )==
                    LotteryService.Channel
                        .BLOODCORE,
            "entry widget"
        );

        require(
            LotteryPresentation
                .resolveEntryWidget(
                    61197
                )==null,
            "hover widget became action"
        );

        byte[] root=
            BootstrapPackets.interface97(
                61150
            );

        require(
            root.length==2&&
            (root[0]&255)==0xee&&
            (root[1]&255)==0xde,
            "S2C97 root bytes"
        );
    }

    private static void enterIntentDoesNotSettle(){
        LotteryService service=
            new LotteryService();

        LotteryService.RoundSnapshot opened=
            service.openRound(
                "bloodcore:active",
                LotteryService.Channel.BLOODCORE,
                AUTHORITY
            );

        BloodcoreLotteryAdapter.EnterIntent
            intent=
                BloodcoreLotteryAdapter
                    .resolveEnterIntent(
                        service,
                        61150,
                        61196
                    );

        require(
            intent!=null&&
            intent.channel==
                LotteryService.Channel.BLOODCORE&&
            intent.activeRound!=null&&
            opened.roundKey.equals(
                intent.activeRound.roundKey
            ),
            "Bloodcore enter intent"
        );

        LotteryService.RoundSnapshot after=
            service.activeRound(
                LotteryService.Channel.BLOODCORE
            );

        require(
            after!=null&&
            after.participants.isEmpty()&&
            after.totalEntryUnits==0L,
            "enter intent performed settlement"
        );

        require(
            BloodcoreLotteryAdapter
                .resolveEnterIntent(
                    service,
                    52000,
                    61196
                )==null&&
            BloodcoreLotteryAdapter
                .resolveEnterIntent(
                    service,
                    61150,
                    52010
                )==null&&
            BloodcoreLotteryAdapter
                .resolveEnterIntent(
                    service,
                    61150,
                    61197
                )==null,
            "enter root/widget context"
        );
    }

    private static void bloodcoreHistoryProjection(){
        LotteryService service=
            new LotteryService();

        for(int i=0;i<36;i++)
            finalizeRound(
                service,
                LotteryService.Channel.BLOODCORE,
                "bc:"+i,
                "winner:"+i
            );

        finalizeRound(
            service,
            LotteryService.Channel.ORDINARY,
            "ordinary:0",
            "ordinary:winner"
        );

        List<LotteryService.WinnerRecord>
            serviceHistory=
                service.history(
                    LotteryService.Channel.BLOODCORE
                );

        require(
            serviceHistory.size()==35&&
            "bc:1".equals(
                serviceHistory.get(0)
                    .roundKey
            )&&
            "bc:35".equals(
                serviceHistory.get(34)
                    .roundKey
            ),
            "service Bloodcore history capacity"
        );

        BloodcoreLotteryAdapter.HistoryView
            view=
                BloodcoreLotteryAdapter
                    .projectHistory(
                        service
                    );

        require(
            view.rows.size()==35&&
            view.rows.get(0)
                .widgetId==61161&&
            view.rows.get(34)
                .widgetId==61195,
            "exact history row projection"
        );

        require(
            "bc:1".equals(
                view.rows.get(0)
                    .record.roundKey
            )&&
            "bc:35".equals(
                view.rows.get(34)
                    .record.roundKey
            ),
            "service history order changed"
        );

        for(
            BloodcoreLotteryAdapter.HistoryRow
                row:view.rows
        )
            require(
                row.record.channel==
                    LotteryService.Channel
                        .BLOODCORE,
                "ordinary history leaked into Bloodcore"
            );

        boolean immutable=false;
        try{
            view.rows.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "history projection mutable"
        );
    }

    private static void callerFormattedPublication()
        throws Exception{
        LotteryService service=
            new LotteryService();

        finalizeRound(
            service,
            LotteryService.Channel.BLOODCORE,
            "bc:publish",
            "winner:publish"
        );

        BloodcoreLotteryAdapter.HistoryView
            view=
                BloodcoreLotteryAdapter
                    .projectHistory(
                        service
                    );

        OutboundPacketQueue queue=
            new OutboundPacketQueue();
        ServerPacketWriter packets=
            new ServerPacketWriter(
                queue,
                new IsaacCipher(
                    new int[]{0,0,0,0}
                )
            );

        AtomicInteger formatted=
            new AtomicInteger();

        BloodcoreLotteryAdapter
            .publishStatus(
                packets,
                "caller countdown",
                "caller participants",
                view,
                record->{
                    formatted.incrementAndGet();
                    return "WINNER="+
                        record.winnerRef;
                }
            );

        require(
            formatted.get()==1,
            "history formatter count"
        );

        /*
         * Exact LotteryPresentation publishes countdown + participant text +
         * all 35 history rows, clearing unused rows. Each writer call auto-
         * flushes to one queue entry.
         */
        require(
            queue.queuedPackets()==37&&
            queue.queuedBytes()>0,
            "Bloodcore status packet count"
        );

        byte[] firstHistory=
            BootstrapPackets
                .widgetText126(
                    61161,
                    "WINNER=winner:publish"
                );

        int n=firstHistory.length;
        int target=
            ((firstHistory[n-2]&255)<<8)|
            (((firstHistory[n-1]&255)-128)&255);

        require(
            target==61161,
            "first history S2C126 target"
        );
    }

    private static void opaqueValuesStayEvidenceOnly(){
        require(
            BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .ITEM_ID==22844&&
            BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .VALUE_A==250&&
            BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .VALUE_B==10000&&
            BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .ITEM_WIDGET_A==61158&&
            BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .ITEM_WIDGET_B==61159&&
            !BloodcoreLotteryAdapter
                .OpaqueStaticEvidence
                .SEMANTICS_OWNED,
            "opaque client evidence"
        );

        for(Field field:
                BloodcoreLotteryAdapter
                    .EnterIntent.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("cost")||
               name.contains("price")||
               name.contains("pot")||
               name.contains("prize")||
               name.contains("amount")||
               name.contains("units"))
                throw new AssertionError(
                    "enter intent assigned economics "+
                    field.getName()
                );
        }
    }

    private static void
        semanticServiceHasNoProtocolIdentity(){
        for(Field field:
                LotteryService.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("widget")||
               name.contains("opcode")||
               name.contains("packet"))
                throw new AssertionError(
                    "LotteryService leaked protocol field "+
                    field.getName()
                );
        }
    }

    private static void policyStillExternal(){
        for(Method method:
                BloodcoreLotteryAdapter.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("settle")||
               name.contains("winnerselect")||
               name.contains("random")||
               name.contains("rng")||
               name.contains("price")||
               name.contains("refund")||
               name.contains("schedule")||
               name.contains("payout"))
                throw new AssertionError(
                    "Bloodcore adapter owns policy "+
                    method.getName()
                );
        }
    }

    private static void finalizeRound(
        LotteryService service,
        LotteryService.Channel channel,
        String roundKey,
        String winner
    ){
        service.openRound(
            roundKey,
            channel,
            AUTHORITY
        );

        service.confirmEntrySettled(
            roundKey,
            winner,
            "receipt:"+roundKey,
            1L
        );

        service.recordAuthoritativeWinner(
            roundKey,
            winner,
            "draw:"+roundKey
        );

        service.confirmPrizeSettledAndFinalize(
            roundKey
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(
                label
            );
    }

    private BloodcoreLotteryAdapterTest(){}
}
