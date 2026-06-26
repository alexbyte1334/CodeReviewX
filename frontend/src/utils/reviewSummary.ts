import type { IssueSummary, ReviewIssue, ReviewTask, RiskLevel } from '../types/reviewTask';

export function computeIssueSummaryFromIssues(issues: ReviewIssue[]): IssueSummary {
  const highCount = issues.filter((issue) => issue.severity === 'HIGH').length;
  const mediumCount = issues.filter((issue) => issue.severity === 'MEDIUM').length;
  const lowCount = issues.filter((issue) => issue.severity === 'LOW').length;

  let riskLevel: RiskLevel = 'NONE';

  if (highCount > 0) {
    riskLevel = 'HIGH';
  } else if (mediumCount > 0) {
    riskLevel = 'MEDIUM';
  } else if (lowCount > 0) {
    riskLevel = 'LOW';
  }

  return {
    totalIssues: issues.length,
    highCount,
    mediumCount,
    lowCount,
    riskLevel,
  };
}

export function getIssueSummary(task: ReviewTask): IssueSummary {
  return task.issueSummary ?? computeIssueSummaryFromIssues(task.issues);
}
