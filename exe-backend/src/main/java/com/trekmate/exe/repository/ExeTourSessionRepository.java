package com.trekmate.exe.repository;

import com.trekmate.exe.model.ExeTourSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExeTourSessionRepository extends JpaRepository<ExeTourSession, Long> {

    Optional<ExeTourSession> findByJoinCodeAndActiveTrue(String joinCode);

    Optional<ExeTourSession> findByTourIdAndActiveTrue(String tourId);
}
