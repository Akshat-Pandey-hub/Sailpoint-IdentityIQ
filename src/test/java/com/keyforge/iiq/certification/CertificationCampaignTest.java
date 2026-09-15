package com.keyforge.iiq.certification;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused tests for certification-campaign extraction: parsing of the real live
 * {@code rest/certificationGroups} response, mapping, canonical UUID normalization, epoch-millis →
 * UTC timestamp conversion, and NULL handling for fields the source does not expose.
 */
class CertificationCampaignTest {

    /** Verbatim body captured live from POST /identityiq/rest/certificationGroups (2 real campaigns). */
    private static final String LIVE_JSON = "{\"status\":\"success\",\"requestID\":null,\"warnings\":null,"
            + "\"errors\":null,\"retryWait\":0,\"metaData\":null,\"attributes\":null,\"objects\":["
            + "{\"id\":\"7f000101a08f1fbf81a0a5d0e07b2818\",\"name\":\"Kf_Test Target Cert\","
            + "\"ownerDisplayName\":\"Alexander Walker\",\"status\":\"Active\","
            + "\"percentComplete\":\"0% (0 of 1)\",\"created\":1789488324731,\"tags\":[]},"
            + "{\"id\":\"7f000101a08f1fbf81a0a5bc304227cf\",\"name\":\"KF Test Targeted Cert\","
            + "\"ownerDisplayName\":\"Molly J\",\"status\":\"Active\","
            + "\"percentComplete\":\"0% (0 of 1)\",\"created\":1789486968898,\"tags\":[]}"
            + "],\"count\":2,\"complete\":true,\"success\":true,\"retry\":false,\"failure\":false}";

    private final CertificationCampaignService service = new CertificationCampaignService(null);

    @Test
    void parsesLiveResponseFieldsExactly() {
        List<CertificationCampaign> campaigns = service.parseCampaigns(LIVE_JSON);
        assertEquals(2, campaigns.size());
        assertEquals(2, service.parseTotal(LIVE_JSON));

        CertificationCampaign molly = campaigns.get(1);
        assertEquals("7f000101a08f1fbf81a0a5bc304227cf", molly.id());
        assertEquals("KF Test Targeted Cert", molly.name());
        assertEquals("Molly J", molly.ownerDisplayName());
        assertEquals("Active", molly.status());
        assertEquals("0% (0 of 1)", molly.percentComplete());
        assertEquals(1789486968898L, molly.created());
        assertEquals("[]", molly.tagsJson());
    }

    @Test
    void mapsCampaignToRowWithCanonicalUuidAndRawSource() {
        CertificationCampaign c = service.parseCampaigns(LIVE_JSON).get(1);
        CertificationCampaignRow row = CertificationCampaignRowMapper.map(c);

        // 32-hex id becomes the canonical dashed UUID; the raw id is preserved.
        assertEquals("7f000101-a08f-1fbf-81a0-a5bc304227cf", row.campaignid());
        assertEquals("7f000101a08f1fbf81a0a5bc304227cf", row.sourceId());
        assertEquals("KF Test Targeted Cert", row.name());
        assertEquals("Molly J", row.ownerDisplayName());
        assertEquals("Active", row.status());
        assertEquals("0% (0 of 1)", row.percentComplete());
        assertEquals("[]", row.tagsJson());
    }

    @Test
    void convertsCreatedEpochMillisToUtcInstant() {
        LocalDateTime createdAt = CertificationCampaignRowMapper.epochMillisToUtc(1789486968898L);
        // The stored wall-clock, read back at UTC, must equal the exact source instant.
        assertEquals(Instant.ofEpochMilli(1789486968898L), createdAt.toInstant(ZoneOffset.UTC));
    }

    @Test
    void canonicalUuidHandlesDashedBracedAndRejectsGarbage() {
        assertEquals("7f000101-a08f-1fbf-81a0-a5bc304227cf",
                CertificationCampaignRowMapper.toCanonicalUuid("7f000101a08f1fbf81a0a5bc304227cf"));
        assertEquals("7f000101-a08f-1fbf-81a0-a5bc304227cf",
                CertificationCampaignRowMapper.toCanonicalUuid("{7f000101a08f1fbf81a0a5bc304227cf}"));
        assertEquals("7f000101-a08f-1fbf-81a0-a5bc304227cf",
                CertificationCampaignRowMapper.toCanonicalUuid("7f000101-a08f-1fbf-81a0-a5bc304227cf"));
        assertThrows(CertificationCampaignMappingException.class,
                () -> CertificationCampaignRowMapper.toCanonicalUuid("not-a-guid"));
        assertThrows(CertificationCampaignMappingException.class,
                () -> CertificationCampaignRowMapper.toCanonicalUuid(" "));
    }

    @Test
    void nullAndUnavailableFieldsAreNotFabricated() {
        // A campaign with only an id: everything else absent -> NULL, created -> null, tags -> null.
        List<CertificationCampaign> parsed = service.parseCampaigns(
                "{\"objects\":[{\"id\":\"7f000101a08f1fbf81a0a5bc304227cf\"}],\"count\":1}");
        assertEquals(1, parsed.size());
        CertificationCampaign c = parsed.get(0);
        assertNull(c.name());
        assertNull(c.ownerDisplayName());
        assertNull(c.status());
        assertNull(c.percentComplete());
        assertNull(c.created());
        assertNull(c.tagsJson());

        CertificationCampaignRow row = CertificationCampaignRowMapper.map(c);
        assertEquals("7f000101-a08f-1fbf-81a0-a5bc304227cf", row.campaignid());
        assertNull(row.name());
        assertNull(row.createdAt());
        assertNull(row.tagsJson());
    }

    @Test
    void emptyResponseIsValidEmpty() {
        String empty = "{\"status\":\"success\",\"objects\":[],\"count\":0}";
        assertTrue(service.parseCampaigns(empty).isEmpty());
        assertEquals(0, service.parseTotal(empty));
    }

    @Test
    void repositoryRespectsPgSchema() {
        CertificationCampaignRepository repo = new CertificationCampaignRepository("iiq_migration_final");
        assertEquals("iiq_migration_final", repo.schema());
        assertEquals("iiq_migration_final.kf_certification_campaign", repo.targetTable());
    }
}
