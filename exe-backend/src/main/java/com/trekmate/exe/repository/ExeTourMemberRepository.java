package com.trekmate.exe.repository;

import com.trekmate.exe.model.ExeTourMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExeTourMemberRepository extends JpaRepository<ExeTourMember, Long> {

    List<ExeTourMember> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndUserId(Long sessionId, String userId);
}
