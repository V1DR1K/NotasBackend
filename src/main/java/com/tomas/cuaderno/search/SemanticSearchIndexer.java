package com.tomas.cuaderno.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SemanticSearchIndexer {
    private static final Logger log = LoggerFactory.getLogger(SemanticSearchIndexer.class);
    private static final int CLAIM_SIZE = 5;
    private static final int MAX_BATCH_SIZE = 100;
    private static final int CHUNK_SIZE = 4200;
    private static final int CHUNK_OVERLAP = 300;
    private final JdbcTemplate jdbc;
    private final GeminiEmbeddingService embeddings;
    private final TransactionTemplate transactions;

    public SemanticSearchIndexer(
            JdbcTemplate jdbc,
            GeminiEmbeddingService embeddings,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 5000)
    public void indexPendingRecords() {
        if (!embeddings.isConfigured()) return;
        List<QueueRecord> claimed = claimRecords();
        if (claimed.isEmpty()) return;
        for (QueueRecord record : claimed) {
            try {
                index(record);
            } catch (Exception exception) {
                retry(record);
                log.warn("Semantic search indexing failed for source {} ({}); it will be retried",
                        record.sourceType(), exception.getClass().getSimpleName());
            }
        }
    }

    private List<QueueRecord> claimRecords() {
        return transactions.execute(status -> jdbc.query("""
                WITH ready AS (
                    SELECT id
                    FROM semantic_search_queue
                    WHERE next_attempt_at <= now()
                      AND (claimed_at IS NULL OR claimed_at < now() - interval '10 minutes')
                    ORDER BY requested_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE semantic_search_queue queue
                SET claimed_at = now()
                FROM ready
                WHERE queue.id = ready.id
                RETURNING queue.id, queue.owner_id, queue.source_type, queue.source_id,
                          queue.title, queue.detail, queue.content, queue.record_date,
                          queue.content_hash, queue.requested_at, queue.claimed_at
                """, (result, rowNum) -> mapRecord(result), CLAIM_SIZE));
    }

    private QueueRecord mapRecord(ResultSet row) throws SQLException {
        return new QueueRecord(
                row.getLong("id"),
                row.getObject("owner_id", UUID.class),
                row.getString("source_type"),
                row.getObject("source_id", UUID.class),
                row.getString("title"),
                row.getString("detail"),
                row.getString("content"),
                row.getObject("record_date", LocalDate.class),
                row.getString("content_hash"),
                row.getTimestamp("requested_at").toInstant(),
                row.getTimestamp("claimed_at").toInstant());
    }

    private void index(QueueRecord record) {
        List<String> chunks = split(record.content());
        List<float[]> vectors = new ArrayList<>(chunks.size());
        for (int start = 0; start < chunks.size(); start += MAX_BATCH_SIZE) {
            int end = Math.min(start + MAX_BATCH_SIZE, chunks.size());
            vectors.addAll(embeddings.embed(chunks.subList(start, end), "RETRIEVAL_DOCUMENT"));
        }
        if (chunks.size() != vectors.size()) throw new IllegalStateException("Embedding count does not match chunks");

        transactions.executeWithoutResult(status -> {
            Boolean current = jdbc.query("""
                    SELECT requested_at = ? AND claimed_at = ?
                    FROM semantic_search_queue
                    WHERE id = ?
                    FOR UPDATE
                    """, result -> result.next() && result.getBoolean(1),
                    Timestamp.from(record.requestedAt()), Timestamp.from(record.claimedAt()), record.id());
            if (!Boolean.TRUE.equals(current)) return;

            jdbc.update("""
                    DELETE FROM semantic_search_documents
                    WHERE owner_id = ? AND source_type = ? AND source_id = ?
                    """, record.ownerId(), record.sourceType(), record.sourceId());
            for (int i = 0; i < chunks.size(); i++) {
                jdbc.update("""
                        INSERT INTO semantic_search_documents
                            (owner_id, source_type, source_id, chunk_number, title, detail, record_date, content_hash, content, embedding)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
                        """,
                        record.ownerId(), record.sourceType(), record.sourceId(), i,
                        record.title(), record.detail(), record.recordDate(), record.contentHash(),
                        chunks.get(i), vectorLiteral(vectors.get(i)));
            }
            jdbc.update("DELETE FROM semantic_search_queue WHERE id = ?", record.id());
        });
    }

    private void retry(QueueRecord record) {
        transactions.executeWithoutResult(status -> jdbc.update("""
                UPDATE semantic_search_queue
                SET claimed_at = NULL,
                    attempts = attempts + 1,
                    next_attempt_at = now() + make_interval(secs => LEAST(3600, 5 * power(2, LEAST(attempts, 10)))::INTEGER)
                WHERE id = ? AND requested_at = ? AND claimed_at = ?
                """, record.id(), Timestamp.from(record.requestedAt()), Timestamp.from(record.claimedAt())));
    }

    private List<String> split(String content) {
        String value = content == null || content.isBlank() ? " " : content;
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < value.length()) {
            int end = Math.min(start + CHUNK_SIZE, value.length());
            if (end < value.length()) {
                int boundary = value.lastIndexOf(' ', end);
                if (boundary > start + CHUNK_SIZE / 2) end = boundary;
            }
            chunks.add(value.substring(start, end).trim());
            if (end >= value.length()) break;
            start = Math.max(start + 1, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private String vectorLiteral(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) result.append(',');
            result.append(vector[i]);
        }
        return result.append(']').toString();
    }

    private record QueueRecord(
            long id,
            UUID ownerId,
            String sourceType,
            UUID sourceId,
            String title,
            String detail,
            String content,
            LocalDate recordDate,
            String contentHash,
            Instant requestedAt,
            Instant claimedAt) {}
}
