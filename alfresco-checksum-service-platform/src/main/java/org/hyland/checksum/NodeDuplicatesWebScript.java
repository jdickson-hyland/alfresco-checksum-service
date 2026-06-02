package org.hyland.checksum;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /checksum/node/{store_type}/{store_id}/{id}/duplicates
 * Returns all nodes that share the same checksum as the specified node.
 */
public class NodeDuplicatesWebScript extends DeclarativeWebScript {

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
        Map<String, String> vars = req.getServiceMatch().getTemplateVars();
        String storeType = vars.get("store_type");
        String storeId = vars.get("store_id");
        String id = vars.get("id");

        NodeRef nodeRef = new NodeRef(new StoreRef(storeType, storeId), id);

        if (!nodeService.exists(nodeRef)) {
            status.setCode(Status.STATUS_NOT_FOUND);
            status.setMessage("Node not found: " + nodeRef);
            status.setRedirect(true);
            return null;
        }

        if (!nodeService.hasAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE)) {
            status.setCode(Status.STATUS_BAD_REQUEST);
            status.setMessage("Node does not have the cs:checksumable aspect applied. " +
                "Checksum has not been computed for this node.");
            status.setRedirect(true);
            return null;
        }

        String checksum = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM);
        String algorithm = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM_ALGORITHM);

        List<NodeRef> duplicates = checksumService.findDuplicates(nodeRef);
        List<Map<String, Object>> duplicateList = new ArrayList<>();
        for (NodeRef dup : duplicates) {
            Map<String, Object> info = new HashMap<>();
            info.put("nodeRef", dup.toString());
            info.put("name", nodeService.getProperty(dup, ContentModel.PROP_NAME));
            duplicateList.add(info);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("nodeRef", nodeRef.toString());
        model.put("checksum", checksum != null ? checksum : "");
        model.put("algorithm", algorithm != null ? algorithm : "");
        model.put("duplicates", duplicateList);
        return model;
    }
}
