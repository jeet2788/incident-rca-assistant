package com.rca.repository;

import com.rca.model.IncidentEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentEmbeddingRepository extends JpaRepository<IncidentEmbedding, UUID> {

    /**
     * Finds the top-K most similar incidents across all services using cosine distance.
     * The cast ::vector is required for pgvector to accept the string parameter.
     */
    @Query(value = """
        SELECT * FROM incident_embeddings
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<IncidentEmbedding> findSimilar(
        @Param("queryVector") String queryVector,
        @Param("topK") int topK
    );

    /**
     * Same as above but scoped to a specific service for more relevant results.
     */
    @Query(value = """
        SELECT * FROM incident_embeddings
        WHERE service = :service
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<IncidentEmbedding> findSimilarByService(
        @Param("queryVector") String queryVector,
        @Param("service") String service,
        @Param("topK") int topK
    );
}
