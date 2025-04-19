<template>
  <TopNav/>

  <el-container class="schema-manager-layout">
    <!-- Left Sidebar (remains the same) -->
    <el-aside :width="asideWidth" class="schema-sidebar">
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="currentMenuItem"
          class="schema-menu"
          :collapse="isSidebarCollapsed"
          :collapse-transition="false"
          @select="handleMenuSelect"
          unique-opened
        >
          <template v-for="item in menuData" :key="item.index">
            <el-sub-menu :index="item.index">
              <template #title>
                <el-icon v-if="item.icon"><component :is="item.icon"/></el-icon>
                <span v-show="!isSidebarCollapsed">{{ item.title }}</span>
              </template>
              <el-menu-item v-for="subItem in item.children" :key="subItem.index" :index="subItem.index">
                <el-icon v-if="subItem.icon"><component :is="subItem.icon"/></el-icon>
                <span>{{ subItem.title }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
      <div class="sidebar-footer" @click="toggleSidebarCollapse">
        <el-icon><component :is="isSidebarCollapsed ? DArrowRight : DArrowLeft" /></el-icon>
      </div>
    </el-aside>

    <!-- Right Main Content Area -->
    <el-main class="schema-manager-content" :style="{ marginLeft: 0 }">
      <div class="header">
        <h2>{{ selectedMenuItemTitle }}</h2>
        <!-- Show "新建事件结构" button only on Meta Events page -->
        <el-button v-if="currentMenuItem === 'data-management-meta-events'" type="primary" :icon="Plus" @click="openSchemaModal('add')">新建事件结构</el-button>
        <!-- Show tab switcher on Realtime Data page -->
        <div v-if="currentMenuItem === 'tracking-management-realtime'">
          <el-radio-group v-model="realtimeSubView" @change="handleRealtimeSubViewChange">
            <el-radio-button label="ingested">实时入库</el-radio-button>
            <el-radio-button label="errored">错误数据</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- Content based on selected menu item and sub-view -->
      <!-- Meta Events Content -->
      <div v-if="currentMenuItem === 'data-management-meta-events'">
        <el-table :data="eventSchemas" v-loading="loading" style="width: 100%">
          <el-table-column prop="eventName" label="事件名称"></el-table-column>
          <el-table-column label="参数结构定义">
            <template #default="scope">
              <pre>{{ formatParameterSchema(scope.row.parameterSchema) }}</pre>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button size="small" @click="openSchemaModal('edit', scope.row)">编辑</el-button>
              <el-popconfirm title="确定删除此事件结构吗?" @confirm="deleteSchema(scope.row.id)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <!-- Realtime Data Content -->
      <div v-else-if="currentMenuItem === 'tracking-management-realtime'">
        <!-- Realtime Ingested Data Table -->
        <div v-show="realtimeSubView === 'ingested'">
          <el-table :data="realtimeEventsIngested" v-loading="loadingRealtime" style="width: 100%">
            <el-table-column prop="timestamp" label="时间" width="180">
              <template #default="scope">
                {{ formatTimestamp(scope.row.timestamp) }}
              </template>
            </el-table-column>
            <el-table-column prop="eventName" label="事件名称"></el-table-column>
            <el-table-column prop="userId" label="用户ID"></el-table-column>
            <el-table-column prop="deviceId" label="设备ID"></el-table-column>
            <el-table-column label="参数">
              <template #default="scope">
                <pre>{{ formatEventParameters(scope.row.parameters) }}</pre>
              </template>
            </el-table-column>
          </el-table>
          <!-- Pagination for Ingested Data -->
          <el-pagination
            v-if="realtimePaginationIngested.total > realtimePaginationIngested.pageSize"
            @current-change="handleRealtimeIngestedPageChange"
            :current-page="realtimePaginationIngested.currentPage"
            :page-size="realtimePaginationIngested.pageSize"
            layout="total, prev, pager, next"
            :total="realtimePaginationIngested.total">
          </el-pagination>
        </div>

        <!-- Errored Data Table -->
        <div v-show="realtimeSubView === 'errored'">
          <el-table :data="realtimeEventsErrored" v-loading="loadingRealtime" style="width: 100%">
            <el-table-column prop="receivedTimestamp" label="接收时间" width="180">
              <template #default="scope">
                {{ formatTimestamp(scope.row.receivedTimestamp) }}
              </template>
            </el-table-column>
            <el-table-column prop="eventName" label="事件名称"></el-table-column>
            <el-table-column prop="userId" label="用户ID"></el-table-column>
            <el-table-column prop="deviceId" label="设备ID"></el-table-column>
            <el-table-column prop="errorReason" label="错误原因"></el-table-column>
            <el-table-column label="原始参数">
              <template #default="scope">
                <pre>{{ formatEventParameters(scope.row.rawParameters) }}</pre>
              </template>
            </el-table-column>
          </el-table>
          <!-- Pagination for Errored Data -->
          <el-pagination
            v-if="realtimePaginationErrored.total > realtimePaginationErrored.pageSize"
            @current-change="handleRealtimeErroredPageChange"
            :current-page="realtimePaginationErrored.currentPage"
            :page-size="realtimePaginationErrored.pageSize"
            layout="total, prev, pager, next"
            :total="realtimePaginationErrored.total">
          </el-pagination>
        </div>
      </div>
      <div v-else>
        <!-- Placeholder for other content sections -->
        <p>这是 "{{ selectedMenuItemTitle }}" 的内容区域。</p>
        <!-- TODO: 根据 currentMenuItem 的值使用 v-else-if 或动态组件来加载实际的内容组件 -->
      </div>


    </el-main>

    <!-- Add/Edit Schema Dialog (remains the same) -->
    <el-dialog v-model="schemaModalVisible" :title="modalMode === 'add' ? '新建事件结构' : '编辑事件结构'" width="600px" :close-on-click-modal="false">
      <!-- ... dialog content ... -->
      <el-form ref="schemaFormRef" :model="schemaForm" :rules="schemaFormRules" label-width="100px">
        <el-form-item label="事件名称" prop="eventName">
          <el-input v-model="schemaForm.eventName" :disabled="modalMode === 'edit'"></el-input>
        </el-form-item>
        <el-form-item label="参数定义">
          <div class="parameter-list">
            <div v-for="(param, index) in schemaForm.parameters" :key="index" class="parameter-item">
              <el-input v-model="param.name" placeholder="参数名称" style="width: 120px; margin-right: 10px;"></el-input>
              <el-select v-model="param.type" placeholder="类型" style="width: 100px; margin-right: 10px;">
                <el-option label="string" value="string"></el-option>
                <el-option label="integer" value="integer"></el-option>
                <el-option label="float" value="float"></el-option>
                <el-option label="boolean" value="boolean"></el-option>
                <!-- Add other types as needed -->
              </el-select>
              <el-checkbox v-model="param.required">必需</el-checkbox>
              <el-button type="danger" :icon="Delete" circle size="small" @click="removeParameter(index)"></el-button>
            </div>
            <el-button :icon="Plus" @click="addParameter">添加参数</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="schemaModalVisible = false">取消</el-button>
          <el-button type="primary" @click="submitSchemaForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

  </el-container>
</template>

<script setup>
import { ref, onMounted, reactive, computed, shallowRef, onUnmounted, watch } from 'vue';
import TopNav from "@/components/TopNav.vue";
import {
  ElMessage, ElMessageBox, ElTable, ElTableColumn, ElButton, ElPopconfirm,
  ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElCheckbox, ElLoading,
  ElContainer, ElAside, ElMain, ElScrollbar, ElMenu, ElSubMenu, ElMenuItem, ElIcon,
  ElPagination, ElRadioGroup, ElRadioButton // Import Radio components
} from 'element-plus';
import { Plus, Delete, DArrowLeft, DArrowRight, Setting, Document, DataAnalysis, Monitor, Bell, Grid, List } from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import api from "@/api/index.js"; // Make sure dayjs is installed and imported


// --- Sidebar State and Logic (remains the same) ---
const isSidebarCollapsed = ref(false);
const asideWidth = computed(() => (isSidebarCollapsed.value ? '64px' : '200px'));
const currentMenuItem = ref('data-management-meta-events'); // Default selected menu item

const menuData = ref([
  {
    index: 'data-management',
    title: '数据管理',
    icon: shallowRef(DataAnalysis),
    children: [
      { index: 'data-management-meta-events', title: '元事件' },
      { index: 'data-management-event-properties', title: '事件属性' },
      { index: 'data-management-user-properties', title: '用户属性' },
      { index: 'data-management-data-tables', title: '数据表' },
      { index: 'data-management-metrics', title: '指标' },
      { index: 'data-management-exchange-rates', title: '汇率换算' }
    ]
  },
  {
    index: 'tracking-management',
    title: '埋点管理',
    icon: shallowRef(Setting),
    children: [
      { index: 'tracking-management-plans', title: '埋点方案' },
      { index: 'tracking-management-acceptance', title: '数据验收' },
      { index: 'tracking-management-reporting', title: '上报管理' },
      { index: 'tracking-management-realtime', title: '实时数据' }, // Realtime Data
      { index: 'tracking-management-debug', title: 'Debug 模式' }
    ]
  },
  {
    index: 'data-integration',
    title: '数据接入',
    icon: shallowRef(Monitor),
    children: [
      { index: 'data-integration-guide', title: '接入指南' },
      { index: 'data-integration-third-party', title: '三方集成' }
    ]
  }
]);

const selectedMenuItemTitle = computed(() => {
  for (const section of menuData.value) {
    if (section.index === currentMenuItem.value) return section.title;
    const subItem = section.children.find(item => item.index === currentMenuItem.value);
    if (subItem) return subItem.title;
  }
  return '未知页面';
});

const toggleSidebarCollapse = () => {
  isSidebarCollapsed.value = !isSidebarCollapsed.value;
};

const handleMenuSelect = (index) => {
  currentMenuItem.value = index;
  console.log('Selected menu item:', index);
  // Stop realtime refresh when switching away from realtime view
  stopRealtimeRefresh();

  // Load content based on the selected index
  if (index === 'data-management-meta-events') {
    fetchEventSchemas();
  } else if (index === 'tracking-management-realtime') {
    // Reset sub-view to default and fetch data for it
    realtimeSubView.value = 'ingested'; // Default to '实时入库'
    resetRealtimePagination(); // Reset pagination for both views
    fetchRealtimeDataForCurrentSubView(); // Fetch data for the default sub-view
    startRealtimeRefresh(); // Start refresh timer
  } else {
    // Clear data and close modal for other items
    eventSchemas.value = [];
    realtimeEventsIngested.value = [];
    realtimeEventsErrored.value = [];
    schemaModalVisible.value = false;
  }
};


// --- Event Schema Management State and Logic (remains the same) ---
const eventSchemas = ref([]);
const loading = ref(false);

const schemaModalVisible = ref(false);
const modalMode = ref('add');
const currentSchemaId = ref(null);

const schemaFormRef = ref(null);
const schemaForm = reactive({
  eventName: '',
  parameters: []
});

const schemaFormRules = {
  eventName: [{ required: true, message: '请输入事件名称', trigger: 'blur' }]
};

// --- Event Schema Data Methods (remains the same, added check for currentMenuItem) ---
const fetchEventSchemas = async () => {
  if (currentMenuItem.value !== 'data-management-meta-events') {
    console.log('Not on Meta Events page, skipping fetch.');
    return;
  }
  loading.value = true;
  try {
    const response = await api.get('/api/schemas/events');
    eventSchemas.value = response.data;
  } catch (error) {
    console.error('Failed to fetch event schemas:', error);
    ElMessage.error('加载事件结构失败');
  } finally {
    loading.value = false;
  }
};

const formatParameterSchema = (jsonString) => {
  if (!jsonString) return '无参数';
  try {
    const schema = JSON.parse(jsonString);
    return JSON.stringify(schema, null, 2);
  } catch (e) {
    return '无效的 JSON 格式';
  }
};

// ... openSchemaModal, resetSchemaForm, addParameter, removeParameter, submitSchemaForm, deleteSchema remain the same, added check for currentMenuItem before fetchSchemas ...
const openSchemaModal = (mode, schema = null) => {
  modalMode.value = mode;
  resetSchemaForm();
  if (mode === 'edit' && schema) {
    currentSchemaId.value = schema.id;
    schemaForm.eventName = schema.eventName;
    if (schema.parameterSchema) {
      try {
        const params = JSON.parse(schema.parameterSchema);
        schemaForm.parameters = Object.keys(params).map(key => {
          const paramDef = params[key];
          if (typeof paramDef === 'string') {
            return { name: key, type: paramDef, required: false };
          } else if (typeof paramDef === 'object' && paramDef !== null) {
            return {
              name: key,
              type: paramDef.type || '',
              required: paramDef.required || false
            };
          }
          return { name: key, type: '', required: false };
        });
      } catch (e) {
        console.error('Failed to parse parameter schema:', e);
        ElMessage.error('解析参数结构进行编辑失败');
      }
    }
  }
  schemaModalVisible.value = true;
};

const resetSchemaForm = () => {
  currentSchemaId.value = null;
  schemaForm.eventName = '';
  schemaForm.parameters = [];
  if (schemaFormRef.value) {
    schemaFormRef.value.resetFields();
  }
};

const addParameter = () => {
  schemaForm.parameters.push({ name: '', type: '', required: false });
};

const removeParameter = (index) => {
  schemaForm.parameters.splice(index, 1);
};
const submitSchemaForm = async () => {
  if (!schemaFormRef.value) return;
  await schemaFormRef.value.validate(async (valid) => {
    if (valid) {
      const parameterSchemaJson = {};
      schemaForm.parameters.forEach(param => {
        if (param.name && param.type) {
          if (param.required) {
            parameterSchemaJson[param.name] = { type: param.type, required: true };
          } else {
            parameterSchemaJson[param.name] = param.type;
          }
        }
      });

      const schemaData = {
        eventName: schemaForm.eventName,
        parameterSchema: Object.keys(parameterSchemaJson).length > 0 ? JSON.stringify(parameterSchemaJson) : null
      };

      try {
        if (modalMode.value === 'add') {
          await api.post('/api/schemas/events', schemaData);
          ElMessage.success('事件结构创建成功');
        } else {
          await api.put(`/api/schemas/events/${currentSchemaId.value}`, schemaData);
          ElMessage.success('事件结构更新成功');
        }
        schemaModalVisible.value = false;
        if (currentMenuItem.value === 'data-management-meta-events') {
          fetchEventSchemas(); // Refresh only if on Meta Events page
        }
      } catch (error) {
        console.error('Failed to save event schema:', error);
        if (error.response && error.response.status === 409) {
          ElMessage.error(`事件名称 "${schemaForm.eventName}" 已存在`);
        } else {
          ElMessage.error(`保存事件结构失败: ${error.message || '未知错误'}`);
        }
      }
    } else {
      console.log('Form validation failed');
      return false;
    }
  });
};

const deleteSchema = async (id) => {
  try {
    await api.delete(`/api/schemas/events/${id}`);
    ElMessage.success('事件结构删除成功');
    if (currentMenuItem.value === 'data-management-meta-events') {
      fetchEventSchemas(); // Refresh only if on Meta Events page
    }
  } catch (error) {
    console.error('Failed to delete event schema:', error);
    ElMessage.error(`删除事件结构失败: ${error.message || '未知错误'}`);
  }
};


// --- Realtime Data State and Logic ---
const realtimeSubView = ref('ingested'); // 'ingested' or 'errored'
const realtimeEventsIngested = ref([]);
const realtimeEventsErrored = ref([]);
const loadingRealtime = ref(false);

// Separate pagination for each sub-view
const realtimePaginationIngested = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
});
const realtimePaginationErrored = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
});

