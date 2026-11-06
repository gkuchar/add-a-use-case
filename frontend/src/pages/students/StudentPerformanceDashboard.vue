<template>
  <div class="student-header">
    <el-text class="mx-1" size="large">{{ studentName }}</el-text>
    <template v-if="student">
      <el-tag v-if="student.enabled" type="success" data-cy="student-status">Active</el-tag>
      <el-tag v-else type="danger" data-cy="student-status">Deactivated</el-tag>
      <el-text data-cy="student-team">Team: {{ student.teamName ?? 'None' }}</el-text>
      <el-button
        v-if="student.enabled && userInfoStore.isInstructor"
        type="danger"
        plain
        :loading="deactivating"
        data-cy="deactivate-student"
        @click="confirmDeactivation"
        >Deactivate</el-button
      >
    </template>
  </div>
  <StudentActivities :studentId="studentId" />
  <el-divider />
  <StudentEvaluations :studentId="studentId" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import StudentActivities from '../activities/admin/StudentActivities.vue'
import StudentEvaluations from '../evaluations/admin/StudentEvaluations.vue'

import { useRoute } from 'vue-router'
import { deactivateStudent, findStudentById } from '@/apis/student'
import type { FindStudentByIdResponse, Student } from '@/apis/student/types'
import { useUserInfoStore } from '@/stores/userInfo'

const route = useRoute()
const userInfoStore = useUserInfoStore()

const studentId: number = Number(route.params.studentId)
const studentName = ref<string>('')
const student = ref<Student>()
const deactivating = ref<boolean>(false)

async function loadStudent() {
  const result: FindStudentByIdResponse = await findStudentById(studentId)
  student.value = result.data
  studentName.value = result.data.firstName + ' ' + result.data.lastName
}

// UC-STU-deactivate-student steps 3-6. Who may deactivate is decided by the server (BR-student-lifecycle); a refused
// click lands on the forbidden page like every 403.
async function confirmDeactivation() {
  try {
    await ElMessageBox.confirm(
      `Deactivate ${studentName.value}? This student will no longer be able to log in or submit weekly activity reports or peer evaluations, and will be removed from their team. Their account and submitted work are kept. Reactivating them later does not put them back on a team.`,
      'Deactivate student',
      { confirmButtonText: 'Deactivate', cancelButtonText: 'Cancel', type: 'warning' }
    )
  } catch {
    return // 5a: not confirmed, nothing is sent
  }
  deactivating.value = true
  try {
    const result = await deactivateStudent(studentId)
    ElMessage.success(result.message)
    await loadStudent()
  } finally {
    deactivating.value = false
  }
}

onMounted(loadStudent)
</script>

<style scoped>
.student-header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}
</style>
