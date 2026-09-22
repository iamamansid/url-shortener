package com.iamamansid.urlshortener.repository;

import com.iamamansid.urlshortener.entity.ShortUrl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    boolean existsByCode(String code);

    void deleteByCode(String code);

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
