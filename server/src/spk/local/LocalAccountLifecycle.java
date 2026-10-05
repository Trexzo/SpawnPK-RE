package spk.local;

import java.util.function.IntPredicate;

/**
 * Session account/profile orchestration extracted from LocalSession.
 *
 * Runtime persistence is repository/snapshot backed. Historical component-based
 * overloads remain only as compatibility seams for inherited LocalLab fixtures.
 */
final class LocalAccountLifecycle {
    static Selection select(World world,String loginAlias,String tag){
        String username=LocalAccountProfiles.chooseForLogin(world,loginAlias);
        boolean persistent=LocalAccountProfiles.isPersistent(username);

        if(!username.equalsIgnoreCase(loginAlias) && !loginAlias.equalsIgnoreCase("localtest"))
            System.out.println(tag+"V5123_LOCAL_PROFILE_ALIAS loginAlias="+loginAlias+" selected="+username+" reason="+
                (username.equalsIgnoreCase(LocalAccountProfiles.SECONDARY)?"PRIMARY_ALREADY_ONLINE":"CANONICAL_ALIAS"));
        else if(username.equalsIgnoreCase(LocalAccountProfiles.SECONDARY))
            System.out.println(tag+"V5123_LOCAL_PROFILE_ALIAS loginAlias="+loginAlias+" selected=src reason=PRIMARY_ALREADY_ONLINE");

        return new Selection(username,persistent);
    }

    static LoadResult load(
        Selection selection,
        WorldPlayer player,
        PlayerRepository repository,
        IntPredicate accessoryAllowed,
        String tag
    ){
        return loadSnapshot(
            selection,
            player,
            new SnapshotSource(){
                @Override public java.util.Optional<PlayerSnapshot> load(
                    String username
                )throws java.io.IOException{
                    return repository.load(
                        username
                    );
                }

                @Override public String name(){
                    return repository.getClass()
                        .getSimpleName();
                }
            },
            accessoryAllowed,
            tag
        );
    }

    static LoadResult load(
        Selection selection,
        WorldPlayer player,
        WorldPlayerPersistence persistence,
        IntPredicate accessoryAllowed,
        String tag
    ){
        return loadSnapshot(
            selection,
            player,
            new SnapshotSource(){
                @Override public java.util.Optional<PlayerSnapshot> load(
                    String username
                )throws java.io.IOException{
                    return persistence.load(
                        username
                    );
                }

                @Override public String name(){
                    return persistence.repositoryName();
                }
            },
            accessoryAllowed,
            tag
        );
    }

    private static LoadResult loadSnapshot(
        Selection selection,
        WorldPlayer player,
        SnapshotSource source,
        IntPredicate accessoryAllowed,
        String tag
    ){
        if(!selection.persistent)
            return new LoadResult(
                0,
                LoadStatus.NOT_PERSISTENT
            );

        try{
            java.util.Optional<PlayerSnapshot> loaded=
                source.load(
                    selection.username
                );

            if(!loaded.isPresent()){
                System.out.println(
                    tag+
                    "V5123_ACCOUNT ACCOUNT_DEFAULTS_NO_FILE"+
                    " profile="+selection.username+
                    " repository="+
                    source.name()
                );
                return new LoadResult(
                    0,
                    LoadStatus.MISSING
                );
            }

            PlayerSnapshot sourceSnapshot=
                loaded.get();

            PlayerSnapshot normalized=
                PlayerSnapshotCodec.applyValidated(
                    sourceSnapshot,
                    player
                );

            int persistedAccessory=
                PlayerSnapshotCodec.accessoryItem(
                    sourceSnapshot
                );

            int accessoryItem=
                accessoryAllowed.test(
                    persistedAccessory
                )
                    ?persistedAccessory
                    :0;

            System.out.println(
                tag+
                "V5123_ACCOUNT ACCOUNT_LOADED"+
                " profile="+selection.username+
                " repository="+
                source.name()+
                " snapshot="+normalized
            );

            System.out.println(
                tag+
                "V5131_PET_ACCESSORY_PERSIST_LOAD item="+
                (accessoryItem==0
                    ?"NONE"
                    :accessoryItem)+
                " authority=ACCOUNT_SEMANTIC_STATE"
            );

            return new LoadResult(
                accessoryItem,
                LoadStatus.LOADED
            );
        }catch(Throwable e){
            System.err.println(
                tag+
                "V5123_ACCOUNT_LOAD_FAILED"+
                " profile="+selection.username+
                " repository="+
                source.name()+
                " error="+e+
                " action=REJECT_SESSION"
            );
            return new LoadResult(
                0,
                LoadStatus.FAILED
            );
        }
    }

