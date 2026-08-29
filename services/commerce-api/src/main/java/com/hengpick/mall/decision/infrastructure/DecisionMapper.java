package com.hengpick.mall.decision.infrastructure;

import com.hengpick.mall.decision.domain.DecisionRun;
import com.hengpick.mall.decision.domain.DecisionSession;
import com.hengpick.mall.decision.domain.DecisionSessionSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 将 Decision 仓储操作映射到冻结的数据表。 */
@Mapper
public interface DecisionMapper {
    @Select("""
            SELECT s.id AS sessionId, r.id AS currentRunId,
                   s.current_run_version AS currentRunVersion, s.status,
                   s.current_report_version AS currentReportVersion
            FROM decision_sessions s
            LEFT JOIN decision_runs r
              ON r.session_id = s.id AND r.run_version = s.current_run_version
            WHERE s.id = #{sessionId} AND s.user_id = #{userId} AND s.deleted_at IS NULL
            """)
    DecisionSessionSnapshot findSessionSnapshot(
            @Param("sessionId") String sessionId, @Param("userId") String userId);

    @Select("""
            SELECT r.id
            FROM decision_sessions s
            JOIN decision_runs r ON r.session_id = s.id AND r.run_version = s.current_run_version
            WHERE s.id = #{sessionId} AND s.user_id = #{userId} AND s.deleted_at IS NULL
            """)
    String findCurrentRunId(@Param("sessionId") String sessionId, @Param("userId") String userId);

    @Select("SELECT session_id FROM decision_runs WHERE id = #{runId}")
    String findSessionIdByRunId(@Param("runId") String runId);

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

    @Select("SELECT content_hash FROM agent_steps WHERE run_id = #{runId} AND sequence_no = #{sequence}")
    String findStepContentHash(@Param("runId") String runId, @Param("sequence") int sequence);

    @Insert("""
            INSERT INTO agent_steps
              (id, run_id, run_version, sequence_no, node_name, status, input_summary_json,
               output_summary_json, content_hash, started_at, completed_at, duration_ms)
            SELECT #{id}, id, #{step.runVersion}, #{step.sequence}, #{step.node}, #{step.status},
                   #{inputJson}, #{outputJson}, #{step.contentHash}, #{step.startedAt}, #{step.completedAt},
                   TIMESTAMPDIFF(MICROSECOND, #{step.startedAt}, #{step.completedAt}) DIV 1000
            FROM decision_runs
            WHERE id = #{step.runId} AND run_version = #{step.runVersion}
            """)
    int insertStep(
            @Param("id") String id,
            @Param("step") com.hengpick.mall.decision.domain.AgentStepCallback step,
            @Param("inputJson") String inputJson,
            @Param("outputJson") String outputJson);

    @Select("SELECT content_hash FROM decision_run_results WHERE run_id = #{runId}")
    String findCompletionContentHash(@Param("runId") String runId);

    @Update("""
            UPDATE decision_sessions s
            JOIN decision_runs r ON r.session_id = s.id
            SET s.status = #{sessionStatus}, s.updated_at = #{completedAt}, s.version = s.version + 1
            WHERE r.id = #{runId} AND r.run_version = #{runVersion}
              AND r.status = 'RUNNING' AND s.current_run_version = #{runVersion}
            """)
    int completeSessionIfCurrent(
            @Param("runId") String runId,
            @Param("runVersion") int runVersion,
            @Param("sessionStatus") String sessionStatus,
            @Param("completedAt") java.time.Instant completedAt);

    @Update("""
            UPDATE decision_runs
            SET status = #{status}, completed_at = #{completedAt}
            WHERE id = #{runId} AND run_version = #{runVersion} AND status = 'RUNNING'
            """)
    int completeRun(
            @Param("runId") String runId,
            @Param("runVersion") int runVersion,
            @Param("status") String status,
            @Param("completedAt") java.time.Instant completedAt);

    @Insert("""
            INSERT INTO decision_run_results
              (run_id, run_version, completion_type, result_summary_json, content_hash, is_current, completed_at)
            VALUES
              (#{completion.runId}, #{completion.runVersion}, #{completion.completionType},
               #{resultJson}, #{completion.contentHash}, #{isCurrent}, #{completion.completedAt})
            """)
    void insertRunResult(
            @Param("completion") com.hengpick.mall.decision.domain.RunCompletionCallback completion,
            @Param("resultJson") String resultJson,
            @Param("isCurrent") boolean isCurrent);
}
