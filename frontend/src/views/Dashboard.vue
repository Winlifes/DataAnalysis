<template>
  <TopNav/>
  <el-container class="main-layout">
    <el-container class="content-wrapper">
      <Sidebar
        :is-collapsed="isSidebarCollapsed"
        :current-dashboard-id="currentDashboardId"
        :menu-data="menuData"
        @toggle-collapse="toggleSidebarCollapse"
        @add-folder="openAddModal('folder')"
        @add-dashboard="openAddModal('dashboard')"
        @select-dashboard="handleDashboardSelect"
        @edit-item="openEditModal"
        @delete-item="handleDeleteItem"
        @move-item="openMoveModal"
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
            <LineChartCard
              v-bind="chartConfig"
              :current-size="chartConfig.size"
              @toggle-size="handleChartResize"
              @remove="handleChartRemoveFromLayout"
              @settings="handleChartSettings"
              @refresh="handleChartRefresh"
              @fullscreen="handleChartFullscreen"
              @title-click="handleChartTitleClick"
              @granularity-change="handleGranularityChange"
              @daterange-change="handleDateRangeChange"
              @comparison-toggle="handleComparisonToggle"
              :card-id="chartConfig.id"
            />
          </div>
          <div v-if="!currentChartLayouts || currentChartLayouts.length === 0" class="no-dashboard">
            请从左侧菜单选择一个看板或新建看板。
          </div>
        </div>
      </el-main>
    </el-container>

    <el-dialog
      v-model="itemModalVisible"
      :title="modalMode === 'add' ? (addItemType === 'dashboard' ? '新建看板' : '新建文件夹') : '重命名'"
      width="400px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form ref="itemFormRef" :model="itemForm" :rules="itemFormRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="itemForm.name" placeholder="请输入名称"/>
        </el-form-item>
        <el-form-item
          v-if="modalMode === 'add' && addItemType === 'dashboard'"
          label="所属文件夹"
          prop="parentId"
        >
          <el-select v-model="itemForm.parentId" placeholder="请选择文件夹" style="width: 100%">
            <el-option
              v-for="folder in availableFolders"
              :key="folder.id"
              :label="folder.section ? `[${folder.section}] ${folder.title}` : folder.title"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="modalMode === 'add' && addItemType === 'folder'"
          label="所属分组"
          prop="parentId"
        >
          <el-select
            v-model="itemForm.parentId"
            placeholder="请选择分组 (我的看板/项目空间)"
            style="width: 100%"
          >
            <el-option label="我的看板" value="section-my"/>
            <el-option label="项目空间" value="section-project"/>
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
    <!-- 移动看板对话框 -->
    <el-dialog
      v-model="moveModalVisible"
      title="移动看板"
      width="400px"
      :close-on-click-modal="false"
      @closed="resetMoveForm"
    >
      <el-form ref="moveFormRef" :model="moveForm" :rules="moveFormRules" label-width="80px">
        <el-form-item label="目标文件夹" prop="targetFolderId">
          <el-select v-model="moveForm.targetFolderId" placeholder="请选择文件夹" style="width: 100%">
            <el-option
              v-for="folder in availableFolders"
              :key="folder.id"
              :label="folder.section ? `[${folder.section}] ${folder.title}` : folder.title"
              :value="folder.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="moveModalVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMove">确定</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup>
import { ref, shallowRef, onMounted } from 'vue';
import {
  ElMessage,
  ElMessageBox,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElSelect,
  ElOption,
  ElContainer,
  ElMain,
  ElTag,
  ElButton
} from 'element-plus';
import TopNav from '@/components/TopNav.vue';
import Sidebar from '@/components/Sidebar.vue';
import LineChartCard from '@/components/LineChartCard.vue';
import {
  Filter,
  Refresh,
  MoreFilled,
  Plus,
  Star,
  Folder
} from '@element-plus/icons-vue';
import api from '@/api/index.js';

// ---------- 状态定义 ----------
const timezone = ref('UTC-8');
const isSidebarCollapsed = ref(false);
const menuData = ref([]);                // 传给 Sidebar 的已处理菜单数据
const availableFolders = ref([]);        // 新建看板时的文件夹列表
const currentDashboardId = ref('db-core-metrics');
const currentDashboardTitle = ref('看板');
const currentChartLayouts = ref([]);
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

// 移动
const moveModalVisible = ref(false);
const moveFormRef = ref(null);
const moveForm = ref({ id: '', targetFolderId: null });
const moveFormRules = {
  targetFolderId: [{ required: true, message: '请选择目标文件夹', trigger: 'change' }]
};

