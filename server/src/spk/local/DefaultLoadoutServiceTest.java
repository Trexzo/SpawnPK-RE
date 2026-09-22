package spk.local;

import java.lang.reflect.*;
import java.util.*;

public final class DefaultLoadoutServiceTest {
    private static final String POLICY=
        "LOCAL_LAB_POLICY_DEFAULT_LOADOUT";

    public static void main(String[] args){
        LoadoutService loadouts=
            new LoadoutService();

        PlayerLoadout a1=
            fixture(
                "loadout:a",
                1L,
                "item:a1"
            );
        PlayerLoadout b1=
            fixture(
                "loadout:b",
                1L,
                "item:b1"
            );

        loadouts.create(a1);
        loadouts.create(b1);

        DefaultLoadoutService service=
            new DefaultLoadoutService(
                loadouts,
                POLICY
            );

        DefaultLoadoutService.Snapshot empty=
            service.snapshot(
                " Player:Alice "
            );

        require(
            "player:alice".equals(
                empty.ownerRef
            )&&
            !empty.hasDefault()&&
            empty.currentLoadout==null&&
            empty.selectionRevision==0L&&
            service.ownerCount()==0,
            "default loadout initial state"
        );

        expect(
            IllegalArgumentException.class,
            ()->service.setDefault(
                "player:alice",
                PlayerLoadoutId.of(
                    "loadout:missing"
                )
            ),
            "unknown default loadout"
        );

        require(
            service.ownerCount()==0,
            "failed default selection mutated state"
        );

        DefaultLoadoutService.Snapshot first=
            service.setDefault(
                "PLAYER:ALICE",
                a1.id
            );

        require(
            first.hasDefault()&&
            first.defaultLoadoutId.equals(
                a1.id
            )&&
            first.currentLoadout==
                a1&&
            first.selectionRevision==1L,
            "first default selection"
        );

        DefaultLoadoutService.Snapshot retry=
            service.setDefault(
                "player:alice",
                a1.id
            );

        require(
            retry.selectionRevision==1L&&
            retry.currentLoadout==a1,
            "same default idempotent"
        );

        PlayerLoadout a2=
            fixture(
                "loadout:a",
                2L,
                "item:a2"
            );

        loadouts.replace(
            a2,
            PlayerLoadoutVersion.of(1L)
        );

        DefaultLoadoutService.Snapshot
            followed=
                service.snapshot(
                    "player:alice"
                );

        require(
            followed.defaultLoadoutId
                .equals(a1.id)&&
            followed.currentLoadout==
                a2&&
            followed.currentLoadout.version
                .equals(
                    PlayerLoadoutVersion.of(
                        2L
                    )
                )&&
            followed.selectionRevision==1L,
            "default follows current semantic loadout revision"
        );

        DefaultLoadoutService.Snapshot second=
            service.setDefault(
                "player:alice",
                b1.id
            );

        require(
            second.defaultLoadoutId.equals(
                b1.id
            )&&
            second.currentLoadout==
                b1&&
            second.selectionRevision==2L,
            "default selection change"
        );

        DefaultLoadoutService.Snapshot bob=
            service.setDefault(
                "player:bob",
                PlayerLoadoutId.of(
                    "loadout:a"
                )
            );

        // Bob does not own Alice's loadout, so this path must fail before state.
        throw new AssertionError(
            "unreachable bob snapshot "+bob
        );
    }

    private static PlayerLoadout fixture(
        String id,
        long version,
        String item
    ){
        return new PlayerLoadout(
            PlayerLoadoutId.of(id),
            PlayerLoadoutVersion.of(
                version
            ),
            "player:alice",
            Collections.singletonList(
                new PlayerLoadout.InventoryEntry(
                    item,
                    1L
                )
            ),
            Collections.emptyList(),
            null,
            null,
            POLICY
        );
    }

    private static void authorityFence(
        LoadoutService loadouts
    ){
        expect(
            IllegalArgumentException.class,
            ()->new DefaultLoadoutService(
                loadouts,
                "EXACT_CURRENT_CLIENT"
            ),
            "presentation authority as default policy"
        );

        expect(
            IllegalArgumentException.class,
            ()->new DefaultLoadoutService(
                loadouts,
                "UNKNOWN_SERVER_AUTHORITY"
            ),
            "unknown authority as default policy"
        );
    }

    private static void protocolBoundary(){
        for(Class<?> type:new Class<?>[]{
                DefaultLoadoutService.class,
                DefaultLoadoutService
                    .Snapshot.class
        }){
            for(Field field:
                    type.getDeclaredFields()){
                String name=
                    field.getName()
                        .toLowerCase(
                            Locale.ROOT
                        );

                if(name.contains("widget")||
                   name.contains("opcode")||
                   name.contains("packet")||
                   name.contains("command")||
                   name.contains("clientslot"))
                    throw new AssertionError(
                        "protocol identity leaked into default loadout "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
    }

    private static void noInventedOperations(){
        for(Method method:
                DefaultLoadoutService.class
                    .getDeclaredMethods()){
            String name=
                method.getName()
                    .toLowerCase(
                        Locale.ROOT
                    );

            if(name.contains("clear")||
               name.contains("reset")||
               name.contains("apply")||
               name.contains("persist"))
                throw new AssertionError(
                    "unproven default-loadout behavior leaked into "+
                    method.getName()
                );
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

    private DefaultLoadoutServiceTest(){}
}
