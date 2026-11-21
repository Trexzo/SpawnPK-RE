package spk.local;

/** R104 OFFLINE ONLY: run the existing R103 suite plus the World-backed
 * repeating-plugin cancellation, final-capture, persistence and retirement
 * regression. No LocalSession, World.shared or plugin API integration. */
public final class R104OfflineRepeatingPluginRetirementTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)
            throw new IllegalArgumentException("R104 unexpected arguments");
        R103OfflineOwnedPluginTaskRetirementTest.main(new String[0]);
        R103OfflineOwnedPluginTaskRetirementTest.runR104RepeatingForTest();
        System.out.println(
            "R104_OFFLINE_REPEATING_PLUGIN_RETIREMENT_PASS "+
            "R103OneShotPluginRetirementInherited=true "+
            "positiveRepeatsRunUnderWorldOwnerLock=true "+
            "cancelledRecurringTaskCannotRequeue=true "+
            "postFinalOwnedTaskEnqueueRefused=true "+
            "wrongGenerationOwnedRepeatRefused=true "+
            "untaggedPublicRepeatRejectedOnlyInStrictTestWorld=true "+
            "queuedBeforeFinalRepeatRejectedAtDispatch=true "+
            "rejectedRepeatHandleRetired=true "+
            "R72R89R90FinalSaveAndR96WitnessCommitted=true "+
            "noProductionPluginMigrationOrDeployment=true");
    }
    private R104OfflineRepeatingPluginRetirementTest(){}
}
