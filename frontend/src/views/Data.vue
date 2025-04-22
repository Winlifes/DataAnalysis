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
        <!-- Show buttons based on selected menu item -->
        <template v-if="currentMenuItem === 'data-management-meta-events'">
          <el-button type="primary" :icon="Plus" @click="openSchemaModal('add')">新建事件结构</el-button>
        </template>
        <template v-else-if="currentMenuItem === 'tracking-management-realtime'">
          <el-radio-group v-model="realtimeSubView" @change="handleRealtimeSubViewChange">
            <el-radio-button label="ingested">实时入库</el-radio-button>
            <el-radio-button label="errored">错误数据</el-radio-button>
          </el-radio-group>
        </template>
        <template v-else-if="currentMenuItem === 'tracking-management-debug'">
          <el-input v-model="debugDeviceIdFilter" placeholder="按设备ID过滤" style="width: 200px; margin-left: auto;" clearable @input="handleDebugFilterChange"></el-input>
        </template>
        <template v-else-if="currentMenuItem === 'data-management-user-properties'">
          <el-button type="primary" @click="openUserPropertySchemaModal">配置用户属性</el-button>
        </template>
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
      <!-- Debug Mode Content -->
      <div v-else-if="currentMenuItem === 'tracking-management-debug'">
        <el-table :data="debugEvents" v-loading="loadingDebug" style="width: 100%">
          <el-table-column prop="receivedTimestamp" label="接收时间" width="180">
            <template #default="scope">
              {{ formatTimestamp(scope.row.receivedTimestamp) }}
            </template>
          </el-table-column>
          <el-table-column prop="eventName" label="事件名称"></el-table-column>
          <el-table-column prop="userId" label="用户ID"></el-table-column>
          <el-table-column prop="deviceId" label="设备ID"></el-table-column>
          <el-table-column prop="isValid" label="事件有效" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.valid ? 'success' : 'danger'">{{ scope.row.valid ? '是' : '否' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="validationError" label="验证错误原因"></el-table-column>
          <el-table-column label="原始事件参数">
            <template #default="scope">
              <pre>{{ formatEventParameters(scope.row.rawParameters) }}</pre>
            </template>
          </el-table-column>
          <el-table-column label="原始用户属性">
            <template #default="scope">
              <pre>{{ formatEventParameters(scope.row.rawUserProperties) }}</pre>
            </template>
          </el-table-column>
        </el-table>
        <!-- Pagination for Debug Data -->
        <el-pagination
          v-if="debugPagination.total > debugPagination.pageSize"
          @current-change="handleDebugPageChange"
          :current-page="debugPagination.currentPage"
          :page-size="debugPagination.pageSize"
          layout="total, prev, pager, next"
          :total="debugPagination.total">
        </el-pagination>
      </div>
      <!-- User Properties Schema Content -->
      <div v-else-if="currentMenuItem === 'data-management-user-properties'">
        <h3>当前用户属性定义</h3>
        <div v-if="userPropertySchema">
          <pre>{{ formatParameterSchema(userPropertySchema.propertySchema) }}</pre>
        </div>
        <div v-else>
          <p>暂无用户属性定义。</p>
        </div>
        <!-- The "配置用户属性" button is in the header -->
      </div>

      <div v-else>
        <!-- Placeholder for other content sections -->
        <p>这是 "{{ selectedMenuItemTitle }}" 的内容区域。</p>
      </div>


    </el-main>

    <!-- Add/Edit Event Schema Dialog (remains the same) -->
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

    <!-- Configure User Property Schema Dialog -->
    <el-dialog v-model="userPropertySchemaModalVisible" title="配置用户属性结构" width="600px" :close-on-click-modal="false">
      <el-form ref="userPropertySchemaFormRef" :model="userPropertySchemaForm" label-width="100px">
        <el-form-item label="属性定义">
          <div class="parameter-list">
            <div v-for="(prop, index) in userPropertySchemaForm.properties" :key="index" class="parameter-item">
              <el-input v-model="prop.name" placeholder="属性名称" style="width: 120px; margin-right: 10px;"></el-input>
              <el-select v-model="prop.type" placeholder="类型" style="width: 100px; margin-right: 10px;">
                <el-option label="string" value="string"></el-option>
                <el-option label="integer" value="integer"></el-option>
                <el-option label="float" value="float"></el-option>
                <el-option label="boolean" value="boolean"></el-option>
                <!-- Add other types as needed -->
              </el-select>
              <el-checkbox v-model="prop.required">必需</el-checkbox>
              <el-button type="danger" :icon="Delete" circle size="small" @click="removeUserProperty(index)"></el-button>
            </div>
            <el-button :icon="Plus" @click="addUserProperty">添加属性</el-button>
          </div>
        </el-form-item>
        <!-- TODO: Add validation rules for user properties if needed -->
      </el-form>
      <template #footer>
             <span class="dialog-footer">
                 <el-button @click="userPropertySchemaModalVisible = false">取消</el-button>
                 <el-button type="primary" @click="submitUserPropertySchemaForm">确定</el-button>
             </span>
      </template>
    </el-dialog>

  </el-container>
