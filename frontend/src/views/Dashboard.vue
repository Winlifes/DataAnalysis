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

      <el-main class="main-content" :style="{ marginLeft: 0 }" v-loading="isLoadingDashboard || isLoadingAnalysis">
        <div class="main-content-header">
          <div class="title-area">
            <h2 class="page-title">{{ currentDashboardTitle || '看板' }}</h2>
          </div>
          <div class="actions-area">
            <el-button type="primary" :icon="Plus" size="small" @click="handleAddNewAnalysis" :disabled="!currentDashboardId">新建分析项</el-button>
          </div>
        </div>
        <div class="charts-grid">
          <div v-for="(analysisResult, index) in loadedAnalysisResults" :key="analysisResult.value.id || index"
               class="chart-grid-item"
               :class="{ 'chart-grid-item-large': analysisResult.value.size === 'large' }"
               >
            <AnalysisResultCard
              :analysis-data="analysisResult.value.data"
              :id="analysisResult.value.id"
            :config="JSON.parse(analysisResult.value.config)"
            :title="analysisResult.value.title"
            :is-loading="analysisResult.value.isLoading"
            :error="analysisResult.value.error"
              :size="analysisResult.value.size"
              :selectedChartType="analysisResult.value.selectedChartType"
            @refresh="handleRefreshAnalysisItem(index)"
            @remove="handleRemoveAnalysisItem(index)"
            @settings="handleEditAnalysisItem(index)"
              @change-chart-type="handleChangeAnalysisItemChartType(index, $event)"
            @toggle-size="handleToggleAnalysisItemSize(index)"
            @export="handleExportAnalysisItem(index)"
            />
          </div>
          <div v-if="!currentDashboardId && !isLoadingDashboard" class="no-dashboard">
            请从左侧菜单选择一个看板或新建看板。
          </div>
          <div v-else-if="currentDashboardId && !isLoadingDashboard && !isLoadingAnalysis && (!loadedAnalysisResults || loadedAnalysisResults.length === 0)" class="no-dashboard">
            当前看板没有分析项。点击右上角“新建分析项”添加。
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

    <el-dialog
      v-model="showAddAnalysisDialog"
      title="添加新的分析项"
      width="600px"
      @close="resetAddAnalysisForm"
    >
      <p>Placeholder for Analysis Configuration Form...</p>
      <template #footer>
             <span class="dialog-footer">
                 <el-button @click="showAddAnalysisDialog = false">取消</el-button>
                 <el-button type="primary" @click="saveNewAnalysisItem">
                     添加到看板
                 </el-button>
             </span>
      </template>
    </el-dialog>

  </el-container>
</template>

<script setup>
import { ref, shallowRef, onMounted, watch } from 'vue'; // Import watch
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
  ElButton,
  ElLoading // Import ElLoading
} from 'element-plus';
import TopNav from '@/components/TopNav.vue';
import Sidebar from '@/components/Sidebar.vue';
// import LineChartCard from '@/components/LineChartCard.vue'; // Replace with AnalysisResultCard
import AnalysisResultCard from '@/components/AnalysisResultCard.vue'; // Assuming you create this component
import {
  Filter,
  Refresh,
  MoreFilled,
  Plus,
  Star,
  Folder
} from '@element-plus/icons-vue';
import api from '@/api/index.js';
import router from "@/router/index.js";

// ---------- State Definition ----------
const timezone = ref('UTC-8'); // Timezone state
const isSidebarCollapsed = ref(false); // Sidebar state
const menuData = ref([]);                // Data for Sidebar (folders and dashboards)
const availableFolders = ref([]);        // List of folders for "New Dashboard" modal
const currentDashboardId = ref(null); // Currently selected dashboard ID
const currentDashboardTitle = ref('看板'); // Title of the currently selected dashboard

// ** NEW: State for loaded dashboard details and analysis results **
const selectedDashboardDetails = ref(null); // Full details of the selected dashboard
const loadedAnalysisResults = ref([]); // Array of results for each analysis item in the dashboard
const isLoadingDashboard = ref(false); // Loading state for fetching dashboard details
const isLoadingAnalysis = ref(false); // Loading state for running all analysis items

// ** NEW: State for Add Analysis Item Dialog **
const showAddAnalysisDialog = ref(false);
const newAnalysisConfig = ref(null); // State for the new analysis item configuration


// Modals state (Add/Edit/Move)
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

