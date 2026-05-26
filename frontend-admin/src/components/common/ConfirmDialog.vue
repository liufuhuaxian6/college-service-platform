<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="460px"
    append-to-body
  >
    <p class="confirm-text">{{ message }}</p>
    <el-input
      v-if="showComment"
      v-model="comment"
      type="textarea"
      :rows="4"
      :placeholder="placeholder"
    />
    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button :type="type" :loading="loading" @click="confirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)
const comment = ref('')
const loading = ref(false)
let resolver = null

defineProps({
  title: { type: String, default: '确认操作' },
  message: { type: String, default: '确定执行该操作吗？' },
  placeholder: { type: String, default: '请输入说明，可不填' },
  showComment: { type: Boolean, default: false },
  type: { type: String, default: 'primary' },
})

function open() {
  visible.value = true
  comment.value = ''
  return new Promise((resolve) => {
    resolver = resolve
  })
}

function close() {
  visible.value = false
  resolver?.({ confirmed: false, comment: '' })
}

function confirm() {
  visible.value = false
  resolver?.({ confirmed: true, comment: comment.value })
}

defineExpose({ open, loading })
</script>

<style scoped>
.confirm-text {
  margin: 0 0 14px;
  color: var(--app-text-regular);
  line-height: 1.7;
}
</style>
