<script setup>
import {computed, inject, ref} from "vue";
import AppIcon from "@/components/AppIcon.vue";
import EmptyState from "@/components/EmptyState.vue";

const app = inject("workspace");
const dragging = ref(false);

const selectedKnowledgeBase = computed(() => app.knowledgeBases.find(item => String(item.id) === String(app.selectedKnowledgeBaseId)) || null);

function handleDrop(event) {
  dragging.value = false;
  if (!event.dataTransfer?.files?.length) return;
  app.uploadDocument({target: {files: event.dataTransfer.files, value: ""}});
}

function enterStudy() {
  if (app.selectedKnowledgeBaseId) {
    app.chatForm.knowledgeBaseId = app.selectedKnowledgeBaseId;
  }
  app.navigateTo("chat");
}
</script>

<template>
  <div class="page-stack library-page">
    <header class="page-heading">
      <div>
        <span class="eyebrow">学习资料库</span>
        <h1>把零散资料，整理成可提问的知识空间</h1>
        <p>按学习方向管理八股文、课程讲义和技术文档，解析完成后即可进入 AI 伴学。</p>
      </div>
      <button class="btn primary" @click="enterStudy"><AppIcon name="sparkles" :size="18"/>进入 AI 伴学</button>
    </header>

    <section class="library-layout">
      <div class="library-main">
        <div class="section-heading">
          <div><h2>我的资料库</h2><p>{{ app.knowledgeBases.length }} 个学习空间</p></div>
          <button class="icon-button" @click="app.loadKnowledgeBases" title="刷新资料库"><AppIcon name="refresh" :size="18"/></button>
        </div>

        <div v-if="app.knowledgeBases.length" class="knowledge-card-grid">
          <article
            v-for="kb in app.knowledgeBases"
            :key="kb.id"
            class="knowledge-card-shell"
          >
            <button
              class="knowledge-card"
              :class="{selected: String(app.selectedKnowledgeBaseId) === String(kb.id)}"
              @click="app.selectKnowledgeBase(kb.id)"
            >
              <span class="knowledge-icon"><AppIcon name="library" :size="22"/></span>
              <div class="knowledge-copy">
                <div><strong>{{ kb.name }}</strong><span>{{ kb.department || '综合学习' }}</span></div>
                <p>{{ kb.description || '还没有描述，可以从上传第一份资料开始。' }}</p>
              </div>
              <span class="selected-check"><AppIcon name="check" :size="15"/></span>
            </button>
            <div class="knowledge-card-actions">
              <button type="button" title="编辑资料库" :aria-label="`编辑资料库 ${kb.name}`" @click="app.startEditKnowledgeBase(kb)">
                <AppIcon name="edit" :size="15"/>
              </button>
              <button type="button" class="danger" title="删除资料库" :aria-label="`删除资料库 ${kb.name}`" @click="app.deleteKnowledgeBase(kb)">
                <AppIcon name="trash" :size="15"/>
              </button>
            </div>
          </article>
        </div>
        <EmptyState v-else icon="library" title="创建你的第一个学习资料库" description="例如 Java 面试八股、AI 应用开发、计算机网络或考研笔记。"/>

        <section class="surface-card document-card">
          <div class="section-heading">
            <div>
              <span class="eyebrow">{{ selectedKnowledgeBase ? selectedKnowledgeBase.name : '尚未选择资料库' }}</span>
              <h2>资料与解析状态</h2>
            </div>
            <div class="document-summary">
              <span class="ready"><i></i>{{ app.stats.availableDocs }} 可学习</span>
              <span class="processing"><i></i>{{ app.stats.processingDocs }} 解析中</span>
              <span v-if="app.stats.failedDocs" class="failed"><i></i>{{ app.stats.failedDocs }} 失败</span>
            </div>
          </div>

          <div v-if="app.documents.length" class="responsive-table">
            <table>
              <thead><tr><th>资料名称</th><th>状态</th><th>知识片段</th><th>最近更新</th></tr></thead>
              <tbody>
                <tr v-for="doc in app.documents" :key="doc.id">
                  <td><div class="file-name"><span><AppIcon name="document" :size="18"/></span><strong>{{ doc.filename }}</strong></div></td>
                  <td><span class="status-badge" :class="app.statusClass(doc.status)">{{ doc.status === 'AVAILABLE' ? '可学习' : doc.status === 'PROCESSING' ? '解析中' : doc.status === 'FAILED' ? '解析失败' : doc.status }}</span></td>
                  <td>{{ doc.chunkCount || 0 }}</td>
                  <td>{{ app.formatDate(doc.updatedAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <EmptyState v-else icon="document" title="这里还没有资料" description="选择资料库并上传 PDF、Markdown、Word 或文本资料。"/>
        </section>
      </div>

      <aside class="library-side">
        <section v-if="app.editingKnowledgeBaseId" class="surface-card create-library-card edit-library-card">
          <div class="section-heading compact">
            <div><span class="eyebrow">管理空间</span><h2>编辑资料库</h2></div>
            <button class="icon-button" type="button" title="取消编辑" @click="app.cancelEditKnowledgeBase"><AppIcon name="close"/></button>
          </div>
          <label class="form-field"><span>资料库名称</span><input v-model.trim="app.kbEditForm.name" maxlength="100" placeholder="例如：Java 面试八股"></label>
          <label class="form-field"><span>学习方向</span><input v-model.trim="app.kbEditForm.department" maxlength="100" placeholder="例如：后端开发"></label>
          <label class="form-field"><span>学习目标</span><textarea v-model.trim="app.kbEditForm.description" maxlength="2000" rows="3" placeholder="准备面试、课程复习或项目学习"></textarea></label>
          <div class="edit-library-actions">
            <button class="btn secondary" type="button" @click="app.cancelEditKnowledgeBase">取消</button>
            <button class="btn primary" type="button" @click="app.updateKnowledgeBase"><AppIcon name="check" :size="17"/>保存修改</button>
          </div>
        </section>

        <section v-else class="surface-card create-library-card">
          <div class="section-heading compact">
            <div><span class="eyebrow">新建空间</span><h2>创建资料库</h2></div>
            <span class="soft-icon"><AppIcon name="plus"/></span>
          </div>
          <label class="form-field"><span>资料库名称</span><input v-model.trim="app.kbForm.name" placeholder="例如：Java 面试八股"></label>
          <label class="form-field"><span>学习方向</span><input v-model.trim="app.kbForm.department" placeholder="例如：后端开发"></label>
          <label class="form-field"><span>学习目标</span><textarea v-model.trim="app.kbForm.description" rows="3" placeholder="准备面试、课程复习或项目学习"></textarea></label>
          <button class="btn primary full" @click="app.createKnowledgeBase"><AppIcon name="plus" :size="17"/>创建资料库</button>
        </section>

        <section class="surface-card upload-card">
          <div class="section-heading compact">
            <div><span class="eyebrow">添加资料</span><h2>上传并解析</h2></div>
          </div>
          <label
            class="upload-zone"
            :class="{dragging, disabled: !app.selectedKnowledgeBaseId}"
            @dragenter.prevent="dragging = true"
            @dragover.prevent="dragging = true"
            @dragleave.prevent="dragging = false"
            @drop.prevent="handleDrop"
          >
            <input type="file" accept=".pdf,.md,.markdown,.docx,.txt" :disabled="!app.selectedKnowledgeBaseId" @change="app.uploadDocument">
            <span><AppIcon name="upload" :size="25"/></span>
            <strong>{{ app.selectedKnowledgeBaseId ? '拖拽或点击上传资料' : '请先选择资料库' }}</strong>
            <small>PDF · Markdown · Word（DOCX）· TXT</small>
          </label>
          <p class="upload-tip"><AppIcon name="clock" :size="15"/>上传后会自动解析、切分并写入向量库。</p>
        </section>
      </aside>
    </section>
  </div>
</template>
