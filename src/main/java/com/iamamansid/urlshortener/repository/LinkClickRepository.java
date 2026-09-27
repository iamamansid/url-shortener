package com.iamamansid.urlshortener.repository;

import com.iamamansid.urlshortener.entity.LinkClick;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LinkClickRepository extends JpaRepository<LinkClick, Long> {

    /**
     * Clicks per UTC day since {@code since}. Each row is
     * {@code [java.sql.Date day, Long count]}.
     */
    @Query("SELECT FUNCTION('DATE', FUNCTION('timezone', 'UTC', c.clickedAt)), COUNT(c) FROM LinkClick c "
            + "WHERE c.clickedAt >= :since GROUP BY FUNCTION('DATE', FUNCTION('timezone', 'UTC', c.clickedAt))")
    List<Object[]> countByDay(@Param("since") Instant since);
}
