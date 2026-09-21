package spk.local;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

public final class SocialListServiceTest {
    public static void main(String[] args){
        SocialListService.Policy policy=
            new SocialListService.Policy(
                2,
                1,
                true,
                "LOCAL_LAB_POLICY"
            );

        SocialListService service=
            new SocialListService(
                policy
            );

        SocialListService.AccountRef alice=
            new SocialListService.AccountRef(
                " Alice "
            );
        SocialListService.AccountRef bob=
            new SocialListService.AccountRef(
                "BOB"
            );
        SocialListService.AccountRef charlie=
            new SocialListService.AccountRef(
                "charlie"
            );
        SocialListService.AccountRef dave=
            new SocialListService.AccountRef(
                "dave"
            );

        require(
            "alice".equals(
                alice.value()
            ),
            "owner normalization"
        );

        requireStatus(
            service.addFriend(
                alice,
                bob
            ),
            SocialListService
                .MutationStatus.ADDED,
            true,
            "first friend"
        );

        requireStatus(
            service.addFriend(
                alice,
                new SocialListService.AccountRef(
                    " bob "
                )
            ),
            SocialListService
                .MutationStatus.ALREADY_PRESENT,
            false,
            "duplicate friend"
        );

        requireStatus(
            service.addIgnore(
                alice,
                bob
            ),
            SocialListService
                .MutationStatus.CONFLICTING_LIST,
            false,
            "cross-list exclusivity"
        );

        requireStatus(
            service.addIgnore(
                charlie,
                bob
            ),
            SocialListService
                .MutationStatus.ADDED,
            true,
            "owner isolation"
        );

        SocialListService.Snapshot
            charlieSnapshot=
                service.get(
                    charlie
                );

        require(
            charlieSnapshot!=null&&
            charlieSnapshot.ignored(
                bob
            )&&
            !charlieSnapshot.friend(
                bob
            ),
            "same target independent for second owner"
        );

        requireStatus(
            service.addFriend(
                alice,
                charlie
            ),
            SocialListService
                .MutationStatus.ADDED,
            true,
            "second friend"
        );

        requireStatus(
            service.addFriend(
                alice,
                dave
            ),
            SocialListService
                .MutationStatus.CAPACITY_REACHED,
            false,
            "caller policy friend capacity"
        );

        requireStatus(
            service.addIgnore(
                alice,
                dave
            ),
            SocialListService
                .MutationStatus.ADDED,
            true,
            "first ignore"
        );

        requireStatus(
            service.addIgnore(
                alice,
                new SocialListService.AccountRef(
                    "eve"
                )
            ),
            SocialListService
                .MutationStatus.CAPACITY_REACHED,
            false,
            "caller policy ignore capacity"
        );

        requireStatus(
            service.removeFriend(
                alice,
                dave
            ),
            SocialListService
                .MutationStatus.NOT_PRESENT,
            false,
            "absent remove"
        );

        SocialListService.Snapshot snapshot=
            service.get(
                alice
            );

        require(
            snapshot!=null&&
            snapshot.version()==
                SocialListService
                    .SNAPSHOT_VERSION&&
            snapshot.owner().equals(
                alice
            )&&
            snapshot.friends().size()==2&&
            snapshot.ignores().size()==1&&
            snapshot.friend(bob)&&
            snapshot.friend(charlie)&&
            snapshot.ignored(dave),
            "snapshot state"
        );

        boolean immutable=false;

        try{
            snapshot.friends().clear();
        }catch(
            UnsupportedOperationException expected
        ){
            immutable=true;
        }

        require(
            immutable,
            "snapshot friends mutable"
        );

        SocialListService restored=
            new SocialListService(
                policy
            );

        SocialListService.Snapshot
            restoredSnapshot=
                restored.restore(
                    snapshot
                );

        require(
            restored.ownerCount()==1&&
            restoredSnapshot.owner().equals(
                alice
            )&&
            restoredSnapshot.friends()
                .equals(
                    snapshot.friends()
                )&&
            restoredSnapshot.ignores()
                .equals(
                    snapshot.ignores()
                ),
            "snapshot restore round trip"
        );

        boolean overlapRejected=false;

        try{
            restored.restore(
                new SocialListService.Snapshot(
                    SocialListService
                        .SNAPSHOT_VERSION,
                    new SocialListService.AccountRef(
                        "mallory"
                    ),
                    Arrays.asList(
                        bob
                    ),
                    Arrays.asList(
                        bob
                    )
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            overlapRejected=true;
        }

        require(
            overlapRejected,
            "overlapping restore accepted"
        );

        boolean overCapacityRejected=false;

        try{
            restored.restore(
                new SocialListService.Snapshot(
                    SocialListService
                        .SNAPSHOT_VERSION,
                    new SocialListService.AccountRef(
                        "trent"
                    ),
                    Arrays.asList(
                        alice,
                        bob,
                        charlie
                    ),
                    Collections
                        .<SocialListService.AccountRef>
                            emptyList()
                )
            );
        }catch(
            IllegalArgumentException expected
        ){
            overCapacityRejected=true;
        }

        require(
            overCapacityRejected,
            "over-capacity restore accepted"
        );

        assertProtocolIndependent();

        require(
            policy.maxFriends==2&&
            policy.maxIgnores==1&&
            policy.crossListExclusive&&
            "LOCAL_LAB_POLICY".equals(
                policy.sourceAuthority
            ),
            "policy not explicit"
        );

        System.out.println(
            "SOCIAL_LIST_SERVICE_PASS "+
            "ownerIsolation=true "+
            "normalizedAccountIdentity=true "+
            "duplicateIdempotent=true "+
            "absentRemoveIdempotent=true "+
            "crossListPolicy=true "+
            "callerCapacityPolicy=true "+
            "clientCapsHardcoded=false "+
            "immutableSnapshot=true "+
            "restoreRoundTrip=true "+
            "invalidRestoreFailClosed=true "+
            "persistenceSeam=true "+
            "protocolIndependent=true"
        );
    }

    private static void requireStatus(
        SocialListService.MutationResult result,
        SocialListService.MutationStatus expected,
        boolean changed,
        String label
    ){
        require(
            result.status==expected,
            label+
                " expected="+expected+
                " actual="+result.status
        );

        require(
            result.changed()==changed,
            label+
                " changed="+
                result.changed()
        );
    }

    private static void assertProtocolIndependent(){
        Class<?>[] types={
            SocialListService.class,
            SocialListService.AccountRef.class,
            SocialListService.Policy.class,
            SocialListService.Snapshot.class,
            SocialListService.MutationResult.class
        };

        for(Class<?> type:types){
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
                   name.contains("scen")||
                   name.contains("namekey")||
                   name.contains("clientarray"))
                    throw new AssertionError(
                        "transport/client identity leaked "+
                        type.getSimpleName()+
                        "."+
                        field.getName()
                    );
            }
        }
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

    private SocialListServiceTest(){}
}
