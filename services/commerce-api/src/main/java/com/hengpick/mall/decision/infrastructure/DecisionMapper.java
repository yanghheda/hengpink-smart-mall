package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 将 Decision 仓储操作映射到冻结的数据表。 */
@Mapper
public interface DecisionMapper {
    @Select("""
            SELECT EXISTS(
                SELECT 1 FROM decision_runs WHERE session_id = #{sessionId} AND status = 'RUNNING'
            )
            """)
    boolean hasActiveRun(@Param("sessionId") String sessionId);

    @Insert("""
            INSERT INTO decision_runs
              (id, session_id, run_version, status, trigger_type, started_at, completed_at, created_at)
            VALUES
              (#{id}, #{sessionId}, #{runVersion}, #{status}, #{triggerType}, #{startedAt}, #{completedAt}, #{startedAt})
            """)
    void insertRun(DecisionRun run);

    @Update("""
            UPDATE decision_sessions
            SET status = #{status}, current_run_version = #{currentRunVersion},
                updated_at = #{updatedAt}, version = #{version}
            WHERE id = #{session.id}
              AND current_run_version = #{expectedRunVersion}
              AND version = #{expectedVersion}
            """)
    int advanceSession(
            @Param("session") DecisionSession session,
            @Param("status") String status,
            @Param("currentRunVersion") int currentRunVersion,
            @Param("updatedAt") java.time.Instant updatedAt,
            @Param("version") long version,
            @Param("expectedRunVersion") int expectedRunVersion,
            @Param("expectedVersion") long expectedVersion);
}