// Move modal state
const moveModalVisible = ref(false);
const moveFormRef = ref(null);
const moveForm = ref({ id: '', targetFolderId: null });
const moveFormRules = {
  targetFolderId: [{ required: true, message: '请选择目标文件夹', trigger: 'change' }]
};

// ---------- API & Refresh Methods ----------
/** 拉取并处理菜单数据，构造“我的看板”“项目空间”两个分区 */
const fetchMenu = async () => {
  try {
    const response = await api.get('/api/menu/list'); // Assuming this endpoint returns folder/dashboard structure
    const raw = response.data;
    const processed = [];
    const mySection = { id: 'section-my', type: 'section', title: '我的看板', children: [] };
    const projSection = { id: 'section-project', type: 'section', title: '项目空间', children: [] };

    // Keep track of the first dashboard found for default selection
    let firstDashboardId = null;

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


    // ** NEW: Select the first dashboard item found after fetching menu **
    if (menuData.value.length > 0 && menuData.value[0].children.length > 0 && menuData.value[0].children[0].children.length > 0) {
      firstDashboardId = menuData.value[0].children[0].children[0].id;
      console.log("Auto-selecting first dashboard:", firstDashboardId);
      handleDashboardSelect(firstDashboardId);
    } else {
      console.log("No dashboards found in menu.");
      // Clear any previously loaded dashboard data
      currentDashboardId.value = null;
      currentDashboardTitle.value = '看板';
      selectedDashboardDetails.value = null;
      loadedAnalysisResults.value = [];
    }


  } catch (err) {
    console.error('fetchMenu error:', err);
    ElMessage.error('加载菜单失败');
  }
};

/**
 * Fetches the detailed configuration for a specific dashboard.
 * Assumes a backend endpoint GET /api/dashboards/{id} exists.
 */
const fetchDashboardDetails = async (dashboardId) => {
  isLoadingDashboard.value = true;
  selectedDashboardDetails.value = null;
  loadedAnalysisResults.value = []; // Clear previous results

  if (!dashboardId) {
    isLoadingDashboard.value = false;
    return;
  }

  try {
    console.log(`Fetching details for dashboard ID: ${dashboardId}`);
    const res = await api.get(`/api/menu/dashboard/${dashboardId}`);
    // Update the current dashboard title from the fetched details
    currentDashboardTitle.value = res.data?.title || '看板';

    // Assuming the backend endpoint returns a Dashboard object with a 'config' JSON string field
    const response = await api.get(`/api/menu/dashboard/config/${dashboardId}`);
    selectedDashboardDetails.value = response.data;
    console.log("Fetched dashboard details:", selectedDashboardDetails.value);




    // ** After fetching details, run the analysis for each item in the config **
    if (selectedDashboardDetails.value) {
      try {
        // Assuming the 'config' field is a JSON string representing an array of analysis configs
        const analysisConfigs = selectedDashboardDetails.value;
        if (Array.isArray(analysisConfigs)) {
          console.log(`Found ${analysisConfigs.length} analysis items in config. Running analysis for each.`);
          // Run analysis for each config item
          await runAnalysisForDashboardItems(analysisConfigs);
        } else {
          console.warn("Dashboard config is not an array:", analysisConfigs);
          ElMessage.warning("看板配置格式不正确。");
        }
      } catch (e) {
        console.error("Failed to parse dashboard config JSON:", e);
        ElMessage.error("加载看板配置失败：解析错误。");
      }
    } else {
      console.log("Dashboard has no config or empty config.");
      // If no config or empty config, ensure analysis results are empty and loading is false
      loadedAnalysisResults.value = [];
    }


  } catch (error) {
    console.error(`Failed to fetch dashboard details for ID ${dashboardId}:`, error);
    if (error.response && error.response.data && error.response.data.message) {
      ElMessage.error(`加载看板详情失败: ${error.response.data.message}`);
    } else {
      ElMessage.error(`加载看板详情失败: ${error.message || '未知错误'}`);
    }
    selectedDashboardDetails.value = null; // Clear on error
    loadedAnalysisResults.value = []; // Clear results on error

  } finally {
    isLoadingDashboard.value = false;
  }
};

/**
 * Runs the analysis for each configuration item loaded from the dashboard.
 * Populates the loadedAnalysisResults array.
 */
