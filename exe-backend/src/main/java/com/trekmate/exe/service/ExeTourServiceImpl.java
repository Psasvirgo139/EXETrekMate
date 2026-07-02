package com.trekmate.exe.service;

import com.trekmate.exe.dto.request.CreateTourRequest;
import com.trekmate.exe.dto.request.EndTourRequest;
import com.trekmate.exe.dto.request.JoinTourRequest;
import com.trekmate.exe.dto.response.*;
import com.trekmate.exe.model.ExeTourMember;
import com.trekmate.exe.model.ExeTourSession;
import com.trekmate.exe.repository.ExeTourMemberRepository;
import com.trekmate.exe.repository.ExeTourSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExeTourServiceImpl implements ExeTourService {

    private static final String ALPHANUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExeTourSessionRepository sessionRepo;
    private final ExeTourMemberRepository memberRepo;

    @Override
    @Transactional
    public CreateTourResponse createTour(CreateTourRequest request) {
        String tourId = UUID.randomUUID().toString();
        String groupId = randomAlphaNum(8);
        String joinCode = randomAlphaNum(6);
        String qrPayload = "trekmate://join?code=" + joinCode;

        ExeTourSession session = ExeTourSession.builder()
                .tourId(tourId)
                .groupId(groupId)
                .joinCode(joinCode)
                .qrPayload(qrPayload)
                .leaderId(request.leaderId())
                .active(true)
                .build();

        ExeTourMember leaderMember = ExeTourMember.builder()
                .session(session)
                .userId(request.leaderId())
                .isLeader(true)
                .build();

        session.getMembers().add(leaderMember);
        sessionRepo.save(session);

        log.info("Tour created: tourId={} groupId={} joinCode={} leader={}", tourId, groupId, joinCode, request.leaderId());

        return new CreateTourResponse(tourId, groupId, joinCode, qrPayload, request.leaderId());
    }

    @Override
    @Transactional
    public JoinTourResponse joinTour(JoinTourRequest request) {
        ExeTourSession session = sessionRepo.findByJoinCodeAndActiveTrue(request.joinCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active tour found for join code: " + request.joinCode()));

        boolean alreadyMember = memberRepo.existsBySessionIdAndUserId(session.getId(), request.userId());
        if (!alreadyMember) {
            ExeTourMember newMember = ExeTourMember.builder()
                    .session(session)
                    .userId(request.userId())
                    .isLeader(false)
                    .build();
            memberRepo.save(newMember);
            session.getMembers().add(newMember);
        }

        List<MemberDto> memberDtos = session.getMembers().stream()
                .map(m -> new MemberDto(m.getUserId(), m.isLeader()))
                .toList();

        log.info("Tour joined: tourId={} userId={}", session.getTourId(), request.userId());

        return new JoinTourResponse(
                session.getTourId(),
                session.getGroupId(),
                session.getLeaderId(),
                session.getJoinCode(),
                session.getQrPayload(),
                memberDtos
        );
    }

    @Override
    @Transactional
    public EndTourResponse endTour(EndTourRequest request) {
        ExeTourSession session = sessionRepo.findByTourIdAndActiveTrue(request.tourId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active tour found with id: " + request.tourId()));

        if (!session.getLeaderId().equals(request.leaderId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the tour leader can end the tour");
        }

        session.setActive(false);
        sessionRepo.save(session);

        log.info("Tour ended: tourId={} leader={}", request.tourId(), request.leaderId());

        return new EndTourResponse(true, "Tour ended successfully");
    }

    @Override
    @Transactional(readOnly = true)
    public MemberListResponse getMembers(String tourId) {
        ExeTourSession session = sessionRepo.findByTourIdAndActiveTrue(tourId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No active tour found with id: " + tourId));

        List<MemberDto> members = session.getMembers().stream()
                .map(m -> new MemberDto(m.getUserId(), m.isLeader()))
                .toList();

        return new MemberListResponse(members);
    }

    private String randomAlphaNum(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
