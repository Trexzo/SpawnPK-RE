package spk.local;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class EventActivityPresentationTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_EVENT_ACTIVITY_PRESENTATION";

    public static void main(String[] args){
        exactOperationBodies();
        serviceProjection();
        failClosedWireBoundaries();
        authorityBoundary();

        System.out.println(
            "EVENT_ACTIVITY_PRESENTATION_PASS "+
            "subtype6=true "+
            "root30072=true "+
            "clearAppendRender=true "+
            "maxRows7=true "+
            "visibleDetails3=true "+
            "detailCountU8=true "+
            "i32CurrentLimit=true "+
            "i64Duration=true "+
            "lockedUnlimitedFinite=true "+
            "quotaPolicyOwned=false "+
            "tokenEconomicsOwned=false"
        );
    }

    private static void exactOperationBodies(){
        requireBytes(
            ApplicationPacket250Writer.encode(
                EventActivityPresentation
                    .APPLICATION_SUBTYPE,
                EventActivityPresentation
                    .clearBody()
            ),
            new int[]{
                0x00,0x06,0x00
            },
            "clear"
        );

        requireBytes(
            ApplicationPacket250Writer.encode(
                EventActivityPresentation
                    .APPLICATION_SUBTYPE,
                EventActivityPresentation
                    .renderBody()
            ),
            new int[]{
                0x00,0x06,0x02
            },
            "render"
        );

        byte[] root=
            BootstrapPackets.interface97(
                EventActivityPresentation.ROOT
            );

        require(
            root.length==2&&
            (root[0]&255)==0x75&&
            (root[1]&255)==0x78,
            "root 30072 body"
        );
    }

    private static void serviceProjection(){
        UsageQuotaService quotas=
            new UsageQuotaService();

        quotas.define(
            new UsageQuotaDefinition(
                "event:finite:quota",
                10L,
                POLICY
            )
        );
        quotas.openWindow(
            "event:finite:quota",
            "window:one",
            3L,
            123L
        );

        EventActivityService service=
            new EventActivityService(
                quotas
            );

        EventActivityService.Snapshot snapshot=
            service.replaceRows(
                Arrays.asList(
                    new EventActivityService.RowSpec(
                        "event:finite",
                        "Finite",
                        Arrays.asList(
                            "One",
                            "Two"
                        ),
                        EventActivityService.Mode.FINITE,
                        "event:finite:quota",
                        0L,
                        90_000L,
                        POLICY
                    ),
                    new EventActivityService.RowSpec(
                        "event:locked",
                        "Locked",
                        Collections.emptyList(),
                        EventActivityService.Mode.LOCKED,
                        null,
                        0L,
                        0L,
                        POLICY
                    ),
                    new EventActivityService.RowSpec(
                        "event:unlimited",
                        "Unlimited",
                        Collections.singletonList(
                            "Open"
                        ),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        4L,
                        1L,
                        POLICY
                    )
                )
            );

        byte[] finite=
            ApplicationPacket250Writer.encode(
                EventActivityPresentation
                    .APPLICATION_SUBTYPE,
                EventActivityPresentation
                    .appendBody(
                        snapshot.row(0)
                    )
            );

        requireBytes(
            finite,
            new int[]{
                0x00,0x06,
                0x01,
                'F','i','n','i','t','e',0x0a,
                0x02,
                'O','n','e',0x0a,
                'T','w','o',0x0a,
                0x00,0x00,0x00,0x03,
                0x00,0x00,0x00,0x0a,
                0x00,0x00,0x00,0x00,
                0x00,0x01,0x5f,0x90
            },
            "finite append"
        );

        byte[] locked=
            ApplicationPacket250Writer.encode(
                EventActivityPresentation
                    .APPLICATION_SUBTYPE,
                EventActivityPresentation
                    .appendBody(
                        snapshot.row(1)
                    )
            );

        require(
            containsSequence(
                locked,
                new int[]{
                    0xff,0xff,0xff,0xff,
                    0x00,0x00,0x00,0x00,
                    0x00,0x00,0x00,0x00
                }
            ),
            "locked -1 limit + zero duration"
        );

        byte[] unlimited=
            ApplicationPacket250Writer.encode(
                EventActivityPresentation
                    .APPLICATION_SUBTYPE,
                EventActivityPresentation
                    .appendBody(
                        snapshot.row(2)
                    )
            );

        require(
            containsSequence(
                unlimited,
                new int[]{
                    0x00,0x00,0x00,0x04,
                    0x00,0x00,0x00,0x00,
                    0x00,0x00,0x00,0x00,
                    0x00,0x00,0x00,0x01
                }
            ),
            "unlimited zero limit"
        );
    }

    private static void failClosedWireBoundaries(){
        UsageQuotaService quotas=
            new UsageQuotaService();
        EventActivityService service=
            new EventActivityService(quotas);

        EventActivityService.Snapshot fourDetails=
            service.replaceRows(
                Collections.singletonList(
                    new EventActivityService.RowSpec(
                        "event:details",
                        "Details",
                        Arrays.asList(
                            "one",
                            "two",
                            "three",
                            "four"
                        ),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        0L,
                        1L,
                        POLICY
                    )
                )
            );

        byte[] fourDetailBody=
            EventActivityPresentation.appendBody(
                fourDetails.row(0)
            );

        require(
            (fourDetailBody[
                1+"Details".length()+1
            ]&255)==4,
            "wire permits fourth detail even though only three lines are visible"
        );

        EventActivityService.Snapshot newline=
            service.replaceRows(
                Collections.singletonList(
                    new EventActivityService.RowSpec(
                        "event:newline",
                        "Line\nBreak",
                        Collections.emptyList(),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        0L,
                        1L,
                        POLICY
                    )
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->EventActivityPresentation
                .appendBody(
                    newline.row(0)
                ),
            "embedded newline"
        );

        EventActivityService.Snapshot nonLatin=
            service.replaceRows(
                Collections.singletonList(
                    new EventActivityService.RowSpec(
                        "event:unicode",
                        "\u0100",
                        Collections.emptyList(),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        0L,
                        1L,
                        POLICY
                    )
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->EventActivityPresentation
                .appendBody(
                    nonLatin.row(0)
                ),
            "non ISO-8859-1"
        );

        EventActivityService.Snapshot overflow=
            service.replaceRows(
                Collections.singletonList(
                    new EventActivityService.RowSpec(
                        "event:overflow",
                        "Overflow",
                        Collections.emptyList(),
                        EventActivityService.Mode.UNLIMITED,
                        null,
                        ((long)Integer.MAX_VALUE)+1L,
                        1L,
                        POLICY
                    )
                )
            );

        expect(
            IllegalArgumentException.class,
            ()->EventActivityPresentation
                .appendBody(
                    overflow.row(0)
                ),
            "current usage i32 overflow"
        );
    }

    private static void authorityBoundary(){
        require(
            EventActivityPresentation.MAX_ROWS==
                EventActivityService.MAX_ROWS,
            "row-cap parity"
        );

        for(Field field:
                EventActivityPresentation.class
                    .getDeclaredFields()){
            String name=
                field.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("tokenvalue")||
               name.contains("reward")||
               name.contains("resetpolicy")||
               name.contains("accountscope")||
               name.contains("ipscope")||
               name.contains("antiabuse"))
                throw new AssertionError(
                    "unowned Event Activity policy field "+
                    field.getName()
                );
        }

        for(Method method:
                EventActivityPresentation.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(Locale.ROOT);

            if(name.contains("calculatequota")||
               name.contains("granttoken")||
               name.contains("resetwindow")||
               name.contains("persist"))
                throw new AssertionError(
                    "unowned Event Activity policy method "+
                    method.getName()
                );
        }
    }

    private static boolean containsSequence(
        byte[] actual,
        int[] expected
    ){
        outer:
        for(int i=0;
            i+expected.length<=actual.length;
            i++){
            for(int j=0;j<expected.length;j++)
                if((actual[i+j]&255)!=
                        expected[j])
                    continue outer;
            return true;
        }
        return false;
    }

    private static void requireBytes(
        byte[] actual,
        int[] expected,
        String label
    ){
        require(
            actual.length==expected.length,
            label+
                " length actual="+
                actual.length+
                " expected="+
                expected.length
        );

        for(int i=0;i<expected.length;i++)
            require(
                (actual[i]&255)==expected[i],
                label+
                    " byte["+
                    i+
                    "] actual="+
                    (actual[i]&255)+
                    " expected="+
                    expected[i]
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
                label+
                " wrong failure "+
                failure,
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

    private EventActivityPresentationTest(){}
}
