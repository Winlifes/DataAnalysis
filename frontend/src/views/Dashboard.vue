<template>
  <TopNav/>
  <el-container class="main-layout">
    <el-container class="content-wrapper">
      <Sidebar
        :is-collapsed="isSidebarCollapsed"
        :current-dashboard-id="currentDashboardId"
        @toggle-collapse="toggleSidebarCollapse"
        @add-folder="openAddModal('folder')"
        @add-dashboard="openAddModal('dashboard')"
        @select-dashboard="handleDashboardSelect"
        @edit-item="openEditModal"
        @delete-item="handleDeleteItem"
      />

      <el-main class="main-content" :style="{ marginLeft: 0 }">
        <div class="main-content-header">
          <div class="title-area">
            <h2 class="page-title">{{ currentDashboardTitle }}</h2>
            <el-tag type="info" size="small" effect="plain" class="header-tag">用户1</el-tag>
            <el-tag type="info" size="small" effect="plain" class="header-tag">用户2</el-tag>
          </div>
          <div class="actions-area">
            <el-select v-model="timezone" placeholder="Select" size="small" style="width: 100px;">
              <el-option label="UTC-8" value="UTC-8"></el-option>
              <el-option label="UTC+8" value="UTC+8"></el-option>
            </el-select>
            <el-button :icon="Filter" circle size="small" title="Filter"></el-button>
            <el-button :icon="Refresh" circle size="small" title="Refresh"></el-button>
            <el-button :icon="MoreFilled" circle size="small" title="More"></el-button>
            <el-button type="primary" :icon="Plus" size="small">新建</el-button>
          </div>
        </div>
        <div class="charts-grid">
          <div v-for="(chartConfig) in currentChartLayouts" :key="chartConfig.id"
               :style="{ 'grid-column': chartConfig.size === 'large' ? 'span 2' : 'span 1' }"
               class="chart-grid-item">
            <LineChartCard v-bind="chartConfig" :current-size="chartConfig.size"
                           @toggle-size="handleChartResize" @remove="handleChartRemoveFromLayout"
                           @settings="handleChartSettings" @refresh="handleChartRefresh"
                           @fullscreen="handleChartFullscreen" @title-click="handleChartTitleClick"
                           @granularity-change="handleGranularityChange"
                           @daterange-change="handleDateRangeChange"
                           @comparison-toggle="handleComparisonToggle" :card-id="chartConfig.id"/>
          </div>
          <div v-if="!currentChartLayouts || currentChartLayouts.length === 0" class="no-dashboard">
            请从左侧菜单选择一个看板或新建看板。
          </div>
        </div>
      </el-main>
    </el-container>

    <el-dialog v-model="itemModalVisible"
               :title="modalMode === 'add' ? (addItemType === 'dashboard' ? '新建看板' : '新建文件夹') : '重命名'"
               width="400px" :close-on-click-modal="false" @closed="resetForm">
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemFormRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="itemForm.name" placeholder="请输入名称"></el-input>
        </el-form-item>
        <el-form-item v-if="modalMode === 'add' && addItemType === 'dashboard'" label="所属文件夹"
                      prop="parentId">
          <el-select v-model="itemForm.parentId" placeholder="请选择文件夹" style="width: 100%">
            <el-option v-for="folder in availableFolders" :key="folder.id" :label="folder.title" :value="folder.id">
              <span>{{ folder.section ? `[${folder.section}] ` : '' }}{{ folder.title }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item v-if="modalMode === 'add' && addItemType === 'folder'" label="所属分组"
                      prop="parentId">
          <el-select v-model="itemForm.parentId" placeholder="请选择分组 (我的看板/项目空间)"
                     style="width: 100%">
            <el-option label="我的看板" value="section-my"></el-option>
            <el-option label="项目空间" value="section-project"></el-option>
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="itemModalVisible = false">取消</el-button>
          <el-button type="primary" @click="submitItemForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

  </el-container>
</template>

<script setup>
import { ref, shallowRef, computed, onMounted, nextTick } from 'vue';
import {
  ElMessage,
  ElMessageBox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption
} from 'element-plus';
import {ElContainer, ElMain, ElTag, ElButton} from 'element-plus';
import TopNav from "@/components/TopNav.vue";
import Sidebar from "@/components/Sidebar.vue";
import LineChartCard from "@/components/LineChartCard.vue";
import {
  Filter,
  Refresh,
  MoreFilled,
  Plus,
  Warning,
  Star,
  Folder,
  DataAnalysis
} from '@element-plus/icons-vue';
import api from "@/api/index.js";

// 状态变量
const timezone = ref('UTC-8');
const isSidebarCollapsed = ref(false);
const currentDashboardId = ref('db-core-metrics');
const currentChartLayouts = ref([]);
const currentDashboardTitle = ref('看板');
const itemModalVisible = ref(false);
const modalMode = ref('add');
const addItemType = ref('dashboard');
const editingItemId = ref(null);
const itemFormRef = ref(null);
const itemForm = ref({ name: '', parentId: null });
const itemFormRules = {
  name: [{ required: true, message: '名称不能为空', trigger: 'blur' }],
  parentId: [{ required: true, message: '所属位置不能为空', trigger: 'change' }]
};

// 修复新建看板选择文件夹没有数据的问题
// 定义 availableFolders，并在组件加载时调用 fetchFolders 从 API 获取文件夹数据
const availableFolders = ref([]);
const fetchFolders = async () => {
  try {
    const response = await api.get('/api/menu/list');
    const rawData = response.data;
    const folders = [];
    rawData.forEach(item => {
      // 根据接口数据中标识文件夹的字段做判断，此处假设 icon 字段为 'section-my' 或 'section-project'
      if (item.icon === 'section-my' || item.icon === 'section-project') {
        const sectionTitle = item.icon === 'section-my' ? '我的看板' : '项目空间';
        folders.push({
          id: item.id,
          title: item.title,
          section: sectionTitle
        });
      }
    });
    availableFolders.value = folders;
  } catch (error) {
    console.error('Failed to fetch folders:', error);
  }
};

const allDashboardConfigs = ref({
  'db-sales-overview': { title: '销售总览看板', charts: [] },
  'db-marketing-kpi': { title: '市场KPI看板', charts: [] },
  'db-quick-test': { title: '快速测试看板', charts: [] },
  'db-core-metrics': {
    title: '核心数据看板',
    charts: [{
      id: 'new-users',
      reportId: 'RPT001',
      size: 'small',
      title: "新增用户",
      dateLabel: "2025-04-13(日)",
      mainMetric: 76,
      comparisons: [{ label: '日环比', value: -24.75 }, { label: '周同比', value: -2.56 }],
      average: 79,
      initialGranularity: 'day',
      initialDateRange: null,
      initialComparison: false,
      initialShowLabels: false,
      initialChartType: 'line',
      chartData: {
        xData: ['04/07', '04/08', '04/09', '04/10', '04/11', '04/12', '04/13'],
        yData: [70, 85, 85, 60, 70, 101, 76],
        legend: '游戏安装新增用户数'
      },
      chartColor: "#409EFF",
      availableChartTypes: ['line', 'bar', 'table'],
      showAreaStyle: false
    }, {
      id: 'avg-duration',
      reportId: 'RPT002',
      size: 'small',
      title: "人均在线时长",
      dateLabel: "2025-04-13(日)",
      mainMetric: 35,
      metricUnit: "分钟",
      comparisons: [],
      average: 33,
      initialGranularity: 'day',
      initialDateRange: null,
      initialComparison: false,
      initialShowLabels: false,
      initialChartType: 'line',
      chartData: {
        xData: ['04/07', '04/08', '04/09', '04/10', '04/11', '04/12', '04/13'],
        yData: [30, 32, 31, 29, 33, 38, 35],
        legend: '人均在线时长(分钟)'
      },
      chartColor: "#67C23A",
      availableChartTypes: ['line', 'bar', 'cumulative', 'table'],
      showAreaStyle: true
    }]
  },
  'db-user-activity': {
    title: '用户活跃数据',
    charts: [{
      id: 'active-users',
      reportId: 'RPT003',
      size: 'small',
      title: "活跃用户数",
      dateLabel: "2025-04-13(日)",
      mainMetric: 1305,
      comparisons: [{ label: '日环比', value: 8.1 }, { label: '周同比', value: 15.3 }],
      average: 1195,
      initialGranularity: 'day',
      initialDateRange: null,
      initialComparison: true,
      initialShowLabels: false,
      initialChartType: 'bar',
      chartData: {
        xData: ['04/07', '04/08', '04/09', '04/10', '04/11', '04/12', '04/13'],
        yData: [1150, 1180, 1210, 1190, 1240, 1208, 1305],
        legend: '日活跃用户'
      },
      chartColor: "#E6A23C",
      availableChartTypes: ['line', 'bar', 'table'],
      showAreaStyle: false
    }]
  }
});

const loadDashboardData = (dashboardId) => {
  console.log('Loading dashboard:', dashboardId);
  const config = allDashboardConfigs.value[dashboardId];
  if (config) {
    currentChartLayouts.value = config.charts;
    currentDashboardTitle.value = config.title;
  } else {
    console.warn(`Dashboard config not found for ID: ${dashboardId}`);
    currentChartLayouts.value = [];
    currentDashboardTitle.value = '看板未找到';
  }
};

const toggleSidebarCollapse = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
};

