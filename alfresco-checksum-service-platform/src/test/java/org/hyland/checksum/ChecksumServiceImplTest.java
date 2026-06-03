package org.hyland.checksum;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ChecksumServiceImplTest {

    // SHA-256 of "Hello, World!" (verified against java.security.MessageDigest output)
    private static final String HELLO_SHA256 = "dffd6021bb2bd5b0af676290809ec3a53191dd81c7f70a4b28688a362182986f";
    private static final byte[] HELLO_BYTES = "Hello, World!".getBytes();

    private ChecksumServiceImpl service;
    private NodeService nodeService;
    private ContentService contentService;
    private SearchService searchService;
    private NodeRef nodeRef;

    @Before
    public void setUp() {
        nodeService = Mockito.mock(NodeService.class);
        contentService = Mockito.mock(ContentService.class);
        searchService = Mockito.mock(SearchService.class);

        Properties props = new Properties();
        props.setProperty("checksum.algorithm", "SHA-256");

        service = new ChecksumServiceImpl();
        service.setNodeService(nodeService);
        service.setContentService(contentService);
        service.setSearchService(searchService);
        service.setGlobalProperties(props);
        service.init();

        nodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "test-node-id");
    }

    @Test
    public void testComputeChecksumKnownInput() throws Exception {
        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(HELLO_BYTES));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);

        String result = service.computeChecksum(nodeRef, "SHA-256");

        assertEquals(HELLO_SHA256, result);
    }

    @Test
    public void testComputeChecksumNoContent() {
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(null);

        String result = service.computeChecksum(nodeRef, "SHA-256");

        assertNull(result);
    }

    @Test
    public void testComputeChecksumEmptyContent() throws Exception {
        // SHA-256 of empty input
        String emptySha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);

        String result = service.computeChecksum(nodeRef, "SHA-256");

        assertEquals(emptySha256, result);
    }

    @Test
    public void testApplyChecksumAddsAspectAndProperties() throws Exception {
        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(HELLO_BYTES));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(nodeService.hasAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE)).thenReturn(false);

        service.applyChecksum(nodeRef);

        verify(nodeService).addAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE, null);
        verify(nodeService).addProperties(eq(nodeRef), Mockito.argThat((Map<org.alfresco.service.namespace.QName, Serializable> props) ->
            HELLO_SHA256.equals(props.get(ChecksumModel.PROP_CHECKSUM)) &&
            "SHA-256".equals(props.get(ChecksumModel.PROP_CHECKSUM_ALGORITHM)) &&
            props.containsKey(ChecksumModel.PROP_CHECKSUM_LAST_UPDATED)
        ));
    }

    @Test
    public void testApplyChecksumSkipsAspectIfAlreadyPresent() throws Exception {
        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(HELLO_BYTES));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(nodeService.hasAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE)).thenReturn(true);

        service.applyChecksum(nodeRef);

        verify(nodeService, Mockito.never()).addAspect(any(), any(), any());
    }

    @Test
    public void testValidateChecksumReturnsTrue() throws Exception {
        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream(HELLO_BYTES));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM)).thenReturn(HELLO_SHA256);
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM_ALGORITHM)).thenReturn("SHA-256");

        assertTrue(service.validateChecksum(nodeRef));
    }

    @Test
    public void testValidateChecksumReturnsFalseWhenMismatch() throws Exception {
        ContentReader reader = Mockito.mock(ContentReader.class);
        when(reader.exists()).thenReturn(true);
        when(reader.getContentInputStream()).thenReturn(new ByteArrayInputStream("Different content".getBytes()));
        when(contentService.getReader(nodeRef, ContentModel.PROP_CONTENT)).thenReturn(reader);
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM)).thenReturn(HELLO_SHA256);
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM_ALGORITHM)).thenReturn("SHA-256");

        assertFalse(service.validateChecksum(nodeRef));
    }

    @Test
    public void testValidateChecksumReturnsFalseWhenNoStoredChecksum() {
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM)).thenReturn(null);
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM_ALGORITHM)).thenReturn(null);

        assertFalse(service.validateChecksum(nodeRef));
    }

    @Test
    public void testFindDuplicatesReturnsEmptyListWhenNoChecksum() {
        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM)).thenReturn(null);

        List<NodeRef> result = service.findDuplicates(nodeRef);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testFindDuplicatesExcludesSelf() throws Exception {
        NodeRef otherNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "other-node-id");
        ResultSet results = Mockito.mock(ResultSet.class);

        when(nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM)).thenReturn(HELLO_SHA256);
        when(searchService.query(any())).thenReturn(results);
        when(results.getNodeRefs()).thenReturn(Arrays.asList(nodeRef, otherNode));

        List<NodeRef> duplicates = service.findDuplicates(nodeRef);

        assertEquals(1, duplicates.size());
        assertEquals(otherNode, duplicates.get(0));
        verify(results).close();
    }

    @Test
    public void testFindAllDuplicatesGroupsByChecksum() {
        NodeRef node1 = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "node-1");
        NodeRef node2 = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "node-2");
        NodeRef node3 = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "node-3");
        ResultSet results = Mockito.mock(ResultSet.class);

        when(searchService.query(any())).thenReturn(results);
        when(results.getNodeRefs()).thenReturn(Arrays.asList(node1, node2, node3));
        when(nodeService.getProperty(node1, ChecksumModel.PROP_CHECKSUM)).thenReturn("aabbcc");
        when(nodeService.getProperty(node2, ChecksumModel.PROP_CHECKSUM)).thenReturn("aabbcc");
        when(nodeService.getProperty(node3, ChecksumModel.PROP_CHECKSUM)).thenReturn("ddeeff");

        Map<String, List<NodeRef>> duplicates = service.findAllDuplicates();

        assertEquals(1, duplicates.size());
        assertTrue(duplicates.containsKey("aabbcc"));
        assertEquals(2, duplicates.get("aabbcc").size());
        verify(results).close();
    }
}