const runAnalysisForDashboardItems = async (analysisConfigs) => {
  isLoadingAnalysis.value = true;

  if (!analysisConfigs || analysisConfigs.length === 0) {
    loadedAnalysisResults.value = []; // Clear results if no configs
    isLoadingAnalysis.value = false;
    return;
  }

  // Initialize loadedAnalysisResults with placeholder items to show loading state immediately
  // Use a temporary ID for keying if config doesn't have one
  loadedAnalysisResults.value = analysisConfigs.map(data => ({
    config: data.config,
    title: data.title,
    data: null,
    isLoading: true,
    error: null,
    id: data.id,
    size: data.size,
    selectedChartType: data.chartType
  }));


  // Map each config to a promise that runs the analysis and returns the result item state
  const analysisPromises = analysisConfigs.map(async (data, index) => {
    // Get the corresponding item from the reactive array to update it later
    const resultItem = loadedAnalysisResults.value[index];

    try {
      // Build the query payload for the backend analysis endpoint
      const analysisQueryPayload = resultItem;


      console.log(`Running analysis for item ${index} 分析项 with payload:`, data);
      const response = await api.post('/api/analysis/event',  JSON.parse(data.config)); // Call the analysis API
      console.log(`Analysis result for item ${index}:`, response.data);
      // Return the state for this item on success
      return {
        status: 'fulfilled',
        value: {
          ...resultItem, // Keep original config and ID
          data: response.data,
          isLoading: false,
          error: null,
        }
      };

    } catch (error) {
      console.error(`Failed to run analysis for item ${index} ("${config.title || '分析项'}"):`, error);
      // Return the state for this item on failure
      return {
        status: 'rejected',
        reason: {
          ...resultItem, // Keep original config and ID
          data: null,
          isLoading: false,
          error: error.response?.data?.message || error.message || '未知错误', // Capture specific error message
        }
      };
    }
  });

  // Wait for all promises to settle
  const results = await Promise.allSettled(analysisPromises);

  // Update the reactive loadedAnalysisResults array based on the settled promises
  loadedAnalysisResults.value = results.map(settledResult => {
    if (settledResult.status === 'fulfilled') {
      return settledResult.value; // Use the fulfilled value (which is the updated item state)
    } else {
      return settledResult.reason; // Use the reason (which is the error item state)
    }
  });

  console.log("All analysis promises settled and state updated.", loadedAnalysisResults.value[0].value.config);

  isLoadingAnalysis.value = false;
  console.log("Finished running analysis for all dashboard items.");
};


/** 加载仪表盘数据 (Modified to fetch details) */
const loadDashboardData = (id) => {
  // This method is now primarily a wrapper to set the current ID and trigger fetching details
  if (id !== currentDashboardId.value) {
    currentDashboardId.value = id;
    // Find the title from menuData (optional, fetchDetails will also provide it)
    const dashboardMenuItem = findItemInMenu(menuData.value, id);
    currentDashboardTitle.value = dashboardMenuItem ? dashboardMenuItem.title : '看板';

    // Fetch the full dashboard details including the config
    //console.log("加载仪表盘数据")
    //fetchDashboardDetails(id);
  } else {
    console.log(`Dashboard ID ${id} is already selected.`);
    // If already selected, maybe just refresh the analysis?
    if (selectedDashboardDetails.value) {
      refreshDashboardAnalysis();
    } else {
      // If selected but details not loaded (e.g., failed last time), try fetching again
      console.log("加载仪表盘数据（2）")
      fetchDashboardDetails(id);
    }
  }
};

/** Helper to find an item by ID in the menu data structure */
const findItemInMenu = (menu, id) => {
  for (const section of menu) {
    if (section.children) {
      for (const item of section.children) {
        if (item.id === id) {
          return item;
        }
        // If items can be nested further, add recursion here
        if (item.type === 'folder' && item.children) {
          const foundInChildren = findItemInMenu([{ children: item.children }], id); // Wrap children in a temp section
          if (foundInChildren) return foundInChildren;
        }
      }
    }
  }
  return null;
};


