<script setup>
import {inject, nextTick, ref, watch} from "vue";
import AppIcon from "@/components/AppIcon.vue";
import EmptyState from "@/components/EmptyState.vue";
import CitationList from "@/components/CitationList.vue";

const app = inject("workspace");
const conversationBody = ref(null);

const promptSuggestions = [
  "总结这份资料的核心知识框架",
  "生成 5 道由浅入深的面试题",
  "找出容易混淆的概念并进行对比",
  "结合实际项目举例解释这个知识点"
];

function choosePrompt(prompt) {
  app.chatForm.question = prompt;
}

function chooseSession(sessionId) {
  if (app.chatStreaming) {
    app.setStatus("请先完成或停止当前回答，再切换会话。");
    return;
  }
  app.selectedSessionId = sessionId;
  app.loadMessages();
}

function scrollConversationToBottom() {
  nextTick(() => {
    if (conversationBody.value) {
      conversationBody.value.scrollTop = conversationBody.value.scrollHeight;
    }
  });
}

watch(
  [
    () => app.messages.length,
    () => app.messages[app.messages.length - 1]?.content,
    () => app.chatStreamStatus
  ],
  scrollConversationToBottom
);
</script>

<template>
  <div class="study-page">
    <header class="page-heading study-heading">
      <div>
        <span class="eyebrow">AI 伴学</span>
        <h1>让 AI 基于你的资料，陪你理解与复习</h1>
        <p>回答会保留检索模式和资料来源，方便你回到原文继续学习。</p>
      </div>
      <div class="study-context-pill"><span class="status-dot"></span>{{ app.availableDocuments.length }} 份资料可提问</div>
    </header>

    <section class="study-workspace">
      <aside class="study-sidebar">
        <div class="study-sidebar-head">
          <div><span class="eyebrow">学习会话</span><h2>最近对话</h2></div>
          <button class="icon-button" @click="app.loadSessions" title="刷新会话"><AppIcon name="refresh" :size="17"/></button>
        </div>

        <div class="new-session-box">
          <label class="form-field compact"><span>学习资料库</span>
            <select v-model="app.chatForm.knowledgeBaseId" @change="app.onChatKnowledgeBaseChange">
              <option value="">请选择资料库</option>
              <option v-for="kb in app.knowledgeBases" :key="kb.id" :value="kb.id">{{ kb.name }}</option>
            </select>
          </label>
          <label class="form-field compact"><span>新会话标题</span><input v-model.trim="app.chatForm.title" placeholder="例如：Java 并发复习"></label>
          <button class="btn soft full" @click="app.createSession"><AppIcon name="plus" :size="17"/>创建学习会话</button>
        </div>

        <div v-if="app.sessions.length" class="study-session-list">
          <button v-for="session in app.sessions" :key="session.id" :class="{active: String(app.selectedSessionId) === String(session.id)}" @click="chooseSession(session.id)">
            <span><AppIcon name="chat" :size="17"/></span>
            <div><strong>{{ session.title }}</strong><small>{{ app.formatDate(session.updatedAt || session.createdAt) }}</small></div>
          </button>
        </div>
        <EmptyState v-else icon="chat" title="暂无学习会话" description="选择资料库后创建第一次对话。"/>
      </aside>

      <div class="conversation-panel">
        <div class="conversation-head">
          <div>
            <span class="ai-avatar"><AppIcon name="sparkles" :size="20"/></span>
            <div><strong>{{ app.sessions.find(item => String(item.id) === String(app.selectedSessionId))?.title || '知途 AI 伴学助手' }}</strong><small>基于资料检索并标注回答来源</small></div>
          </div>
          <button class="icon-button" @click="app.loadMessages" title="刷新消息"><AppIcon name="refresh" :size="17"/></button>
        </div>

        <div ref="conversationBody" class="conversation-body" aria-live="polite">
          <template v-if="app.messages.length">
            <article v-for="message in app.messages" :key="message.id" class="chat-message" :class="[message.role, {streaming: message.streaming, error: message.error}]">
              <span class="message-avatar">{{ message.role === 'user' ? app.authInitial : '知' }}</span>
              <div class="message-content">
                <div class="message-bubble">
                  <span v-if="message.content">{{ message.content }}</span>
                  <span v-if="message.streaming && message.content" class="stream-cursor" aria-hidden="true"></span>
                  <span v-if="message.streaming && !message.content" class="typing-indicator" aria-label="AI 正在处理">
                    <i></i><i></i><i></i>
                  </span>
                </div>
                <div v-if="message.streaming" class="message-progress">
                  <span class="mini-spinner"></span>{{ message.streamStatus || app.chatStreamStatus }}
                </div>
                <div class="message-meta">
                  <span>{{ message.role === 'user' ? '你' : '知途 AI' }}</span>
                  <span>{{ app.formatDate(message.createdAt) }}</span>
                  <span v-if="message.retrievalMode" class="retrieval-tag">{{ message.retrievalMode }}</span>
                </div>
                <CitationList v-if="message.sourcesJson" :sources-json="message.sourcesJson"/>
              </div>
            </article>
          </template>
          <div v-else class="conversation-welcome">
            <span><AppIcon name="sparkles" :size="28"/></span>
            <h2>今天想学点什么？</h2>
            <p>选择已解析资料，然后从下面的问题开始。</p>
            <div class="suggestion-chips">
              <button v-for="prompt in promptSuggestions" :key="prompt" @click="choosePrompt(prompt)">{{ prompt }}</button>
            </div>
          </div>
        </div>

        <div class="composer">
          <div class="composer-context">
            <AppIcon name="document" :size="16"/>
            <select v-model="app.chatForm.documentId">
              <option value="">选择要学习的已解析资料</option>
              <option v-for="doc in app.availableDocuments" :key="doc.id" :value="doc.fastApiDocumentId">{{ doc.filename }}</option>
            </select>
          </div>
          <div class="composer-input">
            <textarea
              v-model="app.chatForm.question"
              rows="2"
              :disabled="app.chatStreaming"
              placeholder="输入你的问题，例如：用项目案例解释 Spring 事务传播行为"
              @keydown.enter.exact.prevent="app.askQuestion"
            ></textarea>
            <button
              class="send-button"
              :disabled="app.chatStreaming || !app.selectedSessionId || !app.chatForm.documentId || !app.chatForm.question.trim()"
              @click="app.askQuestion"
            >
              <AppIcon name="arrow" :size="20"/>
            </button>
          </div>
          <div class="composer-footer">
            <small v-if="app.chatStreaming"><span class="mini-spinner"></span>{{ app.chatStreamStatus }}</small>
            <small v-else>Enter 发送，Shift + Enter 换行。重要知识点请结合引用原文核对。</small>
            <button v-if="app.chatStreaming" type="button" @click="app.stopStreamingAnswer">停止生成</button>
          </div>
        </div>
      </div>

      <aside class="study-guide">
        <section>
          <span class="soft-icon mint"><AppIcon name="target"/></span>
          <h3>高效学习提示</h3>
          <ol>
            <li><span>1</span>先让 AI 总结知识框架</li>
            <li><span>2</span>追问不理解的细节</li>
            <li><span>3</span>生成面试题检验掌握度</li>
          </ol>
        </section>
        <section>
          <span class="eyebrow">当前资料</span>
          <div v-if="app.availableDocuments.length" class="context-doc-list">
            <button v-for="doc in app.availableDocuments.slice(0, 5)" :key="doc.id" :class="{active: app.chatForm.documentId === doc.fastApiDocumentId}" @click="app.chatForm.documentId = doc.fastApiDocumentId">
              <AppIcon name="document" :size="16"/><span>{{ doc.filename }}</span>
            </button>
          </div>
          <p v-else class="muted-text">当前资料库还没有可学习文档。</p>
        </section>
      </aside>
    </section>
  </div>
</template>
