package spk.local;

public final class LocalCommandDispatcherTest {
    public static void main(String[] args)throws Exception{
        String clean=LocalCommandDispatcher.clean("  ::dev panel  ");
        if(!"dev panel".equals(clean))
            throw new AssertionError("command normalization changed: "+clean);

        String[] devPanel=LocalCommandDispatcher.tokens(clean);
        if(devPanel.length!=2||
           !"dev".equals(devPanel[0])||
           !"panel".equals(devPanel[1]))
            throw new AssertionError("dev panel tokenization changed");

        if(!LocalCommandDispatcher.isDevPanelRoute(devPanel))
            throw new AssertionError("dev panel alias not routed");
        if(!LocalCommandDispatcher.isDevPanelRoute(
            new String[]{"devpanel"}))
            throw new AssertionError("devpanel alias not routed");
        if(!LocalCommandDispatcher.isDevPanelRoute(
            new String[]{"devui"}))
            throw new AssertionError("devui alias not routed");
        if(!LocalCommandDispatcher.isDevPanelRoute(
            new String[]{"lab"}))
            throw new AssertionError("lab alias not routed");

        if(LocalCommandDispatcher.isDevPanelRoute(
            new String[]{"dev","info"}))
            throw new AssertionError("dev info must not be captured by panel route");
        if(LocalCommandDispatcher.isDevPanelRoute(
            new String[]{"regionload","12850"}))
            throw new AssertionError("unrelated command captured by panel route");

        if(!LocalCommandDispatcher.isMonsterSpawnerRoute(
                new String[]{"monsterspawner"})||
           !LocalCommandDispatcher.isMonsterSpawnerRoute(
                new String[]{"mspawn"}))
            throw new AssertionError(
                "Monster Spawner LocalLab command aliases not routed"
            );

        if(LocalCommandDispatcher.isMonsterSpawnerRoute(
                new String[]{"monsterspawner","extra"})||
           LocalCommandDispatcher.isMonsterSpawnerRoute(
                new String[]{"devpanel"}))
            throw new AssertionError(
                "Monster Spawner route captured unrelated command"
            );

        String untouched=LocalCommandDispatcher.clean("authority");
        if(!"authority".equals(untouched))
            throw new AssertionError("non-prefixed command changed");

        System.out.println(
            "LOCAL_COMMAND_DISPATCHER_PASS "+
            "normalization=true aliases=true unrelatedRejected=true "+
            "monsterSpawnerAliases=true monsterSpawnerExactRoute=true"
        );
    }
}