let realtimeRefreshTimer = null; // Timer instance
const REALTIME_REFRESH_INTERVAL = 5000; // Refresh interval in milliseconds (e.g., 5 seconds)


// Fetch data based on the currently selected realtime sub-view
const fetchRealtimeDataForCurrentSubView = async () => {
  if (realtimeSubView.value === 'ingested') {
    await fetchRealtimeIngestedEvents();
  } else if (realtimeSubView.value === 'errored') {
    await fetchRealtimeErroredEvents();
  }
};

// Fetch realtime ingested events from backend
const fetchRealtimeIngestedEvents = async () => {
  if (currentMenuItem.value !== 'tracking-management-realtime' || realtimeSubView.value !== 'ingested') {
    console.log('Not on Realtime Ingested Data view, skipping fetch.');
    return;
  }

  loadingRealtime.value = true;
  try {
    // Backend page is 0-indexed, frontend is 1-indexed
    const page = realtimePaginationIngested.currentPage - 1;
    const size = realtimePaginationIngested.pageSize;
    const response = await api.get(`/api/data/realtime?page=${page}&size=${size}`);
    realtimeEventsIngested.value = response.data.content; // Spring Data Page returns content in 'content' field
    realtimePaginationIngested.total = response.data.totalElements; // Total number of elements
    console.log("Fetched realtime ingested events:", realtimeEventsIngested.value);
  } catch (error) {
    console.error('Failed to fetch realtime ingested events:', error);
    ElMessage.error('加载实时入库数据失败');
  } finally {
    loadingRealtime.value = false;
  }
};

