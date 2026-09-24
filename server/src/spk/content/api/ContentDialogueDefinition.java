package spk.content.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable semantic dialogue topology owned by content. */
public final class ContentDialogueDefinition {
    private final String dialogueKey;
    private final String startNodeKey;
    private final Map<String,ContentDialogueNode> nodes;

    public ContentDialogueDefinition(
        String dialogueKey,
        String startNodeKey,
        Collection<ContentDialogueNode> nodes
    ){
        this.dialogueKey=
            ContentDialogueNode.normalizeKey(
                dialogueKey,
                "dialogueKey"
            );
        this.startNodeKey=
            ContentDialogueNode.normalizeKey(
                startNodeKey,
                "startNodeKey"
            );

        Objects.requireNonNull(
            nodes,
            "nodes"
        );

        if(nodes.isEmpty())
            throw new IllegalArgumentException(
                "dialogue nodes empty"
            );

        LinkedHashMap<String,ContentDialogueNode>
            copy=new LinkedHashMap<>();

        for(ContentDialogueNode node:nodes){
            ContentDialogueNode checked=
                Objects.requireNonNull(
                    node,
                    "node"
                );

            if(copy.put(
                    checked.nodeKey(),
                    checked)!=null)
                throw new IllegalArgumentException(
                    "duplicate dialogue node "+
                    checked.nodeKey()
                );
        }

        if(!copy.containsKey(
                this.startNodeKey))
            throw new IllegalArgumentException(
                "missing start node "+
                this.startNodeKey
            );

        this.nodes=
            Collections.unmodifiableMap(
                copy
            );
    }

    public String dialogueKey(){
        return dialogueKey;
    }

    public String startNodeKey(){
        return startNodeKey;
    }

    public ContentDialogueNode node(
        String nodeKey
    ){
        return nodes.get(
            ContentDialogueNode.normalizeKey(
                nodeKey,
                "nodeKey"
            )
        );
    }

    public List<ContentDialogueNode> nodes(){
        return Collections.unmodifiableList(
            new ArrayList<>(
                nodes.values()
            )
        );
    }
}
