<template>
  <TopNav/>
  <div class="user-center-container">
    <!-- 左侧菜单栏 -->
    <el-card class="side-menu">
      <el-menu
        :default-active="activeMenu"
        class="el-menu-vertical-demo"
        @select="handleMenuClick"
      >
        <el-menu-item index="info">
          <el-icon><User /></el-icon>
          账号信息
        </el-menu-item>
        <el-menu-item index="permissions">
          <el-icon><Lock /></el-icon>
          权限详情
        </el-menu-item>
        <!-- ✅ 仅管理员可见 -->
        <el-menu-item v-if="isAdmin === 'true'" index="userManage">
          <el-icon><UserFilled /></el-icon>
          用户管理
        </el-menu-item>
      </el-menu>
    </el-card>

    <!-- 右侧内容区域 -->
    <div class="user-content">
      <AccountInfo v-if="activeMenu === 'info'" />
      <PermissionInfo v-else-if="activeMenu === 'permissions'" />
      <UserManage v-else-if="activeMenu === 'userManage'" />
    </div>
  </div>
</template>

<script setup>
import {computed, ref} from 'vue'
import { User, Lock, UserFilled } from '@element-plus/icons-vue'

// 子组件（你可以拆成独立文件）
import AccountInfo from './components/AccountInfo.vue'
import PermissionInfo from './components/PermissionInfo.vue'
import UserManage from './components/UserManage.vue'
import TopNav from "@/components/TopNav.vue";

const activeMenu = ref('info')

// 判断是否为管理员
const isAdmin = computed(() => localStorage.getItem('isSuperAdmin'))

const handleMenuClick = (key) => {
  activeMenu.value = key
}
</script>

<style scoped>
.user-center-container {
  display: flex;
  gap: 20px;
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100%;
}

.side-menu {
  width: 200px;
  min-width: 200px;
}

.user-content {
  flex: 1;
}
</style>