// Fetch realtime errored events from backend
const fetchRealtimeErroredEvents = async () => {
  if (currentMenuItem.value !== 'tracking-management-realtime' || realtimeSubView.value !== 'errored') {
    console.log('Not on Realtime Errored Data view, skipping fetch.');
    return;
  }

  loadingRealtime.value = true;
  try {
    // Backend page is 0-indexed, frontend is 1-indexed
    const page = realtimePaginationErrored.currentPage - 1;
    const size = realtimePaginationErrored.pageSize;
    const response = await api.get(`/api/data/errored?page=${page}&size=${size}`); // New endpoint
    realtimeEventsErrored.value = response.data.content;
    realtimePaginationErrored.total = response.data.totalElements;
    console.log("Fetched realtime errored events:", realtimeEventsErrored.value);
  } catch (error) {
    console.error('Failed to fetch realtime errored events:', error);
    ElMessage.error('加载错误数据失败');
  } finally {
    loadingRealtime.value = false;
  }
};


// Start the realtime refresh timer
const startRealtimeRefresh = () => {
  stopRealtimeRefresh(); // Clear any existing timer
  // Set a new interval timer to fetch data for the currently active sub-view
  realtimeRefreshTimer = setInterval(fetchRealtimeDataForCurrentSubView, REALTIME_REFRESH_INTERVAL);
  console.log("Realtime refresh started.");
};