const handleDashboardSelect = (dashboardId) => {
  currentDashboardId.value = dashboardId;
  loadDashboardData(dashboardId);
};

const resetForm = () => {
  itemForm.value = { name: '', parentId: null };
  editingItemId.value = null;
  if (itemFormRef.value) {
    itemFormRef.value.resetFields();
  }
};

const openAddModal = (type = 'dashboard') => {
  resetForm();
  modalMode.value = 'add';
  addItemType.value = type;
  itemModalVisible.value = true;
};

const openEditModal = (itemInfo) => {
  resetForm();
  modalMode.value = 'edit';
  addItemType.value = itemInfo.type;
  editingItemId.value = itemInfo.id;
  itemForm.value.name = itemInfo.name;
  itemModalVisible.value = true;
};

const submitItemForm = async () => {
  if (!itemFormRef.value) return;
  await itemFormRef.value.validate((valid) => {
    if (valid) {
      if (modalMode.value === 'add') {
        if (addItemType.value === 'dashboard') {
          api.post('/api/menu/dashboard', { title: itemForm.value.name, folderId: itemForm.value.parentId })
            .then(response => {
              ElMessage.success(`看板“${itemForm.value.name}”已添加`);
              itemModalVisible.value = false;
              window.location.reload();
            })
            .catch(error => {
              console.error('Failed to create dashboard:', error);
              ElMessage.error('创建看板失败');
            });
        } else if (addItemType.value === 'folder') {
          api.post('/api/menu/folder', { title: itemForm.value.name, parentId: itemForm.value.parentId })
            .then(response => {
              ElMessage.success(`文件夹“${itemForm.value.name}”已添加`);
              itemModalVisible.value = false;
              window.location.reload();
            })
            .catch(error => {
              console.error('Failed to create folder:', error);
              ElMessage.error('创建文件夹失败');
            });
        }
      } else {
        if (addItemType.value === 'dashboard') {
          api.put('/api/menu/dashboard', { id: editingItemId.value, title: itemForm.value.name })
            .then(response => {
              ElMessage.success('看板重命名成功');
              itemModalVisible.value = false;
              window.location.reload();
            })
            .catch(error => {
              console.error('Failed to rename dashboard:', error);
              ElMessage.error('重命名看板失败');
            });
        } else if (addItemType.value === 'folder') {
          api.put('/api/menu/folder', { id: editingItemId.value, title: itemForm.value.name })
            .then(response => {
              ElMessage.success('文件夹重命名成功');
              itemModalVisible.value = false;
              window.location.reload();
            })
            .catch(error => {
              console.error('Failed to rename folder:', error);
              ElMessage.error('重命名文件夹失败');
            });
        }
      }
    } else {
      console.log('Form validation failed');
      return false;
    }
  });
};

