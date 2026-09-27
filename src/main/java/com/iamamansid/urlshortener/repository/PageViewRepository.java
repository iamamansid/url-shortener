package com.iamamansid.urlshortener.repository;

import com.iamamansid.urlshortener.entity.PageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PageViewRepository extends JpaRepository<PageView, Long> {

    /**
     * Homepage views per UTC day since {@code since}. Each row is
     * {@code [java.sql.Date day, Long count]}.
     */
    @Query("SELECT FUNCTION('DATE', FUNCTION('timezone', 'UTC', v.viewedAt)), COUNT(v) FROM PageView v "
            + "WHERE v.viewedAt >= :since GROUP BY FUNCTION('DATE', FUNCTION('timezone', 'UTC', v.viewedAt))")
    List<Object[]> countByDay(@Param("since") Instant since);
}
