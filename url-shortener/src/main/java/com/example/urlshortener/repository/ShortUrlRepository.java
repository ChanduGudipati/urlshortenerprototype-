package com.example.urlshortener.repository;

import com.example.urlshortener.domain.ShortUrl;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

    Optional<ShortUrl> findByCode(String code);

    /** Atomic increment in the database, so concurrent clicks are never lost. */
    @Modifying
    @Query("update ShortUrl s set s.clickCount = s.clickCount + 1 where s.id = :id")
    int incrementClickCount(@Param("id") Long id);
}
