<template>
  <el-header class="top-nav">
    <!-- 左侧导航文字 -->
    <div class="nav-left">
      <router-link to="/dashboard" class="nav-item" :class="{ active: route.path === '/dashboard' }">看板</router-link>
      <router-link to="/analysis" class="nav-item" :class="{ active: route.path === '/analysis' }">分析</router-link>
      <router-link to="/users" class="nav-item" :class="{ active: route.path === '/users' }">用户</router-link>
      <router-link to="/data" class="nav-item" :class="{ active: route.path === '/data' }">数据</router-link>
    </div>

    <!-- 右侧操作 -->
    <div class="nav-right">

      <!-- 用户下拉菜单 -->
      <UserDropdown/>
    </div>
  </el-header>
</template>

<script setup>
import { ref } from 'vue'
const search = ref('')
import { useRoute } from 'vue-router'
import UserDropdown from "@/components/UserDropdown.vue";
const route = useRoute()
</script>

<style scoped>
.top-nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  background-color: #fff;
  border-bottom: 1px solid #e4e7ed;
  height: 60px;
}

.nav-left {
  display: flex;
  gap: 20px;
  font-size: 16px;
}

.nav-item {
  cursor: pointer;
  color: #606266;
  text-decoration: none;
}

.nav-item.active {
  color: #409eff;
  font-weight: bold;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-input {
  width: 200px;
}

.user-name {
  cursor: pointer;
  color: #606266;
  display: flex;
  align-items: center;
}
</style>
