package org.hyland.checksum;

import org.alfresco.model.ContentModel;
import org.alfresco.rad.test.AbstractAlfrescoIT;
import org.alfresco.rad.test.AlfrescoTestRunner;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.nodelocator.CompanyHomeNodeLocator;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration tests for the checksum service.
 * Runs inside the ACS container via AlfrescoTestRunner.
 * Prerequisites: ./run.sh build_start_it_supported
 */
@RunWith(value = AlfrescoTestRunner.class)
public class ChecksumIT extends AbstractAlfrescoIT {

    @Test
    public void testChecksumAspectAppliedOnContentWrite() {
        NodeRef node = createContentNode("test-checksum-apply-" + System.currentTimeMillis() + ".txt",
            "Checksum IT test content");
        try {
            assertTrue("cs:checksumable aspect should be applied after content write",
                getServiceRegistry().getNodeService().hasAspect(node, ChecksumModel.ASPECT_CHECKSUMABLE));

            String checksum = (String) getServiceRegistry().getNodeService()
                .getProperty(node, ChecksumModel.PROP_CHECKSUM);
            String algorithm = (String) getServiceRegistry().getNodeService()
                .getProperty(node, ChecksumModel.PROP_CHECKSUM_ALGORITHM);

            assertNotNull("Checksum should not be null", checksum);
            assertFalse("Checksum should not be empty", checksum.isEmpty());
            assertEquals("SHA-256", algorithm);
            assertEquals("SHA-256 hex string should be 64 characters", 64, checksum.length());
        } finally {
            getServiceRegistry().getNodeService().deleteNode(node);
        }
    }

    @Test
    public void testValidateChecksumReturnsTrueForUnmodifiedContent() {
        NodeRef node = createContentNode("test-validate-" + System.currentTimeMillis() + ".txt",
            "Content for validation test");
        try {
            ChecksumService checksumService = (ChecksumService) getApplicationContext()
                .getBean("checksumService");
            assertTrue("Checksum should be valid for unmodified content",
                checksumService.validateChecksum(node));
        } finally {
            getServiceRegistry().getNodeService().deleteNode(node);
        }
    }

    @Test
    public void testDuplicateDetectionFindsMatchingNodes() {
        String sharedContent = "Shared duplicate content " + System.currentTimeMillis();
        NodeRef node1 = createContentNode("test-dup-1-" + System.currentTimeMillis() + ".txt", sharedContent);
        NodeRef node2 = createContentNode("test-dup-2-" + System.currentTimeMillis() + ".txt", sharedContent);
        try {
            ChecksumService checksumService = (ChecksumService) getApplicationContext()
                .getBean("checksumService");

            List<NodeRef> duplicatesOfNode1 = checksumService.findDuplicates(node1);
            assertTrue("node2 should appear as a duplicate of node1", duplicatesOfNode1.contains(node2));
            assertFalse("node1 should not list itself as a duplicate", duplicatesOfNode1.contains(node1));
        } finally {
            getServiceRegistry().getNodeService().deleteNode(node1);
            getServiceRegistry().getNodeService().deleteNode(node2);
        }
    }

    @Test
    public void testFindAllDuplicatesIncludesDuplicateGroup() {
        String sharedContent = "All-duplicates test content " + System.nanoTime();
        NodeRef node1 = createContentNode("test-all-dup-1-" + System.currentTimeMillis() + ".txt", sharedContent);
        NodeRef node2 = createContentNode("test-all-dup-2-" + System.currentTimeMillis() + ".txt", sharedContent);
        try {
            ChecksumService checksumService = (ChecksumService) getApplicationContext()
                .getBean("checksumService");
            String checksum = (String) getServiceRegistry().getNodeService()
                .getProperty(node1, ChecksumModel.PROP_CHECKSUM);
            assertNotNull("node1 should have a stored checksum", checksum);

            Map<String, List<NodeRef>> allDuplicates = checksumService.findAllDuplicates();
            assertTrue("findAllDuplicates should contain our test checksum group",
                allDuplicates.containsKey(checksum));

            List<NodeRef> group = allDuplicates.get(checksum);
            assertTrue("Duplicate group should contain node1", group.contains(node1));
            assertTrue("Duplicate group should contain node2", group.contains(node2));
        } finally {
            getServiceRegistry().getNodeService().deleteNode(node1);
            getServiceRegistry().getNodeService().deleteNode(node2);
        }
    }

    @Test
    public void testNoDuplicatesForUniqueContent() {
        NodeRef node = createContentNode("test-unique-" + System.currentTimeMillis() + ".txt",
            "Truly unique content: " + System.nanoTime());
        try {
            ChecksumService checksumService = (ChecksumService) getApplicationContext()
                .getBean("checksumService");
            List<NodeRef> duplicates = checksumService.findDuplicates(node);
            assertTrue("Unique content should have no duplicates", duplicates.isEmpty());
        } finally {
            getServiceRegistry().getNodeService().deleteNode(node);
        }
    }

    private NodeRef createContentNode(String name, String textContent) {
        NodeRef companyHome = getServiceRegistry().getNodeLocatorService()
            .getNode(CompanyHomeNodeLocator.NAME, null, null);

        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(ContentModel.PROP_NAME, name);

        ChildAssociationRef assoc = getServiceRegistry().getNodeService().createNode(
            companyHome,
            ContentModel.ASSOC_CONTAINS,
            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, QName.createValidLocalName(name)),
            ContentModel.TYPE_CONTENT,
            properties
        );

        NodeRef nodeRef = assoc.getChildRef();

        ContentWriter writer = getServiceRegistry().getContentService()
            .getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
        writer.setEncoding("UTF-8");
        writer.putContent(textContent);

        return nodeRef;
    }
}
