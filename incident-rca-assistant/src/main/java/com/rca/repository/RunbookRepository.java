package com.rca.repository;

import com.rca.model.Runbook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunbookRepository extends JpaRepository<Runbook, UUID> {

    /**
     * Finds the top-K most relevant runbooks across all services using cosine distance.
     */
    @Query(value = """
        SELECT * FROM runbooks
        WHERE embedding IS NOT NULL
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Runbook> findSimilar(
        @Param("queryVector") String queryVector,
        @Param("topK") int topK
    );

    /**
     * Same as above but scoped to a specific service.
     */
    @Query(value = """
        SELECT * FROM runbooks
        WHERE embedding IS NOT NULL AND service = :service
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<Runbook> findSimilarByService(
        @Param("queryVector") String queryVector,
        @Param("service") String service,
        @Param("topK") int topK
    );

    List<Runbook> findByService(String service);
}
