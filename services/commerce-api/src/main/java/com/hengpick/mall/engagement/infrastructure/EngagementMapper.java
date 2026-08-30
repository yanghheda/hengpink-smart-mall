package com.hengpick.mall.engagement.infrastructure;

import com.hengpick.mall.engagement.domain.Favorite;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface EngagementMapper {
    @Select("SELECT EXISTS(SELECT 1 FROM products WHERE id = #{productId})")
    boolean productExists(String productId);

    @Select("""
            SELECT r.session_id AS sessionId, s.user_id AS userId, r.version,
                   r.selected_sku_id AS selectedSkuId, r.report_json AS reportJson,
                   r.versions_json AS versionsJson, r.created_at AS createdAt
            FROM decision_reports r
            JOIN decision_sessions s ON s.id = r.session_id
            WHERE r.session_id = #{sessionId} AND r.version = #{version}
              AND s.user_id = #{userId} AND s.deleted_at IS NULL AND r.deleted_at IS NULL
            """)
    HistoricalReportRow findReport(
            @Param("userId") String userId, @Param("sessionId") String sessionId, @Param("version") int version);

    @Select("""
            SELECT id, user_id AS userId, entity_type AS entityType, entity_id AS entityId,
                   snapshot_json AS snapshotJson, created_at AS createdAt
            FROM favorites
            WHERE user_id = #{userId} AND entity_type = #{type} AND entity_id = #{entityId}
            """)
    FavoriteRow findFavorite(
            @Param("userId") String userId, @Param("type") String type, @Param("entityId") String entityId);

    @Insert("""
            INSERT INTO favorites (id, user_id, entity_type, entity_id, snapshot_json, created_at)
            VALUES (#{favorite.id}, #{favorite.userId}, #{favorite.entityType}, #{favorite.entityId},
                    #{snapshotJson}, #{favorite.createdAt})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertFavorite(@Param("favorite") Favorite favorite, @Param("snapshotJson") String snapshotJson);

    @Select("""
            <script>
            SELECT id, user_id AS userId, entity_type AS entityType, entity_id AS entityId,
                   snapshot_json AS snapshotJson, created_at AS createdAt
            FROM favorites
            WHERE user_id = #{userId}
            <if test="type != null">AND entity_type = #{type}</if>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    List<FavoriteRow> findFavorites(@Param("userId") String userId, @Param("type") String type);

    @Delete("DELETE FROM favorites WHERE id = #{favoriteId} AND user_id = #{userId}")
    void deleteFavorite(@Param("userId") String userId, @Param("favoriteId") String favoriteId);

    @Update("""
            UPDATE decision_reports r
            JOIN decision_sessions s ON s.id = r.session_id
            SET r.report_json = JSON_OBJECT(), r.deleted_at = UTC_TIMESTAMP(3)
            WHERE r.session_id = #{sessionId} AND r.version = #{version}
              AND s.user_id = #{userId} AND r.deleted_at IS NULL
            """)
    int deleteReport(
            @Param("userId") String userId, @Param("sessionId") String sessionId, @Param("version") int version);

    @Update("""
            UPDATE favorites
            SET snapshot_json = #{snapshotJson}
            WHERE user_id = #{userId} AND entity_type = 'REPORT' AND entity_id = #{entityId}
            """)
    void sanitizeReportFavorite(
            @Param("userId") String userId,
            @Param("entityId") String entityId,
            @Param("snapshotJson") String snapshotJson);
}
