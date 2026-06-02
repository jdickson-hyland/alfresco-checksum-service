package org.hyland.checksum;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

public class ChecksumBehavior implements ContentServicePolicies.OnContentUpdatePolicy {

    private static final Log logger = LogFactory.getLog(ChecksumBehavior.class);

    private PolicyComponent policyComponent;
    private ChecksumService checksumService;
    private NodeService nodeService;
    private boolean rejectDuplicates;

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setChecksumService(ChecksumService checksumService) {
        this.checksumService = checksumService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setRejectDuplicates(boolean rejectDuplicates) {
        this.rejectDuplicates = rejectDuplicates;
    }

    public void init() {
        policyComponent.bindClassBehaviour(
            ContentServicePolicies.OnContentUpdatePolicy.QNAME,
            ContentModel.TYPE_CONTENT,
            new JavaBehaviour(this, "onContentUpdate", Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
        logger.info("ChecksumBehavior registered: OnContentUpdatePolicy bound to cm:content "
            + "(rejectDuplicates=" + rejectDuplicates + ")");
    }

    @Override
    public void onContentUpdate(NodeRef nodeRef, boolean newContent) {
        logger.debug("onContentUpdate fired: nodeRef=" + nodeRef + ", newContent=" + newContent);

        if (!nodeService.exists(nodeRef)) {
            logger.debug("onContentUpdate skipped: node does not exist: " + nodeRef);
            return;
        }
        if (!StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(nodeRef.getStoreRef())) {
            logger.debug("onContentUpdate skipped: node is not in workspace SpacesStore: " + nodeRef);
            return;
        }

        logger.debug("Applying checksum to node: " + nodeRef);
        checksumService.applyChecksum(nodeRef);

        if (rejectDuplicates) {
            logger.debug("Checking for duplicates of node: " + nodeRef);
            List<NodeRef> duplicates = checksumService.findDuplicates(nodeRef);
            if (!duplicates.isEmpty()) {
                logger.info("Duplicate content detected for node " + nodeRef + ": " + duplicates);
                throw new AlfrescoRuntimeException(
                    "Duplicate content detected. Node " + nodeRef +
                    " has the same content as: " + duplicates +
                    ". Upload rejected because checksum.duplicates.reject=true."
                );
            }
        }
    }
}
