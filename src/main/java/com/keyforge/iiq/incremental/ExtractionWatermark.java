package com.keyforge.iiq.incremental;

import java.time.Instant;

/**
 * One persisted incremental watermark: the newest authoritative <b>source-side</b> change time that
 * has been fully and successfully captured for an entity. It is a position in the <i>source's</i>
 * change history — never our processing time. {@code extracted_at} and PostgreSQL insert/update
 * timestamps are deliberately not used as watermarks.
 *
 * @param entity          the logical entity / target table (e.g. {@code kf_task_result})
 * @param watermarkField  the source field the watermark tracks (e.g. {@code meta.lastModified}),
 *                        recorded for transparency/auditing only
 * @param sourceWatermark the high-water source change time captured so far ({@code null} = never run)
 * @param lastRunId       the {@code kf_extraction_run} id that last advanced it (may be {@code null})
 */
public record ExtractionWatermark(String entity, String watermarkField, Instant sourceWatermark, String lastRunId) {
}
