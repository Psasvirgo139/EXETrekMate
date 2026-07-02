package com.trekmate.exe.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * One member entry in a tour execution session.
 * userId is the 16-char compact string from Android UserIdGenerator.
 */
@Entity
@Table(
    name = "exe_tour_members",
    uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "user_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExeTourMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExeTourSession session;

    /** 16-char compact userId from Android. */
    @Column(name = "user_id", nullable = false, length = 16)
    private String userId;

    @Column(name = "is_leader", nullable = false)
    private boolean isLeader;
}
