import {beforeEach, describe, expect, it, vi} from "vitest";
import appOptions from "./appOptions.js";

describe("application authentication state", () => {
  beforeEach(() => {
    const values = new Map();
    vi.stubGlobal("localStorage", {
      getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, String(value)),
      removeItem: key => values.delete(key),
      clear: () => values.clear()
    });
    localStorage.clear();
  });

  it("starts every user-editable form without hard-coded demo data", () => {
    const state = appOptions.data();

    expect(state.authForm).toEqual({username: "", password: "", displayName: "", department: ""});
    expect(state.identity).toEqual({userId: "", department: ""});
    expect(Object.values(state.profileForm)).toEqual(["", "", ""]);
    expect(Object.values(state.kbForm)).toEqual(["", "", "", ""]);
    expect(Object.values(state.kbEditForm)).toEqual(["", "", ""]);
    expect(Object.values(state.chatForm)).toEqual(["", "", "", ""]);
    expect(Object.values(state.jobForm)).toEqual(["", "", ""]);
    expect(Object.values(state.jobFavoriteForm)).toEqual(["", "", "", ""]);
    expect(Object.values(state.jobResumeVersionForm)).toEqual(["", "", ""]);
  });

  it("clears credentials and returns to login entry after a 401", () => {
    localStorage.setItem("aikb.accessToken", "expired-token");
    localStorage.setItem("aikb.user", JSON.stringify({username: "demo-user"}));
    const replace = vi.fn();
    const model = {
      auth: {accessToken: "expired-token", tokenType: "Bearer", user: {username: "demo-user"}},
      authMode: "register",
      statusMessage: "",
      $router: {replace},
      $route: {path: "/library"},
      clearAuthentication: appOptions.methods.clearAuthentication,
      resetUserWorkspaceState: appOptions.methods.resetUserWorkspaceState,
      setStatus: appOptions.methods.setStatus
    };

    appOptions.methods.redirectToLogin.call(model, "登录状态已失效");

    expect(model.auth).toEqual({accessToken: "", tokenType: "Bearer", user: null});
    expect(model.authMode).toBe("login");
    expect(model.statusMessage).toBe("登录状态已失效");
    expect(localStorage.getItem("aikb.accessToken")).toBeNull();
    expect(localStorage.getItem("aikb.user")).toBeNull();
    expect(replace).toHaveBeenCalledWith("/");
  });

  it("stores a successful login and resets the workspace route", () => {
    const replace = vi.fn();
    const syncIdentityFromAuth = vi.fn();
    const model = {
      auth: {},
      authForm: {password: "secret123"},
      $router: {replace},
      $route: {path: "/career"},
      syncIdentityFromAuth
    };

    appOptions.methods.applyAuthResponse.call(model, {
      accessToken: "valid-token",
      tokenType: "Bearer",
      user: {username: "learner", displayName: "学习者"}
    });

    expect(model.auth.user.username).toBe("learner");
    expect(localStorage.getItem("aikb.accessToken")).toBe("valid-token");
    expect(model.authForm.password).toBe("");
    expect(replace).toHaveBeenCalledWith("/");
    expect(syncIdentityFromAuth).toHaveBeenCalledOnce();
  });

  it("clears previous account form and result data on logout", () => {
    const model = {
      ...appOptions.data(),
      auth: {accessToken: "token", tokenType: "Bearer", user: {username: "old-user"}},
      authForm: {username: "old-user", password: "secret123", displayName: "旧用户", department: "Java"},
      jobForm: {resumeText: "旧简历", jobDescription: "旧 JD", interviewQuestion: "旧问题"},
      jobResult: {matchScore: 90},
      resetUserWorkspaceState: appOptions.methods.resetUserWorkspaceState
    };

    appOptions.methods.clearAuthentication.call(model);

    expect(model.authForm).toEqual({username: "", password: "", displayName: "", department: ""});
    expect(model.jobForm).toEqual({resumeText: "", jobDescription: "", interviewQuestion: ""});
    expect(model.jobResult).toBeNull();
    expect(model.identity).toEqual({userId: "", department: ""});
  });

  it("rejects unsupported learning document extensions before upload", async () => {
    const setStatus = vi.fn();
    const event = {target: {files: [{name: "scores.csv"}], value: "selected"}};
    const model = {selectedKnowledgeBaseId: "kb-1", setStatus};

    await appOptions.methods.uploadDocument.call(model, event);

    expect(setStatus).toHaveBeenCalledWith("上传失败：支持 PDF、Markdown、Word（DOCX）和 TXT 文件。");
    expect(event.target.value).toBe("");
  });

  it("updates profile with a refreshed token without leaving the current page", async () => {
    const applyAuthResponse = vi.fn();
    const setStatus = vi.fn();
    const refreshed = {accessToken: "new-token", tokenType: "Bearer", user: {username: "learner"}};
    const model = {
      profileForm: {username: "learner", displayName: "新昵称", department: "AI 应用开发"},
      profileDrawerOpen: true,
      requestJson: vi.fn().mockResolvedValue(refreshed),
      applyAuthResponse,
      setStatus
    };

    await appOptions.methods.updateProfile.call(model);

    expect(model.requestJson).toHaveBeenCalledWith("/api/auth/me", expect.objectContaining({method: "PATCH"}));
    expect(applyAuthResponse).toHaveBeenCalledWith(refreshed, {redirect: false});
    expect(model.profileDrawerOpen).toBe(false);
    expect(setStatus).toHaveBeenCalledWith("个人资料已更新。");
  });

  it("shows the user question immediately and appends streamed answer deltas", async () => {
    const state = appOptions.data();
    const model = {
      ...state,
      selectedSessionId: "session-1",
      chatForm: {
        knowledgeBaseId: "kb-1",
        documentId: "doc-1",
        title: "",
        question: "  什么是 RAG？  "
      },
      messages: [],
      setStatus: vi.fn(),
      userQuery: vi.fn().mockReturnValue(""),
      normalizeChatMessage: appOptions.methods.normalizeChatMessage,
      requestNdjson: vi.fn(async (url, options, onEvent) => {
        expect(model.chatForm.question).toBe("");
        expect(model.messages).toHaveLength(2);
        expect(model.messages[0].content).toBe("什么是 RAG？");
        await onEvent({
          type: "accepted",
          message: {id: "user-1", role: "USER", content: "什么是 RAG？"}
        });
        await onEvent({type: "status", stage: "retrieving", message: "正在检索"});
        await onEvent({
          type: "sources",
          sources: [{filename: "rag.md", page_number: 1}],
          retrieval_mode: "rerank",
          rerank_elapsed_seconds: 0.2
        });
        await onEvent({type: "delta", content: "检索增强"});
        await onEvent({type: "delta", content: "生成"});
        await onEvent({
          type: "done",
          message: {
            id: "assistant-1",
            role: "ASSISTANT",
            content: "检索增强生成",
            sourcesJson: "[{\"filename\":\"rag.md\"}]",
            retrievalMode: "rerank"
          }
        });
      })
    };

    await appOptions.methods.askQuestion.call(model);

    expect(model.requestNdjson).toHaveBeenCalledOnce();
    expect(model.messages[0]).toMatchObject({
      id: "user-1",
      role: "user",
      optimistic: false
    });
    expect(model.messages[1]).toMatchObject({
      id: "assistant-1",
      role: "assistant",
      content: "检索增强生成",
      retrievalMode: "rerank",
      streaming: false
    });
    expect(model.chatStreaming).toBe(false);
    expect(model.chatStreamStage).toBe("completed");
  });

  it("auto-fills extracted JD after analyzing an uploaded attachment", async () => {
    const state = appOptions.data();
    const file = new File(["fake-image"], "jd.png", {type: "image/png"});
    const finishJobOperation = vi.fn();
    const model = {
      ...state,
      identity: {userId: "learner", department: "AI"},
      jobForm: {
        resumeText: "Spring Boot + RAG 项目",
        jobDescription: "",
        interviewQuestion: ""
      },
      jobAttachmentFile: file,
      startJobOperation: vi.fn().mockReturnValue(true),
      finishJobOperation,
      failJobOperation: vi.fn(),
      loadJobTasks: vi.fn().mockResolvedValue(undefined),
      setStatus: vi.fn(),
      requestJson: vi.fn().mockResolvedValue({
        analysis: {match_score: 91, matched_skills: ["Java"]},
        extractedJobDescription: "岗位要求 Java、Spring Boot、RAG。",
        warnings: ["截图边缘文字较模糊"]
      })
    };

    await appOptions.methods.analyzeJobFromAttachment.call(model);

    expect(model.jobResult.match_score).toBe(91);
    expect(model.jobForm.jobDescription).toBe("岗位要求 Java、Spring Boot、RAG。");
    expect(model.jobAttachmentWarnings).toEqual(["截图边缘文字较模糊"]);
    expect(model.careerResultTab).toBe("analysis");
    expect(finishJobOperation).toHaveBeenCalledWith(
      "附件识别分析完成，JD 已自动回填，匹配分：91"
    );
  });
});
