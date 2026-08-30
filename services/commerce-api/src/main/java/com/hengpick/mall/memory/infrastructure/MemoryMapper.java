package com.hengpick.mall.memory.infrastructure;

import com.hengpick.mall.memory.domain.MemoryProposal;
import com.hengpick.mall.memory.domain.UserPreference;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface MemoryMapper {
    @Select("""
            SELECT user_id AS userId,
                   JSON_UNQUOTE(JSON_EXTRACT(intent_json, '$.category')) AS categoryId,
                   JSON_UNQUOTE(JSON_EXTRACT(intent_json, '$.recipient')) AS recipientKey
            FROM decision_sessions
            WHERE id = #{sessionId} AND deleted_at IS NULL
            """)
    com.hengpick.mall.memory.domain.MemoryRepository.SessionContext findSession(String sessionId);

    @Insert("""
            INSERT INTO memory_proposals
              (id, user_id, session_id, proposal_type, preference_key, scope, recipient_key, category_id,
               value_json, rationale_summary, status, expires_at, created_at, decided_at)
            VALUES
              (#{proposal.id}, #{proposal.userId}, #{proposal.sessionId}, #{proposal.proposalType},
               #{proposal.preferenceKey}, #{proposal.scope}, #{proposal.recipientKey}, #{proposal.categoryId},
               #{valueJson}, #{proposal.rationaleSummary}, #{proposal.status}, #{proposal.expiresAt},
               #{proposal.createdAt}, #{proposal.decidedAt})
            """)
    void insertProposal(@Param("proposal") MemoryProposal proposal, @Param("valueJson") String valueJson);

    @Select("""
            SELECT id, user_id AS userId, session_id AS sessionId, proposal_type AS proposalType,
                   preference_key AS preferenceKey, scope, recipient_key AS recipientKey,
                   category_id AS categoryId, value_json AS valueJson, rationale_summary AS rationaleSummary,
                   status, expires_at AS expiresAt, created_at AS createdAt, decided_at AS decidedAt
            FROM memory_proposals
            WHERE id = #{proposalId} AND user_id = #{userId}
            """)
    MemoryProposalRow findProposal(@Param("proposalId") String proposalId, @Param("userId") String userId);

    @Update("""
            UPDATE memory_proposals
            SET status = #{proposal.status}, value_json = #{valueJson}, decided_at = #{proposal.decidedAt}
            WHERE id = #{proposal.id} AND user_id = #{proposal.userId} AND status = #{expectedStatus}
              AND expires_at > #{proposal.decidedAt}
            """)
    int decideProposal(
            @Param("proposal") MemoryProposal proposal,
            @Param("valueJson") String valueJson,
            @Param("expectedStatus") String expectedStatus);

    @Insert("""
            INSERT INTO user_preferences
              (id, user_id, scope, recipient_key, category_id, preference_type, preference_key, value_json,
               source_session_id, status, confirmed_at, expires_at, created_at, updated_at, version)
            VALUES
              (#{preference.id}, #{preference.userId}, #{preference.scope}, #{preference.recipientKey},
               #{preference.categoryId}, #{preference.preferenceType}, #{preference.preferenceKey}, #{valueJson},
               #{preference.sourceSessionId}, 'ACTIVE', #{preference.confirmedAt}, #{preference.expiresAt},
               #{preference.confirmedAt}, #{preference.confirmedAt}, 0)
            """)
    void insertPreference(@Param("preference") UserPreference preference, @Param("valueJson") String valueJson);

    @Select("""
            SELECT id, user_id AS userId, scope, recipient_key AS recipientKey, category_id AS categoryId,
                   preference_type AS preferenceType, preference_key AS preferenceKey, value_json AS valueJson,
                   source_session_id AS sourceSessionId, confirmed_at AS confirmedAt, expires_at AS expiresAt
            FROM user_preferences
            WHERE user_id = #{userId} AND status = 'ACTIVE' AND expires_at > #{now}
            ORDER BY confirmed_at, id
            """)
    List<UserPreferenceRow> findActivePreferences(@Param("userId") String userId, @Param("now") Instant now);
}
