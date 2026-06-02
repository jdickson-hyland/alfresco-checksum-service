package org.hyland.checksum;

import org.alfresco.service.namespace.QName;

public final class ChecksumModel {

    public static final String NAMESPACE_URI = "http://www.hyland.org/model/checksum/1.0";

    public static final QName ASPECT_CHECKSUMABLE = QName.createQName(NAMESPACE_URI, "checksumable");
    public static final QName PROP_CHECKSUM = QName.createQName(NAMESPACE_URI, "checksum");
    public static final QName PROP_CHECKSUM_ALGORITHM = QName.createQName(NAMESPACE_URI, "checksumAlgorithm");
    public static final QName PROP_CHECKSUM_LAST_UPDATED = QName.createQName(NAMESPACE_URI, "checksumLastUpdated");

    private ChecksumModel() {}
}
