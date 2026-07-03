#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
USER_ID="${USER_ID:-demo-user}"

RESUME_TEXT="${RESUME_TEXT:-我做过 Spring Boot + FastAPI 企业知识库 RAG 项目，使用 MySQL 保存业务数据，使用 Qdrant 保存向量，接入通义千问完成 Embedding、Rerank 和问答，并实现权限控制、聊天会话、消息持久化和求职辅助 Agent。}"
JOB_DESCRIPTION="${JOB_DESCRIPTION:-岗位要求熟悉 Java、Spring Boot、MySQL、Python、FastAPI，有 AI 应用或 RAG 项目经验，了解向量数据库和大模型调用，能够设计接口、排查问题并编写自动化测试。}"
INTERVIEW_QUESTION="${INTERVIEW_QUESTION:-RAG 中如何解决幻觉问题？}"

request_get() {
  local path="$1"
  echo
  echo "==> GET ${BASE_URL}${path}"
  curl -sS "${BASE_URL}${path}"
  echo
}

request_post() {
  local path="$1"
  local payload="$2"
  echo
  echo "==> POST ${BASE_URL}${path}"
  curl -sS -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json" \
    -d "${payload}"
  echo
}

echo "Job Agent demo base URL: ${BASE_URL}"
echo "Demo user: ${USER_ID}"

request_get "/api/health"

request_post "/api/job-agent/resume-versions" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "versionName": "RAG 项目强化版",
  "targetRole": "Java 后端开发工程师",
  "resumeText": "${RESUME_TEXT}",
  "notes": "命令行演示创建的简历版本"
}
JSON
)"

request_post "/api/job-agent/favorites" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "jobTitle": "Java 后端开发工程师",
  "companyName": "Demo AI Company",
  "jobDescription": "${JOB_DESCRIPTION}",
  "sourceUrl": "https://example.com/jobs/java-ai",
  "notes": "命令行演示收藏的岗位"
}
JSON
)"

request_post "/api/job-agent/analyze" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "resumeText": "${RESUME_TEXT}",
  "jobDescription": "${JOB_DESCRIPTION}"
}
JSON
)"

request_post "/api/job-agent/resume/optimize" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "resumeText": "${RESUME_TEXT}",
  "jobDescription": "${JOB_DESCRIPTION}"
}
JSON
)"

request_post "/api/job-agent/interview/prepare" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "resumeText": "${RESUME_TEXT}",
  "jobDescription": "${JOB_DESCRIPTION}"
}
JSON
)"

request_post "/api/job-agent/interview/star-answer" "$(cat <<JSON
{
  "userId": "${USER_ID}",
  "resumeText": "${RESUME_TEXT}",
  "jobDescription": "${JOB_DESCRIPTION}",
  "question": "${INTERVIEW_QUESTION}"
}
JSON
)"

request_get "/api/job-agent/tasks?userId=${USER_ID}"
request_get "/api/job-agent/generated-tasks?userId=${USER_ID}"
request_get "/api/job-agent/resume-versions?userId=${USER_ID}"
request_get "/api/job-agent/favorites?userId=${USER_ID}"

echo
echo "Demo completed. Open ${BASE_URL}/index.html to view the same data in the Vue3 workspace."