const handleDeleteItem = (itemInfo) => {
  ElMessageBox.confirm(
    `确定要删除 ${itemInfo.type === 'dashboard' ? '看板' : '文件夹'} “${itemInfo.name}” 吗? ${itemInfo.type === 'folder' ? '其包含的所有看板也将被删除。' : ''}`,
    '确认删除',
    {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const itemId = itemInfo.id;
    const itemType = itemInfo.type;
    const deleteApiUrl = itemType === 'dashboard' ? `/api/menu/dashboard/${itemId}` : `/api/menu/folder/${itemId}`;
    api.delete(deleteApiUrl)
      .then(response => {
        ElMessage.success('删除成功');
        window.location.reload();
      })
      .catch(error => {
        console.error(`Failed to delete ${itemType}:`, error);
        ElMessage.error('删除失败');
      });
  }).catch(() => {
    ElMessage.info('删除已取消');
  });
};

const handleChartResize = (cardId) => { /* ... */ };
const handleChartSettings = (cardId) => { /* ... */ };
const handleChartRemoveFromLayout = (cardId) => { /* ... */ };
const handleChartRefresh = (cardId) => { /* ... */ };
const handleChartFullscreen = (cardId) => { /* ... */ };
const handleChartTitleClick = (reportId) => { /* ... */ };
const handleGranularityChange = (payload) => { /* ... */ };
const handleDateRangeChange = (payload) => { /* ... */ };
const handleComparisonToggle = (payload) => { /* ... */ };

onMounted(() => {
  loadDashboardData(currentDashboardId.value);
  fetchFolders();
});
</script>

<style scoped>
.main-layout {
  height: calc(100vh - 80px);
  overflow: hidden;
}

.content-wrapper {
  height: 100%;
  overflow: hidden;
  display: flex;
}

.main-content {
  background-color: #f0f2f5;
  padding: 24px;
  overflow-y: auto;
  height: 100%;
  box-sizing: border-box;
  flex-grow: 1;
  transition: margin-left 0.3s ease;
}

.main-content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 15px;
}

.title-area {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
  color: #303133;
}

.header-tag {
  cursor: default;
}

.actions-area {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.chart-grid-item {
  transition: grid-column 0.3s ease;
}

.no-dashboard {
  grid-column: span 2;
  text-align: center;
  padding: 50px;
  color: var(--el-text-color-secondary);
  font-size: 16px;
}

@media (max-width: 1200px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }

  .chart-grid-item {
    grid-column: span 1 !important;
  }

  .no-dashboard {
    grid-column: span 1;
  }
}
</style>
