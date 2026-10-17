package spk.local;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/** G21.371–373: provider-based strict pre-ACTIVE negative admission. */
final class G21371StrictPreActiveLinkProviderFixture {
    enum Scenario { ABSENT, SELECTED_ALIAS, FOREIGN_ALIAS }
    private G21371StrictPreActiveLinkProviderFixture() {}
    private static void check(boolean yes,String why){
        if(!yes)throw new AssertionError("G21.371-373 "+why);
    }
    static void run(Scenario scenario)throws Exception {
        Path root=Files.createTempDirectory("g21371-strict-pre-active-");
        try {
            Path selected=root.resolve("selected.properties");
            Path active=LocalDurableAccountReviewIntentPrimitive
                .markerPath(selected);
            Path other=root.resolve("foreign.properties");
            Path alias=root.resolve("foreign-or-selected-alias");
            if(scenario!=Scenario.ABSENT)
                Files.write(selected,new byte[]{1,2,3,4},
                    StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
            if(scenario==Scenario.SELECTED_ALIAS){
                Files.createLink(alias,selected);
                check(Files.isSameFile(alias,selected),
                    "selected alias not installed");
            }else if(scenario==Scenario.FOREIGN_ALIAS){
                Files.write(other,new byte[]{5,6,7,8});
                Files.createLink(alias,other);
                check(Files.isSameFile(alias,other),
                    "unrelated file alias not installed");
            }
            final boolean provider;
            try {
                Object value=Files.getAttribute(root,"unix:nlink",
                    LinkOption.NOFOLLOW_LINKS);
                provider=value instanceof Number &&
                    ((Number)value).longValue()>=1L;
            }catch(UnsupportedOperationException|
                    IllegalArgumentException unavailable){
                // The hosted Windows Java provider exposes no unix:nlink.
                // Denial before one-shot ACTIVE marker is REQUIRED here.
                IOException failure=null;
                try {
                    LocalFinalSaveNegativeWitness
                        .requirePreAdmissionLinkCountCapability(selected);
                }catch(IOException e){ failure=e; }
                check(failure!=null &&
                    String.valueOf(failure.getMessage()).contains(
                        "G21.371 STRICT_LINK_PROVIDER_UNAVAILABLE_BEFORE_ACTIVE"),
                    "unsupported provider was not denied early: "+failure);
                check(!Files.exists(active,LinkOption.NOFOLLOW_LINKS),
                    "early unavailable provider generated ACTIVE");
                System.out.println("G"+id(scenario)+
                    "_PRE_ACTIVE_LINK_PROVIDER_PASS unixNlinkSupported=false"+
                    " strictAdmissionDeniedBeforeActive=true"+
                    " noAuthenticatedReceipt=true");
                return;
            }
            IOException failure=null;
            try {
                LocalFinalSaveNegativeWitness
                    .requirePreAdmissionLinkCountCapability(selected);
            }catch(IOException e){ failure=e; }
            if(provider&&scenario!=Scenario.SELECTED_ALIAS) {
                check(failure==null,
                    "valid exact selected evidence refused: "+failure);
            } else {
                check(failure!=null,
                    "unproven provider or selected alias admitted");
                if(provider)
                    check(String.valueOf(failure.getMessage()).contains(
                        "G21.372 STRICT_PRE_ADMISSION_ACCOUNT_ALIAS_NEGATIVE"),
                        "wrong selected-alias veto: "+failure);
            }
            check(!Files.exists(active,LinkOption.NOFOLLOW_LINKS),
                "preflight itself must never publish a marker");
            if(scenario==Scenario.FOREIGN_ALIAS)
                check(Files.isSameFile(other,alias),
                    "preflight touched foreign alias");
            if(scenario==Scenario.ABSENT)
                check(!Files.exists(selected,LinkOption.NOFOLLOW_LINKS),
                    "first-time account was created during preflight");
            System.out.println("G"+id(scenario)+
                "_PRE_ACTIVE_LINK_PROVIDER_PASS unixNlinkSupported="+provider+
                " selectedAliasRefused="+(scenario==Scenario.SELECTED_ALIAS)+
                " foreignIsolation="+(scenario==Scenario.FOREIGN_ALIAS)+
                " noAuthenticatedReceipt=true");
        }finally {
            try(Stream<Path> paths=Files.walk(root)){
                for(Path p:paths.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new))Files.deleteIfExists(p);
            }
        }
    }
    private static String id(Scenario s){
        return s==Scenario.ABSENT?"21371":
            s==Scenario.SELECTED_ALIAS?"21372":"21373";
    }
}