// Stop the realtime refresh timer
const stopRealtimeRefresh = () => {
  if (realtimeRefreshTimer) {
    clearInterval(realtimeRefreshTimer);
    realtimeRefreshTimer = null;
    console.log("Realtime refresh stopped.");
  }
};

// Handle sub-view change within Realtime Data
const handleRealtimeSubViewChange = (newSubView) => {
  realtimeSubView.value = newSubView;
  // Reset pagination for the newly selected sub-view and fetch data
  if (newSubView === 'ingested') {
    realtimePaginationIngested.currentPage = 1;
  } else if (newSubView === 'errored') {
    realtimePaginationErrored.currentPage = 1;
  }
  fetchRealtimeDataForCurrentSubView(); // Fetch data for the new sub-view
  // The timer is already running and will pick up the new sub-view on the next tick
};

// Handle page change for Realtime Ingested Data pagination
const handleRealtimeIngestedPageChange = (newPage) => {
  realtimePaginationIngested.currentPage = newPage;
  fetchRealtimeIngestedEvents(); // Fetch data for the new page
};

// Handle page change for Realtime Errored Data pagination
const handleRealtimeErroredPageChange = (newPage) => {
  realtimePaginationErrored.currentPage = newPage;
  fetchRealtimeErroredEvents(); // Fetch data for the new page
};

