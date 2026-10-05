package spk.local;

public final class PlayerDeadRequestFenceTest {
    public static void main(String[] args){
        WorldPlayer player=new WorldPlayer();

        ClientRequestMetadata meta=
            ClientRequestMetadata.exactCurrent(
                1,
                "dead-request-fence",
                "G1_TEST"
            );

        CommandClientRequest command=
            new CommandClientRequest(
                "::item 4151",
                meta
            );

        MovementClientRequest movement=
            new MovementClientRequest(
                new MovementRequest(
                    164,
                    false,
                    new int[]{MovementState.INITIAL_X+1},
                    new int[]{MovementState.INITIAL_Y},
                    new byte[0]
                ),
                meta
            );

        InterfaceCloseClientRequest close=
            new InterfaceCloseClientRequest(meta);
        PublicChatClientRequest publicChat=
            new PublicChatClientRequest(
                0,
                0,
                "dead but chatting",
                meta
            );
        PrivateMessageClientRequest privateMessage=
            new PrivateMessageClientRequest(
                1L,
                "pm",
                meta
            );
        ChatModeClientRequest chatMode=
            new ChatModeClientRequest(
                0,
                0,
                0,
                meta
            );
        ReportAbuseClientRequest report=
            new ReportAbuseClientRequest(
                1L,
                0,
                0,
                meta
            );
        SocialListClientRequest social=
            new SocialListClientRequest(
                SocialListClientRequest.Action.ADD_FRIEND,
                1L,
                meta
            );

        require(
            !LocalPendingRequestDispatcher.rejectWhileDead(
                player,
                command
            )&&
            !LocalPendingRequestDispatcher.rejectWhileDead(
                player,
                movement
            ),
            "alive player incorrectly fenced"
        );

        PlayerLifecycleService.DamageResult lethal=
            new PlayerLifecycleService(
                player,
                "CUSTOM_LOCALLAB_G1_DEAD_REQUEST_FENCE_TEST"
            ).applyDamage(
                500,
                50L,
                "g1-dead-request-fence",
                5L
            );

        require(
            lethal.died&&
            player.lifecycle().dead(),
            "death fixture"
        );

        require(
            LocalPendingRequestDispatcher.rejectWhileDead(
                player,
                command
            ),
            "dead command was not fenced"
        );
        require(
            LocalPendingRequestDispatcher.rejectWhileDead(
                player,
                movement
            ),
            "dead movement was not fenced"
        );

        for(ClientRequest safe:new ClientRequest[]{
                close,
                publicChat,
                privateMessage,
                chatMode,
                report,
                social
        })
            require(
                !LocalPendingRequestDispatcher.rejectWhileDead(
                    player,
                    safe
                ),
                "safe dead request fenced "+
                safe.getClass().getSimpleName()
            );

        require(
            !LocalPendingRequestDispatcher.allowedWhileDead(
                command
            )&&
            !LocalPendingRequestDispatcher.allowedWhileDead(
                movement
            )&&
            LocalPendingRequestDispatcher.allowedWhileDead(
                publicChat
            )&&
            LocalPendingRequestDispatcher.allowedWhileDead(
                social
            ),
            "dead request classifier drift"
        );

        System.out.println(
            "PLAYER_DEAD_REQUEST_FENCE_PASS "+
            "commandsBlocked=true "+
            "movementBlocked=true "+
            "economicGameplayBlockedByDefault=true "+
            "interfaceCloseAllowed=true "+
            "chatAllowed=true "+
            "socialAllowed=true "+
            "reportAllowed=true "+
            "deathLootEscapeBlocked=true "+
            "authority=CUSTOM_LOCALLAB_G1_DEATH_FREEZE"
        );
    }

    private static void require(
        boolean condition,
        String label
    ){
        if(!condition)
            throw new AssertionError(label);
    }

    private PlayerDeadRequestFenceTest(){}
}
