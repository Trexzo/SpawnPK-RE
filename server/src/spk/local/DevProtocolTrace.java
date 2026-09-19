package spk.local;

import java.util.*;

/**
 * Small session-only trace buffer for Dev Authority Workbench operations.
 * This is deliberately semantic rather than a raw packet sniffer: it records
 * request -> route -> state -> publication summaries and never persists them.
 */
final class DevProtocolTrace {
    private static final int MAX_ENTRIES=128;
    private final ArrayDeque<String> entries=new ArrayDeque<>();
    private boolean enabled;
    private long sequence;

    boolean enabled(){ return enabled; }
    void setEnabled(boolean value){ enabled=value; }

    void record(String action,String chain,String authority){
        if(!enabled) return;
        String a=action==null?"UNKNOWN":action;
        String c=chain==null?"":chain;
        String au=authority==null?"UNSPECIFIED":authority;
        String line=String.format(Locale.ROOT,"#%04d action=%s chain=%s authority=%s",++sequence,a,c,au);
        entries.addLast(line);
        while(entries.size()>MAX_ENTRIES) entries.removeFirst();
    }

    void clear(){ entries.clear(); }

    List<String> snapshot(int limit){
        int n=Math.max(1,Math.min(MAX_ENTRIES,limit));
        ArrayList<String> all=new ArrayList<>(entries);
        int from=Math.max(0,all.size()-n);
        return new ArrayList<>(all.subList(from,all.size()));
    }

    int size(){ return entries.size(); }
    String summary(){ return "trace="+(enabled?"ON":"OFF")+" entries="+entries.size()+" max="+MAX_ENTRIES; }
}