// ---------- API & 刷新方法 ----------
/** 拉取并处理菜单数据，构造“我的看板”“项目空间”两个分区 */
const fetchMenu = async () => {
  try {
    const response = await api.get('/api/menu/list');
    const raw = response.data;
    const processed = [];
    const mySection = { id: 'section-my', type: 'section', title: '我的看板', children: [] };
    const projSection = { id: 'section-project', type: 'section', title: '项目空间', children: [] };

    raw.forEach(item => {
      if (item.icon === 'section-my') {
        item.icon = shallowRef(Star);
        mySection.children.push(item);
      } else if (item.icon === 'section-project') {
        item.icon = shallowRef(Folder);
        projSection.children.push(item);
      }
    });

    processed.push(mySection, projSection);
    menuData.value = processed;

    // 同步 availableFolders 用于 “新建看板” 下拉
    availableFolders.value = processed.flatMap(section =>
      section.children.map(f => ({
        id: f.id,
        title: f.title,
        section: section.title
      }))
    );
  } catch (err) {
    console.error('fetchMenu error:', err);
    ElMessage.error('加载菜单失败');
  }
};

/** 加载仪表盘数据 */
const allDashboardConfigs = ref({ /* ...同原配置...*/ });
const loadDashboardData = id => {
  const cfg = allDashboardConfigs.value[id];
  if (cfg) {
    currentChartLayouts.value = cfg.charts;
    currentDashboardTitle.value = cfg.title;
  } else {
    currentChartLayouts.value = [];
    currentDashboardTitle.value = '看板未找到';
  }
};

// ---------- 交互方法 ----------
const toggleSidebarCollapse = () => { isSidebarCollapsed.value = !isSidebarCollapsed.value; };
const handleDashboardSelect = id => { currentDashboardId.value = id; loadDashboardData(id); };
const resetForm = () => {
  itemForm.value = { name: '', parentId: null };
  editingItemId.value = null;
  itemFormRef.value?.resetFields();
};
const openAddModal = type => {
  resetForm();
  modalMode.value = 'add';
  addItemType.value = type;
  itemModalVisible.value = true;
};
const openEditModal = ({ id, type, name }) => {
  resetForm();
  modalMode.value = 'edit';
  addItemType.value = type;
  editingItemId.value = id;
  itemForm.value.name = name;
  itemModalVisible.value = true;
};
const submitItemForm = async () => {
  await itemFormRef.value.validate(valid => {
    if (!valid) return;
    const payload = modalMode.value === 'add'
      ? { title: itemForm.value.name, ...(addItemType.value === 'dashboard' ? { folderId: itemForm.value.parentId } : { parentId: itemForm.value.parentId }) }
      : { id: editingItemId.value, title: itemForm.value.name };
    const call = modalMode.value === 'add'
      ? api.post(`/api/menu/${addItemType.value}`, payload)
      : api.put(`/api/menu/${addItemType.value}`, payload);
    call.then(() => {
      ElMessage.success(`${modalMode.value === 'add' ? '创建' : '重命名'}${addItemType.value === 'dashboard' ? '看板' : '文件夹'}成功`);
      itemModalVisible.value = false;
      fetchMenu(); // 局部刷新侧边栏
    }).catch(err => {
      console.error(err);
      ElMessage.error(`${modalMode.value === 'add' ? '创建' : '重命名'}失败`);
    });
  });
};
const handleDeleteItem = ({ id, type, name }) => {
  ElMessageBox.confirm(
    `确定要删除${type==='dashboard'?'看板':'文件夹'}“${name}”？${type==='folder'?'其包含的看板也将删除':''}`,
    '确认删除', { confirmButtonText:'删除', cancelButtonText:'取消', type:'warning' }
  ).then(() => {
    api.delete(`/api/menu/${type}/${id}`)
      .then(() => {
        ElMessage.success('删除成功');
        fetchMenu(); // 局部刷新侧边栏
      })
      .catch(err => {
        console.error(err);
        ElMessage.error('删除失败');
      });
  });
};

// 打开“移动”对话框
const openMoveModal = ({ id, type }) => {
  // 仅 dashboard 支持移动
  moveForm.value.id = id;
  moveForm.value.targetFolderId = null;
  moveModalVisible.value = true;
};
const resetMoveForm = () => {
  moveForm.value = { id: '', targetFolderId: null };
};
const submitMove = () => {
  moveFormRef.value.validate(valid => {
    if (!valid) return;
    api.put('/api/menu/dashboard/move', {
      id: moveForm.value.id,
      targetFolderId: moveForm.value.targetFolderId
    })
      .then(() => {
        ElMessage.success('移动成功');
        moveModalVisible.value = false;
        fetchMenu();
      })
      .catch(() => {
        ElMessage.error('移动失败');
      });
  });
};

// ---------- 生命周期 ----------
onMounted(() => {
  fetchMenu();
  loadDashboardData(currentDashboardId.value);
});

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
  fetchMenu();
  loadDashboardData(currentDashboardId.value);
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
