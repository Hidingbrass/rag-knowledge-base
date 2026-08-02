function readStoredJson(key) {
    try {
        return JSON.parse(localStorage.getItem(key) || "null");
    } catch (error) {
        return null;
    }
}

export default {
    data() {
        return {
            activeTab: "overview",
            statusMessage: "成长空间已就绪。",
            lastResponse: null,
            authMode: "login",
            authForm: {
                username: "",
                password: "",
                displayName: "",
                department: ""
            },
            auth: {
                accessToken: localStorage.getItem("aikb.accessToken") || "",
                tokenType: localStorage.getItem("aikb.tokenType") || "Bearer",
                user: readStoredJson("aikb.user")
            },
            identity: {
                userId: "",
                department: ""
            },
            profileForm: {
                username: "",
                displayName: "",
                department: ""
            },
            knowledgeBases: [],
            documents: [],
            sessions: [],
            messages: [],
            aiCallLogs: [],
            aiCallSummary: null,
            jobTasks: [],
            jobFavorites: [],
            jobGeneratedTasks: [],
            jobResumeVersions: [],
            generatedTaskFilter: "",
            selectedGeneratedTask: null,
            generatedTaskReviewComment: "",
            jobPages: {
                tasks: {page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
                generatedTasks: {page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
                resumeVersions: {page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true},
                favorites: {page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true}
            },
            selectedCompareTaskIds: [],
            selectedKnowledgeBaseId: "",
            selectedSessionId: "",
            kbForm: {
                name: "",
                description: "",
                ownerId: "",
                department: ""
            },
            editingKnowledgeBaseId: "",
            kbEditForm: {
                name: "",
                description: "",
                department: ""
            },
            chatForm: {
                knowledgeBaseId: "",
                title: "",
                documentId: "",
                question: ""
            },
            chatStreaming: false,
            chatStreamStage: "",
            chatStreamStatus: "",
            chatAbortController: null,
            jobForm: {
                resumeText: "",
                jobDescription: "",
                interviewQuestion: ""
            },
            jobAttachmentFile: null,
            jobAttachmentWarnings: [],
            jobOperation: {
                key: "",
                state: "idle",
                title: "",
                detail: "",
                startedAt: null,
                elapsedSeconds: 0
            },
            jobOperationTimer: null,
            jobFavoriteForm: {
                jobTitle: "",
                companyName: "",
                sourceUrl: "",
                notes: ""
            },
            jobResumeVersionForm: {
                versionName: "",
                targetRole: "",
                notes: ""
            },
            jobResult: null,
            jobCompareResult: null,
            resumeParseResult: null,
            jdParseResult: null,
            resumeOptimizeResult: null,
            interviewPrepResult: null,
            starInterviewAnswerResult: null,
            jobDeliveryPackageResult: null
        };
    },
    computed: {
        pageTitle() {
            const titles = {
                overview: "我的成长主页",
                knowledge: "学习资料库",
                chat: "AI 伴学",
                jobs: "求职工具箱"
            };
            return titles[this.activeTab] || "知途 AI";
        },
        isAuthenticated() {
            return Boolean(this.auth.accessToken && this.auth.user);
        },
        currentUserLabel() {
            if (!this.auth.user) {
                return "未登录";
            }
            return this.auth.user.displayName || this.auth.user.username;
        },
        authInitial() {
            return (this.currentUserLabel || "U").slice(0, 1).toUpperCase();
        },
        stats() {
            return {
                availableDocs: this.documents.filter(item => item.status === "AVAILABLE").length,
                processingDocs: this.documents.filter(item => item.status === "PROCESSING").length,
                failedDocs: this.documents.filter(item => item.status === "FAILED").length
            };
        },
        availableDocuments() {
            return this.documents.filter(item => item.status === "AVAILABLE" && item.fastApiDocumentId);
        },
        rawResponse() {
            return this.lastResponse ? JSON.stringify(this.lastResponse, null, 2) : "暂无接口响应";
        },
        normalizedJobResult() {
            return this.normalizeJobResult(this.jobResult);
        },
        normalizedResumeParseResult() {
            return this.normalizeResumeParseResult(this.resumeParseResult);
        },
        normalizedJdParseResult() {
            return this.normalizeJdParseResult(this.jdParseResult);
        },
        normalizedResumeOptimizeResult() {
            return this.normalizeResumeOptimizeResult(this.resumeOptimizeResult);
        },
        normalizedInterviewPrepResult() {
            return this.normalizeInterviewPrepResult(this.interviewPrepResult);
        },
        normalizedStarInterviewAnswerResult() {
            return this.normalizeStarInterviewAnswerResult(this.starInterviewAnswerResult);
        },
        normalizedJobDeliveryPackageResult() {
            return this.normalizeJobDeliveryPackageResult(this.jobDeliveryPackageResult);
        },
        jobResultText() {
            return this.jobResult ? JSON.stringify(this.jobResult, null, 2) : "暂无求职分析结果";
        },
        resumeParseResultText() {
            return this.resumeParseResult ? JSON.stringify(this.resumeParseResult, null, 2) : "暂无简历结构化结果";
        },
        jdParseResultText() {
            return this.jdParseResult ? JSON.stringify(this.jdParseResult, null, 2) : "暂无 JD 结构化结果";
        },
        resumeOptimizeResultText() {
            return this.resumeOptimizeResult ? JSON.stringify(this.resumeOptimizeResult, null, 2) : "暂无简历优化建议";
        },
        interviewPrepResultText() {
            return this.interviewPrepResult ? JSON.stringify(this.interviewPrepResult, null, 2) : "暂无面试准备包";
        },
        starInterviewAnswerResultText() {
            return this.starInterviewAnswerResult ? JSON.stringify(this.starInterviewAnswerResult, null, 2) : "暂无 STAR 面试答案";
        },
        jobDeliveryPackageResultText() {
            return this.jobDeliveryPackageResult ? JSON.stringify(this.jobDeliveryPackageResult, null, 2) : "暂无求职成品包";
        },
        selectedGeneratedTaskResultText() {
            if (!this.selectedGeneratedTask) {
                return "暂无生成历史详情";
            }
            return JSON.stringify(this.parseJsonObject(this.selectedGeneratedTask.resultJson), null, 2);
        }
    },
    mounted() {
        this.initializeAuthenticatedApp();
    },
    beforeUnmount() {
        if (this.chatAbortController) {
            this.chatAbortController.abort();
        }
        if (this.jobOperationTimer) {
            clearInterval(this.jobOperationTimer);
        }
    },
    methods: {
        async initializeAuthenticatedApp() {
            if (!this.auth.accessToken) {
                this.redirectToLogin("请先登录后使用工作台。");
                return;
            }
            try {
                const user = await this.requestJson("/api/auth/me");
                this.auth.user = user;
                localStorage.setItem("aikb.user", JSON.stringify(user));
                this.syncIdentityFromAuth();
                await this.loadInitialData();
            } catch (error) {
                if (this.isAuthenticated) {
                    this.setStatus(`恢复登录状态失败：${error.message}`);
                }
            }
        },
        clearAuthentication() {
            this.auth = {
                accessToken: "",
                tokenType: "Bearer",
                user: null
            };
            this.authForm = {
                username: "",
                password: "",
                displayName: "",
                department: ""
            };
            this.identity = {userId: "", department: ""};
            this.profileForm = {username: "", displayName: "", department: ""};
            this.resetUserWorkspaceState();
            localStorage.removeItem("aikb.accessToken");
            localStorage.removeItem("aikb.tokenType");
            localStorage.removeItem("aikb.user");
        },
        resetUserWorkspaceState() {
            if (this.chatAbortController) {
                this.chatAbortController.abort();
            }
            this.knowledgeBases = [];
            this.documents = [];
            this.sessions = [];
            this.messages = [];
            this.aiCallLogs = [];
            this.aiCallSummary = null;
            this.jobTasks = [];
            this.jobFavorites = [];
            this.jobGeneratedTasks = [];
            this.jobResumeVersions = [];
            this.selectedCompareTaskIds = [];
            this.selectedKnowledgeBaseId = "";
            this.selectedSessionId = "";
            this.selectedGeneratedTask = null;
            this.generatedTaskReviewComment = "";
            this.generatedTaskFilter = "";
            this.kbForm = {name: "", description: "", ownerId: "", department: ""};
            this.editingKnowledgeBaseId = "";
            this.kbEditForm = {name: "", description: "", department: ""};
            this.chatForm = {knowledgeBaseId: "", title: "", documentId: "", question: ""};
            this.chatStreaming = false;
            this.chatStreamStage = "";
            this.chatStreamStatus = "";
            this.chatAbortController = null;
            this.jobForm = {resumeText: "", jobDescription: "", interviewQuestion: ""};
            this.jobAttachmentFile = null;
            this.jobAttachmentWarnings = [];
            if (this.jobOperationTimer) {
                clearInterval(this.jobOperationTimer);
            }
            this.jobOperationTimer = null;
            this.jobOperation = {
                key: "",
                state: "idle",
                title: "",
                detail: "",
                startedAt: null,
                elapsedSeconds: 0
            };
            this.jobFavoriteForm = {jobTitle: "", companyName: "", sourceUrl: "", notes: ""};
            this.jobResumeVersionForm = {versionName: "", targetRole: "", notes: ""};
            this.jobResult = null;
            this.jobCompareResult = null;
            this.resumeParseResult = null;
            this.jdParseResult = null;
            this.resumeOptimizeResult = null;
            this.interviewPrepResult = null;
            this.starInterviewAnswerResult = null;
            this.jobDeliveryPackageResult = null;
            this.lastResponse = null;
            this.careerStage = "analyze";
            this.careerResultTab = "analysis";
            Object.keys(this.jobPages || {}).forEach(key => {
                this.jobPages[key] = {
                    ...this.jobPages[key],
                    page: 0,
                    totalElements: 0,
                    totalPages: 0,
                    first: true,
                    last: true
                };
            });
        },
        redirectToLogin(message = "登录状态已失效，请重新登录。") {
            this.clearAuthentication();
            this.authMode = "login";
            this.setStatus(message);
            if (this.$router && this.$route?.path !== "/") {
                this.$router.replace("/");
            }
        },
        submitAuthForm() {
            if (this.authMode === "login") {
                this.login();
            } else {
                this.register();
            }
        },
        syncIdentityFromAuth() {
            if (!this.auth.user) {
                return;
            }
            this.identity.userId = this.auth.user.username || this.identity.userId;
            this.identity.department = this.auth.user.department || this.identity.department;
            this.authForm.username = this.auth.user.username || this.authForm.username;
            this.authForm.displayName = this.auth.user.displayName || this.authForm.displayName;
            this.authForm.department = this.auth.user.department || this.authForm.department;
            this.profileForm = {
                username: this.auth.user.username || "",
                displayName: this.auth.user.displayName || "",
                department: this.auth.user.department || ""
            };
            this.onIdentityChange();
        },
        userQuery() {
            return `userId=${encodeURIComponent(this.identity.userId)}&department=${encodeURIComponent(this.identity.department)}`;
        },
        jobUserQuery() {
            return `userId=${encodeURIComponent(this.identity.userId)}`;
        },
        jobPageQuery(pageInfo) {
            return `${this.jobUserQuery()}&page=${encodeURIComponent(pageInfo.page)}&size=${encodeURIComponent(pageInfo.size)}`;
        },
        generatedTaskPageQuery() {
            const query = this.jobPageQuery(this.jobPages.generatedTasks);
            return this.generatedTaskFilter
                ? `${query}&taskType=${encodeURIComponent(this.generatedTaskFilter)}`
                : query;
        },
        applyJobPage(pageKey, targetKey, pageData) {
            this[targetKey] = pageData.content || [];
            this.jobPages[pageKey] = {
                ...this.jobPages[pageKey],
                page: pageData.page ?? 0,
                size: pageData.size ?? this.jobPages[pageKey].size,
                totalElements: pageData.totalElements ?? 0,
                totalPages: pageData.totalPages ?? 0,
                first: pageData.first ?? true,
                last: pageData.last ?? true
            };
        },
        resetJobPage(pageKey, targetKey) {
            this[targetKey] = [];
            this.jobPages[pageKey] = {
                ...this.jobPages[pageKey],
                page: 0,
                totalElements: 0,
                totalPages: 0,
                first: true,
                last: true
            };
        },
        pageTotal(pageInfo) {
            return Math.max(Number(pageInfo.totalPages || 0), 1);
        },
        parseJsonObject(jsonText) {
            try {
                return JSON.parse(jsonText || "{}");
            } catch (error) {
                return {};
            }
        },
        normalizeChatMessage(message) {
            return {
                ...message,
                role: String(message?.role || "").toLowerCase()
            };
        },
        setStatus(message) {
            this.statusMessage = message;
        },
        startJobOperation(key, title, detail) {
            if (this.jobOperation.state === "running") {
                this.setStatus(`“${this.jobOperation.title}”仍在运行，请稍候。`);
                return false;
            }
            if (this.jobOperationTimer) {
                clearInterval(this.jobOperationTimer);
            }
            this.jobOperation = {
                key,
                state: "running",
                title,
                detail,
                startedAt: Date.now(),
                elapsedSeconds: 0
            };
            this.jobOperationTimer = setInterval(() => {
                this.jobOperation.elapsedSeconds = Math.max(
                    0,
                    Math.floor((Date.now() - this.jobOperation.startedAt) / 1000)
                );
            }, 1000);
            this.setStatus(`${title}已开始，AI 正在处理。`);
            return true;
        },
        finishJobOperation(detail) {
            if (this.jobOperationTimer) {
                clearInterval(this.jobOperationTimer);
                this.jobOperationTimer = null;
            }
            this.jobOperation.state = "success";
            this.jobOperation.detail = detail;
            this.setStatus(detail);
        },
        failJobOperation(detail) {
            if (this.jobOperationTimer) {
                clearInterval(this.jobOperationTimer);
                this.jobOperationTimer = null;
            }
            this.jobOperation.state = "error";
            this.jobOperation.detail = detail;
            this.setStatus(detail);
        },
        isJobOperationRunning(key = "") {
            return this.jobOperation.state === "running"
                && (!key || this.jobOperation.key === key);
        },
        async requestJson(url, options = {}) {
            const requestOptions = {...options};
            const headers = new Headers(requestOptions.headers || {});
            if (this.auth.accessToken) {
                headers.set("Authorization", `${this.auth.tokenType || "Bearer"} ${this.auth.accessToken}`);
            }
            requestOptions.headers = headers;

            const response = await fetch(url, requestOptions);
            const contentType = response.headers.get("content-type") || "";
            const data = contentType.includes("application/json")
                ? await response.json()
                : {success: false, message: `接口未返回 JSON: ${url}`};
            this.lastResponse = data;
            if (response.status === 401 && url !== "/api/auth/login") {
                this.redirectToLogin(data.message || "登录状态已失效，请重新登录。");
            }
            if (!response.ok || data.success === false) {
                throw new Error(data.message || `HTTP ${response.status}`);
            }
            return data.data;
        },
        async requestNdjson(url, options, onEvent) {
            const requestOptions = {...options};
            const headers = new Headers(requestOptions.headers || {});
            if (this.auth.accessToken) {
                headers.set("Authorization", `${this.auth.tokenType || "Bearer"} ${this.auth.accessToken}`);
            }
            requestOptions.headers = headers;

            const response = await fetch(url, requestOptions);
            if (!response.ok) {
                const contentType = response.headers.get("content-type") || "";
                const errorData = contentType.includes("application/json")
                    ? await response.json()
                    : {message: `HTTP ${response.status}`};
                this.lastResponse = errorData;
                if (response.status === 401) {
                    this.redirectToLogin(errorData.message || "登录状态已失效，请重新登录。");
                }
                throw new Error(errorData.message || `HTTP ${response.status}`);
            }
            if (!response.body) {
                throw new Error("浏览器未收到可读取的流式响应");
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder("utf-8");
            let buffer = "";

            const consumeLines = async (flush = false) => {
                const lines = buffer.split("\n");
                buffer = flush ? "" : (lines.pop() || "");
                for (const line of lines) {
                    if (!line.trim()) {
                        continue;
                    }
                    const event = JSON.parse(line);
                    this.lastResponse = event;
                    await onEvent(event);
                }
                if (flush && buffer.trim()) {
                    const event = JSON.parse(buffer);
                    this.lastResponse = event;
                    await onEvent(event);
                    buffer = "";
                }
            };

            while (true) {
                const {done, value} = await reader.read();
                if (done) {
                    buffer += decoder.decode();
                    await consumeLines(true);
                    break;
                }
                buffer += decoder.decode(value, {stream: true});
                await consumeLines(false);
            }
        },
        applyAuthResponse(data, {redirect = true} = {}) {
            this.auth = {
                accessToken: data.accessToken,
                tokenType: data.tokenType || "Bearer",
                user: data.user
            };
            localStorage.setItem("aikb.accessToken", this.auth.accessToken);
            localStorage.setItem("aikb.tokenType", this.auth.tokenType);
            localStorage.setItem("aikb.user", JSON.stringify(this.auth.user));
            this.authForm.password = "";
            if (redirect && this.$router && this.$route?.path !== "/") {
                this.$router.replace("/");
            }
            this.syncIdentityFromAuth();
        },
        async register() {
            try {
                const data = await this.requestJson("/api/auth/register", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        username: this.authForm.username,
                        password: this.authForm.password,
                        displayName: this.authForm.displayName,
                        department: this.authForm.department
                    })
                });
                this.applyAuthResponse(data);
                await this.loadInitialData();
                this.setStatus(`注册成功：${this.currentUserLabel}`);
            } catch (error) {
                this.setStatus(`注册失败：${error.message}`);
            }
        },
        async login() {
            try {
                const data = await this.requestJson("/api/auth/login", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        username: this.authForm.username,
                        password: this.authForm.password
                    })
                });
                this.applyAuthResponse(data);
                await this.loadInitialData();
                this.setStatus(`登录成功：${this.currentUserLabel}`);
            } catch (error) {
                this.setStatus(`登录失败：${error.message}`);
            }
        },
        logout() {
            this.redirectToLogin("已安全退出，请重新登录。");
        },
        async loadCurrentUser() {
            try {
                const user = await this.requestJson("/api/auth/me");
                this.auth.user = user;
                localStorage.setItem("aikb.user", JSON.stringify(user));
                this.syncIdentityFromAuth();
                this.setStatus(`当前账号已刷新：${this.currentUserLabel}`);
            } catch (error) {
                this.setStatus(`刷新账号失败：${error.message}`);
            }
        },
        openProfileEditor() {
            if (!this.auth.user) {
                return;
            }
            this.profileForm = {
                username: this.auth.user.username || "",
                displayName: this.auth.user.displayName || "",
                department: this.auth.user.department || ""
            };
            this.profileDrawerOpen = true;
        },
        async updateProfile() {
            try {
                const data = await this.requestJson("/api/auth/me", {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        displayName: this.profileForm.displayName,
                        department: this.profileForm.department
                    })
                });
                this.applyAuthResponse(data, {redirect: false});
                this.profileDrawerOpen = false;
                this.setStatus("个人资料已更新。");
            } catch (error) {
                this.setStatus(`更新个人资料失败：${error.message}`);
            }
        },
        async loadInitialData() {
            try {
                await this.loadKnowledgeBases();
                await this.loadSessions();
                await this.loadJobTasks(false, 0);
                await this.loadJobFavorites(false, 0);
                await this.loadJobGeneratedTasks(false, 0);
                await this.loadResumeVersions(false, 0);
                await this.loadAiCallLogs(false);
                this.setStatus("数据已同步。");
            } catch (error) {
                this.setStatus(`同步失败：${error.message}`);
            }
        },
        async loadAiCallLogs(showStatus = true) {
            try {
                const [logs, summary] = await Promise.all([
                    this.requestJson("/api/ai-call-logs/recent"),
                    this.requestJson("/api/ai-call-logs/summary?hours=24")
                ]);
                this.aiCallLogs = logs;
                this.aiCallSummary = summary;
                if (showStatus) {
                    this.setStatus(`已加载 ${this.aiCallLogs.length} 条 AI 调用日志。`);
                }
            } catch (error) {
                this.aiCallLogs = [];
                this.aiCallSummary = null;
                this.setStatus(`读取 AI 调用日志失败：${error.message}`);
            }
        },
        async checkHealth() {
            try {
                const data = await this.requestJson("/api/health");
                this.setStatus(`Spring Boot 正常：${data.service} / ${data.status}`);
            } catch (error) {
                this.setStatus(`健康检查失败：${error.message}`);
            }
        },
        async loadKnowledgeBases() {
            this.knowledgeBases = await this.requestJson("/api/knowledge-bases");
            const selectionStillExists = this.knowledgeBases.some(
                item => String(item.id) === String(this.selectedKnowledgeBaseId)
            );
            if (!selectionStillExists) {
                this.selectedKnowledgeBaseId = this.knowledgeBases[0]?.id || "";
                this.chatForm.knowledgeBaseId = this.selectedKnowledgeBaseId;
            }
            if (this.selectedKnowledgeBaseId) {
                await this.loadDocuments();
            } else {
                this.documents = [];
                this.chatForm.documentId = "";
            }
        },
        async createKnowledgeBase() {
            try {
                const data = await this.requestJson("/api/knowledge-bases", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify(this.kbForm)
                });
                this.selectedKnowledgeBaseId = data.id;
                this.chatForm.knowledgeBaseId = data.id;
                this.kbForm = {
                    name: "",
                    description: "",
                    ownerId: this.identity.userId,
                    department: this.identity.department
                };
                await this.loadKnowledgeBases();
                this.setStatus(`知识库已创建：${data.name}`);
            } catch (error) {
                this.setStatus(`创建知识库失败：${error.message}`);
            }
        },
        selectKnowledgeBase(id) {
            this.selectedKnowledgeBaseId = id;
            this.chatForm.knowledgeBaseId = id;
            this.loadDocuments();
            this.setStatus("已选择知识库。");
        },
        startEditKnowledgeBase(knowledgeBase) {
            this.editingKnowledgeBaseId = knowledgeBase.id;
            this.kbEditForm = {
                name: knowledgeBase.name || "",
                description: knowledgeBase.description || "",
                department: knowledgeBase.department || this.identity.department || ""
            };
        },
        cancelEditKnowledgeBase() {
            this.editingKnowledgeBaseId = "";
            this.kbEditForm = {name: "", description: "", department: ""};
        },
        async updateKnowledgeBase() {
            if (!this.editingKnowledgeBaseId) {
                return;
            }
            try {
                const data = await this.requestJson(`/api/knowledge-bases/${this.editingKnowledgeBaseId}`, {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify(this.kbEditForm)
                });
                this.cancelEditKnowledgeBase();
                await this.loadKnowledgeBases();
                this.setStatus(`资料库已更新：${data.name}`);
            } catch (error) {
                this.setStatus(`更新资料库失败：${error.message}`);
            }
        },
        async deleteKnowledgeBase(knowledgeBase) {
            const confirmed = confirm(
                `确定删除资料库“${knowledgeBase.name}”吗？关联资料、向量和伴学会话也会一并删除，此操作不可恢复。`
            );
            if (!confirmed) {
                return;
            }
            try {
                await this.requestJson(`/api/knowledge-bases/${knowledgeBase.id}`, {method: "DELETE"});
                if (String(this.editingKnowledgeBaseId) === String(knowledgeBase.id)) {
                    this.cancelEditKnowledgeBase();
                }
                this.selectedSessionId = "";
                this.messages = [];
                this.chatForm.documentId = "";
                await this.loadKnowledgeBases();
                await this.loadSessions();
                this.setStatus(`资料库已删除：${knowledgeBase.name}`);
            } catch (error) {
                this.setStatus(`删除资料库失败：${error.message}`);
            }
        },
        async loadDocuments() {
            if (!this.selectedKnowledgeBaseId && this.chatForm.knowledgeBaseId) {
                this.selectedKnowledgeBaseId = this.chatForm.knowledgeBaseId;
            }
            if (!this.selectedKnowledgeBaseId) {
                this.documents = [];
                return;
            }
            try {
                this.documents = await this.requestJson(
                    `/api/knowledge-bases/${this.selectedKnowledgeBaseId}/documents?${this.userQuery()}`
                );
                const selectedDoc = this.availableDocuments[0];
                if (selectedDoc && !this.chatForm.documentId) {
                    this.chatForm.documentId = selectedDoc.fastApiDocumentId;
                }
            } catch (error) {
                this.documents = [];
                this.setStatus(`读取文档失败：${error.message}`);
            }
        },
        async uploadDocument(event) {
            const file = event.target.files && event.target.files[0];
            if (!file) {
                return;
            }
            if (!this.selectedKnowledgeBaseId) {
                this.setStatus("请先选择知识库。");
                event.target.value = "";
                return;
            }
            const supportedExtensions = [".pdf", ".md", ".markdown", ".docx", ".txt"];
            const normalizedFilename = String(file.name || "").toLowerCase();
            if (!supportedExtensions.some(extension => normalizedFilename.endsWith(extension))) {
                this.setStatus("上传失败：支持 PDF、Markdown、Word（DOCX）和 TXT 文件。");
                event.target.value = "";
                return;
            }
            try {
                const formData = new FormData();
                formData.append("file", file);
                const data = await this.requestJson(
                    `/api/knowledge-bases/${this.selectedKnowledgeBaseId}/documents?${this.userQuery()}`,
                    {method: "POST", body: formData}
                );
                await this.loadDocuments();
                this.setStatus(`文档已提交：${data.filename}`);
            } catch (error) {
                this.setStatus(`上传失败：${error.message}`);
            } finally {
                event.target.value = "";
            }
        },
        async loadSessions() {
            if (!this.identity.userId) {
                this.sessions = [];
                return;
            }
            this.sessions = await this.requestJson(`/api/chat/sessions?userId=${encodeURIComponent(this.identity.userId)}`);
            if (!this.selectedSessionId && this.sessions.length) {
                this.selectedSessionId = this.sessions[0].id;
                await this.loadMessages();
            }
        },
        async createSession() {
            if (!this.chatForm.knowledgeBaseId) {
                this.setStatus("请先选择知识库。");
                return;
            }
            try {
                const data = await this.requestJson("/api/chat/sessions", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        knowledgeBaseId: this.chatForm.knowledgeBaseId,
                        userId: this.identity.userId,
                        department: this.identity.department,
                        title: this.chatForm.title
                    })
                });
                this.selectedSessionId = data.id;
                await this.loadSessions();
                this.setStatus(`会话已创建：${data.title}`);
            } catch (error) {
                this.setStatus(`创建会话失败：${error.message}`);
            }
        },
        async onChatKnowledgeBaseChange() {
            this.selectedKnowledgeBaseId = this.chatForm.knowledgeBaseId;
            this.chatForm.documentId = "";
            await this.loadDocuments();
        },
        async loadMessages() {
            if (!this.selectedSessionId) {
                this.messages = [];
                return;
            }
            try {
                const messages = await this.requestJson(
                    `/api/chat/sessions/${this.selectedSessionId}/messages?${this.userQuery()}`
                );
                this.messages = messages.map(message => this.normalizeChatMessage(message));
            } catch (error) {
                this.messages = [];
                this.setStatus(`读取消息失败：${error.message}`);
            }
        },
        async askQuestion() {
            if (!this.selectedSessionId) {
                this.setStatus("请先选择或创建会话。");
                return;
            }
            if (!this.chatForm.documentId) {
                this.setStatus("请选择 AVAILABLE 文档。");
                return;
            }
            const question = String(this.chatForm.question || "").trim();
            if (!question) {
                this.setStatus("请输入问题。");
                return;
            }
            if (this.chatStreaming) {
                this.setStatus("当前回答仍在生成，请完成或停止后再提问。");
                return;
            }

            const optimisticId = `pending-${Date.now()}`;
            const createdAt = new Date().toISOString();
            const userMessage = {
                id: `${optimisticId}-user`,
                sessionId: this.selectedSessionId,
                role: "user",
                content: question,
                createdAt,
                optimistic: true
            };
            const assistantMessage = {
                id: `${optimisticId}-assistant`,
                sessionId: this.selectedSessionId,
                role: "assistant",
                content: "",
                sourcesJson: "",
                retrievalMode: "",
                createdAt,
                streaming: true,
                streamStatus: "问题已发送，正在连接 AI 服务"
            };
            this.messages.push(userMessage, assistantMessage);
            this.chatForm.question = "";
            this.chatStreaming = true;
            this.chatStreamStage = "connecting";
            this.chatStreamStatus = assistantMessage.streamStatus;
            this.chatAbortController = new AbortController();
            this.setStatus("问题已发送，AI 正在准备回答。");

            try {
                await this.requestNdjson(`/api/chat/sessions/${this.selectedSessionId}/ask/stream?${this.userQuery()}`, {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        question,
                        documentId: this.chatForm.documentId
                    }),
                    signal: this.chatAbortController.signal
                }, event => {
                    if (event.type === "accepted" && event.message) {
                        Object.assign(userMessage, this.normalizeChatMessage(event.message), {optimistic: false});
                        return;
                    }
                    if (event.type === "status") {
                        this.chatStreamStage = event.stage || "running";
                        this.chatStreamStatus = event.message || "AI 正在处理";
                        assistantMessage.streamStatus = this.chatStreamStatus;
                        this.setStatus(this.chatStreamStatus);
                        return;
                    }
                    if (event.type === "sources") {
                        assistantMessage.sourcesJson = JSON.stringify(event.sources || []);
                        assistantMessage.retrievalMode = event.retrieval_mode || "";
                        assistantMessage.rerankElapsedSeconds = event.rerank_elapsed_seconds ?? null;
                        return;
                    }
                    if (event.type === "delta") {
                        assistantMessage.content += event.content || "";
                        return;
                    }
                    if (event.type === "replace") {
                        assistantMessage.content = event.content || "";
                        assistantMessage.answerValidation = event.answer_validation || null;
                        return;
                    }
                    if (event.type === "verification") {
                        assistantMessage.answerValidation = event.answer_validation || null;
                        return;
                    }
                    if (event.type === "done" && event.message) {
                        Object.assign(assistantMessage, this.normalizeChatMessage(event.message), {
                            streaming: false,
                            streamStatus: ""
                        });
                        return;
                    }
                    if (event.type === "error") {
                        throw new Error(event.message || "AI 回答生成失败");
                    }
                });
                assistantMessage.streaming = false;
                assistantMessage.streamStatus = "";
                this.chatStreamStage = "completed";
                this.chatStreamStatus = "回答已完成";
                this.setStatus(assistantMessage.retrievalMode === "small_talk"
                    ? "回复已完成并保存。"
                    : "问答完成，回答和引用已保存。");
            } catch (error) {
                const stopped = error?.name === "AbortError";
                assistantMessage.streaming = false;
                assistantMessage.error = true;
                assistantMessage.streamStatus = "";
                if (!assistantMessage.content) {
                    assistantMessage.content = stopped
                        ? "已停止生成。你可以重新发送这个问题。"
                        : `回答生成失败：${error.message}`;
                }
                this.chatStreamStage = stopped ? "stopped" : "failed";
                this.chatStreamStatus = stopped ? "已停止生成" : `问答失败：${error.message}`;
                this.setStatus(this.chatStreamStatus);
            } finally {
                this.chatStreaming = false;
                this.chatAbortController = null;
            }
        },
        stopStreamingAnswer() {
            if (this.chatAbortController) {
                this.chatAbortController.abort();
            }
        },
        async parseResume() {
            if (!this.startJobOperation(
                "resume-parse",
                "解析简历",
                "正在提取技能、项目经历和候选人优势"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/resume/parse", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        resumeText: this.jobForm.resumeText
                    })
                });
                this.resumeParseResult = data;
                this.finishJobOperation(
                    `简历结构化完成，识别 ${this.normalizeResumeParseResult(data)?.projects.length ?? 0} 个项目。`
                );
            } catch (error) {
                this.failJobOperation(`简历结构化失败：${error.message}`);
            }
        },
        async parseJd() {
            if (!this.startJobOperation(
                "jd-parse",
                "解析岗位 JD",
                "正在提取岗位职责、必备技能和加分项"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/jd/parse", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        jobDescription: this.jobForm.jobDescription
                    })
                });
                this.jdParseResult = data;
                this.finishJobOperation(
                    `JD 结构化完成，识别 ${this.normalizeJdParseResult(data)?.requiredSkills.length ?? 0} 个必备技能。`
                );
            } catch (error) {
                this.failJobOperation(`JD 结构化失败：${error.message}`);
            }
        },
        async optimizeResume() {
            const targeted = Boolean(String(this.jobForm.jobDescription || "").trim());
            if (!this.startJobOperation(
                "resume-optimize",
                targeted ? "针对岗位优化简历" : "通用优化简历",
                targeted
                    ? "正在结合目标岗位提取差距、关键词和逐段改写建议"
                    : "未填写 JD，正在检查表达、量化成果、技术深度和信息完整性"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/resume/optimize", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        resumeText: this.jobForm.resumeText,
                        jobDescription: this.jobForm.jobDescription
                    })
                });
                this.resumeOptimizeResult = data;
                this.generatedTaskFilter = "RESUME_OPTIMIZE";
                await this.loadJobGeneratedTasks(false, 0);
                this.finishJobOperation(
                    `简历优化完成，生成 ${this.normalizeResumeOptimizeResult(data)?.rewriteSuggestions.length ?? 0} 条改写建议。`
                );
            } catch (error) {
                this.failJobOperation(`简历优化失败：${error.message}`);
            }
        },
        async prepareInterview() {
            if (!this.startJobOperation(
                "interview-prep",
                "生成面试准备包",
                "正在组织自我介绍、项目讲解、技术追问和行为问题"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/interview/prepare", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        resumeText: this.jobForm.resumeText,
                        jobDescription: this.jobForm.jobDescription
                    })
                });
                this.interviewPrepResult = data;
                this.generatedTaskFilter = "INTERVIEW_PREP";
                await this.loadJobGeneratedTasks(false, 0);
                this.finishJobOperation(
                    `面试准备包生成完成，包含 ${this.normalizeInterviewPrepResult(data)?.technicalQuestions.length ?? 0} 个技术追问。`
                );
            } catch (error) {
                this.failJobOperation(`面试准备失败：${error.message}`);
            }
        },
        async generateStarInterviewAnswer() {
            if (!this.startJobOperation(
                "star-answer",
                "生成 STAR 回答",
                "正在从简历经历中组织情境、任务、行动和结果"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/interview/star-answer", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        resumeText: this.jobForm.resumeText,
                        jobDescription: this.jobForm.jobDescription,
                        question: this.jobForm.interviewQuestion
                    })
                });
                this.starInterviewAnswerResult = data;
                this.generatedTaskFilter = "STAR_INTERVIEW_ANSWER";
                await this.loadJobGeneratedTasks(false, 0);
                this.finishJobOperation(
                    `STAR 答案生成完成，包含 ${this.normalizeStarInterviewAnswerResult(data)?.action.length ?? 0} 条行动要点。`
                );
            } catch (error) {
                this.failJobOperation(`生成 STAR 答案失败：${error.message}`);
            }
        },
        async generateJobDeliveryPackage() {
            if (!this.startJobOperation(
                "delivery-package",
                "生成求职成品包",
                "正在整合自我介绍、项目讲解、风险应对和练习清单"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/delivery-package", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        resumeText: this.jobForm.resumeText,
                        jobDescription: this.jobForm.jobDescription
                    })
                });
                this.jobDeliveryPackageResult = data;
                this.generatedTaskFilter = "JOB_DELIVERY_PACKAGE";
                await this.loadJobGeneratedTasks(false, 0);
                this.finishJobOperation(
                    `求职成品包生成完成，准备 ${this.normalizeJobDeliveryPackageResult(data)?.rehearsalChecklist.length ?? 0} 项练习清单。`
                );
            } catch (error) {
                this.failJobOperation(`生成求职成品包失败：${error.message}`);
            }
        },
        async analyzeJob() {
            if (!this.startJobOperation(
                "job-analyze",
                "分析岗位匹配度",
                "正在对比简历和岗位要求，识别优势、差距与面试问题"
            )) {
                return;
            }
            try {
                const data = await this.requestJson("/api/job-agent/analyze", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        resumeText: this.jobForm.resumeText,
                        jobDescription: this.jobForm.jobDescription
                    })
                });
                this.jobResult = data;
                await this.loadJobTasks(false, 0);
                this.finishJobOperation(`求职分析完成，匹配分：${data.match_score ?? data.matchScore ?? "-"}`);
            } catch (error) {
                this.failJobOperation(`求职分析失败：${error.message}`);
            }
        },
        onJobAttachmentChange(event) {
            this.jobAttachmentFile = event.target.files && event.target.files[0]
                ? event.target.files[0]
                : null;
        },
        async analyzeJobFromAttachment() {
            if (!this.jobAttachmentFile) {
                this.setStatus("请先选择 JD 截图或 PDF。");
                return;
            }
            if (!this.jobForm.resumeText || !this.jobForm.resumeText.trim()) {
                this.setStatus("请先填写简历文本。");
                return;
            }
            if (!this.startJobOperation(
                "attachment-analyze",
                "识别岗位附件并分析",
                "正在识别附件文字，随后将自动进行岗位匹配分析"
            )) {
                return;
            }
            try {
                const formData = new FormData();
                formData.append("userId", this.identity.userId);
                formData.append("resumeText", this.jobForm.resumeText);
                formData.append("file", this.jobAttachmentFile);
                const data = await this.requestJson("/api/job-agent/analyze-from-file", {
                    method: "POST",
                    body: formData
                });
                this.jobResult = data.analysis || data;
                this.jobForm.jobDescription = data.extractedJobDescription || this.jobForm.jobDescription;
                this.jobAttachmentWarnings = data.warnings || [];
                this.careerResultTab = "analysis";
                await this.loadJobTasks(false, 0);
                const score = this.jobResult.match_score ?? this.jobResult.matchScore ?? "-";
                this.finishJobOperation(`附件识别分析完成，JD 已自动回填，匹配分：${score}`);
            } catch (error) {
                this.failJobOperation(`附件识别分析失败：${error.message}`);
            }
        },
        async loadJobTasks(showStatus = true, page = this.jobPages.tasks.page) {
            if (!this.identity.userId) {
                this.resetJobPage("tasks", "jobTasks");
                return;
            }
            this.jobPages.tasks.page = Math.max(0, page);
            try {
                const pageData = await this.requestJson(`/api/job-agent/tasks/page?${this.jobPageQuery(this.jobPages.tasks)}`);
                this.applyJobPage("tasks", "jobTasks", pageData);
                const visibleTaskIds = new Set(this.jobTasks.map(task => task.id));
                this.selectedCompareTaskIds = this.selectedCompareTaskIds.filter(id => visibleTaskIds.has(id));
                if (showStatus) {
                    this.setStatus(`已加载第 ${this.jobPages.tasks.page + 1} 页求职分析历史，共 ${this.jobPages.tasks.totalElements} 条。`);
                }
            } catch (error) {
                this.resetJobPage("tasks", "jobTasks");
                this.setStatus(`读取求职分析历史失败：${error.message}`);
            }
        },
        async loadJobGeneratedTasks(showStatus = true, page = this.jobPages.generatedTasks.page) {
            if (!this.identity.userId) {
                this.resetJobPage("generatedTasks", "jobGeneratedTasks");
                this.selectedGeneratedTask = null;
                return;
            }
            this.jobPages.generatedTasks.page = Math.max(0, page);
            try {
                const pageData = await this.requestJson(`/api/job-agent/generated-tasks/page?${this.generatedTaskPageQuery()}`);
                this.applyJobPage("generatedTasks", "jobGeneratedTasks", pageData);
                if (this.selectedGeneratedTask && !this.jobGeneratedTasks.some(task => task.id === this.selectedGeneratedTask.id)) {
                    this.selectedGeneratedTask = null;
                }
                if (showStatus) {
                    const filterLabel = this.generatedTaskFilter ? this.jobGeneratedTaskTypeLabel(this.generatedTaskFilter) : "全部";
                    this.setStatus(`已加载第 ${this.jobPages.generatedTasks.page + 1} 页${filterLabel}生成历史，共 ${this.jobPages.generatedTasks.totalElements} 条。`);
                }
            } catch (error) {
                this.resetJobPage("generatedTasks", "jobGeneratedTasks");
                this.selectedGeneratedTask = null;
                this.setStatus(`读取生成历史失败：${error.message}`);
            }
        },
        async onGeneratedTaskFilterChange() {
            this.selectedGeneratedTask = null;
            await this.loadJobGeneratedTasks(true, 0);
        },
        async viewJobGeneratedTask(taskId) {
            try {
                const task = await this.requestJson(`/api/job-agent/generated-tasks/${encodeURIComponent(taskId)}?${this.jobUserQuery()}`);
                const result = this.parseJsonObject(task.resultJson);
                this.selectedGeneratedTask = task;
                this.generatedTaskReviewComment = task.reviewComment || "";
                this.jobForm.resumeText = task.resumeText || this.jobForm.resumeText;
                this.jobForm.jobDescription = task.jobDescription || this.jobForm.jobDescription;
                if (task.taskType === "RESUME_OPTIMIZE") {
                    this.resumeOptimizeResult = result;
                }
                if (task.taskType === "INTERVIEW_PREP") {
                    this.interviewPrepResult = result;
                }
                if (task.taskType === "STAR_INTERVIEW_ANSWER") {
                    this.starInterviewAnswerResult = result;
                    this.jobForm.interviewQuestion = result.question || this.jobForm.interviewQuestion;
                }
                this.setStatus(`已加载${this.jobGeneratedTaskTypeLabel(task.taskType)}历史。`);
            } catch (error) {
                this.setStatus(`读取生成历史详情失败：${error.message}`);
            }
        },
        async deleteJobGeneratedTask(taskId) {
            if (!confirm("确定删除这条生成历史吗？")) {
                return;
            }
            try {
                await this.requestJson(`/api/job-agent/generated-tasks/${encodeURIComponent(taskId)}?${this.jobUserQuery()}`, {
                    method: "DELETE"
                });
                if (this.selectedGeneratedTask?.id === taskId) {
                    this.selectedGeneratedTask = null;
                }
                await this.loadJobGeneratedTasks(false);
                this.setStatus("生成历史已删除。");
            } catch (error) {
                this.setStatus(`删除生成历史失败：${error.message}`);
            }
        },
        async reviewJobGeneratedTask(status) {
            if (!this.selectedGeneratedTask?.id) {
                return;
            }
            try {
                const task = await this.requestJson(
                    `/api/job-agent/generated-tasks/${encodeURIComponent(this.selectedGeneratedTask.id)}/review`,
                    {
                        method: "PATCH",
                        headers: {"Content-Type": "application/json"},
                        body: JSON.stringify({
                            userId: this.identity.userId,
                            status,
                            comment: this.generatedTaskReviewComment
                        })
                    }
                );
                this.selectedGeneratedTask = task;
                const index = this.jobGeneratedTasks.findIndex(item => item.id === task.id);
                if (index >= 0) {
                    this.jobGeneratedTasks.splice(index, 1, task);
                }
                this.setStatus(status === "APPROVED" ? "已确认这份 AI 内容。" : "已驳回这份 AI 内容。");
            } catch (error) {
                this.setStatus(`审核生成内容失败：${error.message}`);
            }
        },
        async createResumeVersion() {
            try {
                const data = await this.requestJson("/api/job-agent/resume-versions", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        versionName: this.jobResumeVersionForm.versionName,
                        targetRole: this.jobResumeVersionForm.targetRole,
                        resumeText: this.jobForm.resumeText,
                        notes: this.jobResumeVersionForm.notes
                    })
                });
                await this.loadResumeVersions(false, 0);
                this.setStatus(`简历版本已保存：${data.versionName}`);
            } catch (error) {
                this.setStatus(`保存简历版本失败：${error.message}`);
            }
        },
        async loadResumeVersions(showStatus = true, page = this.jobPages.resumeVersions.page) {
            if (!this.identity.userId) {
                this.resetJobPage("resumeVersions", "jobResumeVersions");
                return;
            }
            this.jobPages.resumeVersions.page = Math.max(0, page);
            try {
                const pageData = await this.requestJson(`/api/job-agent/resume-versions/page?${this.jobPageQuery(this.jobPages.resumeVersions)}`);
                this.applyJobPage("resumeVersions", "jobResumeVersions", pageData);
                if (showStatus) {
                    this.setStatus(`已加载第 ${this.jobPages.resumeVersions.page + 1} 页简历版本，共 ${this.jobPages.resumeVersions.totalElements} 个。`);
                }
            } catch (error) {
                this.resetJobPage("resumeVersions", "jobResumeVersions");
                this.setStatus(`读取简历版本失败：${error.message}`);
            }
        },
        useResumeVersion(version) {
            this.jobResumeVersionForm.versionName = version.versionName || "";
            this.jobResumeVersionForm.targetRole = version.targetRole || "";
            this.jobResumeVersionForm.notes = version.notes || "";
            this.jobForm.resumeText = version.resumeText || "";
            this.setStatus(`已使用简历版本：${version.versionName}`);
        },
        async updateResumeVersion(versionId) {
            try {
                const data = await this.requestJson(`/api/job-agent/resume-versions/${encodeURIComponent(versionId)}`, {
                    method: "PUT",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        versionName: this.jobResumeVersionForm.versionName,
                        targetRole: this.jobResumeVersionForm.targetRole,
                        resumeText: this.jobForm.resumeText,
                        notes: this.jobResumeVersionForm.notes
                    })
                });
                await this.loadResumeVersions(false);
                this.setStatus(`简历版本已更新：${data.versionName}`);
            } catch (error) {
                this.setStatus(`更新简历版本失败：${error.message}`);
            }
        },
        async deleteResumeVersion(versionId) {
            if (!confirm("确定删除这个简历版本吗？")) {
                return;
            }
            try {
                await this.requestJson(`/api/job-agent/resume-versions/${encodeURIComponent(versionId)}?${this.jobUserQuery()}`, {
                    method: "DELETE"
                });
                await this.loadResumeVersions(false);
                this.setStatus("简历版本已删除。");
            } catch (error) {
                this.setStatus(`删除简历版本失败：${error.message}`);
            }
        },
        async createJobFavorite() {
            try {
                const data = await this.requestJson("/api/job-agent/favorites", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        jobTitle: this.jobFavoriteForm.jobTitle,
                        companyName: this.jobFavoriteForm.companyName,
                        jobDescription: this.jobForm.jobDescription,
                        sourceUrl: this.jobFavoriteForm.sourceUrl,
                        notes: this.jobFavoriteForm.notes
                    })
                });
                await this.loadJobFavorites(false, 0);
                this.setStatus(`岗位已收藏：${data.jobTitle}`);
            } catch (error) {
                this.setStatus(`收藏岗位失败：${error.message}`);
            }
        },
        async loadJobFavorites(showStatus = true, page = this.jobPages.favorites.page) {
            if (!this.identity.userId) {
                this.resetJobPage("favorites", "jobFavorites");
                return;
            }
            this.jobPages.favorites.page = Math.max(0, page);
            try {
                const pageData = await this.requestJson(`/api/job-agent/favorites/page?${this.jobPageQuery(this.jobPages.favorites)}`);
                this.applyJobPage("favorites", "jobFavorites", pageData);
                if (showStatus) {
                    this.setStatus(`已加载第 ${this.jobPages.favorites.page + 1} 页收藏岗位，共 ${this.jobPages.favorites.totalElements} 个。`);
                }
            } catch (error) {
                this.resetJobPage("favorites", "jobFavorites");
                this.setStatus(`读取收藏岗位失败：${error.message}`);
            }
        },
        useFavoriteJob(favorite) {
            this.jobFavoriteForm.jobTitle = favorite.jobTitle || "";
            this.jobFavoriteForm.companyName = favorite.companyName || "";
            this.jobFavoriteForm.sourceUrl = favorite.sourceUrl || "";
            this.jobFavoriteForm.notes = favorite.notes || "";
            this.jobForm.jobDescription = favorite.jobDescription || "";
            this.setStatus(`已使用收藏岗位：${favorite.jobTitle}`);
        },
        async deleteJobFavorite(favoriteId) {
            if (!confirm("确定删除这个收藏岗位吗？")) {
                return;
            }
            try {
                await this.requestJson(`/api/job-agent/favorites/${encodeURIComponent(favoriteId)}?${this.jobUserQuery()}`, {
                    method: "DELETE"
                });
                await this.loadJobFavorites(false);
                this.setStatus("收藏岗位已删除。");
            } catch (error) {
                this.setStatus(`删除收藏岗位失败：${error.message}`);
            }
        },
        async compareSelectedJobTasks() {
            try {
                const data = await this.requestJson("/api/job-agent/tasks/compare", {
                    method: "POST",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({
                        userId: this.identity.userId,
                        taskIds: this.selectedCompareTaskIds
                    })
                });
                this.jobCompareResult = data;
                this.setStatus(`已对比 ${data.items.length} 条求职分析记录。`);
            } catch (error) {
                this.setStatus(`对比失败：${error.message}`);
            }
        },
        async viewJobTask(taskId) {
            try {
                const data = await this.requestJson(`/api/job-agent/tasks/${encodeURIComponent(taskId)}?${this.jobUserQuery()}`);
                this.jobResult = data;
                this.setStatus("已加载求职分析详情。");
            } catch (error) {
                this.setStatus(`读取求职分析详情失败：${error.message}`);
            }
        },
        async deleteJobTask(taskId) {
            if (!confirm("确定删除这条求职分析记录吗？")) {
                return;
            }
            try {
                await this.requestJson(`/api/job-agent/tasks/${encodeURIComponent(taskId)}?${this.jobUserQuery()}`, {
                    method: "DELETE"
                });
                await this.loadJobTasks(false);
                this.selectedCompareTaskIds = this.selectedCompareTaskIds.filter(id => id !== taskId);
                this.jobResult = null;
                this.setStatus("求职分析记录已删除。");
            } catch (error) {
                this.setStatus(`删除失败：${error.message}`);
            }
        },
        switchIdentity(userId, department) {
            this.identity.userId = userId;
            this.identity.department = department;
            this.onIdentityChange();
        },
        async onIdentityChange() {
            this.kbForm.ownerId = this.identity.userId;
            this.kbForm.department = this.identity.department;
            await this.loadInitialData();
        },
        statusClass(status) {
            if (status === "AVAILABLE") return "green";
            if (status === "FAILED") return "red";
            if (status === "PROCESSING") return "amber";
            return "";
        },
        jobGeneratedTaskTypeLabel(taskType) {
            if (taskType === "RESUME_OPTIMIZE") return "简历优化";
            if (taskType === "INTERVIEW_PREP") return "面试准备";
            if (taskType === "STAR_INTERVIEW_ANSWER") return "STAR 答案";
            if (taskType === "JOB_DELIVERY_PACKAGE") return "求职成品包";
            return taskType || "未知";
        },
        jobReviewStatusLabel(status) {
            if (status === "APPROVED") return "已确认";
            if (status === "REJECTED") return "已驳回";
            return "待本人审核";
        },
        isBestCompareItem(item) {
            return item?.taskId === this.jobCompareResult?.bestTaskId;
        },
        compareScoreGap(score) {
            return Math.max(0, Number(this.jobCompareResult?.bestScore || 0) - Number(score || 0));
        },
        scorePercent(score) {
            const value = Math.max(0, Math.min(100, Number(score || 0)));
            return `${value}%`;
        },
        compareScoreBadgeClass(score) {
            const value = Number(score || 0);
            if (value >= 85) return "green";
            if (value >= 70) return "amber";
            return "red";
        },
        compareScoreBarClass(score) {
            const value = Number(score || 0);
            if (value >= 85) return "";
            if (value >= 70) return "amber";
            return "red";
        },
        formatDate(value) {
            if (!value) return "-";
            return String(value).replace("T", " ").replace("Z", "");
        },
        normalizeJobResult(value) {
            if (!value) {
                return null;
            }

            let source = value;
            if (value.resultJson) {
                try {
                    source = JSON.parse(value.resultJson);
                } catch (error) {
                    source = {};
                }
            }

            const matchScore = source.match_score ?? source.matchScore ?? value.matchScore ?? "-";
            return {
                matchScore,
                matchedSkills: this.toList(source.matched_skills ?? source.matchedSkills),
                missingSkills: this.toList(source.missing_skills ?? source.missingSkills),
                strengths: this.toList(source.strengths),
                risks: this.toList(source.risks),
                suggestions: this.toList(source.suggestions),
                interviewQuestions: this.toList(source.interview_questions ?? source.interviewQuestions)
            };
        },
        normalizeResumeParseResult(value) {
            if (!value) {
                return null;
            }

            const projects = Array.isArray(value.projects)
                ? value.projects.map(project => ({
                    name: project?.name || "",
                    role: project?.role || "",
                    techStack: this.toList(project?.tech_stack ?? project?.techStack),
                    description: project?.description || "",
                    highlights: this.toList(project?.highlights)
                }))
                : [];

            return {
                targetRoles: this.toList(value.target_roles ?? value.targetRoles),
                skills: this.toList(value.skills),
                projects,
                workExperiences: this.toList(value.work_experiences ?? value.workExperiences),
                education: this.toList(value.education),
                certifications: this.toList(value.certifications),
                strengths: this.toList(value.strengths),
                keywords: this.toList(value.keywords)
            };
        },
        normalizeJdParseResult(value) {
            if (!value) {
                return null;
            }

            return {
                jobTitle: value.job_title ?? value.jobTitle ?? "未识别",
                seniority: value.seniority || "未识别",
                requiredSkills: this.toList(value.required_skills ?? value.requiredSkills),
                preferredSkills: this.toList(value.preferred_skills ?? value.preferredSkills),
                responsibilities: this.toList(value.responsibilities),
                requirements: this.toList(value.requirements),
                keywords: this.toList(value.keywords),
                risks: this.toList(value.risks)
            };
        },
        normalizeResumeOptimizeResult(value) {
            if (!value) {
                return null;
            }

            const rewriteSuggestions = Array.isArray(value.rewrite_suggestions ?? value.rewriteSuggestions)
                ? (value.rewrite_suggestions ?? value.rewriteSuggestions).map(item => ({
                    section: item?.section || "",
                    issue: item?.issue || "",
                    suggestion: item?.suggestion || "",
                    beforeText: item?.before_text ?? item?.beforeText ?? "",
                    afterText: item?.after_text ?? item?.afterText ?? "",
                    keywordsAdded: this.toList(item?.keywords_added ?? item?.keywordsAdded)
                }))
                : [];
            const allKeywordsAdded = [...new Set(
                rewriteSuggestions.flatMap(item => item.keywordsAdded)
            )];

            return {
                summary: value.summary || "暂无",
                targetPosition: value.target_position ?? value.targetPosition ?? "未识别",
                gapSummary: this.toList(value.gap_summary ?? value.gapSummary),
                rewriteSuggestions,
                missingKeywords: this.toList(value.missing_keywords ?? value.missingKeywords),
                actionItems: this.toList(value.action_items ?? value.actionItems),
                allKeywordsAdded
            };
        },
        normalizeInterviewPrepResult(value) {
            if (!value) {
                return null;
            }

            const normalizeQuestionAnswers = items => Array.isArray(items)
                ? items.map(item => ({
                    question: item?.question || "",
                    answerPoints: this.toList(item?.answer_points ?? item?.answerPoints)
                }))
                : [];
            const projectTalkingPoints = Array.isArray(value.project_talking_points ?? value.projectTalkingPoints)
                ? (value.project_talking_points ?? value.projectTalkingPoints).map(item => ({
                    projectName: item?.project_name ?? item?.projectName ?? "",
                    pitch: item?.pitch || "",
                    technicalDepth: this.toList(item?.technical_depth ?? item?.technicalDepth),
                    likelyFollowups: this.toList(item?.likely_followups ?? item?.likelyFollowups)
                }))
                : [];

            return {
                targetPosition: value.target_position ?? value.targetPosition ?? "未识别",
                selfIntroduction: value.self_introduction ?? value.selfIntroduction ?? "暂无",
                projectTalkingPoints,
                technicalQuestions: normalizeQuestionAnswers(value.technical_questions ?? value.technicalQuestions),
                behavioralQuestions: normalizeQuestionAnswers(value.behavioral_questions ?? value.behavioralQuestions),
                questionsToAsk: this.toList(value.questions_to_ask ?? value.questionsToAsk),
                preparationChecklist: this.toList(value.preparation_checklist ?? value.preparationChecklist)
            };
        },
        normalizeStarInterviewAnswerResult(value) {
            if (!value) {
                return null;
            }

            return {
                targetPosition: value.target_position ?? value.targetPosition ?? "未识别",
                question: value.question || "未识别",
                situation: value.situation || "暂无",
                task: value.task || "暂无",
                action: this.toList(value.action),
                result: value.result || "暂无",
                answer: value.answer || "暂无",
                highlights: this.toList(value.highlights),
                followUpQuestions: this.toList(value.follow_up_questions ?? value.followUpQuestions)
            };
        },
        normalizeJobDeliveryPackageResult(value) {
            if (!value) {
                return null;
            }

            return {
                targetPosition: value.target_position ?? value.targetPosition ?? "未识别",
                selfIntroduction: value.self_introduction ?? value.selfIntroduction ?? "暂无",
                projectPitch: value.project_pitch ?? value.projectPitch ?? "暂无",
                architectureTalkingPoints: this.toList(value.architecture_talking_points ?? value.architectureTalkingPoints),
                riskResponse: this.toList(value.risk_response ?? value.riskResponse),
                closingStatement: value.closing_statement ?? value.closingStatement ?? "暂无",
                rehearsalChecklist: this.toList(value.rehearsal_checklist ?? value.rehearsalChecklist)
            };
        },
        exportJobAnalysisReport() {
            const result = this.normalizedJobResult;
            if (!result) {
                this.setStatus("暂无求职分析结果可导出。");
                return;
            }
            const content = [
                "# 求职分析报告",
                "",
                `生成时间：${this.formatDate(new Date().toISOString())}`,
                "",
                "## 岗位 JD",
                "",
                this.jobForm.jobDescription || "暂无",
                "",
                "## 匹配结论",
                "",
                `匹配分：${result.matchScore}`,
                "",
                "## 匹配技能",
                "",
                this.markdownList(result.matchedSkills),
                "",
                "## 缺失技能",
                "",
                this.markdownList(result.missingSkills),
                "",
                "## 优势",
                "",
                this.markdownList(result.strengths),
                "",
                "## 风险",
                "",
                this.markdownList(result.risks),
                "",
                "## 建议",
                "",
                this.markdownList(result.suggestions),
                "",
                "## 面试题",
                "",
                this.markdownList(result.interviewQuestions),
                ""
            ].join("\n");
            this.downloadMarkdown("job-analysis-report.md", content);
            this.setStatus("求职分析报告已导出。");
        },
        exportResumeOptimizeReport() {
            const result = this.normalizedResumeOptimizeResult;
            if (!result) {
                this.setStatus("暂无简历优化建议可导出。");
                return;
            }
            const rewriteSections = result.rewriteSuggestions.length
                ? result.rewriteSuggestions.flatMap((item, index) => [
                    `### ${index + 1}. ${item.section || "未命名模块"}`,
                    "",
                    `问题：${item.issue || "暂无"}`,
                    "",
                    `建议：${item.suggestion || "暂无"}`,
                    "",
                    `原表述：${item.beforeText || "暂无"}`,
                    "",
                    `优化后：${item.afterText || "暂无"}`,
                    "",
                    "补入关键词：",
                    "",
                    this.markdownList(item.keywordsAdded),
                    ""
                ])
                : ["暂无改写建议", ""];
            const content = [
                "# 简历优化报告",
                "",
                `生成时间：${this.formatDate(new Date().toISOString())}`,
                "",
                `目标岗位：${result.targetPosition}`,
                "",
                "## 优化方向",
                "",
                result.summary || "暂无",
                "",
                "## 主要差距",
                "",
                this.markdownList(result.gapSummary),
                "",
                "## 缺失关键词",
                "",
                this.markdownList(result.missingKeywords),
                "",
                "## 行动项",
                "",
                this.markdownList(result.actionItems),
                "",
                "## 改写建议",
                "",
                ...rewriteSections
            ].join("\n");
            this.downloadMarkdown("resume-optimize-report.md", content);
            this.setStatus("简历优化报告已导出。");
        },
        exportInterviewPrepReport() {
            const result = this.normalizedInterviewPrepResult;
            if (!result) {
                this.setStatus("暂无面试准备包可导出。");
                return;
            }
            const projectSections = result.projectTalkingPoints.length
                ? result.projectTalkingPoints.flatMap((project, index) => [
                    `### ${index + 1}. ${project.projectName || "未命名项目"}`,
                    "",
                    project.pitch || "暂无项目讲解",
                    "",
                    "技术深度点：",
                    "",
                    this.markdownList(project.technicalDepth),
                    "",
                    "可能追问：",
                    "",
                    this.markdownList(project.likelyFollowups),
                    ""
                ])
                : ["暂无项目讲解", ""];
            const qaSections = (title, items) => [
                `## ${title}`,
                "",
                ...(items.length
                    ? items.flatMap((item, index) => [
                        `### ${index + 1}. ${item.question || "未命名问题"}`,
                        "",
                        this.markdownList(item.answerPoints),
                        ""
                    ])
                    : ["暂无", ""])
            ];
            const content = [
                "# 面试准备包",
                "",
                `生成时间：${this.formatDate(new Date().toISOString())}`,
                "",
                `目标岗位：${result.targetPosition}`,
                "",
                "## 自我介绍",
                "",
                result.selfIntroduction || "暂无",
                "",
                "## 项目讲解",
                "",
                ...projectSections,
                ...qaSections("技术追问", result.technicalQuestions),
                ...qaSections("行为问题", result.behavioralQuestions),
                "## 反问面试官",
                "",
                this.markdownList(result.questionsToAsk),
                "",
                "## 准备清单",
                "",
                this.markdownList(result.preparationChecklist),
                ""
            ].join("\n");
            this.downloadMarkdown("interview-prep-report.md", content);
            this.setStatus("面试准备包已导出。");
        },
        exportStarInterviewAnswerReport() {
            const result = this.normalizedStarInterviewAnswerResult;
            if (!result) {
                this.setStatus("暂无 STAR 面试答案可导出。");
                return;
            }
            const content = [
                "# STAR 面试答案",
                "",
                `生成时间：${this.formatDate(new Date().toISOString())}`,
                "",
                `目标岗位：${result.targetPosition}`,
                "",
                "## 面试问题",
                "",
                result.question || "暂无",
                "",
                "## 完整口述答案",
                "",
                result.answer || "暂无",
                "",
                "## STAR 拆解",
                "",
                `S 情境：${result.situation || "暂无"}`,
                "",
                `T 任务：${result.task || "暂无"}`,
                "",
                "A 行动：",
                "",
                this.markdownList(result.action),
                "",
                `R 结果：${result.result || "暂无"}`,
                "",
                "## 突出能力",
                "",
                this.markdownList(result.highlights),
                "",
                "## 可能追问",
                "",
                this.markdownList(result.followUpQuestions),
                ""
            ].join("\n");
            this.downloadMarkdown("star-interview-answer.md", content);
            this.setStatus("STAR 面试答案已导出。");
        },
        exportJobDeliveryPackageReport() {
            const result = this.normalizedJobDeliveryPackageResult;
            if (!result) {
                this.setStatus("暂无求职成品包可导出。");
                return;
            }
            const content = [
                "# 求职成品包",
                "",
                `生成时间：${this.formatDate(new Date().toISOString())}`,
                "",
                `目标岗位：${result.targetPosition}`,
                "",
                "## 自我介绍",
                "",
                result.selfIntroduction || "暂无",
                "",
                "## 项目讲解稿",
                "",
                result.projectPitch || "暂无",
                "",
                "## 架构讲点",
                "",
                this.markdownList(result.architectureTalkingPoints),
                "",
                "## 风险应对",
                "",
                this.markdownList(result.riskResponse),
                "",
                "## 收尾话术",
                "",
                result.closingStatement || "暂无",
                "",
                "## 练习清单",
                "",
                this.markdownList(result.rehearsalChecklist),
                ""
            ].join("\n");
            this.downloadMarkdown("job-delivery-package.md", content);
            this.setStatus("求职成品包已导出。");
        },
        markdownList(items) {
            const values = this.toList(items);
            return values.length
                ? values.map(item => `- ${item}`).join("\n")
                : "- 暂无";
        },
        downloadMarkdown(filename, content) {
            const blob = new Blob([content], {type: "text/markdown;charset=utf-8"});
            const url = URL.createObjectURL(blob);
            const link = document.createElement("a");
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            link.remove();
            URL.revokeObjectURL(url);
        },
        toList(value) {
            return Array.isArray(value)
                ? value.filter(item => item !== null && item !== undefined && String(item).trim() !== "")
                : [];
        },
        summarizeSources(sourcesJson) {
            try {
                const sources = JSON.parse(sourcesJson);
                if (!Array.isArray(sources) || sources.length === 0) {
                    return "暂无引用";
                }
                return sources.slice(0, 3).map((source, index) => {
                    const name = source.filename || source.document_id || "source";
                    const score = source.rerank_score
                        ?? source.fusion_score
                        ?? source.vector_score
                        ?? source.sparse_score
                        ?? "-";
                    return `${index + 1}. ${name} / score=${score}`;
                }).join("\n");
            } catch (error) {
                return sourcesJson;
            }
        }
    }

};
