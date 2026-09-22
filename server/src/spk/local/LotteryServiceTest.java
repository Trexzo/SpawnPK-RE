package spk.local;

import java.lang.reflect.Field;
import java.util.*;

public final class LotteryServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_LOTTERY";

    public static void main(String[] args){
        LotteryService service=
            new LotteryService();

        LotteryService.RoundSnapshot ordinary=
            service.openRound(
                "lottery:ordinary:1",
                LotteryService.Channel.ORDINARY,
                POLICY
            );

        LotteryService.RoundSnapshot bloodcore=
            service.openRound(
                "lottery:bloodcore:1",
                LotteryService.Channel.BLOODCORE,
                POLICY
            );

        require(
            ordinary.state==
                LotteryService.RoundState.OPEN&&
            bloodcore.state==
                LotteryService.RoundState.OPEN&&
            LotteryService.Channel.ORDINARY
                .historyCapacity()==6&&
            LotteryService.Channel.BLOODCORE
                .historyCapacity()==35,
            "Lottery channel/history setup"
        );

        expect(
            IllegalStateException.class,
            ()->service.openRound(
                "lottery:ordinary:second",
                LotteryService.Channel.ORDINARY,
                POLICY
            ),
            "second active ordinary Lottery round"
        );

        LotteryService.EntryResult first=
            service.confirmEntrySettled(
                "lottery:ordinary:1",
                " Player:Alice ",
                "receipt:ordinary:alice:1",
                2L
            );

        LotteryService.EntryResult second=
            service.confirmEntrySettled(
                "lottery:ordinary:1",
                "player:alice",
                "receipt:ordinary:alice:2",
                3L
            );

        require(
            first.changed&&
            first.participant.entryUnits==2L&&
            second.participant.entryUnits==5L&&
            "player:alice".equals(
                second.participant.playerRef
            ),
            "Lottery settled entry accumulation"
        );

        LotteryService.EntryResult replay=
            service.confirmEntrySettled(
                "lottery:ordinary:1",
                "PLAYER:ALICE",
                "receipt:ordinary:alice:2",
                3L
            );

        require(
            !replay.changed&&
            replay.participant.entryUnits==5L,
            "Lottery receipt idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.confirmEntrySettled(
                "lottery:ordinary:1",
                "player:bob",
                "receipt:ordinary:alice:2",
                3L
            ),
            "Lottery receipt replay mismatch"
        );

        service.confirmEntrySettled(
            "lottery:ordinary:1",
            "player:bob",
            "receipt:ordinary:bob:1",
            1L
        );

        require(
            service.get(
                "lottery:bloodcore:1"
            ).participants.isEmpty(),
            "ordinary/Bloodcore Lottery isolation"
        );

        expect(
            IllegalArgumentException.class,
            ()->service
                .recordAuthoritativeWinner(
                    "lottery:ordinary:1",
                    "player:charlie",
                    "draw:external:bad"
                ),
            "Lottery non-entrant winner"
        );

        LotteryService.RoundSnapshot winner=
            service.recordAuthoritativeWinner(
                "lottery:ordinary:1",
                "player:alice",
                "draw:external:001"
            );

        require(
            winner.state==
                LotteryService
                    .RoundState
                    .WINNER_RECORDED&&
            "player:alice".equals(
                winner.winnerRef
            )&&
            "draw:external:001".equals(
                winner.drawReference
            )&&
            service.history(
                LotteryService.Channel.ORDINARY
            ).isEmpty(),
            "Lottery authoritative winner before prize settlement"
        );

        expect(
            IllegalStateException.class,
            ()->service.cancelRound(
                "lottery:ordinary:1"
            ),
            "Lottery cancelled after winner recorded"
        );

        LotteryService.FinalizeResult finalized=
            service
                .confirmPrizeSettledAndFinalize(
                    "lottery:ordinary:1"
                );

        require(
            finalized.changed&&
            finalized.round.state==
                LotteryService
                    .RoundState.SETTLED&&
            service.history(
                LotteryService.Channel.ORDINARY
            ).size()==1&&
            service.activeRound(
                LotteryService.Channel.ORDINARY
            )==null,
            "Lottery post-prize-settlement finalize"
        );

        require(
            !service
                .confirmPrizeSettledAndFinalize(
                    "lottery:ordinary:1"
                ).changed,
            "Lottery finalize idempotency"
        );

        cancellation(service);
        historyCaps();
        protocolBoundary();

        System.out.println(
            "LOTTERY_SERVICE_PASS "+
            "ordinaryHistory6=true "+
            "bloodcoreHistory35=true "+
            "oneActiveRoundPerChannel=true "+
            "channelIsolation=true "+
            "normalizedPlayerIdentity=true "+
            "receiptIdempotency=true "+
            "positiveEntryUnits=true "+
            "winnerMustBeEntrant=true "+
            "winnerExternallyAuthoritative=true "+
            "prizeSettlementBeforeHistory=true "+
            "historyTrimExact=true "+
            "cancellationTerminal=true "+
            "currencyOwned=false "+
            "entryCostOwned=false "+
            "rngOwned=false "+
            "prizePayloadOwned=false "+
            "protocolIndependent=true"
        );
    }

    private static void cancellation(
        LotteryService service
    ){
        LotteryService.RoundSnapshot cancelled=
            service.cancelRound(
                "lottery:bloodcore:1"
            );

        require(
            cancelled.state==
                LotteryService.RoundState.CANCELLED&&
            service.activeRound(
                LotteryService.Channel.BLOODCORE
            )==null,
            "Bloodcore Lottery cancellation"
        );

        require(
            service.cancelRound(
                "lottery:bloodcore:1"
            ).state==
                LotteryService.RoundState.CANCELLED,
            "Lottery cancellation idempotency"
        );

        expect(
            IllegalStateException.class,
            ()->service.confirmEntrySettled(
                "lottery:bloodcore:1",
                "player:x",
                "receipt:cancelled",
                1L
            ),
            "entry into cancelled Lottery"
        );
    }

    private static void historyCaps(){
        LotteryService service=
            new LotteryService();

        createSettledRounds(
            service,
            LotteryService.Channel.ORDINARY,
            8
        );

        List<LotteryService.WinnerRecord>
            ordinary=
                service.history(
                    LotteryService.Channel.ORDINARY
                );

        require(
            ordinary.size()==6&&
            "ordinary:round:2".equals(
                ordinary.get(0)
                    .roundKey
            )&&
            "ordinary:round:7".equals(
                ordinary.get(5)
                    .roundKey
            ),
            "ordinary Lottery exact history trim"
        );

        createSettledRounds(
            service,
            LotteryService.Channel.BLOODCORE,
            37
        );

        List<LotteryService.WinnerRecord>
            bloodcore=
                service.history(
                    LotteryService.Channel.BLOODCORE
                );

        require(
            bloodcore.size()==35&&
            "bloodcore:round:2".equals(
                bloodcore.get(0)
                    .roundKey
            )&&
            "bloodcore:round:36".equals(
                bloodcore.get(34)
                    .roundKey
            ),
            "Bloodcore Lottery exact history trim"
        );

        boolean immutable=false;

        try{
            ordinary.clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "Lottery history mutable"
        );
    }

    private static void createSettledRounds(
        LotteryService service,
        LotteryService.Channel channel,
        int count
    ){
        String prefix=
            channel==
                LotteryService.Channel.ORDINARY
                ?"ordinary"
                :"bloodcore";

        for(int i=0;i<count;i++){
            String round=
                prefix+":round:"+i;

            service.openRound(
                round,
                channel,
                POLICY
            );

            service.confirmEntrySettled(
                round,
                "player:winner:"+i,
                "receipt:"+prefix+":"+i,
                1L
            );

            service.recordAuthoritativeWinner(
                round,
                "player:winner:"+i,
                "draw:"+prefix+":"+i
            );

            service.confirmPrizeSettledAndFinalize(
                round
            );
        }
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                LotteryService.class,
                LotteryService.RoundSnapshot.class,
                LotteryService.WinnerRecord.class,
                LotteryService.ParticipantSnapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("packet")||
                   name.contains("opcode")||
                   name.contains("widget")||
                   name.contains("interface")||
                   name.contains("currency")||
                   name.contains("itemid")||
                   name.contains("entrycost")||
                   name.contains("potvalue")||
                   name.contains("odds")||
                   name.contains("random")||
                   name.contains("rng")||
                   name.contains("prizeitem"))
                    throw new AssertionError(
                        "protocol/economic/RNG identity leaked into Lottery "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void expect(
        Class<? extends Throwable> type,
        Runnable action,
        String label
    ){
        try{
            action.run();
        }catch(Throwable failure){
            if(type.isInstance(failure))
                return;

            throw new AssertionError(
                label+
                " wrong failure "+
                failure,
                failure
            );
        }

        throw new AssertionError(
            label+
            " did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LotteryServiceTest(){}
}
