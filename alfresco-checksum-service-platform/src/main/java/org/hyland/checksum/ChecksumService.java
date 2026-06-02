package org.hyland.checksum;

import org.alfresco.service.cmr.repository.NodeRef;

import java.util.List;
import java.util.Map;

public interface ChecksumService {

    /**
     * Computes a hex-encoded digest of the node's content using the given algorithm.
     * Returns null if the node has no content.
     */
    String computeChecksum(NodeRef nodeRef, String algorithm);

    /**
     * Computes the checksum and stores it on the node via the cs:checksumable aspect.
     * Uses the configured default algorithm.
     */
    void applyChecksum(NodeRef nodeRef);

    /**
     * Returns all nodes in the workspace that share the same checksum as the given node,
     * excluding the node itself. Returns an empty list if the node has no stored checksum.
     */
    List<NodeRef> findDuplicates(NodeRef nodeRef);

    /**
     * Returns all duplicate groups in the workspace, keyed by checksum hex string.
     * Only groups with two or more nodes are included.
     */
    Map<String, List<NodeRef>> findAllDuplicates();

    /**
     * Recomputes the digest from current content and compares it to the stored checksum.
     * Returns false if the node has no stored checksum.
     */
    boolean validateChecksum(NodeRef nodeRef);
}