// Reset pagination for both views
const resetRealtimePagination = () => {
  realtimePaginationIngested.currentPage = 1;
  realtimePaginationErrored.currentPage = 1;
  // Total will be updated by the fetch calls
};

// Format timestamp for display
const formatTimestamp = (timestamp) => {
  // Timestamps from backend are likely milliseconds since epoch
  if (!timestamp) return '';
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss');
};

// Format event parameters for display
const formatEventParameters = (parameters) => {
  if (!parameters) return '无参数';
  // Parameters are stored as JSON string in the backend GameEvent model,
  // but api might automatically parse it back into an object/map if content type is JSON.
  // If it's still a string, parse it first.
  let paramsObject = parameters;
  if (typeof parameters === 'string') {
    try {
      paramsObject = JSON.parse(parameters);
    } catch (e) {
      console.error('Failed to parse parameters JSON string:', e);
      return '无效的参数 JSON';
    }
  }

  return JSON.stringify(paramsObject, null, 2); // Pretty print the parameters JSON
};


// --- Lifecycle Hooks ---
onMounted(() => {
  // When the component is mounted, check the default menu item
  // and load the corresponding content
  if (currentMenuItem.value === 'data-management-meta-events') {
    fetchEventSchemas();
  } else if (currentMenuItem.value === 'tracking-management-realtime') {
    realtimeSubView.value = 'ingested'; // Default to '实时入库'
    resetRealtimePagination();
    fetchRealtimeDataForCurrentSubView(); // Fetch initial data
    startRealtimeRefresh(); // Start refresh timer
  }
});

