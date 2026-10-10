package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ClickEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    long countByShortUrlId(Long shortUrlId);

    @Query("select max(c.clickedAt) from ClickEvent c where c.shortUrlId = :shortUrlId")
    Instant findLastClickedAt(@Param("shortUrlId") Long shortUrlId);
}
