package com.iamamansid.urlshortener.repository;

import com.iamamansid.urlshortener.entity.AppUser;
import com.iamamansid.urlshortener.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    void deleteByCode(String code);

    long countByOwner(AppUser owner);

    Page<ShortUrl> findByOwnerOrderByCreatedAtDesc(AppUser owner, Pageable pageable);

    /**
     * Links per UTC day since {@code since}. Each row is
     * {@code [java.sql.Date day, Long count]}.
     */
    @Query("SELECT FUNCTION('DATE', FUNCTION('timezone', 'UTC', s.createdAt)), COUNT(s) FROM ShortUrl s "
            + "WHERE s.createdAt >= :since GROUP BY FUNCTION('DATE', FUNCTION('timezone', 'UTC', s.createdAt))")
    List<Object[]> countByDay(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(s.clicks), 0) FROM ShortUrl s")
    long sumClicks();

    /**
     * Top links by click count with owners fetched, for the admin dashboard.
     */
    @Query("SELECT s FROM ShortUrl s LEFT JOIN FETCH s.owner ORDER BY s.clicks DESC")
    List<ShortUrl> findTopByClicks(Pageable pageable);

    /**
     * Atomically bumps the click counter. Used by the Kafka consumer so
     * concurrent batches never lose increments (no read-modify-write).
     *
     * @return number of rows updated (0 when the code no longer exists)
     */
    @Modifying
    @Query("UPDATE ShortUrl s SET s.clicks = s.clicks + :increment, s.lastClickedAt = :clickedAt WHERE s.code = :code")
    int incrementClicks(@Param("code") String code,
                        @Param("increment") long increment,
                        @Param("clickedAt") Instant clickedAt);
}
