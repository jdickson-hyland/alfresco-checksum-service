package org.hyland.checksum;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.search.QueryConsistency;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ChecksumServiceImpl implements ChecksumService {

    private static final Log logger = LogFactory.getLog(ChecksumServiceImpl.class);
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_SEARCH_ITEMS = 10000;

    private NodeService nodeService;
    private ContentService contentService;
    private SearchService searchService;
    private Properties globalProperties;
    private String algorithm;

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    public void setGlobalProperties(Properties globalProperties) {
        this.globalProperties = globalProperties;
    }

    public void init() {
        algorithm = globalProperties.getProperty("checksum.algorithm", "SHA-256");
    }

    @Override
    public String computeChecksum(NodeRef nodeRef, String algo) {
        logger.debug("computeChecksum: nodeRef=" + nodeRef + ", algorithm=" + algo);
        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null) {
            logger.debug("computeChecksum: no ContentReader for node " + nodeRef);
            return null;
        }
        if (!reader.exists()) {
            logger.debug("computeChecksum: ContentReader exists()=false for node " + nodeRef
                + " (contentUrl=" + reader.getContentUrl() + ")");
            return null;
        }
        logger.debug("computeChecksum: reading content from " + reader.getContentUrl()
            + " (" + reader.getSize() + " bytes)");
        try (InputStream is = reader.getContentInputStream()) {
            MessageDigest digest = MessageDigest.getInstance(algo);
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            String hex = bytesToHex(digest.digest());
            logger.debug("computeChecksum: computed " + algo + " = " + hex + " for node " + nodeRef);
            return hex;
        } catch (NoSuchAlgorithmException e) {
            throw new AlfrescoRuntimeException("Unsupported checksum algorithm: " + algo, e);
        } catch (IOException e) {
            throw new AlfrescoRuntimeException("Failed to read content for checksum computation on node: " + nodeRef, e);
        }
    }

    @Override
    public void applyChecksum(NodeRef nodeRef) {
        logger.debug("applyChecksum: start for node " + nodeRef + " using algorithm=" + algorithm);
        String checksum = computeChecksum(nodeRef, algorithm);
        if (checksum == null) {
            logger.info("applyChecksum: skipped node " + nodeRef + " (no readable content)");
            return;
        }
        if (!nodeService.hasAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE)) {
            logger.debug("applyChecksum: adding cs:checksumable aspect to node " + nodeRef);
            nodeService.addAspect(nodeRef, ChecksumModel.ASPECT_CHECKSUMABLE, null);
        }
        Map<org.alfresco.service.namespace.QName, Serializable> props = new HashMap<>();
        props.put(ChecksumModel.PROP_CHECKSUM, checksum);
        props.put(ChecksumModel.PROP_CHECKSUM_ALGORITHM, algorithm);
        props.put(ChecksumModel.PROP_CHECKSUM_LAST_UPDATED, new Date());
        nodeService.addProperties(nodeRef, props);
        logger.info("applyChecksum: applied " + algorithm + " checksum to node " + nodeRef + " = " + checksum);
    }

    @Override
    public List<NodeRef> findDuplicates(NodeRef nodeRef) {
        String checksum = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM);
        if (checksum == null) {
            return new ArrayList<>();
        }

        // Checksum is hex so injection is impossible; escaping is purely defensive
        String safeChecksum = checksum.replace("'", "\\'");
        String query = "SELECT cs.cmis:objectId FROM cs:checksumable cs WHERE cs.cs:checksum = '" + safeChecksum + "'";

        ResultSet results = null;
        try {
            results = executeQuery(query, SearchService.LANGUAGE_CMIS_ALFRESCO, MAX_SEARCH_ITEMS);
            List<NodeRef> duplicates = new ArrayList<>();
            for (NodeRef found : results.getNodeRefs()) {
                if (!found.equals(nodeRef)) {
                    duplicates.add(found);
                }
            }
            return duplicates;
        } finally {
            closeQuietly(results);
        }
    }

    @Override
    public Map<String, List<NodeRef>> findAllDuplicates() {
        String query = "SELECT cs.cmis:objectId FROM cs:checksumable cs";

        ResultSet results = null;
        try {
            results = executeQuery(query, SearchService.LANGUAGE_CMIS_ALFRESCO, MAX_SEARCH_ITEMS);
            Map<String, List<NodeRef>> grouped = new LinkedHashMap<>();
            for (NodeRef found : results.getNodeRefs()) {
                String checksum = (String) nodeService.getProperty(found, ChecksumModel.PROP_CHECKSUM);
                if (checksum != null) {
                    grouped.computeIfAbsent(checksum, k -> new ArrayList<>()).add(found);
                }
            }
            grouped.entrySet().removeIf(e -> e.getValue().size() < 2);
            return grouped;
        } finally {
            closeQuietly(results);
        }
    }

    @Override
    public boolean validateChecksum(NodeRef nodeRef) {
        String stored = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM);
        String algo = (String) nodeService.getProperty(nodeRef, ChecksumModel.PROP_CHECKSUM_ALGORITHM);
        if (stored == null || algo == null) {
            return false;
        }
        String computed = computeChecksum(nodeRef, algo);
        return stored.equals(computed);
    }

    private ResultSet executeQuery(String query, String language, int maxItems) {
        SearchParameters params = new SearchParameters();
        params.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        params.setLanguage(language);
        params.setQuery(query);
        params.setQueryConsistency(QueryConsistency.TRANSACTIONAL);
        params.setMaxItems(maxItems);
        return searchService.query(params);
    }

    private void closeQuietly(ResultSet results) {
        if (results != null) {
            try {
                results.close();
            } catch (Exception e) {
                logger.warn("Failed to close search ResultSet", e);
            }
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
