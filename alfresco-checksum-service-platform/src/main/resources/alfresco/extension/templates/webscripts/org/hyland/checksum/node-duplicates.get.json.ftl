{
    "nodeRef": "${nodeRef}",
    "checksum": "${checksum?json_string}",
    "algorithm": "${algorithm?json_string}",
    "duplicates": [
        <#list duplicates as dup>
        {
            "nodeRef": "${dup.nodeRef}",
            "name": "${dup.name?json_string}"
        }<#sep>,</#sep>
        </#list>
    ]
}
