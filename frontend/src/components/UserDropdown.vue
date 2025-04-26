<template>
  <el-dropdown @command="handleCommand">
    <span class="el-dropdown-link">
      {{nickname}}
      <el-icon><arrow-down /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="profile">个人中心</el-dropdown-item>
        <el-dropdown-item divided @click="toggleDarkMode">
          <el-icon>
            <component :is="isDark ? Sunny : Moon" />
          </el-icon>
          {{ isDark ? '浅色模式' : '暗黑模式' }}
        </el-dropdown-item>
        <el-dropdown-item command="logout">
          <el-icon><switch-button /></el-icon> 登出
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, SwitchButton, Moon, Sunny } from '@element-plus/icons-vue'
const isDark = ref(false)

const nickname = ref('')

// 初始化读取本地设置
onMounted(() => {
  nickname.value = localStorage.getItem('nickname') || '用户'
  const saved = localStorage.getItem('theme')
  if (saved === 'dark') {
    isDark.value = true
    document.documentElement.classList.add('dark')
  }
})

// 切换主题
const toggleDarkMode = () => {
  isDark.value = !isDark.value
  if (isDark.value) {
    document.documentElement.classList.add('dark')
    localStorage.setItem('theme', 'dark')
  } else {
    document.documentElement.classList.remove('dark')
    localStorage.setItem('theme', 'light')
  }
}

const handleCommand = (command) => {
  switch (command) {
    case 'profile':
      window.location.href = '/userCenter'
      break
    case 'logout':
      ElMessage.success('退出成功')
      localStorage.removeItem('token')
      localStorage.removeItem('nickname')
      localStorage.removeItem("isSuperAdmin");
      localStorage.removeItem("isCheck");
      localStorage.removeItem("isEdit");
      localStorage.removeItem("isExport");
      window.location.href = '/login'
      break
    default:
      ElMessage.warning("功能暂未实现")
  }
}
</script>

<style scoped>
.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
}
</style>
