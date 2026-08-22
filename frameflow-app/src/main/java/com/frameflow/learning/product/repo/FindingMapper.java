package com.frameflow.learning.product.repo;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** findings 表访问。 */
@Mapper
public interface FindingMapper {

    @Insert("INSERT INTO findings"
            + "(run_id, candidate_id, detector, detector_version, dimension, passed, "
            + "severity, timecode_ms, evidence, message, verdict) "
            + "VALUES(#{runId}, #{candidateId}, #{detector}, #{detectorVersion}, #{dimension}, "
            + "#{passed}, #{severity}, #{timecodeMs}, #{evidence}::jsonb, #{message}, #{verdict})")
    int insert(@Param("runId") Long runId,
               @Param("candidateId") Long candidateId,
               @Param("detector") String detector,
               @Param("detectorVersion") String detectorVersion,
               @Param("dimension") String dimension,
               @Param("passed") boolean passed,
               @Param("severity") String severity,
               @Param("timecodeMs") Long timecodeMs,
               @Param("evidence") String evidence,
               @Param("message") String message,
               @Param("verdict") String verdict);

    @Select("SELECT f.id, f.run_id, f.candidate_id, f.detector, f.detector_version, "
            + "f.dimension, f.passed, f.severity, f.timecode_ms, f.evidence::text AS evidence, "
            + "f.message, f.verdict FROM findings f WHERE f.candidate_id = #{candidateId} "
            + "ORDER BY f.id")
    List<FindingRow> listByCandidate(Long candidateId);

    @Select("SELECT count(*) FROM findings WHERE run_id = #{runId}")
    int countByRun(Long runId);

    /** Finding 行对象。 */
    class FindingRow {
        private Long id;
        private Long runId;
        private Long candidateId;
        private String detector;
        private String detectorVersion;
        private String dimension;
        private boolean passed;
        private String severity;
        private Long timecodeMs;
        private String evidence;
        private String message;
        private String verdict;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getRunId() { return runId; }
        public void setRunId(Long runId) { this.runId = runId; }
        public Long getCandidateId() { return candidateId; }
        public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
        public String getDetector() { return detector; }
        public void setDetector(String detector) { this.detector = detector; }
        public String getDetectorVersion() { return detectorVersion; }
        public void setDetectorVersion(String detectorVersion) { this.detectorVersion = detectorVersion; }
        public String getDimension() { return dimension; }
        public void setDimension(String dimension) { this.dimension = dimension; }
        public boolean isPassed() { return passed; }
        public void setPassed(boolean passed) { this.passed = passed; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public Long getTimecodeMs() { return timecodeMs; }
        public void setTimecodeMs(Long timecodeMs) { this.timecodeMs = timecodeMs; }
        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }
    }
}