// ** NEW: Method to re-run analysis for all items in the current dashboard **
const refreshDashboardAnalysis = () => {
  if (selectedDashboardDetails.value && selectedDashboardDetails.value.config) {
    console.log("Refreshing analysis for current dashboard.");
    try {
      const analysisConfigs = JSON.parse(selectedDashboardDetails.value.config);
      if (Array.isArray(analysisConfigs)) {
        runAnalysisForDashboardItems(analysisConfigs); // Re-run analysis
      } else {
        console.warn("Dashboard config is not an array, cannot refresh analysis.");
        ElMessage.warning("看板配置格式不正确，无法刷新分析。");
      }
    } catch (e) {
      console.error("Failed to parse dashboard config for refresh:", e);
      ElMessage.error("刷新分析失败：解析看板配置错误。");
    }
  } else {
    console.log("No dashboard details or config to refresh analysis.");
  }
};

// ** NEW: Methods for managing analysis items within a dashboard (placeholders) **
const handleAddNewAnalysis = () => {
  if (!currentDashboardId.value) {
    ElMessage.warning("请先选择一个看板。");
    return;
  }
  router.push("/analysis");
  // Open a modal/dialog to configure a new analysis item
  // This modal would likely be similar to the Analysis.vue page's form
  //showAddAnalysisDialog.value = true;
  // Reset the form state for a new analysis item
  resetAddAnalysisForm();

  // TODO: Fetch event schemas and user properties here for the modal form
  // This might involve adapting the fetching logic from Analysis.vue
};

const resetAddAnalysisForm = () => {
  // Reset the form state for a new analysis item
  newAnalysisConfig.value = {
    dateRange: [Date.now() - 3600 * 1000 * 24 * 30, Date.now()],
    selectedEvent: '',
    calculations: [],
    includeEventCount: true,
    includeUniqueUserCount: true,
    includeAverageCountPerUser: false,
    groupingAttribute: '',
    globalFilters: [],
    selectedChartType: 'bar', // Default chart type for new item
    title: '', // Title for this specific analysis item
  };
};

const saveNewAnalysisItem = async () => {
  if (!currentDashboardId.value) {
    ElMessage.warning("无法保存分析项：未选择看板。");
    return;
  }
  if (!newAnalysisConfig.value || !newAnalysisConfig.value.title) {
    ElMessage.warning("请输入分析项的标题。");
    return;
  }
  // Validate the newAnalysisConfig further if needed (e.g., required fields)
  // You might want to build a query payload from newAnalysisConfig and validate it first


  // Prepare the new analysis item config to be added to the dashboard's config array
  const newItemConfig = {
    // Assign a temporary client-side ID for keying if backend doesn't provide one immediately
    id: Math.random().toString(36).substring(2, 15),
    ...newAnalysisConfig.value,
    // Ensure only relevant fields are saved, maybe clean up temporary state
  };

  // Get the current dashboard config, add the new item, and save the whole dashboard
  if (!selectedDashboardDetails.value) {
    ElMessage.error("无法保存分析项：未加载看板详情。");
    return;
  }

  let currentConfigArray = [];
  try {
    // Parse the existing config JSON string
    if (selectedDashboardDetails.value.config) {
      currentConfigArray = JSON.parse(selectedDashboardDetails.value.config);
      if (!Array.isArray(currentConfigArray)) {
        console.warn("Existing dashboard config is not an array, starting with empty array.");
        currentConfigArray = [];
      }
    }
  } catch (e) {
    console.error("Failed to parse existing dashboard config:", e);
    ElMessage.error("保存分析项失败：读取现有配置错误。");
    return;
  }

  // Add the new item config to the array
  currentConfigArray.push(newItemConfig);

  // Serialize the updated config array back to a JSON string
  let updatedConfigJsonString = '';
  try {
    updatedConfigJsonString = JSON.stringify(currentConfigArray);
  } catch (e) {
    console.error("Failed to serialize updated config to JSON:", e);
    ElMessage.error("保存分析项失败：序列化新配置错误。");
    return;
  }

  // Prepare the payload to update the dashboard
  const updatePayload = {
    id: currentDashboardId.value,
    // Keep other fields the same, only update the config
    title: selectedDashboardDetails.value.title,
    folderId: selectedDashboardDetails.value.folderId, // Ensure folderId is included if needed by update API
    config: updatedConfigJsonString,
  };

  console.log("Saving new analysis item to dashboard. Update payload:", updatePayload);

  // Call the backend API to update the dashboard with the new config
  // Assuming backend endpoint is PUT /api/dashboards/{id} or PUT /api/dashboards
  try {
    // Use PUT to update the existing dashboard
    const response = await api.put(`/api/dashboards/${currentDashboardId.value}`, updatePayload); // Adjust endpoint if needed
    console.log("Dashboard updated with new analysis item:", response.data);
    ElMessage.success("分析项已添加到看板！");
    showAddAnalysisDialog.value = false; // Close dialog
    // Refresh the dashboard display by re-fetching details and running analysis
    console.log("分析项已添加到看板")
    fetchDashboardDetails(currentDashboardId.value);

  } catch (error) {
    console.error('Failed to save new analysis item to dashboard:', error);
    if (error.response && error.response.data && error.response.data.message) {
      ElMessage.error(`添加分析项失败: ${error.response.data.message}`);
    }
    else {
      ElMessage.error(`添加分析项失败: ${error.message || '未知错误'}`);
    }
  }
};

