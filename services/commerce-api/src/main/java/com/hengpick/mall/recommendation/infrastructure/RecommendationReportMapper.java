package com.hengpick.mall.recommendation.infrastructure;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface RecommendationReportMapper {
    @Select("""
            SELECT r.session_id AS sessionId, s.user_id AS userId, r.version,
                   r.selected_sku_id AS selectedSkuId, r.report_json AS reportJson,
                   r.recommendation_snapshot_json AS recommendationSnapshotJson,
                   r.versions_json AS versionsJson, r.created_at AS createdAt
            FROM decision_sessions s
            JOIN decision_reports r
              ON r.session_id = s.id AND r.version = s.current_report_version
            WHERE s.id = #{sessionId} AND s.user_id = #{userId}
              AND s.deleted_at IS NULL AND r.deleted_at IS NULL
              AND r.recommendation_snapshot_json IS NOT NULL
            """)
    RecommendationReportRow findCurrent(
            @Param("userId") String userId, @Param("sessionId") String sessionId);

    @Update("""
            UPDATE decision_sessions
            SET current_report_version = 1, weights_json = #{weightsJson},
                updated_at = #{createdAt}, version = version + 1
            WHERE id = #{sessionId} AND user_id = #{userId}
              AND current_report_version IS NULL AND deleted_at IS NULL
            """)
    int claimInitialVersion(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("weightsJson") String weightsJson,
            @Param("createdAt") java.time.Instant createdAt);

    @Update("""
            UPDATE decision_sessions
            SET current_report_version = #{nextVersion}, weights_json = #{weightsJson},
                updated_at = #{createdAt}, version = version + 1
            WHERE id = #{sessionId} AND user_id = #{userId}
              AND current_report_version = #{expectedVersion} AND deleted_at IS NULL
            """)
    int advanceVersion(
            @Param("userId") String userId,
            @Param("sessionId") String sessionId,
            @Param("expectedVersion") int expectedVersion,
            @Param("nextVersion") int nextVersion,
            @Param("weightsJson") String weightsJson,
            @Param("createdAt") java.time.Instant createdAt);

    @Insert("""
            INSERT INTO decision_reports
              (session_id, version, selected_sku_id, report_json,
               recommendation_snapshot_json, versions_json, created_at)
            VALUES
              (#{sessionId}, #{version}, #{selectedSkuId}, #{reportJson},
               #{snapshotJson}, #{versionsJson}, #{createdAt})
            """)
    void insertReport(
            @Param("sessionId") String sessionId,
            @Param("version") int version,
            @Param("selectedSkuId") String selectedSkuId,
            @Param("reportJson") String reportJson,
            @Param("snapshotJson") String snapshotJson,
            @Param("versionsJson") String versionsJson,
            @Param("createdAt") java.time.Instant createdAt);
}
