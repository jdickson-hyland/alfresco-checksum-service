{
    "duplicateGroups": [
        <#list duplicateGroups as group>
        {
            "checksum": "${group.checksum}",
            "algorithm": "${group.algorithm?json_string}",
            "count": ${group.count},
            "nodes": [
                <#list group.nodes as node>
                {
                    "nodeRef": "${node.nodeRef}",
                    "name": "${node.name?json_string}"
                }<#sep>,</#sep>
                </#list>
            ]
        }<#sep>,</#sep>
        </#list>
    ]
}
