package com.restaurant.userservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "full_name", length = 100)
    private String fullName;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    private Integer age;

    @Column(length = 100)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verification_expires_at")
    private LocalDateTime emailVerificationExpiresAt;

    // Legacy columns — tồn tại trong DB cũ, chưa được DROP migration
    // Hibernate cần map để INSERT không bị lỗi "doesn't have a default value"
    @Column(name = "email_verification_otp_attempt_count")
    private Integer emailVerificationOtpAttemptCount = 0;

    @Column(name = "email_verification_otp_used")
    private Boolean emailVerificationOtpUsed = false;

    @Column(name = "email_verification_otp_email", length = 100)
    private String emailVerificationOtpEmail;

    @Column(name = "email_verification_otp_status", length = 20)
    private String emailVerificationOtpStatus;

    @Column(name = "email_verification_otp_sent_at")
    private LocalDateTime emailVerificationOtpSentAt;

    @Column(name = "email_verification_otp_used_at")
    private LocalDateTime emailVerificationOtpUsedAt;

    @Column(name = "email_verification_otp_last_attempt_at")
    private LocalDateTime emailVerificationOtpLastAttemptAt;

    @Column(name = "is_banned", nullable = false)
    private boolean isBanned = false;

    @Column(name = "ban_reason", length = 500)
    private String banReason;

    @Column(name = "banned_at")
    private LocalDateTime bannedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