</template>

<script setup>
import {ref, onMounted, reactive, computed, shallowRef, onUnmounted, watch} from 'vue';
import TopNav from "@/components/TopNav.vue";
import {
  ElMessage, ElMessageBox, ElTable, ElTableColumn, ElButton, ElPopconfirm,
  ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElCheckbox, ElLoading,
  ElContainer, ElAside, ElMain, ElScrollbar, ElMenu, ElSubMenu, ElMenuItem, ElIcon,
  ElPagination, ElRadioGroup, ElRadioButton, ElTag // Import ElTag for 'isValid'
} from 'element-plus';
import {
  Plus,
  Delete,
  DArrowLeft,
  DArrowRight,
  Setting,
  Document,
  DataAnalysis,
  Monitor,
  Bell,
  Grid,
  List
} from '@element-plus/icons-vue';
import dayjs from 'dayjs';
import api from "@/api/index.js";


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
      {index: 'data-management-meta-events', title: '元事件'},
      {index: 'data-management-event-properties', title: '事件属性'},
      {index: 'data-management-user-properties', title: '用户属性'},
      {index: 'data-management-data-tables', title: '数据表'},
      {index: 'data-management-metrics', title: '指标'},
      {index: 'data-management-exchange-rates', title: '汇率换算'}
    ]
  },
  {
    index: 'tracking-management',
    title: '埋点管理',
    icon: shallowRef(Setting),
    children: [
      {index: 'tracking-management-plans', title: '埋点方案'},
      {index: 'tracking-management-acceptance', title: '数据验收'},
      {index: 'tracking-management-reporting', title: '上报管理'},
      {index: 'tracking-management-realtime', title: '实时数据'},
      {index: 'tracking-management-debug', title: 'Debug 模式'} // Debug Mode
    ]
  },
  {
    index: 'data-integration',
    title: '数据接入',
    icon: shallowRef(Monitor),
    children: [
      {index: 'data-integration-guide', title: '接入指南'},
      {index: 'data-integration-third-party', title: '三方集成'}
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
  // Stop all polling/refresh timers when switching views
  stopRealtimeRefresh();
  stopDebugRefresh();


  // Clear data from previous view
  eventSchemas.value = [];
  realtimeEventsIngested.value = [];
  realtimeEventsErrored.value = [];
  debugEvents.value = [];
  userPropertySchema.value = null; // Clear user property schema display


  // Load content based on the selected index
  if (index === 'data-management-meta-events') {
    fetchEventSchemas();
  } else if (index === 'tracking-management-realtime') {
    realtimeSubView.value = 'ingested'; // Default to '实时入库'
    resetRealtimePagination();
    fetchRealtimeDataForCurrentSubView();
    startRealtimeRefresh();

  } else if (index === 'tracking-management-debug') {
    debugDeviceIdFilter.value = ''; // Clear device ID filter
    resetDebugPagination();
    fetchDebugEvents();
    startDebugRefresh();

  } else if (index === 'data-management-user-properties') {
    // Load user property schema for display
    fetchUserPropertySchema();
  }
  // TODO: Add else if for other menu items
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
  eventName: [{required: true, message: '请输入事件名称', trigger: 'blur'}]
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
            return {name: key, type: paramDef, required: false};
          } else if (typeof paramDef === 'object' && paramDef !== null) {
            return {
              name: key,
              type: paramDef.type || '',
              required: paramDef.required || false
            };
          }
          return {name: key, type: '', required: false};
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
  schemaForm.parameters.push({name: '', type: '', required: false});
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
            parameterSchemaJson[param.name] = {type: param.type, required: true};
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
const REALTIME_REFRESH_INTERVAL = 5000; // Refresh interval in milliseconds (5 seconds)


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
    const page = realtimePaginationIngested.currentPage - 1;
    const size = realtimePaginationIngested.pageSize;
    const response = await api.get(`/api/data/realtime?page=${page}&size=${size}`);
    realtimeEventsIngested.value = response.data.content;
    realtimePaginationIngested.total = response.data.totalElements;
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
    const page = realtimePaginationErrored.currentPage - 1;
    const size = realtimePaginationErrored.pageSize;
    const response = await api.get(`/api/data/errored?page=${page}&size=${size}`);
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
  // Pagination reset is handled in resetRealtimePagination, call fetch after reset
  if (newSubView === 'ingested') {
    realtimePaginationIngested.currentPage = 1;
  } else if (newSubView === 'errored') {
    realtimePaginationErrored.currentPage = 1;
  }
  fetchRealtimeDataForCurrentSubView(); // Fetch data for the new sub-view immediately
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

// Reset pagination for both realtime views
const resetRealtimePagination = () => {
  realtimePaginationIngested.currentPage = 1;
  realtimePaginationErrored.currentPage = 1;
  // Total will be updated by the fetch calls
};


// --- Debug Mode State and Logic ---
const debugEvents = ref([]);
const loadingDebug = ref(false);
const debugDeviceIdFilter = ref(''); // State for device ID filter input
const debugPagination = reactive({
  currentPage: 1,
  pageSize: 10,
  total: 0
});
let debugRefreshTimer = null; // Timer instance for debug data polling
const DEBUG_REFRESH_INTERVAL = 3000; // Polling interval for debug data (e.g., 3 seconds)


// Fetch debug events from backend
const fetchDebugEvents = async () => {
  if (currentMenuItem.value !== 'tracking-management-debug') {
    console.log('Not on Debug Mode view, skipping fetch.');
    return;
  }

  loadingDebug.value = true;
  try {
    const page = debugPagination.currentPage - 1;
    const size = debugPagination.pageSize;
    const deviceId = debugDeviceIdFilter.value.trim(); // Get filter value

    const response = await api.get(`/api/data/debug`, {
      params: {
        page: page,
        size: size,
        deviceId: deviceId // Include deviceId as a query parameter
      }
    });
    debugEvents.value = response.data.content;
    debugPagination.total = response.data.totalElements;
    console.log("Fetched debug events:", debugEvents.value);
  } catch (error) {
    console.error('Failed to fetch debug events:', error);
    ElMessage.error('加载 Debug 数据失败');
  } finally {
    loadingDebug.value = false;
  }
};

// Start the debug refresh (polling) timer
const startDebugRefresh = () => {
  stopDebugRefresh(); // Clear any existing timer
  debugRefreshTimer = setInterval(fetchDebugEvents, DEBUG_REFRESH_INTERVAL);
  console.log("Debug refresh (polling) started.");
};

// Stop the debug refresh (polling) timer
const stopDebugRefresh = () => {
  if (debugRefreshTimer) {
    clearInterval(debugRefreshTimer);
    debugRefreshTimer = null;
    console.log("Debug refresh (polling) stopped.");
  }
};

// Handle page change for Debug Data pagination
const handleDebugPageChange = (newPage) => {
  debugPagination.currentPage = newPage;
  fetchDebugEvents(); // Fetch data for the new page
};

// Handle change in Device ID filter for Debug Data
// Use a debounce if the list is very large and input is fast
const handleDebugFilterChange = () => {
  // When the filter changes, reset to the first page and fetch
  debugPagination.currentPage = 1;
  fetchDebugEvents();
  // The polling timer will pick up the new filter value on the next tick
};

// Reset pagination for debug view
const resetDebugPagination = () => {
  debugPagination.currentPage = 1;
  // Total will be updated by the fetch call
};

// --- User Property Schema State and Logic ---
const userPropertySchema = ref(null); // Stores the fetched user property schema
const userPropertySchemaModalVisible = ref(false); // Modal visibility
const userPropertySchemaFormRef = ref(null); // Form ref
const userPropertySchemaForm = reactive({
  properties: [] // [{ name: '', type: '', required: false }]
});
// Note: User property schema doesn't have an eventName field in the form


// Fetch the user property schema
const fetchUserPropertySchema = async () => {
  if (currentMenuItem.value !== 'data-management-user-properties') {
    console.log('Not on User Properties page, skipping fetch.');
    return;
  }
  try {
    const response = await api.get('/api/schemas/user-properties');
    userPropertySchema.value = response.data;
    console.log("Fetched user property schema:", userPropertySchema.value);
  } catch (error) {
    // Handle 404 specifically, as it means no schema is defined yet
    if (error.response && error.response.status === 404) {
      userPropertySchema.value = null; // Set to null if schema not found
      console.log("No user property schema defined.");
    } else {
      console.error('Failed to fetch user property schema:', error);
      ElMessage.error('加载用户属性结构失败');
    }
  }
};

// Open the user property schema configuration modal
const openUserPropertySchemaModal = () => {
  resetUserPropertySchemaForm(); // Reset form before opening
  // Populate the form with existing schema data if available
  if (userPropertySchema.value && userPropertySchema.value.propertySchema) {
    try {
      const properties = JSON.parse(userPropertySchema.value.propertySchema);
      userPropertySchemaForm.properties = Object.keys(properties).map(key => {
        const propDef = properties[key];
        if (typeof propDef === 'string') {
          return { name: key, type: propDef, required: false };
        } else if (typeof propDef === 'object' && propDef !== null) {
          return {
            name: key,
            type: propDef.type || '',
            required: propDef.required || false
          };
        }
        return { name: key, type: '', required: false }; // Handle unexpected format
      });
    } catch (e) {
      console.error('Failed to parse user property schema JSON for editing:', e);
      ElMessage.error('解析用户属性结构进行编辑失败');
      userPropertySchemaForm.properties = []; // Clear properties on parse error
    }
  }
  userPropertySchemaModalVisible.value = true;
};

// Reset the user property schema form
const resetUserPropertySchemaForm = () => {
  userPropertySchemaForm.properties = [];
  if (userPropertySchemaFormRef.value) {
    // Note: resetFields might not work as expected for reactive properties
    // Manually clear if needed or rely on reassignment above
  }
};

// Add a new user property field to the form
const addUserProperty = () => {
  userPropertySchemaForm.properties.push({ name: '', type: '', required: false });
};

// Remove a user property field from the form
const removeUserProperty = (index) => {
  userPropertySchemaForm.properties.splice(index, 1);
};

// Submit the user property schema form
const submitUserPropertySchemaForm = async () => {
  // TODO: Add validation for user property names (e.g., unique, non-empty) and types
  if (!userPropertySchemaFormRef.value) return;
  // await userPropertySchemaFormRef.value.validate(async (valid) => { // Add validation if rules are defined
  // if (valid) {
  const propertySchemaJson = {};
  userPropertySchemaForm.properties.forEach(prop => {
    if (prop.name && prop.type) {
      if (prop.required) {
        propertySchemaJson[prop.name] = { type: prop.type, required: true };
      } else {
        propertySchemaJson[prop.name] = prop.type;
      }
    }
  });

  const schemaData = {
    // User property schema doesn't have eventName, just the propertySchema JSON string
    propertySchema: Object.keys(propertySchemaJson).length > 0 ? JSON.stringify(propertySchemaJson) : null
  };

  try {
    // Use PUT for upserting the single user property schema
    const response = await api.put('/api/schemas/user-properties', schemaData);
    userPropertySchema.value = response.data; // Update displayed schema
    ElMessage.success('用户属性结构保存成功');
    userPropertySchemaModalVisible.value = false; // Close modal
    console.log("User property schema saved:", userPropertySchema.value);
  } catch (error) {
    console.error('Failed to save user property schema:', error);
    // Handle validation errors from backend if any (e.g., 400)
    if (error.response && error.response.headers['x-error-reason']) {
      ElMessage.error(`保存用户属性结构失败: ${error.response.headers['x-error-reason']}`);
    } else {
      ElMessage.error(`保存用户属性结构失败: ${error.message || '未知错误'}`);
    }
  }
  // } else {
  //    console.log('User property schema form validation failed');
  // }
  // }); // End validation block
};

// --- Utility Methods ---
const formatTimestamp = (timestamp) => {
  if (!timestamp) return '';
  // Backend timestamps are likely milliseconds since epoch
  return dayjs(timestamp).format('YYYY-MM-DD HH:mm:ss');
};

const formatEventParameters = (parameters) => {
  if (!parameters) return '无参数';
  let paramsObject = parameters;
  if (typeof parameters === 'string') {
    try {
      paramsObject = JSON.parse(parameters);
    } catch (e) {
      console.error('Failed to parse parameters JSON string:', e);
      return '无效的参数 JSON';
    }
  }
  return JSON.stringify(paramsObject, null, 2);
};


// --- Lifecycle Hooks ---
onMounted(() => {
  // On mount, load data for the default menu item
  if (currentMenuItem.value === 'data-management-meta-events') {
    fetchEventSchemas();
  } else if (currentMenuItem.value === 'tracking-management-realtime') {
    realtimeSubView.value = 'ingested';
    resetRealtimePagination();
    fetchRealtimeDataForCurrentSubView();
    startRealtimeRefresh();
  } else if (currentMenuItem.value === 'tracking-management-debug') {
    debugDeviceIdFilter.value = '';
    resetDebugPagination();
    fetchDebugEvents();
    startDebugRefresh();
  } else if (currentMenuItem.value === 'data-management-user-properties') {
    fetchUserPropertySchema();
  }
  // TODO: Add initial fetch for other menu items
});

// Lifecycle hook to stop timers when the component is unmounted
onUnmounted(() => {
  stopRealtimeRefresh();
  stopDebugRefresh();
});

// Watch for changes in currentMenuItem to manage timers and data clearing
watch(currentMenuItem, (newValue, oldValue) => {
  // Stop timers and clear data when leaving a view
  if (oldValue === 'tracking-management-realtime') {
    stopRealtimeRefresh();
    realtimeEventsIngested.value = [];
    realtimeEventsErrored.value = [];
    resetRealtimePagination();
  }
  if (oldValue === 'tracking-management-debug') {
    stopDebugRefresh();
    debugEvents.value = [];
    debugDeviceIdFilter.value = '';
    resetDebugPagination();
  }
  if (oldValue === 'data-management-user-properties') {
    userPropertySchema.value = null; // Clear user property schema display
  }

  // Start timers and fetch data when entering a view (handled in handleMenuSelect)
});

// Watch for changes in debugDeviceIdFilter to trigger a data fetch
watch(debugDeviceIdFilter, (newValue, oldValue) => {
  // Delay fetch slightly to avoid excessive calls while typing
  const delay = 300; // milliseconds
  // Clear previous timeout if exists
  if (debugDeviceIdFilter.value._timeoutId) {
    clearTimeout(debugDeviceIdFilter.value._timeoutId);
  }
  debugDeviceIdFilter.value._timeoutId = setTimeout(() => {
    console.log("Debug device ID filter changed to:", newValue);
    // Reset to first page and fetch data
    debugPagination.currentPage = 1;
    fetchDebugEvents();
  }, delay);
});
</script>

<style scoped>
/* Overall layout (remains the same) */
.schema-manager-layout {
  height: calc(100vh - 80px);
  overflow: hidden;
  display: flex;
}

/* Sidebar styles (remains the same) */
.schema-sidebar {
  background-color: #ffffff;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  transition: width 0.3s ease;
  overflow: hidden;
  flex-shrink: 0;
}

.schema-sidebar .menu-scrollbar {
  flex-grow: 1;
  height: 0;
}

.schema-menu:not(.el-menu--collapse) {
  width: 200px;
  padding: 0 10px;
}

.schema-menu {
  border-right: none;
}

/* Main content area styles (remains the same) */
.schema-manager-content {
  padding: 20px;
  background-color: #f0f2f5;
  overflow-y: auto;
  height: 100%;
  box-sizing: border-box;
  flex-grow: 1;
  transition: margin-left 0.3s ease;
}

/* Header styles (adjusted for potential multiple items on the right) */
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap; /* Allow items to wrap */
  gap: 15px; /* Spacing between header items */
}

.header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
}

/* Adjustments for specific header items */
.header .el-button {
  flex-shrink: 0; /* Prevent buttons from shrinking */
}

.header .el-radio-group {
  flex-shrink: 0; /* Prevent radio group from shrinking */
}

/* Ensure the filter input doesn't take too much space */
.header .el-input {
  flex-shrink: 0;
  min-width: 150px; /* Give it a minimum width */
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
  white-space: pre-wrap;
  word-wrap: break-word;
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

/* User Properties Schema Specific Styles */
.user-property-schema-display pre {
  margin-top: 10px;
}

/* Adjustments for header items gap if necessary */
.header {
  gap: 15px; /* Increase gap if needed to space out buttons/inputs */
}
</style>