// Lifecycle hook to stop the timer when the component is unmounted
onUnmounted(() => {
  stopRealtimeRefresh();
});

// Watch for changes in currentMenuItem to stop/start timer and clear data as needed
watch(currentMenuItem, (newValue, oldValue) => {
  // Stop realtime refresh when switching away from realtime view
  if (oldValue === 'tracking-management-realtime') {
    stopRealtimeRefresh();
    realtimeEventsIngested.value = []; // Clear data when leaving the view
    realtimeEventsErrored.value = [];
    resetRealtimePagination(); // Reset pagination when leaving the view
  }

  // Start realtime data fetching and refresh when switching to realtime view
  if (newValue === 'tracking-management-realtime') {
    realtimeSubView.value = 'ingested'; // Default to '实时入库'
    resetRealtimePagination();
    fetchRealtimeDataForCurrentSubView(); // Fetch initial data
    startRealtimeRefresh(); // Start refresh timer
  }
});
</script>

<style scoped>
/* Overall layout for schema manager page */
.schema-manager-layout {
  height: calc(100vh - 80px); /* Adjust based on TopNav height */
  overflow: hidden;
  display: flex; /* Use flexbox for layout */
}

/* Sidebar styles (remains the same) */
.schema-sidebar {
  background-color: #ffffff;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  transition: width 0.3s ease; /* Width transition animation */
  overflow: hidden; /* Hide overflow when collapsed */
  flex-shrink: 0; /* Prevent shrinking */
}

