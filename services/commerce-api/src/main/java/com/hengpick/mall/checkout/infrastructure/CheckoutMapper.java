package com.hengpick.mall.checkout.infrastructure;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
interface CheckoutMapper {
    @Select("""
            SELECT r.session_id AS sessionId, r.version AS reportVersion, r.report_json AS reportJson
            FROM decision_reports r JOIN decision_sessions s ON s.id = r.session_id
            WHERE r.session_id = #{sessionId} AND r.version = #{version} AND s.user_id = #{userId}
              AND r.deleted_at IS NULL AND s.deleted_at IS NULL
            """)
    ReportRow findReport(@Param("userId") String userId, @Param("sessionId") String sessionId,
            @Param("version") int version);

    @Select("""
            SELECT id, user_id AS userId, session_id AS sessionId, report_version AS reportVersion,
                   sku_id AS skuId, price_plan_snapshot_json AS snapshotJson, status,
                   expires_at AS expiresAt, created_at AS createdAt, confirmed_at AS confirmedAt,
                   idempotency_key AS idempotencyKey
            FROM purchase_intents WHERE user_id = #{userId} AND idempotency_key = #{key}
            """)
    PurchaseIntentRow findByKey(@Param("userId") String userId, @Param("key") String key);

    @Select("""
            SELECT id, user_id AS userId, session_id AS sessionId, report_version AS reportVersion,
                   sku_id AS skuId, price_plan_snapshot_json AS snapshotJson, status,
                   expires_at AS expiresAt, created_at AS createdAt, confirmed_at AS confirmedAt,
                   idempotency_key AS idempotencyKey
            FROM purchase_intents WHERE user_id = #{userId} AND id = #{id}
            """)
    PurchaseIntentRow findOwned(@Param("userId") String userId, @Param("id") String id);

    @Insert("""
            INSERT INTO purchase_intents
              (id, user_id, session_id, report_version, sku_id, price_plan_snapshot_json, status,
               expires_at, created_at, idempotency_key)
            VALUES (#{id}, #{userId}, #{sessionId}, #{reportVersion}, #{skuId}, #{snapshotJson}, #{status},
                    #{expiresAt}, #{createdAt}, #{idempotencyKey})
            """)
    void insert(@Param("id") String id, @Param("userId") String userId, @Param("sessionId") String sessionId,
            @Param("reportVersion") int reportVersion, @Param("skuId") String skuId,
            @Param("snapshotJson") String snapshotJson, @Param("status") String status,
            @Param("expiresAt") LocalDateTime expiresAt, @Param("createdAt") LocalDateTime createdAt,
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE purchase_intents SET status = #{status}, confirmed_at = #{confirmedAt}
            WHERE id = #{id} AND user_id = #{userId} AND status = 'CREATED'
            """)
    void update(@Param("id") String id, @Param("userId") String userId, @Param("status") String status,
            @Param("confirmedAt") LocalDateTime confirmedAt);
}
