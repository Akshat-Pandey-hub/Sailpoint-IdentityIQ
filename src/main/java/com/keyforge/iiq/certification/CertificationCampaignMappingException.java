package com.keyforge.iiq.certification;

/** Thrown when a {@link CertificationCampaign} cannot be mapped to a persistable row. */
public class CertificationCampaignMappingException extends RuntimeException {

    public CertificationCampaignMappingException(String message) {
        super(message);
    }
}
