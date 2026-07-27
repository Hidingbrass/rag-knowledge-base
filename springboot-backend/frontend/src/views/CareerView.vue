<script setup>
import {inject} from "vue";
import AppIcon from "@/components/AppIcon.vue";
import EmptyState from "@/components/EmptyState.vue";

const app = inject("workspace");

const stages = [
  {id: "analyze", index: "01", icon: "target", title: "岗位分析", subtitle: "识别匹配与差距"},
  {id: "resume", index: "02", icon: "document", title: "简历优化", subtitle: "沉淀针对性版本"},
  {id: "interview", index: "03", icon: "chat", title: "面试准备", subtitle: "项目讲解与 STAR"},
  {id: "deliver", index: "04", icon: "briefcase", title: "交付与记录", subtitle: "成品包和历史复盘"}
];

const generatedTaskTypes = [
  {value: "", label: "全部类型"},
  {value: "RESUME_OPTIMIZE", label: "简历优化"},
  {value: "INTERVIEW_PREP", label: "面试准备"},
  {value: "STAR_INTERVIEW_ANSWER", label: "STAR 答案"},
  {value: "JOB_DELIVERY_PACKAGE", label: "求职成品包"}
];

function switchStage(stage) {
  app.careerStage = stage;
  window.scrollTo({top: 0, behavior: "smooth"});
}
</script>

