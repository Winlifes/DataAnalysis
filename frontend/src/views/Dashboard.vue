<template>
  <TopNav></TopNav>
  <div class="dashboard-layout">
    <!-- 左侧 -->
    <aside class="sidebar">
      <div class="header">
        <el-input
          v-model="search"
          placeholder="搜索看板"
          size="small"
          clearable
        />
        <div class="actions">
          <el-button icon="Plus" size="small" @click="addBoard">新建</el-button>
          <el-button icon="Setting" size="small">管理</el-button>
        </div>
      </div>

      <el-menu class="menu" default-active="1">
        <el-menu-item index="1">数据分析</el-menu-item>
        <el-menu-item index="2">用户活跃度</el-menu-item>
      </el-menu>
    </aside>

    <!-- 右侧 -->
    <main class="content">
      <h2>📊 数据分析看板</h2>
      <LineChart />
      <LineChart />
      <LineChart />
    </main>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElInput, ElButton, ElMenu, ElMenuItem } from 'element-plus'
import LineChart from '@/components/LineChart.vue'
import TopNav from "@/components/TopNav.vue";

const search = ref('')

const addBoard = () => {
  console.log('点击新建看板')
}
</script>

<style scoped>
.dashboard-layout {
  display: flex;
  height: calc(100vh - 80px); /* 减去顶部导航栏高度 */
  overflow: hidden;
}

.sidebar {
  width: 260px;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #ddd;
  padding: 12px;
  overflow-y: auto;
}

.header {
  margin-bottom: 16px;
}

.actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.menu {
  flex: 1;
  overflow-y: auto;
}

.content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