// Placeholder methods for AnalysisResultCard interactions
const handleRefreshAnalysisItem = (index) => {
  console.log("Refreshing analysis item at index:", index);
  // Re-run analysis for a specific item
  if (loadedAnalysisResults.value[index] && loadedAnalysisResults.value[index].config) {
    const configToRefresh = loadedAnalysisResults.value[index].config;
    // Update loading state for this item
    loadedAnalysisResults.value[index].isLoading = true;
    loadedAnalysisResults.value[index].error = null;
    loadedAnalysisResults.value[index].data = null; // Clear old data

    // Run analysis for this single item
    runAnalysisForDashboardItems([configToRefresh]).then(results => {
      // The runAnalysisForDashboardItems updates the array in place,
      // so no need to manually update loadedAnalysisResults.value[index] here.
      console.log("Finished refreshing single item.");
    }).catch(error => {
      // Error is already logged and messaged inside runAnalysisForDashboardItems
      console.error("Error during single item refresh:", error);
    });
  } else {
    console.warn("Cannot refresh analysis item at index:", index, "Config not found.");
  }
};

const handleRemoveAnalysisItem = async (index) => {

  console.log("Removing analysis item at index:", loadedAnalysisResults.value[index].value.id);
  if (!currentDashboardId.value) {
    ElMessage.warning("无法移除分析项：未选择看板。");
    return;
  }
  if (loadedAnalysisResults.value[index] && loadedAnalysisResults.value[index].config) {
    ElMessage.error("无法移除分析项：分析项未知");
    return;
  }

  ElMessageBox.confirm(
    `确定要从看板中移除此分析项吗？`,
    '确认移除', { confirmButtonText:'移除', cancelButtonText:'取消', type:'warning' }
  ).then(async () => {
    try {
      // Call the backend API to update the dashboard
      const response = await api.delete(`/api/menu/dashboard/config/${loadedAnalysisResults.value[index].value.id}`);
      console.log("Dashboard updated after item removal:", response.data);
      ElMessage.success("分析项已移除。");

      // Refresh the dashboard display
      fetchDashboardDetails(currentDashboardId.value);

    } catch (error) {
      console.error('Failed to remove analysis item:', error);
      if (error.response && error.response.data && error.response.data.message) {
        ElMessage.error(`移除分析项失败: ${error.response.data.message}`);
      }
      else {
        ElMessage.error(`移除分析项失败: ${error.message || '未知错误'}`);
      }
    }
  }).catch(() => {
    // User cancelled
  });
};

// ** Modified: Handle chart type change from AnalysisResultCard (local update only) **
const handleChangeAnalysisItemChartType = async (index, newType) => {
  console.log(`Changing chart type for item ${index} to ${newType} (local update)`);
  if (!loadedAnalysisResults.value[index]) {
    console.warn("Cannot change chart type: Analysis item not found at index.", index);
    return;
  }

  // Update the chart type for the specific item in the local reactive state
  loadedAnalysisResults.value[index].value.selectedChartType = newType;
  console.log(`Item ${loadedAnalysisResults.value[index].value.id} chart type updated locally to: ${newType}`);

  const updatePayload = {
    id: loadedAnalysisResults.value[index].value.id,
    chartType: loadedAnalysisResults.value[index].value.selectedChartType,
    size: loadedAnalysisResults.value[index].value.size,
  };
  // ** Removed backend PUT call here **
  const response = await api.put(`/api/menu/dashboard/config`, updatePayload);
  console.log("Dashboard updated after chart type change:", response.data);

  // Note: The AnalysisResultCard's watcher on props.config will automatically trigger re-render
};

