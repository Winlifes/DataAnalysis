<template>
  <div class="account-info-page">
    <!-- 左侧：基础信息 -->
    <div class="left-panel shadow-card">
      <h3 class="section-title">基础信息</h3>
      <el-avatar :size="80" class="avatar">{{ getInitial(user.nickname) }}</el-avatar>
      <div class="username">{{ user.username }}</div>
      <div class="nickname">
        {{ user.nickname }}
        <el-icon @click="nicknameDialogVisible = true" class="edit-icon"><Edit /></el-icon>
      </div>
      <el-divider />
      <div class="version-label">系统版本号</div>
      <div class="version-number">0.0.1</div>
    </div>

    <!-- 右侧：账号安全 -->
    <div class="right-panel">
      <h3 class="section-title">账号安全</h3>
      <div class="security-list">
        <div class="security-item shadow-card" v-for="item in securityList" :key="item.label">
          <div class="icon-wrapper">
            <el-icon :size="24" class="security-icon">
              <component :is="item.icon" />
            </el-icon>
          </div>
          <div class="info">
            <div class="label">
              {{ item.label }}
              <el-tag v-if="item.status" :type="item.status === '已绑定' ? 'success' : 'warning'" size="small">{{ item.status }}</el-tag>
            </div>
            <div class="desc">{{ item.description }}</div>
          </div>
          <el-button type="primary" size="small" @click="item.action">{{ item.buttonText }}</el-button>
        </div>
      </div>
    </div>

    <!-- 修改昵称弹窗 -->
    <el-dialog v-model="nicknameDialogVisible" title="修改昵称" width="30%">
      <el-input v-model="nicknameForm.value" placeholder="请输入新昵称" />
      <template #footer>
        <el-button @click="nicknameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNickname">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="30%">
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>

    <!-- 绑定手机号弹窗 -->
    <el-dialog v-model="phoneDialogVisible" title="绑定手机号" width="30%">
      <el-form :model="phoneForm" :rules="phoneRules" ref="phoneFormRef" label-width="80px">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="phoneForm.value" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="phoneDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPhone">绑定</el-button>
      </template>
    </el-dialog>

    <!-- 绑定邮箱弹窗 -->
    <el-dialog v-model="emailDialogVisible" title="绑定邮箱" width="30%">
      <el-form :model="emailForm" :rules="emailRules" ref="emailFormRef" label-width="80px">
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="emailForm.value" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="emailDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEmail">绑定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { Edit, Lock, Phone, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import api from "@/api/index.js";

const user = ref({ username: 'moumou', nickname: '某某', phone: '', email: '' })
const getInitial = (name) => name ? name[0] : '?'

onMounted(async () => {
  try {
    const response = await api.get('/api/user/user-info')
    user.value = response.data
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
})

const nicknameDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const phoneDialogVisible = ref(false)
const emailDialogVisible = ref(false)

const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const nicknameForm = ref({ type: 'nickname', value: '' })
const phoneForm = ref({ type: 'phone', value: '' })
const emailForm = ref({ type: 'email', value: '' })

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_, value) => value === passwordForm.value.newPassword,
      message: '两次输入密码不一致',
      trigger: 'blur'
    },
  ],
}
const phoneRules = {
  value: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
}
const emailRules = {
  value: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
}

const submitNickname = async () => {
  try {
    await api.post('api/user/bind-contact', nicknameForm.value)
    user.value.nickname = nicknameForm.value.value
    localStorage.setItem("nickname", nicknameForm.value.value)
    ElMessage.success('昵称修改成功')
    nicknameDialogVisible.value = false
  } catch {
    ElMessage.error('昵称修改失败')
  }
}
const submitPassword = async () => {
  try {
    await api.post('api/user/change-password', passwordForm.value)
    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码修改失败')
  }
}
const submitPhone = async () => {
  try {
    await api.post('api/user/bind-contact', phoneForm.value)
    user.value.phone = phoneForm.value.value
    ElMessage.success('手机号绑定成功')
    phoneDialogVisible.value = false
  } catch {
    ElMessage.error('绑定失败')
  }
}
const submitEmail = async () => {
  try {
    await api.post('api/user/bind-contact', emailForm.value)
    user.value.email = emailForm.value.value
    ElMessage.success('邮箱绑定成功')
    emailDialogVisible.value = false
  } catch {
    ElMessage.error('绑定失败')
  }
}

const securityList = computed(() => [
  {
    label: '手机号',
    description: user.value.phone
      ? `${user.value.phone.slice(0, 3)}****${user.value.phone.slice(-4)}`
      : '绑定后，可通过手机号接收消息',
    status: user.value.phone ? '已绑定' : '未绑定',
    icon: Phone,
    buttonText: user.value.phone ? '换绑' : '绑定',
    action: () => (phoneDialogVisible.value = true),
  },
  {
    label: '邮箱',
    description: user.value.email
      ? user.value.email.replace(/(.{1}).*(@.*)/, '$1***$2')
      : '绑定后，可通过邮箱接收消息',
    status: user.value.email ? '已绑定' : '未绑定',
    icon: Message,
    buttonText: user.value.email ? '换绑' : '绑定',
    action: () => (emailDialogVisible.value = true),
  },
  {
    label: '密码',
    description: '********',
    status: '',
    icon: Lock,
    buttonText: '修改',
    action: () => (passwordDialogVisible.value = true),
  },
])
</script>

<style scoped>
/* 样式同之前一致，不变 */
.account-info-page {
  display: flex;
  gap: 32px;
  padding: 32px;
  background-color: #f9fafc;
}
.section-title {
  font-size: 18px;
  font-weight: bold;
  margin-bottom: 24px;
}
.shadow-card {
  background-color: #fff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}
.left-panel {
  width: 260px;
  text-align: center;
}
.avatar {
  margin: 12px 0;
}
.username {
  font-weight: 500;
  font-size: 16px;
  margin-top: 8px;
}
.nickname {
  display: flex;
  justify-content: center;
  align-items: center;
  color: #666;
  gap: 6px;
  margin-top: 6px;
}
.edit-icon {
  cursor: pointer;
  font-size: 16px;
}
.version-label {
  margin-top: 20px;
  font-size: 13px;
  color: #999;
}
.version-number {
  font-weight: bold;
  margin-top: 4px;
}
.right-panel {
  flex: 1;
}
.security-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}
.security-item {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
  transition: box-shadow 0.2s ease;
}
.security-item:hover {
  box-shadow: 0 0 0 1px #dcdfe6;
}
.icon-wrapper {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  background-color: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.security-icon {
  color: #409EFF;
}
.info {
  flex: 1;
}
.label {
  font-weight: 500;
  margin-bottom: 4px;
}
.desc {
  font-size: 13px;
  color: #999;
}
</style>
