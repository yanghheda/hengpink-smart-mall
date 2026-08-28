package com.hengpick.mall.identity.infrastructure;

import java.time.Instant;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdentityMapper {
    @Select("""
            SELECT id, account, display_name AS displayName, password_hash AS passwordHash, role, status
            FROM users
            WHERE account = #{account}
            """)
    UserRow findUserByAccount(@Param("account") String account);

    @Insert("""
            INSERT INTO auth_sessions
              (id, user_id, device_session_id, refresh_token_hash, status, expires_at, last_used_at, created_at)
            VALUES
              (#{id}, #{userId}, #{deviceSessionId}, #{refreshTokenHash}, #{status}, #{expiresAt}, #{lastUsedAt}, #{createdAt})
            """)
    void insertSession(SessionRow session);

    @Select("""
            SELECT s.id AS sessionId, s.refresh_token_hash AS refreshTokenHash,
                   u.id AS userId, u.account, u.display_name AS displayName,
                   u.password_hash AS passwordHash, u.role, u.status AS userStatus
            FROM auth_sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.refresh_token_hash = #{refreshTokenHash}
              AND s.status = 'ACTIVE'
              AND s.expires_at > #{now}
              AND u.status = 'ACTIVE'
            """)
    RefreshSessionRow findRefreshSession(
            @Param("refreshTokenHash") String refreshTokenHash,
            @Param("now") Instant now);

    @Update("""
            UPDATE auth_sessions
            SET refresh_token_hash = #{replacementHash}, last_used_at = #{lastUsedAt}, expires_at = #{expiresAt}
            WHERE id = #{sessionId}
              AND refresh_token_hash = #{expectedHash}
              AND status = 'ACTIVE'
              AND expires_at > #{lastUsedAt}
            """)
    int rotateRefreshToken(
            @Param("sessionId") String sessionId,
            @Param("expectedHash") String expectedHash,
            @Param("replacementHash") String replacementHash,
            @Param("lastUsedAt") Instant lastUsedAt,
            @Param("expiresAt") Instant expiresAt);

    @Insert("""
            INSERT INTO deletion_audit_logs
              (id, action, subject_hash, object_type, object_id_hash, occurred_at)
            VALUES
              (#{id}, #{action}, #{subjectHash}, #{objectType}, #{objectIdHash}, #{occurredAt})
            """)
    void insertDeletionAudit(DeletionAuditRow row);
}