    static LoadResult load(
        Selection selection,
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState petState,
        PlayerState playerState,
        IntPredicate accessoryAllowed,
        String tag
    ){
        if(!selection.persistent)
            return new LoadResult(
                0,
                LoadStatus.NOT_PERSISTENT
            );

        try{
            String loadResult=
                LocalAccountProfiles.load(
                    selection.username,
                    bank,
                    equipment,
                    movement,
                    petState,
                    playerState
                );

            System.out.println(
                tag+"V5123_ACCOUNT "+
                loadResult
            );

            boolean missing=
                loadResult.startsWith(
                    "ACCOUNT_DEFAULTS_NO_FILE"
                )||
                loadResult.startsWith(
                    "NEW_ACCOUNT_DEFAULTS"
                );

            if(missing)
                return new LoadResult(
                    0,
                    LoadStatus.MISSING
                );

            int persistedAccessory=PetAccessoryPersistence.load(selection.username);
            int accessoryItem=accessoryAllowed.test(persistedAccessory)?persistedAccessory:0;
            System.out.println(tag+"V5131_PET_ACCESSORY_PERSIST_LOAD item="+
                (accessoryItem==0?"NONE":accessoryItem)+" authority=ACCOUNT_SEMANTIC_STATE");
            return new LoadResult(
                accessoryItem,
                LoadStatus.LOADED
            );
        }catch(Throwable e){
            System.err.println(tag+"V5123_ACCOUNT_LOAD_FAILED file="+LocalAccountProfiles.accountFile(selection.username)+
                " profile="+selection.username+" error="+e+" action=REJECT_SESSION");
            return new LoadResult(
                0,
                LoadStatus.FAILED
            );
        }
    }

    static PlayerSnapshot captureAndSave(
        String username,
        WorldPlayer player,
        PlayerRepository repository
    )throws java.io.IOException{
        return captureAndSave(
            username,
            player,
            repository,
            0
        );
    }

    static PlayerSnapshot captureAndSave(
        String username,
        WorldPlayer player,
        PlayerRepository repository,
        int activePetAccessoryItem
    )throws java.io.IOException{
        PlayerSnapshot snapshot=
            PlayerSnapshotCodec.capture(
                username,
                player,
                activePetAccessoryItem
            );

        repository.save(snapshot);
        return snapshot;
    }

    static void saveQuiet(
        String username,
        boolean persistent,
        WorldPlayer player,
        PlayerRepository repository,
        int activePetAccessoryItem,
        String tag,
        String reason
    ){
        if(!persistent)return;

        try{
            PlayerSnapshot snapshot=
                captureAndSave(
                    username,
                    player,
                    repository,
                    activePetAccessoryItem
                );

            System.out.println(
                tag+
                "V5123_ACCOUNT_SAVE reason="+
                reason+
                " repository="+
                repository.getClass().getSimpleName()+
                " snapshot="+snapshot+
                " petAccessory="+
                (activePetAccessoryItem==0
                    ?"NONE"
                    :activePetAccessoryItem)
            );
        }catch(Throwable e){
            System.err.println(
                tag+
                "V5123_ACCOUNT_SAVE_FAILED reason="+
                reason+
                " profile="+username+
                " repository="+
                repository.getClass().getSimpleName()+
                " error="+e
            );
        }
    }

    static void saveQuiet(
        String username,
        boolean persistent,
        BankState bank,
        EquipmentState equipment,
        MovementState movement,
        PetState petState,
        PlayerState playerState,
        int activePetAccessoryItem,
        String tag,
        String reason
    ){
        if(!persistent)return;

        try{
            String result=LocalAccountProfiles.save(username,bank,equipment,movement,petState,playerState);
            PetAccessoryPersistence.save(username,activePetAccessoryItem);
            System.out.println(tag+"V5123_ACCOUNT_SAVE reason="+reason+" "+result+
                " petAccessory="+(activePetAccessoryItem==0?"NONE":activePetAccessoryItem));
        }catch(Throwable e){
            System.err.println(tag+"V5123_ACCOUNT_SAVE_FAILED reason="+reason+
                " file="+LocalAccountProfiles.accountFile(username)+" profile="+username+" error="+e);
        }
    }

    static final class Selection{
        final String username;
        final boolean persistent;
        Selection(String username,boolean persistent){
            this.username=username;
            this.persistent=persistent;
        }
    }

    enum LoadStatus{
        NOT_PERSISTENT,
        MISSING,
        LOADED,
        FAILED
    }

    static final class LoadResult{
        final int accessoryItem;
        final LoadStatus status;
        final boolean loaded;
        final boolean missing;
        final boolean failed;

        LoadResult(
            int accessoryItem,
            LoadStatus status
        ){
            this.accessoryItem=accessoryItem;
            this.status=java.util.Objects.requireNonNull(
                status,
                "status"
            );
            this.loaded=
                status==LoadStatus.LOADED;
            this.missing=
                status==LoadStatus.MISSING;
            this.failed=
                status==LoadStatus.FAILED;
        }
    }

    private interface SnapshotSource {
        java.util.Optional<PlayerSnapshot> load(
            String username
        )throws java.io.IOException;

        String name();
    }

    private LocalAccountLifecycle(){}
}