// ** Modified: Handle size toggle from AnalysisResultCard (local update only) **
const handleToggleAnalysisItemSize = async (index) => {
  console.log(`Toggling size for item ${index} (local update)`);
  if (!loadedAnalysisResults.value[index]) {
    console.warn("Cannot toggle size: Analysis item not found at index.", index);
    return;
  }

  // Toggle the size ('small' -> 'large', 'large' -> 'small') in the local reactive state
  const currentSize = loadedAnalysisResults.value[index].value.size || 'small';
  loadedAnalysisResults.value[index].value.size = currentSize === 'small' ? 'large' : 'small';
  console.log(`Item ${index} size updated locally to: ${loadedAnalysisResults.value[index].value.size}`);

  const updatePayload = {
    id: loadedAnalysisResults.value[index].value.id,
    chartType: loadedAnalysisResults.value[index].value.selectedChartType,
    size: loadedAnalysisResults.value[index].value.size,
  };
  // ** Removed backend PUT call here **
  const response = await api.put(`/api/menu/dashboard/config`, updatePayload);
  console.log("Dashboard updated after size type change:", response.data);
  // Note: The grid layout will react to the size property change in loadedAnalysisResults.value[index].config.size
};

// ** NEW: Handle export from AnalysisResultCard (delegated) **
const handleExportAnalysisItem = (index) => {
  console.log(`Exporting analysis item at index: ${index}. Card will handle actual export.`);
  // The AnalysisResultCard component handles the actual export logic internally
  // when it receives the 'export' command from its dropdown.
  // This handler in the parent is just a listener if needed for logging or other actions.
  // No need to call anything here, the card does the work.
};

const handleEditAnalysisItem = (index) => {
  console.log("Editing analysis item at index:", index);
  // Open a modal/dialog pre-filled with the config of the item at 'index'
  // This modal would likely be the same one used for adding, but in "edit" mode
  // Load the config from loadedAnalysisResults[index].config into the form
  // showAddAnalysisDialog.value = true; // Reuse the add dialog
  // newAnalysisConfig.value = { ...loadedAnalysisResults.value[index].config }; // Load config
  // Need to handle saving the updated config back to the dashboard's config array
  ElMessage.info("编辑分析项功能待实现。"); // Placeholder message
};


// Methods for Add/Edit/Move Modals (Keep existing methods)
// ** Add missing methods **
const toggleSidebarCollapse = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
};

const handleDashboardSelect = (id) => {
  console.log("Dashboard selected:", id);
  loadDashboardData(id);
};

