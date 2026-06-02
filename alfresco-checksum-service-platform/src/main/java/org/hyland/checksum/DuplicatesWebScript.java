package org.hyland.checksum;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /checksum/duplicates
 * Returns all duplicate content groups across the entire workspace.
 */
public class DuplicatesWebScript extends DeclarativeWebScript {

    private ChecksumService checksumService;
    private NodeService nodeService;

    public void setChecksumService(ChecksumService checksumService) {
        this.checksumService = checksumService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        Map<String, List<NodeRef>> duplicates = checksumService.findAllDuplicates();

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<NodeRef>> entry : duplicates.entrySet()) {
            String checksum = entry.getKey();
            List<NodeRef> nodes = entry.getValue();

            String algo = (String) nodeService.getProperty(nodes.get(0), ChecksumModel.PROP_CHECKSUM_ALGORITHM);

            List<Map<String, Object>> nodeList = new ArrayList<>();
            for (NodeRef nodeRef : nodes) {
                nodeList.add(buildNodeInfo(nodeRef));
            }

            Map<String, Object> group = new HashMap<>();
            group.put("checksum", checksum);
            group.put("algorithm", algo != null ? algo : "");
            group.put("count", nodes.size());
            group.put("nodes", nodeList);
            groups.add(group);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("duplicateGroups", groups);
        return model;
    }

    private Map<String, Object> buildNodeInfo(NodeRef nodeRef) {
        Map<String, Object> info = new HashMap<>();
        info.put("nodeRef", nodeRef.toString());
        info.put("name", nodeService.getProperty(nodeRef, ContentModel.PROP_NAME));
        return info;
    }
}
