<script setup>
import AppIcon from "./AppIcon.vue";

defineProps({
  model: {type: Object, required: true},
  open: {type: Boolean, default: false}
});

defineEmits(["close"]);
</script>

<template>
  <Teleport to="body">
    <Transition name="drawer">
      <div v-if="open" class="drawer-layer" @click.self="$emit('close')">
        <aside class="system-drawer" aria-label="系统运行状态">
          <div class="drawer-header">
            <div>
              <span class="eyebrow">开发者视图</span>
              <h2>运行状态</h2>
            </div>
            <button class="icon-button" @click="$emit('close')"><AppIcon name="close"/></button>
          </div>

          <div class="drawer-body">
            <section class="status-panel healthy">
              <span class="status-dot"></span>
              <div><strong>知途 AI 工作台</strong><p>{{ model.statusMessage }}</p></div>
            </section>

            <div class="action-row">
              <button class="btn secondary" @click="model.checkHealth"><AppIcon name="refresh" :size="17"/>健康检查</button>
              <button class="btn secondary" @click="model.loadCurrentUser"><AppIcon name="user" :size="17"/>刷新账号</button>
              <button class="btn secondary" @click="model.loadAiCallLogs"><AppIcon name="sparkles" :size="17"/>刷新日志</button>
            </div>

            <section class="drawer-section">
              <div class="section-heading compact"><h3>当前身份</h3></div>
              <dl class="identity-list">
                <div><dt>用户</dt><dd>{{ model.identity.userId }}</dd></div>
                <div><dt>学习方向</dt><dd>{{ model.identity.department }}</dd></div>
                <div><dt>资料库</dt><dd>{{ model.knowledgeBases.length }}</dd></div>
              </dl>
            </section>

            <section class="drawer-section">
              <div class="section-heading compact">
                <h3>24 小时 AI 指标</h3>
                <span v-if="model.aiCallSummary">{{ model.aiCallSummary.totalCalls }} 次</span>
              </div>
              <div v-if="model.aiCallSummary" class="ai-metric-grid">
                <article>
                  <small>成功率</small>
                  <strong>{{ Math.round((model.aiCallSummary.successRate || 0) * 1000) / 10 }}%</strong>
                </article>
                <article>
                  <small>P95 延迟</small>
                  <strong>{{ model.aiCallSummary.p95ElapsedMs }} ms</strong>
                </article>
                <article>
                  <small>Token</small>
                  <strong>{{ model.aiCallSummary.totalTokens }}</strong>
                </article>
                <article>
                  <small>估算费用</small>
                  <strong>¥{{ Number(model.aiCallSummary.estimatedCostYuan || 0).toFixed(4) }}</strong>
                </article>
              </div>
              <p v-else class="muted-text">暂无聚合指标。</p>
            </section>

            <section class="drawer-section">
              <div class="section-heading compact">
                <h3>最近 AI 调用</h3><span>{{ model.aiCallLogs.length }} 条</span>
              </div>
              <div v-if="model.aiCallLogs.length" class="log-list">
                <article v-for="log in model.aiCallLogs.slice(0, 6)" :key="log.id">
                  <strong>{{ log.operation || log.modelName || 'AI 调用' }}</strong>
                  <span>
                    {{ log.success === false ? '失败' : '成功' }}
                    · {{ log.elapsedMs }} ms
                    · {{ log.totalTokens || 0 }} Token
                  </span>
                </article>
              </div>
              <p v-else class="muted-text">暂无调用记录。</p>
            </section>

            <details class="developer-raw">
              <summary>查看最近接口响应</summary>
              <pre>{{ model.rawResponse }}</pre>
            </details>

            <a class="debug-link" href="/debug.html" target="_blank">
              打开独立联调页 <AppIcon name="arrow" :size="16"/>
            </a>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
