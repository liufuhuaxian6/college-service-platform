<template>
  <view class="page">
    <view class="top-card" v-if="detail">
      <view>
        <text class="eyebrow">{{ isTemplateMode ? '流程模板' : '我的流程' }}</text>
        <text class="title">{{ detail.templateName }}</text>
        <text class="subtitle">
          {{ isTemplateMode ? '官方节点说明' : `当前第 ${detail.currentStep || 1} 步` }}
        </text>
      </view>
      <StatusPill v-if="!isTemplateMode" :status="detail.status" />
    </view>

    <view class="timeline" v-if="detail">
      <view class="stage-block" v-for="stage in stages" :key="stage.name">
        <view class="stage-title">
          <text>{{ stage.name }}</text>
          <text>{{ stage.start }}-{{ stage.end }}</text>
        </view>

        <view class="step" v-for="step in stage.steps" :key="step.stepOrder">
          <view class="step-rail">
            <view class="step-indicator" :class="stepStatus(step)">{{ step.stepOrder }}</view>
            <view class="step-line" v-if="step.stepOrder !== detail.steps.length" :class="stepStatus(step)" />
          </view>
          <view class="step-content">
            <view class="step-head">
              <text class="step-name">{{ step.name }}</text>
              <text class="step-tag" v-if="stepStatus(step) === 'current'">当前</text>
              <text class="step-tag done" v-else-if="step.completed">完成</text>
            </view>
            <text class="step-desc" v-if="step.description">{{ step.description }}</text>
            <text class="step-time" v-if="step.completedAt">完成时间：{{ formatDateTime(step.completedAt) }}</text>
            <text class="step-time" v-else-if="step.expectedEnd">预计节点：{{ step.expectedEnd }}</text>
            <text class="step-time" v-else-if="step.durationDays">预计需要 {{ step.durationDays }} 天</text>
          </view>
        </view>
      </view>
    </view>

    <EmptyState v-else title="暂无流程详情" />
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { partyApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import StatusPill from '@/components/StatusPill.vue'

const detail = ref(null)
const mode = ref('progress')

const partyTemplateSteps = [
  ['入党积极分子确定', '教育引导'],
  ['入党积极分子确定', '接收入党申请书并派人谈话'],
  ['入党积极分子确定', '确定入党积极分子并报党委备案'],
  ['入党积极分子确定', '指定培养联系人并进行培养教育'],
  ['入党积极分子确定', '考察'],
  ['发展对象确定', '支部委员会听取意见后讨论'],
  ['发展对象确定', '报党委备案后确定发展对象'],
  ['发展对象确定', '确定入党介绍人'],
  ['发展对象确定', '政治审查'],
  ['发展对象确定', '短期集中培训'],
  ['预备党员接收', '支部委员会听取意见后讨论'],
  ['预备党员接收', '报党委预审'],
  ['预备党员接收', '公示'],
  ['预备党员接收', '召开支部大会讨论接收预备党员'],
  ['预备党员接收', '将有关材料报党委'],
  ['预备党员接收', '党委委员或组织员与发展对象谈话'],
  ['预备党员接收', '党委审批'],
  ['预备党员接收', '党委审批结果通知党支部'],
  ['预备党员接收', '报上级党委组织部门备案'],
  ['预备党员教育和转正', '编入党支部和党小组'],
  ['预备党员教育和转正', '入党宣誓'],
  ['预备党员教育和转正', '教育和考察'],
  ['预备党员教育和转正', '提交转正申请并征求意见并审查'],
  ['预备党员教育和转正', '公示'],
  ['预备党员教育和转正', '召开支部大会讨论预备党员转正'],
  ['预备党员教育和转正', '将有关材料报党委'],
  ['预备党员教育和转正', '党委审批'],
  ['预备党员教育和转正', '党委审批结果通知党支部'],
  ['正式党员', '存档'],
]

const leagueTemplateSteps = [
  ['入团申请', '递交入团申请书', '向团组织递交入团申请书'],
  ['资格审查', '团组织审查', '团支部对申请人进行审查'],
  ['培养学习', '团课学习', '参加团课学习并通过考核'],
  ['支部讨论', '支部大会表决', '团支部大会讨论表决'],
  ['审批归档', '上级团委审批', '报上级团委审批并颁发团员证'],
]

const isTemplateMode = computed(() => mode.value === 'template')

const stages = computed(() => {
  if (!detail.value?.steps?.length) return []
  const map = new Map()
  detail.value.steps.forEach((step) => {
    const stageName = step.stage || step.description || '流程节点'
    if (!map.has(stageName)) {
      map.set(stageName, [])
    }
    map.get(stageName).push(step)
  })
  return Array.from(map.entries()).map(([name, steps]) => ({
    name,
    steps,
    start: steps[0]?.stepOrder,
    end: steps[steps.length - 1]?.stepOrder,
  }))
})

function stepStatus(step) {
  if (isTemplateMode.value) return 'template'
  if (step.completed) return 'done'
  if (step.stepOrder === detail.value?.currentStep) return 'current'
  return 'todo'
}

function formatDateTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function buildTemplateDetail(id, name) {
  const isLeague = Number(id) === 2 || name.includes('团')
  const source = isLeague ? leagueTemplateSteps : partyTemplateSteps
  return {
    templateName: name || (isLeague ? '入团流程' : '入党流程'),
    status: '',
    currentStep: 0,
    steps: source.map((item, index) => ({
      stepOrder: index + 1,
      stage: item[0],
      name: item[1],
      description: item[2] || item[0],
      completed: false,
    })),
  }
}

onMounted(async () => {
  const pages = getCurrentPages()
  const page = pages[pages.length - 1]
  const options = page.options || {}
  mode.value = options.mode || 'progress'

  if (mode.value === 'template') {
    detail.value = buildTemplateDetail(options.id, decodeURIComponent(options.name || ''))
    return
  }

  if (options.id) {
    const res = await partyApi.getProgressDetail(options.id)
    detail.value = res.data
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 24rpx;
  background: var(--mp-bg);
  box-sizing: border-box;
}

.top-card {
  display: flex;
  justify-content: space-between;
  gap: 20rpx;
  align-items: flex-start;
  margin-bottom: 20rpx;
  padding: 30rpx;
  color: #fff;
  background: linear-gradient(135deg, #9B2C36 0%, #7E2430 100%);
  border-radius: 24rpx;
  box-shadow: 0 18rpx 42rpx rgba(155, 44, 54, .18);
}

.eyebrow {
  display: inline-flex;
  margin-bottom: 14rpx;
  padding: 6rpx 14rpx;
  color: rgba(255, 255, 255, .88);
  font-size: 22rpx;
  background: rgba(255, 255, 255, .14);
  border-radius: 999rpx;
}

.title {
  display: block;
  font-size: 38rpx;
  font-weight: 760;
}

.subtitle {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, .78);
  font-size: 24rpx;
}

.timeline {
  padding: 24rpx;
  background: var(--mp-card);
  border: 1rpx solid var(--mp-border);
  border-radius: 24rpx;
}

.stage-block + .stage-block {
  margin-top: 28rpx;
  padding-top: 28rpx;
  border-top: 1rpx solid var(--mp-border);
}

.stage-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22rpx;
  color: var(--mp-text-main);
  font-size: 28rpx;
  font-weight: 720;
}

.stage-title text:last-child {
  color: var(--mp-text-sub);
  font-size: 22rpx;
  font-weight: 500;
}

.step {
  display: flex;
  gap: 18rpx;
  padding-bottom: 22rpx;
}

.step:last-child {
  padding-bottom: 0;
}

.step-rail {
  width: 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
}

.step-indicator {
  width: 42rpx;
  height: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 4rpx;
  color: #7A8494;
  font-size: 20rpx;
  font-weight: 700;
  border-radius: 50%;
  background: #F0F2F5;
}

.step-indicator.done {
  color: #fff;
  background: var(--mp-success);
}

.step-indicator.current {
  color: #fff;
  background: var(--mp-primary);
}

.step-indicator.template {
  color: var(--mp-primary);
  background: var(--mp-primary-light);
}

.step-line {
  width: 4rpx;
  flex: 1;
  margin-top: 8rpx;
  border-radius: 2rpx;
  background: var(--mp-border);
}

.step-line.done {
  background: rgba(47, 125, 85, .35);
}

.step-line.current {
  background: rgba(155, 44, 54, .35);
}

.step-content {
  flex: 1;
  min-width: 0;
}

.step-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14rpx;
}

.step-name {
  color: var(--mp-text-main);
  font-size: 29rpx;
  font-weight: 650;
  line-height: 1.35;
}

.step-tag {
  flex-shrink: 0;
  padding: 4rpx 12rpx;
  color: var(--mp-primary);
  font-size: 20rpx;
  font-weight: 650;
  border-radius: 999rpx;
  background: var(--mp-primary-light);
}

.step-tag.done {
  color: var(--mp-success);
  background: #ECF6F0;
}

.step-desc,
.step-time {
  display: block;
  margin-top: 8rpx;
  color: var(--mp-text-regular);
  font-size: 24rpx;
  line-height: 1.55;
}

.step-time {
  color: var(--mp-text-sub);
}
</style>