const resetForm = () => {
  itemForm.value = { name: '', parentId: null };
  editingItemId.value = null;
  if (itemFormRef.value) itemFormRef.value.resetFields();
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
  if (!itemFormRef.value) return;
  await itemFormRef.value.validate(async valid => { // Use async here
    if (!valid) return;
    const payload = modalMode.value === 'add'
      ? { title: itemForm.value.name, ...(addItemType.value === 'dashboard' ? { folderId: itemForm.value.parentId } : { parentId: itemForm.value.parentId }) }
      : { id: editingItemId.value, title: itemForm.value.name };
    const call = modalMode.value === 'add'
      ? api.post(`/api/menu/${addItemType.value}`, payload) // Assuming endpoints like /api/menu/dashboard, /api/menu/folder
      : api.put(`/api/menu/${addItemType.value}`, payload); // Assuming endpoints like /api/menu/dashboard, /api/menu/folder
    call.then(() => {
      ElMessage.success(`${modalMode.value === 'add' ? '创建' : '重命名'}${addItemType.value === 'dashboard' ? '看板' : '文件夹'}成功`);
      itemModalVisible.value = false;
      fetchMenu(); // Refresh sidebar menu
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
    api.delete(`/api/menu/${type}/${id}`) // Assuming endpoints like /api/menu/dashboard/{id}, /api/menu/folder/{id}
      .then(() => {
        ElMessage.success('删除成功');
        fetchMenu(); // Refresh sidebar menu
        // If the deleted item was the currently selected dashboard, clear the right area
        if (type === 'dashboard' && id === currentDashboardId.value) {
          currentDashboardId.value = null;
          currentDashboardTitle.value = '看板';
          selectedDashboardDetails.value = null;
          loadedAnalysisResults.value = [];
        }
      })
      .catch(err => {
        console.error(err);
        ElMessage.error('删除失败');
      });
  });
};

const openMoveModal = ({ id, type }) => {
  // Only dashboard items can be moved
  if (type === 'dashboard') {
    moveForm.value.id = id;
    moveForm.value.targetFolderId = null;
    moveModalVisible.value = true;
  } else {
    ElMessage.warning("只有看板可以移动。");
  }
};
const resetMoveForm = () => {
  moveForm.value = { id: '', targetFolderId: null };
  if (moveFormRef.value) moveFormRef.value.resetFields();
};
const submitMove = () => {
  if (!moveFormRef.value) return;
  moveFormRef.value.validate(valid => {
    if (!valid) return;
    api.put('/api/menu/dashboard/move', { // Assuming endpoint PUT /api/menu/dashboard/move
      id: moveForm.value.id,
      targetFolderId: moveForm.value.targetFolderId
    })
      .then(() => {
        ElMessage.success('移动成功');
        moveModalVisible.value = false;
        fetchMenu(); // Refresh sidebar menu
      })
      .catch(() => {
        ElMessage.error('移动失败');
      });
  });
};


// ---------- 生命周期 ----------
onMounted(() => {
  console.log("Dashboard component mounted.");
  // Fetch menu data on mount, which will trigger auto-selection of the first dashboard
  fetchMenu();
  // loadDashboardData is now called by fetchMenu after menu is loaded and first dashboard is identified
});

// Watch for changes in currentDashboardId to trigger loading dashboard details
watch(currentDashboardId, (newId, oldId) => {
  console.log(`currentDashboardId changed from ${oldId} to ${newId}`);
  if (newId !== oldId && newId !== null) {
    // When a new dashboard is selected (or the first one is auto-selected)
    console.log("观察")
    fetchDashboardDetails(newId);
  } else if (newId === null) {
    // When dashboard is deselected (e.g., deleted)
    selectedDashboardDetails.value = null;
    loadedAnalysisResults.value = [];
    currentDashboardTitle.value = '看板';
  }
});


// Placeholder methods for AnalysisResultCard events (if needed)
// These would handle interactions within the rendered analysis cards
// They are now handled by handleRefreshAnalysisItem, handleRemoveAnalysisItem, handleEditAnalysisItem
// const handleChartResize = (cardId) => { console.log("Chart resize event from card:", cardId); /* ... */ };
// const handleChartSettings = (cardId) => { console.log("Chart settings event from card:", cardId); /* ... */ };
// const handleChartRemoveFromLayout = (cardId) => { console.log("Chart remove event from card:", cardId); /* ... */ };
// const handleChartRefresh = (cardId) => { console.log("Chart refresh event from card:", cardId); /* ... */ };
// const handleChartFullscreen = (cardId) => { console.log("Chart fullscreen event from card:", cardId); /* ... */ };
// const handleChartTitleClick = (reportId) => { console.log("Chart title click event from card:", reportId); /* ... */ };
// const handleGranularityChange = (payload) => { console.log("Granularity change event from card:", payload); /* ... */ };
// const handleDateRangeChange = (payload) => { console.log("Date range change event from card:", payload); /* ... */ };
// const handleComparisonToggle = (payload) => { console.log("Comparison toggle event from card:", payload); /* ... */ };


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
  grid-template-columns: repeat(auto-fill, minmax(400px, 1fr)); /* Responsive grid */
  gap: 20px;
}

/* Style for large items to span more columns */
.chart-grid-item-large {
  grid-column: span 2; /* Make large items span 2 columns */
  /* Ensure it doesn't exceed the grid bounds */
  grid-column: span 2 / auto;
}

/* Adjust grid for smaller screens if needed */
@media (max-width: 900px) { /* Example breakpoint */
  .charts-grid {
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  }
  /* On smaller screens, large items might still only span 1 column */
  .chart-grid-item-large {
    grid-column: span 1 / auto;
  }
}

.chart-grid-item {
  /* No fixed span needed with auto-fill */
  /* transition: grid-column 0.3s ease; */
}

.no-dashboard {
  grid-column: 1 / -1; /* Span across all columns */
  text-align: center;
  padding: 50px;
  color: var(--el-text-color-secondary);
  font-size: 16px;
}

/* Removed media query as grid is now responsive */
/* @media (max-width: 1200px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }

  .chart-grid-item {
    grid-column: span 1 !important;
  }

  .no-dashboard {
    grid-column: span 1;
  }
} */

/* Style for the dialog footer */
.el-dialog__footer {
  text-align: right;
}
</style>
