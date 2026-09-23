package spk.local;

public final class LotteryPresentationTest {
    public static void main(String[] args){
        exactEntryRouting();
        exactRoots();
        exactHistoryRanges();
        authorityBoundary();

        System.out.println(
            "LOTTERY_PRESENTATION_PASS "+
            "ordinaryRoot52000=true "+
            "bloodcoreRoot61150=true "+
            "ordinaryEntry52010=true "+
            "bloodcoreEntry61196=true "+
            "ordinaryHistory6=true "+
            "bloodcoreHistory35=true "+
            "entryEconomicsOwned=false "+
            "rngOwned=false "+
            "drawCadenceOwned=false "+
            "bloodcoreQuantityMeaningOwned=false"
        );
    }

    private static void exactEntryRouting(){
        require(
            LotteryPresentation
                .resolveEntryWidget(52010)==
                LotteryService.Channel.ORDINARY,
            "ordinary entry"
        );
        require(
            LotteryPresentation
                .resolveEntryWidget(61196)==
                LotteryService.Channel.BLOODCORE,
            "bloodcore entry"
        );
        require(
            LotteryPresentation
                .resolveEntryWidget(52011)==null&&
            LotteryPresentation
                .resolveEntryWidget(61197)==null,
            "hover widgets are not actions"
        );
    }

    private static void exactRoots(){
        require(
            LotteryPresentation.root(
                LotteryService.Channel.ORDINARY
            )==52000,
            "ordinary root"
        );
        require(
            LotteryPresentation.root(
                LotteryService.Channel.BLOODCORE
            )==61150,
            "bloodcore root"
        );

        byte[] ordinary=
            BootstrapPackets.interface97(52000);
        byte[] bloodcore=
            BootstrapPackets.interface97(61150);

        require(
            ordinary.length==2&&
            (ordinary[0]&255)==0xcb&&
            (ordinary[1]&255)==0x20,
            "ordinary root bytes"
        );
        require(
            bloodcore.length==2&&
            (bloodcore[0]&255)==0xee&&
            (bloodcore[1]&255)==0xde,
            "bloodcore root bytes"
        );
    }

    private static void exactHistoryRanges(){
        require(
            LotteryService.Channel
                .ORDINARY
                .historyCapacity()==6&&
            LotteryService.Channel
                .BLOODCORE
                .historyCapacity()==35,
            "service history capacities"
        );

        require(
            LotteryPresentation.historyWidget(
                LotteryService.Channel.ORDINARY,
                0
            )==52014&&
            LotteryPresentation.historyWidget(
                LotteryService.Channel.ORDINARY,
                5
            )==52019,
            "ordinary history range"
        );

        require(
            LotteryPresentation.historyWidget(
                LotteryService.Channel.BLOODCORE,
                0
            )==61161&&
            LotteryPresentation.historyWidget(
                LotteryService.Channel.BLOODCORE,
                34
            )==61195,
            "bloodcore history range"
        );

        expect(
            IllegalArgumentException.class,
            ()->LotteryPresentation.historyWidget(
                LotteryService.Channel.ORDINARY,
                6
            ),
            "ordinary history overflow"
        );

        expect(
            IllegalArgumentException.class,
            ()->LotteryPresentation.historyWidget(
                LotteryService.Channel.BLOODCORE,
                35
            ),
            "bloodcore history overflow"
        );
    }

    private static void authorityBoundary(){
        require(
            LotteryPresentation
                .WIDGET_ACTION_OPCODE==185,
            "widget action opcode"
        );
        require(
            "EXACT_CURRENT_CLIENT".equals(
                LotteryPresentation
                    .PRESENTATION_AUTHORITY
            ),
            "presentation authority"
        );
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
                label+" wrong failure "+failure,
                failure
            );
        }

        throw new AssertionError(
            label+" did not fail"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private LotteryPresentationTest(){}
}
