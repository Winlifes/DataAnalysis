<template>
  <div class="permission-detail-page">
    <el-card class="shadow-card">
      <div class="card-header">
        <el-icon><Lock /></el-icon>
        <span class="card-title">权限详情</span>
      </div>

      <el-table
        :data="permissions"
        style="width: 100%"
        stripe
        border
        empty-text="暂无权限数据"
        class="permission-table"
      >
        <el-table-column prop="name" label="权限名称" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="180" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '启用' ? 'success' : 'danger'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import {onMounted, ref} from 'vue'
import { Lock } from '@element-plus/icons-vue'
import api from "@/api/index.js";
import {ElMessage} from "element-plus";

onMounted(async () => {
  try {
    const response = await api.get('/api/user/permissions')
    console.log('权限接口返回：', response.data.data)
    permissions.value = response.data.data
  } catch (err) {
    ElMessage.error('获取权限失败')
  }
})

const permissions = ref([]) // ✅ 默认空数组，等待后端返回

</script>

<style scoped>
.permission-detail-page {
  padding: 24px;
  background-color: #f9fafc;
  min-height: 100%;
}

.shadow-card {
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.card-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.card-title {
  font-size: 18px;
  font-weight: bold;
  margin-left: 8px;
}

.permission-table .el-table__cell {
  font-size: 14px;
}
</style>
