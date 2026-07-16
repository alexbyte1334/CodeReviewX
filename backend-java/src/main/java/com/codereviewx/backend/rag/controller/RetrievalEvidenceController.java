package com.codereviewx.backend.rag.controller;

import com.codereviewx.backend.common.ApiResponse;
import com.codereviewx.backend.rag.dto.RetrievalTraceResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class RetrievalEvidenceController {
 private final JdbcTemplate jdbc; private final boolean enabled;
 public RetrievalEvidenceController(JdbcTemplate jdbc){this(jdbc,true);} public RetrievalEvidenceController(JdbcTemplate jdbc, boolean enabled){this.jdbc=jdbc;this.enabled=enabled;}
 @GetMapping("/api/review-runs/{runId}/retrieval") public ApiResponse<RetrievalTraceResponse> run(@PathVariable long runId){
  if (!enabled) throw new RagDisabledException(); if(runId<=0) throw new RagInvalidRequestException();
  var rows=jdbc.query("SELECT degraded,latency_ms,vector_candidate_count+lexical_candidate_count,selected_count FROM rag_retrieval_trace WHERE review_run_id=? ORDER BY created_at DESC,id DESC LIMIT 1",(r,n)->new RetrievalTraceResponse(r.getBoolean(1),r.getBoolean(1)?"RETRIEVAL_DEGRADED":null,r.getLong(2),r.getInt(3),r.getInt(4),null,List.of()),runId);
  if(rows.isEmpty()) throw new RagNotFoundException("Retrieval trace not found"); return ApiResponse.success(rows.get(0)); }
 @GetMapping("/api/review-tasks/{taskId}/issues/{issueKey}/evidence") public ApiResponse<List<RetrievalTraceResponse.Evidence>> evidence(@PathVariable long taskId,@PathVariable String issueKey){
  if (!enabled) throw new RagDisabledException(); if(taskId<=0 || issueKey==null || !issueKey.matches("[A-Za-z0-9_.:-]{1,64}")) throw new RagInvalidRequestException();
  var rows=jdbc.query("SELECT e.citation_label,e.path,e.start_line,e.end_line,e.evidence_excerpt,e.retrieval_rank,e.retrieval_score FROM review_issue_evidence e JOIN review_issue i ON i.id=e.review_issue_id WHERE i.review_task_id=? AND i.issue_key=? ORDER BY e.retrieval_rank,e.citation_label",(r,n)->new RetrievalTraceResponse.Evidence(r.getString(1),r.getString(2),r.getInt(3),r.getInt(4),r.getString(5),r.getInt(6),r.getDouble(7)),taskId,issueKey);
  if (rows.isEmpty()) throw new RagNotFoundException("Issue not found"); return ApiResponse.success(rows); }
}