<template>
  <div class="page-stack career-page">
    <header class="page-heading career-heading">
      <div>
        <span class="eyebrow">求职工具箱</span>
        <h1>把学习成果，转化为更有说服力的求职表达</h1>
        <p>围绕真实岗位完成匹配分析、简历优化、面试准备和交付材料沉淀。</p>
      </div>
      <div class="career-overview-chip"><AppIcon name="briefcase" :size="18"/><strong>{{ app.jobPages.tasks.totalElements }}</strong> 次岗位分析</div>
    </header>

    <section
      v-if="app.jobOperation.state !== 'idle'"
      class="ai-operation-banner"
      :class="app.jobOperation.state"
      aria-live="polite"
    >
      <span class="operation-icon">
        <span v-if="app.jobOperation.state === 'running'" class="operation-spinner"></span>
        <AppIcon v-else :name="app.jobOperation.state === 'success' ? 'check' : 'close'" :size="19"/>
      </span>
      <div>
        <strong>{{ app.jobOperation.title }}</strong>
        <p>{{ app.jobOperation.detail }}</p>
      </div>
      <span class="operation-time">
        {{ app.jobOperation.state === 'running' ? `已运行 ${app.jobOperation.elapsedSeconds} 秒` : (app.jobOperation.state === 'success' ? '已完成' : '运行失败') }}
      </span>
    </section>

    <nav class="career-steps" aria-label="求职工作流">
      <button v-for="stage in stages" :key="stage.id" :class="{active: app.careerStage === stage.id}" @click="switchStage(stage.id)">
        <span class="step-index">{{ stage.index }}</span>
        <span class="step-icon"><AppIcon :name="stage.icon" :size="20"/></span>
        <span class="step-copy"><strong>{{ stage.title }}</strong><small>{{ stage.subtitle }}</small></span>
        <AppIcon name="chevron" :size="16"/>
      </button>
    </nav>

    <section v-if="app.careerStage === 'analyze'" class="career-stage">
      <div class="career-input-grid">
        <section class="surface-card career-form-card">
          <div class="section-heading">
            <div><span class="eyebrow">候选人画像</span><h2>粘贴你的简历内容</h2></div>
            <span class="soft-icon"><AppIcon name="user"/></span>
          </div>
          <label class="form-field"><span>简历文本</span><textarea v-model="app.jobForm.resumeText" rows="12" placeholder="粘贴教育、技能、项目与工作经历"></textarea></label>
          <div class="button-cluster">
            <button class="btn soft" :disabled="app.isJobOperationRunning()" @click="app.parseResume"><span v-if="app.isJobOperationRunning('resume-parse')" class="mini-spinner"></span><AppIcon v-else name="sparkles" :size="17"/>{{ app.isJobOperationRunning('resume-parse') ? '正在解析…' : '解析简历' }}</button>
            <button class="btn secondary" @click="app.careerStage = 'resume'"><AppIcon name="document" :size="17"/>管理简历版本</button>
          </div>
        </section>

        <section class="surface-card career-form-card">
          <div class="section-heading">
            <div><span class="eyebrow">目标机会</span><h2>输入目标岗位 JD</h2></div>
            <span class="soft-icon mint"><AppIcon name="target"/></span>
          </div>
          <label class="form-field"><span>岗位描述</span><textarea v-model="app.jobForm.jobDescription" rows="8" placeholder="粘贴岗位职责和任职要求"></textarea></label>
          <label class="attachment-zone">
            <input type="file" accept="application/pdf,image/png,image/jpeg,image/webp" @change="app.onJobAttachmentChange">
            <AppIcon name="upload" :size="19"/>
            <span>{{ app.jobAttachmentFile ? app.jobAttachmentFile.name : '也可以上传 JD 截图或 PDF' }}</span>
          </label>
          <p v-if="app.jobAttachmentWarnings.length" class="attachment-warning">{{ app.jobAttachmentWarnings.join('；') }}</p>
          <div class="button-cluster">
            <button class="btn soft" :disabled="app.isJobOperationRunning()" @click="app.parseJd"><span v-if="app.isJobOperationRunning('jd-parse')" class="mini-spinner"></span><AppIcon v-else name="sparkles" :size="17"/>{{ app.isJobOperationRunning('jd-parse') ? '正在解析…' : '解析 JD' }}</button>
            <button class="btn primary" :disabled="app.isJobOperationRunning()" @click="app.analyzeJob"><span v-if="app.isJobOperationRunning('job-analyze')" class="mini-spinner"></span><AppIcon v-else name="target" :size="17"/>{{ app.isJobOperationRunning('job-analyze') ? '正在分析…' : '分析匹配度' }}</button>
            <button class="btn secondary" :disabled="app.isJobOperationRunning() || !app.jobAttachmentFile" @click="app.analyzeJobFromAttachment"><span v-if="app.isJobOperationRunning('attachment-analyze')" class="mini-spinner"></span>{{ app.isJobOperationRunning('attachment-analyze') ? '正在识别并分析…' : '识别附件并分析' }}</button>
          </div>
        </section>
      </div>

      <section class="surface-card result-workbench">
        <div class="section-heading result-heading">
          <div><span class="eyebrow">AI 分析结果</span><h2>找到优势，也看清下一步</h2></div>
          <div class="result-tabs">
            <button :class="{active: app.careerResultTab === 'analysis'}" @click="app.careerResultTab = 'analysis'">匹配分析</button>
            <button :class="{active: app.careerResultTab === 'resume'}" @click="app.careerResultTab = 'resume'">简历画像</button>
            <button :class="{active: app.careerResultTab === 'jd'}" @click="app.careerResultTab = 'jd'">岗位画像</button>
          </div>
        </div>

        <div v-if="app.careerResultTab === 'analysis'">
          <div v-if="app.isJobOperationRunning('job-analyze') || app.isJobOperationRunning('attachment-analyze')" class="result-skeleton">
            <i></i><i></i><i></i><span>AI 正在整理匹配结果</span>
          </div>
          <template v-else-if="app.normalizedJobResult">
            <div class="match-summary">
              <div class="score-ring" :style="{'--score': app.scorePercent(app.normalizedJobResult.matchScore)}">
                <strong>{{ app.normalizedJobResult.matchScore }}</strong><span>岗位匹配分</span>
              </div>
              <div class="skill-summary">
                <div><h3>已匹配技能</h3><div class="chips"><span v-for="skill in app.normalizedJobResult.matchedSkills" :key="skill">{{ skill }}</span><span v-if="!app.normalizedJobResult.matchedSkills.length">暂无</span></div></div>
                <div><h3>待补充技能</h3><div class="chips missing"><span v-for="skill in app.normalizedJobResult.missingSkills" :key="skill">{{ skill }}</span><span v-if="!app.normalizedJobResult.missingSkills.length">暂无明显缺失</span></div></div>
              </div>
            </div>
            <div class="insight-grid">
              <article><span class="insight-icon success"><AppIcon name="check"/></span><h3>你的优势</h3><ul><li v-for="item in app.normalizedJobResult.strengths" :key="item">{{ item }}</li><li v-if="!app.normalizedJobResult.strengths.length">暂无</li></ul></article>
              <article><span class="insight-icon danger">!</span><h3>潜在风险</h3><ul><li v-for="item in app.normalizedJobResult.risks" :key="item">{{ item }}</li><li v-if="!app.normalizedJobResult.risks.length">暂无</li></ul></article>
              <article><span class="insight-icon primary"><AppIcon name="sparkles"/></span><h3>行动建议</h3><ul><li v-for="item in app.normalizedJobResult.suggestions" :key="item">{{ item }}</li><li v-if="!app.normalizedJobResult.suggestions.length">暂无</li></ul></article>
              <article><span class="insight-icon amber"><AppIcon name="chat"/></span><h3>可能面试题</h3><ul><li v-for="item in app.normalizedJobResult.interviewQuestions" :key="item">{{ item }}</li><li v-if="!app.normalizedJobResult.interviewQuestions.length">暂无</li></ul></article>
            </div>
            <div class="result-actions"><button class="btn soft" @click="app.exportJobAnalysisReport"><AppIcon name="download" :size="17"/>导出分析报告</button><button class="btn primary" @click="switchStage('resume')">继续优化简历<AppIcon name="arrow" :size="17"/></button></div>
          </template>
          <EmptyState v-else icon="target" title="等待第一次岗位分析" description="输入简历和目标岗位 JD，AI 将整理匹配技能、差距与面试建议。"/>
        </div>

        <div v-else-if="app.careerResultTab === 'resume'">
          <div v-if="app.isJobOperationRunning('resume-parse')" class="result-skeleton">
            <i></i><i></i><i></i><span>AI 正在提取简历画像</span>
          </div>
          <template v-else-if="app.normalizedResumeParseResult">
            <div class="profile-summary-grid">
              <article><small>目标岗位</small><div class="chips"><span v-for="role in app.normalizedResumeParseResult.targetRoles" :key="role">{{ role }}</span><span v-if="!app.normalizedResumeParseResult.targetRoles.length">未识别</span></div></article>
              <article><small>技能关键词</small><div class="chips"><span v-for="skill in app.normalizedResumeParseResult.skills" :key="skill">{{ skill }}</span></div></article>
              <article><small>候选人优势</small><ul><li v-for="item in app.normalizedResumeParseResult.strengths" :key="item">{{ item }}</li><li v-if="!app.normalizedResumeParseResult.strengths.length">暂无</li></ul></article>
            </div>
            <div v-if="app.normalizedResumeParseResult.projects.length" class="project-result-list">
              <article v-for="project in app.normalizedResumeParseResult.projects" :key="project.name">
                <div><h3>{{ project.name || '未命名项目' }}</h3><span>{{ project.role || '角色未识别' }}</span></div>
                <p>{{ project.description || '暂无项目描述' }}</p>
                <div class="chips"><span v-for="tech in project.techStack" :key="tech">{{ tech }}</span></div>
              </article>
            </div>
          </template>
          <EmptyState v-else icon="user" title="尚未解析简历" description="点击上方“解析简历”，提取技能、项目与目标岗位。"/>
        </div>

        <div v-else>
          <div v-if="app.isJobOperationRunning('jd-parse')" class="result-skeleton">
            <i></i><i></i><i></i><span>AI 正在提取岗位画像</span>
          </div>
          <template v-else-if="app.normalizedJdParseResult">
            <div class="jd-title-card"><span class="soft-icon mint"><AppIcon name="briefcase"/></span><div><small>识别岗位</small><h3>{{ app.normalizedJdParseResult.jobTitle }}</h3><p>{{ app.normalizedJdParseResult.seniority }}</p></div></div>
            <div class="insight-grid two-columns">
              <article><h3>必备技能</h3><div class="chips"><span v-for="skill in app.normalizedJdParseResult.requiredSkills" :key="skill">{{ skill }}</span></div></article>
              <article><h3>加分技能</h3><div class="chips"><span v-for="skill in app.normalizedJdParseResult.preferredSkills" :key="skill">{{ skill }}</span></div></article>
              <article><h3>核心职责</h3><ul><li v-for="item in app.normalizedJdParseResult.responsibilities" :key="item">{{ item }}</li></ul></article>
              <article><h3>关注风险</h3><ul><li v-for="item in app.normalizedJdParseResult.risks" :key="item">{{ item }}</li><li v-if="!app.normalizedJdParseResult.risks.length">暂无明显风险</li></ul></article>
            </div>
          </template>
          <EmptyState v-else icon="briefcase" title="尚未解析岗位" description="点击上方“解析 JD”，提取岗位关键词和能力要求。"/>
        </div>
      </section>

      <details class="surface-card favorite-capture">
        <summary><span><AppIcon name="briefcase" :size="18"/>收藏这个岗位，方便后续跟进</span><AppIcon name="chevron" :size="18"/></summary>
        <div class="details-body form-grid two">
          <label class="form-field"><span>岗位名称</span><input v-model.trim="app.jobFavoriteForm.jobTitle" placeholder="例如：AI 应用开发工程师"></label>
          <label class="form-field"><span>公司名称</span><input v-model.trim="app.jobFavoriteForm.companyName" placeholder="填写目标公司"></label>
          <label class="form-field"><span>来源链接</span><input v-model.trim="app.jobFavoriteForm.sourceUrl" placeholder="https://"></label>
          <label class="form-field"><span>跟进备注</span><input v-model.trim="app.jobFavoriteForm.notes" placeholder="记录投递状态或准备重点"></label>
          <button class="btn soft" @click="app.createJobFavorite">收藏岗位</button>
        </div>
      </details>
    </section>

    <section v-else-if="app.careerStage === 'resume'" class="career-stage">
      <div class="career-input-grid resume-stage-grid">
        <section class="surface-card career-form-card">
          <div class="section-heading"><div><span class="eyebrow">当前简历</span><h2>优化简历内容与表达</h2></div><span class="soft-icon"><AppIcon name="document"/></span></div>
          <label class="form-field"><span>简历文本</span><textarea v-model="app.jobForm.resumeText" rows="14" placeholder="粘贴需要优化的真实简历内容"></textarea></label>
          <label class="form-field">
            <span>目标岗位 JD <em class="optional-tag">选填</em></span>
            <textarea v-model="app.jobForm.jobDescription" rows="6" placeholder="填写后进行针对岗位优化；留空则进行通用简历优化"></textarea>
          </label>
          <p class="form-mode-hint">{{ app.jobForm.jobDescription.trim() ? '当前模式：针对岗位优化' : '当前模式：通用简历优化' }}</p>
          <button class="btn primary full" :disabled="app.isJobOperationRunning()" @click="app.optimizeResume"><span v-if="app.isJobOperationRunning('resume-optimize')" class="mini-spinner"></span><AppIcon v-else name="sparkles" :size="17"/>{{ app.isJobOperationRunning('resume-optimize') ? '正在生成优化建议…' : (app.jobForm.jobDescription.trim() ? '生成针对性优化建议' : '开始通用简历优化') }}</button>
        </section>
        <section class="surface-card version-form-card">
          <div class="section-heading compact"><div><span class="eyebrow">版本沉淀</span><h2>保存本次简历</h2></div></div>
          <label class="form-field"><span>版本名称</span><input v-model.trim="app.jobResumeVersionForm.versionName" placeholder="例如：Java 后端强化版"></label>
          <label class="form-field"><span>目标岗位</span><input v-model.trim="app.jobResumeVersionForm.targetRole" placeholder="例如：Java 后端开发工程师"></label>
          <label class="form-field"><span>版本备注</span><textarea v-model.trim="app.jobResumeVersionForm.notes" rows="3" placeholder="记录本版重点调整"></textarea></label>
          <button class="btn soft full" @click="app.createResumeVersion"><AppIcon name="plus" :size="17"/>保存为新版本</button>
          <p class="muted-text">在下方历史版本中可载入、更新或删除。</p>
        </section>
      </div>

      <section class="surface-card result-workbench">
        <div class="section-heading"><div><span class="eyebrow">优化建议</span><h2>让经历更清晰、更有说服力</h2></div><button v-if="app.normalizedResumeOptimizeResult" class="btn soft small" @click="app.exportResumeOptimizeReport"><AppIcon name="download" :size="16"/>导出</button></div>
        <div v-if="app.isJobOperationRunning('resume-optimize')" class="result-skeleton tall">
          <i></i><i></i><i></i><span>AI 正在检查表达并生成逐段改写建议</span>
        </div>
        <template v-else-if="app.normalizedResumeOptimizeResult">
          <div class="optimize-summary"><span class="soft-icon"><AppIcon name="sparkles"/></span><div><small>目标岗位 · {{ app.normalizedResumeOptimizeResult.targetPosition }}</small><p>{{ app.normalizedResumeOptimizeResult.summary }}</p></div></div>
          <div class="insight-grid two-columns">
            <article><h3>主要差距</h3><ul><li v-for="item in app.normalizedResumeOptimizeResult.gapSummary" :key="item">{{ item }}</li></ul></article>
            <article><h3>缺失关键词</h3><div class="chips missing"><span v-for="item in app.normalizedResumeOptimizeResult.missingKeywords" :key="item">{{ item }}</span></div></article>
          </div>
          <div class="rewrite-list">
            <article v-for="(item, index) in app.normalizedResumeOptimizeResult.rewriteSuggestions" :key="`${item.section}-${index}`">
              <header><span>{{ index + 1 }}</span><div><strong>{{ item.section || '简历模块' }}</strong><small>{{ item.issue }}</small></div></header>
              <div class="rewrite-compare"><div><small>原表述</small><p>{{ item.beforeText || '暂无' }}</p></div><AppIcon name="arrow"/><div class="after"><small>建议表述</small><p>{{ item.afterText || item.suggestion }}</p></div></div>
            </article>
          </div>
        </template>
        <EmptyState v-else icon="document" title="等待简历优化" description="生成后会展示岗位差距、关键词和逐段改写建议。"/>
      </section>

      <section class="surface-card history-card">
        <div class="section-heading"><div><span class="eyebrow">版本管理</span><h2>我的简历版本</h2></div><button class="icon-button" @click="app.loadResumeVersions"><AppIcon name="refresh" :size="17"/></button></div>
        <div v-if="app.jobResumeVersions.length" class="responsive-table">
          <table><thead><tr><th>版本</th><th>目标岗位</th><th>更新时间</th><th>操作</th></tr></thead><tbody>
            <tr v-for="version in app.jobResumeVersions" :key="version.id">
              <td><strong>{{ version.versionName }}</strong><small class="table-note">{{ version.notes || '无备注' }}</small></td>
              <td>{{ version.targetRole || '-' }}</td><td>{{ app.formatDate(version.updatedAt) }}</td>
              <td><div class="table-actions"><button @click="app.useResumeVersion(version)">载入</button><button @click="app.updateResumeVersion(version.id)">更新</button><button class="danger" @click="app.deleteResumeVersion(version.id)">删除</button></div></td>
            </tr>
          </tbody></table>
        </div>
        <EmptyState v-else icon="document" title="还没有简历版本" description="保存第一个针对性版本，方便按岗位持续迭代。"/>
        <div class="pagination"><button :disabled="app.jobPages.resumeVersions.first" @click="app.loadResumeVersions(true, app.jobPages.resumeVersions.page - 1)">上一页</button><span>第 {{ app.jobPages.resumeVersions.page + 1 }} / {{ app.pageTotal(app.jobPages.resumeVersions) }} 页</span><button :disabled="app.jobPages.resumeVersions.last" @click="app.loadResumeVersions(true, app.jobPages.resumeVersions.page + 1)">下一页</button></div>
      </section>
    </section>

    <section v-else-if="app.careerStage === 'interview'" class="career-stage">
      <section class="surface-card interview-launcher">
        <div><span class="eyebrow">面试训练</span><h2>从岗位要求生成一套可练习的表达</h2><p>复用当前简历和岗位 JD，准备自我介绍、项目讲解、技术追问与行为问题。</p></div>
        <div class="interview-actions">
          <button class="btn primary" :disabled="app.isJobOperationRunning()" @click="app.prepareInterview"><span v-if="app.isJobOperationRunning('interview-prep')" class="mini-spinner"></span><AppIcon v-else name="sparkles" :size="17"/>{{ app.isJobOperationRunning('interview-prep') ? '正在生成准备包…' : '生成面试准备包' }}</button>
          <label class="form-field"><span>需要 STAR 回答的问题</span><input v-model.trim="app.jobForm.interviewQuestion" placeholder="例如：你如何解决项目中的关键问题？"></label>
          <button class="btn soft" :disabled="app.isJobOperationRunning()" @click="app.generateStarInterviewAnswer"><span v-if="app.isJobOperationRunning('star-answer')" class="mini-spinner"></span><AppIcon v-else name="chat" :size="17"/>{{ app.isJobOperationRunning('star-answer') ? '正在生成 STAR…' : '生成 STAR 答案' }}</button>
        </div>
      </section>

      <section class="surface-card result-workbench">
        <div class="section-heading"><div><span class="eyebrow">面试准备包</span><h2>先讲清自己，再应对追问</h2></div><button v-if="app.normalizedInterviewPrepResult" class="btn soft small" @click="app.exportInterviewPrepReport"><AppIcon name="download" :size="16"/>导出准备包</button></div>
        <div v-if="app.isJobOperationRunning('interview-prep')" class="result-skeleton tall">
          <i></i><i></i><i></i><span>AI 正在组织面试准备内容</span>
        </div>
        <template v-else-if="app.normalizedInterviewPrepResult">
          <article class="intro-result"><span class="soft-icon"><AppIcon name="user"/></span><div><small>自我介绍 · {{ app.normalizedInterviewPrepResult.targetPosition }}</small><p>{{ app.normalizedInterviewPrepResult.selfIntroduction }}</p></div></article>
          <div class="project-talk-list">
            <article v-for="project in app.normalizedInterviewPrepResult.projectTalkingPoints" :key="project.projectName">
              <h3>{{ project.projectName || '项目讲解' }}</h3><p>{{ project.pitch }}</p>
              <div class="talk-columns"><div><small>技术深度点</small><ul><li v-for="item in project.technicalDepth" :key="item">{{ item }}</li></ul></div><div><small>可能追问</small><ul><li v-for="item in project.likelyFollowups" :key="item">{{ item }}</li></ul></div></div>
            </article>
          </div>
          <div class="qa-grid">
            <article><h3>技术追问</h3><div v-for="item in app.normalizedInterviewPrepResult.technicalQuestions" :key="item.question"><strong>{{ item.question }}</strong><ul><li v-for="point in item.answerPoints" :key="point">{{ point }}</li></ul></div></article>
            <article><h3>行为问题</h3><div v-for="item in app.normalizedInterviewPrepResult.behavioralQuestions" :key="item.question"><strong>{{ item.question }}</strong><ul><li v-for="point in item.answerPoints" :key="point">{{ point }}</li></ul></div></article>
          </div>
        </template>
        <EmptyState v-else icon="chat" title="等待生成面试准备包" description="建议先完成岗位分析，让内容更贴近目标岗位。"/>
      </section>

      <section class="surface-card result-workbench star-workbench">
        <div class="section-heading"><div><span class="eyebrow">STAR 回答</span><h2>把项目经历讲得具体可信</h2></div><button v-if="app.normalizedStarInterviewAnswerResult" class="btn soft small" @click="app.exportStarInterviewAnswerReport"><AppIcon name="download" :size="16"/>导出答案</button></div>
        <div v-if="app.isJobOperationRunning('star-answer')" class="result-skeleton">
          <i></i><i></i><i></i><span>AI 正在组织 STAR 结构</span>
        </div>
        <template v-else-if="app.normalizedStarInterviewAnswerResult">
          <div class="star-grid">
            <article><span>S</span><div><strong>Situation 情境</strong><p>{{ app.normalizedStarInterviewAnswerResult.situation }}</p></div></article>
            <article><span>T</span><div><strong>Task 任务</strong><p>{{ app.normalizedStarInterviewAnswerResult.task }}</p></div></article>
            <article><span>A</span><div><strong>Action 行动</strong><ul><li v-for="item in app.normalizedStarInterviewAnswerResult.action" :key="item">{{ item }}</li></ul></div></article>
            <article><span>R</span><div><strong>Result 结果</strong><p>{{ app.normalizedStarInterviewAnswerResult.result }}</p></div></article>
          </div>
          <article class="final-answer"><small>完整回答示例</small><p>{{ app.normalizedStarInterviewAnswerResult.answer }}</p></article>
        </template>
        <EmptyState v-else icon="sparkles" title="还没有 STAR 回答" description="输入一个真实面试问题，让 AI 帮你组织结构。"/>
      </section>
    </section>

    <section v-else class="career-stage deliver-stage">
      <section class="delivery-hero">
        <div><span class="hero-kicker"><AppIcon name="sparkles" :size="16"/>求职交付</span><h2>把准备成果整理成一份可直接练习的成品包</h2><p>包含自我介绍、项目讲解、架构讲点、风险应对和收尾话术。</p></div>
        <button class="btn primary" :disabled="app.isJobOperationRunning()" @click="app.generateJobDeliveryPackage"><span v-if="app.isJobOperationRunning('delivery-package')" class="mini-spinner"></span><AppIcon v-else name="briefcase" :size="18"/>{{ app.isJobOperationRunning('delivery-package') ? '正在生成成品包…' : '生成求职成品包' }}</button>
      </section>

      <section class="surface-card result-workbench">
        <div class="section-heading"><div><span class="eyebrow">成品包</span><h2>{{ app.normalizedJobDeliveryPackageResult?.targetPosition || '等待生成' }}</h2></div><button v-if="app.normalizedJobDeliveryPackageResult" class="btn soft small" @click="app.exportJobDeliveryPackageReport"><AppIcon name="download" :size="16"/>导出 Markdown</button></div>
        <div v-if="app.isJobOperationRunning('delivery-package')" class="result-skeleton tall">
          <i></i><i></i><i></i><span>AI 正在整合求职交付内容</span>
        </div>
        <template v-else-if="app.normalizedJobDeliveryPackageResult">
          <div class="delivery-grid">
            <article class="wide"><small>自我介绍</small><p>{{ app.normalizedJobDeliveryPackageResult.selfIntroduction }}</p></article>
            <article class="wide"><small>项目讲解稿</small><p>{{ app.normalizedJobDeliveryPackageResult.projectPitch }}</p></article>
            <article><small>架构讲点</small><ul><li v-for="item in app.normalizedJobDeliveryPackageResult.architectureTalkingPoints" :key="item">{{ item }}</li></ul></article>
            <article><small>风险应对</small><ul><li v-for="item in app.normalizedJobDeliveryPackageResult.riskResponse" :key="item">{{ item }}</li></ul></article>
            <article class="wide"><small>收尾话术</small><p>{{ app.normalizedJobDeliveryPackageResult.closingStatement }}</p></article>
          </div>
        </template>
        <EmptyState v-else icon="briefcase" title="尚未生成求职成品包" description="准备好简历和目标岗位后，即可生成可直接练习的交付内容。"/>
      </section>

      <section class="record-grid">
        <div class="surface-card history-card">
          <div class="section-heading"><div><span class="eyebrow">机会管理</span><h2>收藏岗位</h2></div><button class="icon-button" @click="app.loadJobFavorites"><AppIcon name="refresh" :size="17"/></button></div>
          <div v-if="app.jobFavorites.length" class="record-list">
            <article v-for="favorite in app.jobFavorites" :key="favorite.id">
              <span class="record-icon"><AppIcon name="briefcase"/></span><div><strong>{{ favorite.jobTitle }}</strong><small>{{ favorite.companyName || '公司未填写' }} · {{ favorite.notes || '无备注' }}</small></div>
              <div class="record-actions"><button @click="app.useFavoriteJob(favorite)">使用</button><button class="danger" @click="app.deleteJobFavorite(favorite.id)"><AppIcon name="trash" :size="15"/></button></div>
            </article>
          </div>
          <EmptyState v-else icon="briefcase" title="还没有收藏岗位" description="在岗位分析页保存感兴趣的机会。"/>
          <div class="pagination"><button :disabled="app.jobPages.favorites.first" @click="app.loadJobFavorites(true, app.jobPages.favorites.page - 1)">上一页</button><span>{{ app.jobPages.favorites.page + 1 }} / {{ app.pageTotal(app.jobPages.favorites) }}</span><button :disabled="app.jobPages.favorites.last" @click="app.loadJobFavorites(true, app.jobPages.favorites.page + 1)">下一页</button></div>
        </div>

        <div class="surface-card history-card">
          <div class="section-heading"><div><span class="eyebrow">AI 生成记录</span><h2>内容历史</h2></div><select class="compact-select" v-model="app.generatedTaskFilter" @change="app.onGeneratedTaskFilterChange"><option v-for="type in generatedTaskTypes" :key="type.value" :value="type.value">{{ type.label }}</option></select></div>
          <div v-if="app.jobGeneratedTasks.length" class="record-list">
            <article v-for="task in app.jobGeneratedTasks" :key="task.id">
              <span class="type-badge">{{ app.jobGeneratedTaskTypeLabel(task.taskType) }}</span><div><strong>{{ task.jobDescription || '未填写岗位描述' }}</strong><small>{{ app.jobReviewStatusLabel(task.reviewStatus) }} · {{ app.formatDate(task.createdAt) }}</small></div>
              <div class="record-actions"><button @click="app.viewJobGeneratedTask(task.id)">查看</button><button class="danger" @click="app.deleteJobGeneratedTask(task.id)"><AppIcon name="trash" :size="15"/></button></div>
            </article>
          </div>
          <EmptyState v-else icon="sparkles" title="暂无生成记录" description="简历优化、面试包和成品包会保存在这里。"/>
          <div class="pagination"><button :disabled="app.jobPages.generatedTasks.first" @click="app.loadJobGeneratedTasks(true, app.jobPages.generatedTasks.page - 1)">上一页</button><span>{{ app.jobPages.generatedTasks.page + 1 }} / {{ app.pageTotal(app.jobPages.generatedTasks) }}</span><button :disabled="app.jobPages.generatedTasks.last" @click="app.loadJobGeneratedTasks(true, app.jobPages.generatedTasks.page + 1)">下一页</button></div>
        </div>
      </section>

      <section v-if="app.selectedGeneratedTask" class="surface-card selected-history">
        <div class="section-heading"><div><span class="eyebrow">本人审核</span><h2>{{ app.jobGeneratedTaskTypeLabel(app.selectedGeneratedTask.taskType) }}</h2></div><span class="review-status" :class="String(app.selectedGeneratedTask.reviewStatus || '').toLowerCase()">{{ app.jobReviewStatusLabel(app.selectedGeneratedTask.reviewStatus) }}</span></div>
        <pre>{{ app.selectedGeneratedTaskResultText }}</pre>
        <label class="form-field review-comment"><span>审核意见（仅自己可见）</span><textarea rows="3" maxlength="1000" v-model.trim="app.generatedTaskReviewComment" placeholder="记录需要修改的事实、措辞或遗漏"></textarea></label>
        <div class="review-actions">
          <button class="btn secondary" @click="app.reviewJobGeneratedTask('REJECTED')">驳回并继续修改</button>
          <button class="btn primary" @click="app.reviewJobGeneratedTask('APPROVED')">确认内容可信</button>
        </div>
      </section>

      <section class="surface-card history-card job-history-card">
        <div class="section-heading"><div><span class="eyebrow">复盘与比较</span><h2>岗位分析历史</h2></div><div class="button-cluster"><button class="btn soft small" :disabled="app.selectedCompareTaskIds.length < 2" @click="app.compareSelectedJobTasks">比较所选</button><button class="icon-button" @click="app.loadJobTasks"><AppIcon name="refresh" :size="17"/></button></div></div>
        <div v-if="app.jobTasks.length" class="responsive-table">
          <table><thead><tr><th>选择</th><th>岗位摘要</th><th>匹配分</th><th>时间</th><th>操作</th></tr></thead><tbody>
            <tr v-for="task in app.jobTasks" :key="task.id">
              <td><input type="checkbox" :value="task.id" v-model="app.selectedCompareTaskIds"></td><td class="wide-cell">{{ task.jobDescription }}</td><td><span class="score-badge" :class="app.compareScoreBadgeClass(task.matchScore)">{{ task.matchScore }}</span></td><td>{{ app.formatDate(task.createdAt) }}</td>
              <td><div class="table-actions"><button @click="app.viewJobTask(task.id)">查看</button><button class="danger" @click="app.deleteJobTask(task.id)">删除</button></div></td>
            </tr>
          </tbody></table>
        </div>
        <EmptyState v-else icon="target" title="暂无岗位分析历史" description="完成分析后，可以对多个岗位的匹配结果进行比较。"/>
        <div class="pagination"><button :disabled="app.jobPages.tasks.first" @click="app.loadJobTasks(true, app.jobPages.tasks.page - 1)">上一页</button><span>第 {{ app.jobPages.tasks.page + 1 }} / {{ app.pageTotal(app.jobPages.tasks) }} 页</span><button :disabled="app.jobPages.tasks.last" @click="app.loadJobTasks(true, app.jobPages.tasks.page + 1)">下一页</button></div>
      </section>

      <section v-if="app.jobCompareResult" class="surface-card compare-result">
        <div class="section-heading"><div><span class="eyebrow">岗位对比</span><h2>最佳匹配 {{ app.jobCompareResult.bestScore }} 分</h2></div><span>平均 {{ app.jobCompareResult.averageScore }} 分</span></div>
        <div class="compare-list"><article v-for="item in app.jobCompareResult.items" :key="item.taskId" :class="{best: app.isBestCompareItem(item)}"><header><strong>{{ item.matchScore }} 分</strong><span v-if="app.isBestCompareItem(item)">最佳匹配</span></header><p>{{ item.jobDescription }}</p><div class="score-bar"><i :style="{width: app.scorePercent(item.matchScore)}" :class="app.compareScoreBarClass(item.matchScore)"></i></div></article></div>
      </section>

      <section class="surface-card export-center">
        <div><span class="eyebrow">导出中心</span><h2>带走你的求职成果</h2></div>
        <div class="button-cluster">
          <button class="btn secondary" @click="app.exportJobAnalysisReport">岗位分析</button>
          <button class="btn secondary" @click="app.exportResumeOptimizeReport">简历优化</button>
          <button class="btn secondary" @click="app.exportInterviewPrepReport">面试准备</button>
          <button class="btn secondary" @click="app.exportStarInterviewAnswerReport">STAR 答案</button>
          <button class="btn primary" @click="app.exportJobDeliveryPackageReport">求职成品包</button>
        </div>
      </section>
    </section>
  </div>
</template>