.schema-sidebar .menu-scrollbar {
  flex-grow: 1;
  height: 0; /* Allow flex-grow to work */
}

.schema-menu:not(.el-menu--collapse) {
  width: 200px; /* Full width when not collapsed */
  padding: 0 10px;
}

.schema-menu {
  border-right: none; /* Remove default border */
}

/* Main content area styles */
.schema-manager-content {
  padding: 20px;
  background-color: #f0f2f5; /* Background color */
  overflow-y: auto; /* Enable vertical scrolling */
  height: 100%;
  box-sizing: border-box;
  flex-grow: 1; /* Take remaining space */
  transition: margin-left 0.3s ease; /* Smooth transition for margin when collapsing */
}

/* Header styles (remains the same, adjusted for radio group) */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  /* Allow flex items to wrap if needed */
  flex-wrap: wrap;
  /* Add some gap between items */
  gap: 10px;
}

.header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  /* Prevent title from shrinking much */
  flex-shrink: 0;
}

/* Parameter list styles (remains the same) */
.parameter-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 10px;
  background-color: #fff;
}

.parameter-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
  gap: 5px;
}

.parameter-item .el-input,
.parameter-item .el-select {
  flex-shrink: 0;
}

.parameter-item .el-checkbox {
  flex-shrink: 0;
  margin-right: 10px;
}

.parameter-item .el-button {
  flex-shrink: 0;
  margin-left: auto;
}

.parameter-list > .el-button {
  margin-top: 10px;
}

/* Style for pre tag to display JSON nicely */
pre {
  background-color: #f4f4f4;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 10px;
  overflow-x: auto;
  font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  line-height: 1.4;
  color: #333;
  white-space: pre-wrap; /* Allow wrapping for long parameter JSON */
  word-wrap: break-word; /* Break long words */
}

/* Optional: style for table header/rows (remains the same) */
.el-table th {
  background-color: #f5f7fa !important;
  color: #909399;
  font-weight: bold;
}

/* Sidebar Footer Style (reused) */
.sidebar-footer {
  padding: 10px 0;
  text-align: center;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-shrink: 0;
  cursor: pointer;
  color: var(--el-text-color-secondary);
  line-height: 1;
}
.sidebar-footer:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}
.sidebar-footer .el-icon {
  font-size: 16px;
  vertical-align: middle;
}

/* Adjust ElMenu styles to match Dashboard sidebar (reused) */
.schema-menu .el-menu-item,
.schema-menu :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  border-radius: 4px;
  margin: 4px 0;
  padding-left: 20px !important;
  display: flex;
  align-items: center;
}

.schema-menu .el-icon {
  margin-right: 8px;
}

.schema-menu .el-sub-menu .el-menu-item {
  height: 36px;
  line-height: 36px;
  font-size: 13px;
  padding-left: 35px !important;
}

.schema-menu .el-menu-item.is-active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.schema-menu .el-menu-item:hover,
.schema-menu :deep(.el-sub-menu__title):hover {
  background-color: var(--el-fill-color-light);
}

/* Collapsed Menu Styles (reused) */
.schema-menu.el-menu--collapse {
  width: 100%;
}

.schema-menu.el-menu--collapse .el-menu-item,
.schema-menu.el-menu--collapse :deep(.el-sub-menu__title) {
  justify-content: center;
  padding-left: 0 !important;
  padding: 0;
}

.schema-menu.el-menu--collapse .el-icon:first-child {
  margin-right: 0;
  font-size: 18px;
  margin: 0;
}

.schema-menu.el-menu--collapse span,
.schema-menu.el-menu--collapse :deep(.el-sub-menu__icon-arrow) {
  display: none;
}
</style>
