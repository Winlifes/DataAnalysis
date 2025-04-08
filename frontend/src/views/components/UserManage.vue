<template>
  <div class="user-manage-page">
    <el-card shadow="hover">
      <div class="header">
        <h3>用户管理</h3>
        <el-button type="primary" @click="showAddDialog = true">新增用户</el-button>
      </div>

      <el-table :data="userList" style="width: 100%" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column label="权限">
          <template #default="{ row }">
            <el-tag v-if="row.authCheck">查看</el-tag>
            <el-tag v-if="row.authEdit" type="warning">编辑</el-tag>
            <el-tag v-if="row.authExport" type="info">导出</el-tag>
            <el-tag v-if="row.authAdmin" type="danger">系统</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button size="small" v-if="!row.authAdmin" @click="editUser(row)">编辑</el-button>
            <el-button size="small" v-if="!row.authAdmin" type="danger" @click="deleteUser(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增用户弹窗 -->
    <el-dialog v-model="showAddDialog" title="新增用户" width="500px">
      <el-form :model="newUser" :rules="rules" ref="addFormRef" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="newUser.username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="newUser.password" type="password" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="newUser.nickname" />
        </el-form-item>
        <el-form-item label="权限">
          <el-checkbox v-model="newUser.authCheck">查看</el-checkbox>
          <el-checkbox v-model="newUser.authEdit">编辑</el-checkbox>
          <el-checkbox v-model="newUser.authExport">导出</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="submitAddUser">确认</el-button>
      </template>
    </el-dialog>
  </div>
  <!-- 编辑用户弹窗 -->
  <el-dialog v-model="showEditDialog" title="编辑用户" width="500px">
    <el-form :model="editForm" ref="editFormRef" label-width="100px">
      <el-form-item label="用户名">
        <el-input v-model="editForm.username" disabled />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="editForm.password" type="password" placeholder="不修改则留空" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="editForm.nickname" />
      </el-form-item>
      <el-form-item label="权限">
        <el-checkbox v-model="editForm.authCheck">查看</el-checkbox>
        <el-checkbox v-model="editForm.authEdit">编辑</el-checkbox>
        <el-checkbox v-model="editForm.authExport">导出</el-checkbox>
        <el-checkbox v-model="editForm.authAdmin">系统</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showEditDialog = false">取消</el-button>
      <el-button type="primary" @click="submitEditUser">保存</el-button>
    </template>
  </el-dialog>

</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import api from '@/api'

// 数据
const userList = ref([])
const showAddDialog = ref(false)
const addFormRef = ref()

const newUser = ref({
  username: '',
  password: '',
  nickname: '',
  authCheck: true,
  authEdit: true,
  authExport: true,
  authAdmin: false
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 获取所有用户
const fetchUsers = async () => {
  const res = await api.get('/api/admin/users')
  userList.value = res.data
}

onMounted(() => fetchUsers())

const submitAddUser = async () => {
  await addFormRef.value.validate()
  try {
    await api.post('/api/admin/users', newUser.value)
    ElMessage.success('新增成功')
    showAddDialog.value = false
    await fetchUsers()
  } catch (e) {
    ElMessage.error('新增失败')
  }
}

const deleteUser = async (id) => {
  await api.delete(`/api/admin/users/${id}`)
  ElMessage.success('删除成功')
  await fetchUsers()
}

const showEditDialog = ref(false)
const editFormRef = ref()
const editForm = ref({
  id: null,
  username: '',
  password: '',
  nickname: '',
  authCheck: false,
  authEdit: false,
  authExport: false,
  authAdmin: false
})

const editUser = (user) => {
  editForm.value = {
    id: user.id,
    username: user.username,
    password: '',
    nickname: user.nickname,
    authCheck: user.authCheck,
    authEdit: user.authEdit,
    authExport: user.authExport,
    authAdmin: user.authAdmin
  }
  showEditDialog.value = true
}

const submitEditUser = async () => {
  try {
    const updatePayload = { ...editForm.value }
    if (!updatePayload.password) {
      delete updatePayload.password // 若不修改密码，则从 payload 移除
    }
    await api.put(`/api/admin/users/${editForm.value.id}`, updatePayload)
    ElMessage.success('更新成功')
    showEditDialog.value = false
    await fetchUsers()
  } catch (err) {
    ElMessage.error('更新失败')
  }
}

</script>

<style scoped>
.user-manage-page {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}
</style>
