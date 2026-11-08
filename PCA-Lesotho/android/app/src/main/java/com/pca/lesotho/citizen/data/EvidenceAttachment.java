package com.pca.lesotho.citizen.data;

public class EvidenceAttachment {
    public String uri;
    public String name;
    public String mimeType;
    public long sizeBytes;

    public EvidenceAttachment() {
    }

    public EvidenceAttachment(String uri, String name, String mimeType, long sizeBytes) {
        this.uri = uri;
        this.name = name;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
    }
}
