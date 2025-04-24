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
                <el-icon v-if="item.icon">
                  <component :is="item.icon"/>
                </el-icon>
                <span v-show="!isSidebarCollapsed">{{ item.title }}</span>
              </template>
              <el-menu-item v-for="subItem in item.children" :key="subItem.index"
                            :index="subItem.index">
                <el-icon v-if="subItem.icon">
                  <component :is="subItem.icon"/>
                </el-icon>
                <span>{{ subItem.title }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
      <div class="sidebar-footer" @click="toggleSidebarCollapse">
        <el-icon>
          <component :is="isSidebarCollapsed ? DArrowRight : DArrowLeft"/>
        </el-icon>
      </div>
    </el-aside>

    <!-- Right Main Content Area -->
    <el-main class="schema-manager-content" :style="{ marginLeft: 0 }">
      <template class="header">
        <h2>{{ selectedMenuItemTitle }}</h2>
        <!-- Show buttons based on selected menu item -->
        <template v-if="currentMenuItem === 'data-management-meta-events'">
          <el-button type="primary" :icon="Plus" @click="openSchemaModal('add')">新建事件结构
          </el-button>
        </template>
        <template v-else-if="currentMenuItem === 'tracking-management-realtime'">
          <el-radio-group v-model="realtimeSubView" @change="handleRealtimeSubViewChange">
            <el-radio-button label="ingested">实时入库</el-radio-button>
            <el-radio-button label="errored">错误数据</el-radio-button>
          </el-radio-group>
        </template>
        <template v-else-if="currentMenuItem === 'tracking-management-debug'">
          <el-input v-model="debugDeviceIdFilter" placeholder="按设备ID过滤"
                    style="width: 200px; margin-left: auto;" clearable
                    @input="handleDebugFilterChange"></el-input>
        </template>
        <template v-else-if="currentMenuItem === 'data-management-user-properties'">
          <el-button type="primary" @click="openUserPropertySchemaModal">配置用户属性</el-button>
        </template>
        <template v-else-if="currentMenuItem === 'data-management-metrics'">
          <el-button type="primary" :icon="Plus" @click="openMetricModal('add')">新建指标</el-button>
        </template>
      </template>

      <!-- Content based on selected menu item and sub-view -->
      <!-- Meta Events Content -->
      <div v-if="currentMenuItem === 'data-management-meta-events'">
        <el-table :data="eventSchemas" v-loading="loading" style="width: 100%">
          <el-table-column prop="eventName" label="事件名称" width="180"></el-table-column>
          <el-table-column prop="displayName" label="显示名称" width="180"></el-table-column>
          <el-table-column prop="remark" label="备注" width="240"></el-table-column>
          <el-table-column label="参数结构定义"> <template #default="scope">
            <div v-if="scope.row.parameterSchema && scope.row.parameterSchema !== '{}'">
              <el-table :data="formatParameterSchemaForDisplay(scope.row.parameterSchema)" size="small" :show-header="false" :border="true">
                <el-table-column prop="name" label="参数名称" ></el-table-column>
                <el-table-column prop="displayName" label="显示名称" ></el-table-column>
                <el-table-column prop="type" label="类型" ></el-table-column>
                <el-table-column prop="required" label="必需" width="70">
                  <template #default="paramScope">
                    <el-tag :type="paramScope.row.required ? 'success' : 'info'" size="small">
                      {{ paramScope.row.required ? '必需' : '非必需' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-else>
              无参数
            </div>
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
              <el-tag :type="scope.row.valid ? 'success' : 'danger'">{{
                  scope.row.valid ? '是' : '否'
                }}
              </el-tag>
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
        <div v-if="userPropertySchema">
          <el-table :data="userPropertySchemaListForDisplay" style="width: 100%">
            <el-table-column prop="name" label="属性名称"></el-table-column>
            <el-table-column prop="displayName" label="显示名称"></el-table-column> <el-table-column prop="type" label="类型"></el-table-column>
            <el-table-column prop="required" label="必需" width="80">
              <template #default="scope">
                <el-tag :type="scope.row.required ? 'success' : 'info'">{{ scope.row.required ? '是' : '否' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else>
          <p>暂无用户属性定义。</p>
        </div>
      </div>

      <div v-else-if="currentMenuItem === 'data-management-metrics'">
        <el-table :data="metrics" v-loading="loadingMetrics" style="width: 100%">
          <el-table-column prop="name" label="指标名称" width="180"></el-table-column>
          <el-table-column prop="description" label="描述"></el-table-column>
          <el-table-column prop="unit" label="单位" width="100"></el-table-column>
          <el-table-column label="定义">
            <template #default="scope">
              <pre class="definition-display">{{ scope.row.definition }}</pre> </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="scope">
              <el-button size="small" @click="openMetricModal('edit', scope.row)">编辑</el-button>
              <el-popconfirm title="确定删除此指标吗?" @confirm="deleteMetric(scope.row.id)">
                <template #reference>
                  <el-button size="small" type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-else-if="currentMenuItem === 'tracking-management-reporting'">
        <div class="reporting-controls">
          <el-date-picker
            v-model="reportingDateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始日期时间"
            end-placeholder="结束日期时间"
            value-format="x"/>
          <el-button type="primary" @click="fetchReportingStatistics">查询</el-button>
        </div>

        <el-table :data="reportingStatistics" v-loading="loadingReporting"
                  style="width: 100%; margin-top: 20px;">
          <el-table-column prop="eventName" label="事件名称"></el-table-column>
          <el-table-column prop="count" label="上报次数" sortable></el-table-column>
        </el-table>

      </div>

      <div v-else-if="currentMenuItem === 'data-integration-guide'" class="guide-container">
        <p>本指南旨在帮助开发者将游戏或其他客户端的数据事件正确上报到数据分析平台。</p>

        <el-card class="guide-card">
          <template #header>
            <div class="card-header">
              <span>上报地址</span>
            </div>
          </template>
          <p>事件上报的接口地址为：<code>/api/data/event</code></p>
          <p>请根据你的部署环境，将此接口地址与后端的基础 URL 组合使用：</p>
          <ul>
            <li>
              **公网地址 (适用于客户端直接上报):** 请填写你的后端服务对外暴露的公网 IP 或域名和端口。
              <pre
                class="address-code">https://winlife.fun:8080</pre>
            </li>
            <li>
              **私网地址 (适用于内部服务或特定网络环境上报):** 请填写你的后端服务在私有网络中的 IP
              或域名和端口。
              <pre
                class="address-code">http://localhost:8080</pre>
            </li>
          </ul>
          <p>在客户端配置中，通常会有一个基础 URL 的设置项 (例如 Unity 客户端的
            `backendBaseUrl`)，请将其设置为相应的地址。</p>
        </el-card>

        <el-card class="guide-card">
          <template #header>
            <div class="card-header">
              <span>事件上报方法</span>
            </div>
          </template>
          <p>客户端通过向上述地址发送 HTTP POST 请求来上报事件。请求体应为 JSON 格式，对应后端的数据传输对象
            `GameEventDTO` (或 Unity 客户端的 `GameEventData` 类)。</p>

          <h4>数据结构 (`GameEventData` / `GameEventDTO`)</h4>
          <p>请求体 JSON 对象应包含以下字段：</p>
          <dl class="data-structure-list">
            <dt><code>userId</code> (string):</dt>
            <dd>用户唯一标识符。</dd>

            <dt><code>deviceId</code> (string):</dt>
            <dd>设备唯一标识符。</dd>

            <dt><code>timestamp</code> (long):</dt>
            <dd>事件发生时的 Unix 时间戳，单位为**毫秒**。</dd>

            <dt><code>eventName</code> (string):</dt>
            <dd>事件的名称 (例如: "level_start", "item_purchase")。</dd>

            <dt><code>isDebug</code> (int):</dt>
            <dd>调试模式标志。<code>0</code> 表示非调试，<code>1</code> 表示调试。调试模式下的事件通常会被记录到独立的
              Debug 表中，可能不参与正式分析。
            </dd>

            <dt><code>parameters</code> (Dictionary< string,object >):</dt>
            <dd>事件相关的参数，是一个键值对字典。这些参数是针对当前事件特有的数据 (例如: {"level": 1,
              "score": 100})。
            </dd>

            <dt><code>userProperties</code> (Dictionary< string,object >):</dt>
            <dd>附加的当前用户属性，是一个键值对字典。这些属性反映了事件发生时用户的状态 (例如:
              {"coin": 500, "level": 10})。这些属性通常会在客户端通过单独的方法更新和维护，并在上报事件时附加到事件数据中。
            </dd>
          </dl>


          <h4>上报示例 (以 C# Unity 客户端为例)</h4>
          <p>以下是简化的 C# 代码示例，演示如何构造事件数据并使用 Unity 的 `UnityWebRequest` 或其他
            HTTP 客户端发送 POST 请求。请注意，实际项目中应使用更健壮的网络请求和队列管理 (参考提供的
            `DataManager.cs` 文件)。</p>
          <div class="code-example-download"
               @click="downloadCodeExample('DataManager.cs', csharpCodeExample)">
            点击下载 C# 代码示例文件
            <el-icon>
              <Document/>
            </el-icon>
          </div>
        </el-card>
        <el-card class="guide-card">
          <template #header>
            <div class="card-header">
              <span>注意事项</span>
            </div>
          </template>
          <ul>
            <li>请确保客户端生成的时间戳是**毫秒**级的 Unix 时间戳。</li>
            <li>`parameters` 和 `userProperties` 字段都应是 JSON 对象 (或客户端语言中的字典/Map)。
            </li>
            <li>考虑在客户端实现事件队列和批量发送，以减少网络请求次数和提高效率 (参考提供的
              `DataManager.cs` 文件)。
            </li>
            <li>在网络不稳定或请求失败时，应实现数据重试或本地持久化机制，避免数据丢失 (参考
              `DataManager.cs` 中的 TODO)。
            </li>
            <li>调试模式 (`isDebug = 1`) 上报的事件仅用于调试，不影响正常的数据分析统计。</li>
          </ul>
        </el-card>

        <p>如果遇到任何问题，请检查客户端日志、后端服务日志以及网络连接。</p>
      </div>

      <div v-else>
        <!-- Placeholder for other content sections -->
        <p>这是 "{{ selectedMenuItemTitle }}" 的内容区域。</p>
      </div>


    </el-main>

    <!-- Add/Edit Event Schema Dialog (remains the same) -->
    <el-dialog v-model="schemaModalVisible" :title="modalMode === 'add' ? '新建事件结构' : '编辑事件结构'" width="600px" :close-on-click-modal="false">
      <el-form ref="schemaFormRef" :model="schemaForm" :rules="schemaFormRules" label-width="100px">
        <el-form-item label="事件名称" prop="eventName">
          <el-input v-model="schemaForm.eventName" :disabled="modalMode === 'edit'"></el-input>
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="schemaForm.displayName"></el-input>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="schemaForm.remark" type="textarea"></el-input> </el-form-item>
        <el-form-item label="参数定义">
          <div class="parameter-list">
            <div v-for="(param, index) in schemaForm.parameters" :key="index" class="parameter-item">
              <el-input v-model="param.name" placeholder="参数名称" style="width: 105px; margin-right: 10px;"></el-input>
              <el-input v-model="param.displayName" placeholder="显示名称" style="width: 105px; margin-right: 10px;"></el-input>
              <el-select v-model="param.type" placeholder="类型" style="width: 100px; margin-right: 10px;">
                <el-option label="string" value="string"></el-option>
                <el-option label="integer" value="integer"></el-option>
                <el-option label="float" value="float"></el-option>
                <el-option label="boolean" value="boolean"></el-option>
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
          <div class="parameter-list"> <div v-for="(prop, index) in userPropertySchemaForm.properties" :key="index" class="parameter-item">
            <el-input v-model="prop.name" placeholder="属性名称" style="width: 100px; margin-right: 10px;"></el-input>
            <el-input v-model="prop.displayName" placeholder="显示名称" style="width: 100px; margin-right: 10px;"></el-input> <el-select v-model="prop.type" placeholder="类型" style="width: 100px; margin-right: 10px;">
            <el-option label="string" value="string"></el-option>
            <el-option label="integer" value="integer"></el-option>
            <el-option label="float" value="float"></el-option>
            <el-option label="boolean" value="boolean"></el-option>
          </el-select>
            <el-checkbox v-model="prop.required" style="margin-right: 10px;">必需</el-checkbox> <el-button type="danger" :icon="Delete" circle size="small" @click="removeUserProperty(index)"></el-button>
          </div>
            <el-button :icon="Plus" @click="addUserProperty">添加属性</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
         <span class="dialog-footer">
             <el-button @click="userPropertySchemaModalVisible = false">取消</el-button>
             <el-button type="primary" @click="submitUserPropertySchemaForm">确定</el-button>
         </span>
      </template>
    </el-dialog>

    <el-dialog v-model="metricModalVisible" :title="modalMode === 'add' ? '新建指标' : '编辑指标'" width="600px" :close-on-click-modal="false">
      <el-form ref="metricFormRef" :model="metricForm" :rules="metricFormRules" label-width="100px">
        <el-form-item label="指标名称" prop="name">
          <el-input v-model="metricForm.name" :disabled="modalMode === 'edit'"></el-input>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="metricForm.description"></el-input>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="metricForm.unit" placeholder="例如: 人, 元"></el-input>
        </el-form-item>
        <el-form-item label="定义" prop="definition">
          <el-input v-model="metricForm.definition" type="textarea" :rows="5" placeholder="请输入指标计算的定义或说明"></el-input>
        </el-form-item>
      </el-form>
      <template #footer>
    <span class="dialog-footer">
      <el-button @click="metricModalVisible = false">取消</el-button>
      <el-button type="primary" @click="submitMetricForm">确定</el-button>
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

// --- Metric Management State and Logic ---
const metrics = ref([]); // Stores the list of metric definitions
const loadingMetrics = ref(false); // Loading state for metrics table
const metricModalVisible = ref(false); // Modal visibility for add/edit metric
// modalMode and currentSchemaId can be reused, but let's use separate ones for clarity if needed
const metricModalMode = ref('add'); // 'add' or 'edit'
const currentMetricId = ref(null); // Stores the ID of the metric being edited

const metricFormRef = ref(null); // Ref for the metric form
const metricForm = reactive({
  name: '',
  description: '',
  unit: '',
  definition: '' // Store definition as a string for now
});
const metricFormRules = {
  name: [{ required: true, message: '请输入指标名称', trigger: 'blur' }],
  description: [{ required: true, message: '请输入指标描述', trigger: 'blur' }],
  // unit: [{ required: true, message: '请输入指标单位', trigger: 'blur' }], // Unit might be optional
  definition: [{ required: true, message: '请输入指标定义', trigger: 'blur' }]
};


// --- Metric Management Methods ---

/**
 * Fetches all metric definitions from the backend.
 */
const fetchMetrics = async () => {
  loadingMetrics.value = true;
  metrics.value = []; // Clear previous data
  try {
    // Assume backend endpoint is /api/metrics
    const response = await api.get('/api/metrics');
    metrics.value = response.data || [];
    console.log("Fetched metrics:", metrics.value);
  } catch (error) {
    console.error('Failed to fetch metrics:', error);
    ElMessage.error('加载指标数据失败');
    metrics.value = [];
  } finally {
    loadingMetrics.value = false;
  }
};

/**
 * Opens the add/edit metric modal.
 * @param mode 'add' or 'edit'
 * @param metric The metric object to edit (optional, for edit mode)
 */
const openMetricModal = (mode, metric = null) => {
  metricModalMode.value = mode;
  resetMetricForm(); // Reset form state

  if (mode === 'edit' && metric) {
    currentMetricId.value = metric.id;
    // Populate the form with existing metric data
    metricForm.name = metric.name;
    metricForm.description = metric.description;
    metricForm.unit = metric.unit;
    metricForm.definition = metric.definition;
  }
  metricModalVisible.value = true;
};

/**
 * Resets the metric form.
 */
const resetMetricForm = () => {
  currentMetricId.value = null;
  metricForm.name = '';
  metricForm.description = '';
  metricForm.unit = '';
  metricForm.definition = '';
  if (metricFormRef.value) {
    // Use resetFields if possible, otherwise manually reset
    metricFormRef.value.resetFields();
    // Manual reset for fields not included in rules or not reactive enough
    metricForm.name = '';
    metricForm.description = '';
    metricForm.unit = '';
    metricForm.definition = '';
  }
};

/**
 * Submits the metric form (adds or updates a metric).
 */
const submitMetricForm = async () => {
  if (!metricFormRef.value) return;

  await metricFormRef.value.validate(async (valid) => {
    if (valid) {
      const metricData = {
        name: metricForm.name,
        description: metricForm.description,
        unit: metricForm.unit,
        definition: metricForm.definition
        // Backend might expect ID for PUT requests
        // id: modalMode.value === 'edit' ? currentMetricId.value : undefined
      };

      try {
        if (metricModalMode.value === 'add') {
          // Assume backend POST /api/metrics
          await api.post('/api/metrics', metricData);
          ElMessage.success('指标创建成功');
        } else {
          // Assume backend PUT /api/metrics/{id}
          await api.put(`/api/metrics/${currentMetricId.value}`, metricData);
          ElMessage.success('指标更新成功');
        }
        metricModalVisible.value = false;
        // Refresh the list of metrics after saving
        if (currentMenuItem.value === 'data-management-metrics') {
          fetchMetrics();
        }
      } catch (error) {
        console.error('Failed to save metric:', error);
        // Handle specific errors, e.g., duplicate name (409 Conflict)
        if (error.response && error.response.status === 409) {
          ElMessage.error(`指标名称 "${metricForm.name}" 已存在`);
        } else {
          ElMessage.error(`保存指标失败: ${error.message || '未知错误'}`);
        }
      }
    } else {
      console.log('Metric form validation failed');
      return false;
    }
  });
};

/**
 * Deletes a metric definition.
 * @param id The ID of the metric to delete.
 */
const deleteMetric = async (id) => {
  try {
    // Assume backend DELETE /api/metrics/{id}
    await api.delete(`/api/metrics/${id}`);
    ElMessage.success('指标删除成功');
    // Remove the deleted metric from the frontend list or refetch
    metrics.value = metrics.value.filter(metric => metric.id !== id);
  } catch (error) {
    console.error('Failed to delete metric:', error);
    ElMessage.error('删除指标失败');
  }
};

// --- Reporting Management State and Logic ---
const reportingDateRange = ref([Date.now() - 3600 * 1000 * 24 * 7, Date.now()]); // Default to last 7 days
const reportingStatistics = ref([]);
const loadingReporting = ref(false);
// 如果需要分页
// const reportingPagination = reactive({
//   currentPage: 1,
//   pageSize: 10,
//   total: 0
// });
// Fetch reporting statistics from backend
const fetchReportingStatistics = async () => {
  if (currentMenuItem.value !== 'tracking-management-reporting') {
    console.log('Not on Reporting Management page, skipping fetch.');
    return;
  }

  if (!reportingDateRange.value || reportingDateRange.value.length !== 2) {
    ElMessage.warning('请选择有效的日期范围');
    return;
  }

  loadingReporting.value = true;
  try {
    // 假设后端接口是 /api/data/report/statistics 并接受开始时间和结束时间作为查询参数
    // 使用 value-format="x" 会得到毫秒级时间戳，需要转换为秒级或后端需要的格式
    const startTime = reportingDateRange.value[0]; // 直接使用毫秒级时间戳
    const endTime = reportingDateRange.value[1];   // 直接使用毫秒级时间戳

    const response = await api.get('/api/data/report/statistics', {
      params: {
        startTime: startTime,
        endTime: endTime,
        // 如果后端支持分页，添加分页参数
        // page: reportingPagination.currentPage - 1,
        // size: reportingPagination.pageSize
      }
    });

    reportingStatistics.value = response.data; // 假设后端直接返回统计列表
    // 如果后端支持分页
    // reportingStatistics.value = response.data.content;
    // reportingPagination.total = response.data.totalElements;

    console.log("Fetched reporting statistics:", reportingStatistics.value);
  } catch (error) {
    console.error('Failed to fetch reporting statistics:', error);
    ElMessage.error('加载上报统计数据失败');
  } finally {
    loadingReporting.value = false;
  }
};

// 如果需要分页，添加分页变化处理方法
const handleReportingPageChange = (newPage) => {
  reportingPagination.currentPage = newPage;
  fetchReportingStatistics();
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
  userPropertySchema.value = null;  // Clear user property schema display

  // Load content based on the selected index
  if (index === 'data-management-meta-events') {
    fetchEventSchemas();
  } else if (index === 'tracking-management-realtime') {
    realtimeSubView.value = 'ingested';  // Default to '实时入库'
    resetRealtimePagination();
    fetchRealtimeDataForCurrentSubView();
    startRealtimeRefresh();
  } else if (index === 'tracking-management-debug') {
    debugDeviceIdFilter.value = '';  // Clear device ID filter
    resetDebugPagination();
    fetchDebugEvents();
    startDebugRefresh();
  } else if (index === 'data-management-user-properties') {
    // Load user property schema for display
    fetchUserPropertySchema();
  } else if (index === 'tracking-management-reporting') { // Add this block
    // Set default date range and fetch data
    reportingDateRange.value = [Date.now() - 3600 * 1000 * 24 * 7, Date.now()];
    // 如果需要分页，重置分页
    // reportingPagination.currentPage = 1;
    fetchReportingStatistics();
  }
  // TODO: Add else if for other menu items
};

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
      {index: 'data-management-user-properties', title: '用户属性'},
      {index: 'data-management-metrics', title: '指标'},
    ]
  },
  {
    index: 'tracking-management',
    title: '埋点管理',
    icon: shallowRef(Setting),
    children: [
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


// --- Event Schema Management State and Logic (remains the same) ---
const eventSchemas = ref([]);
const loading = ref(false);

const schemaModalVisible = ref(false);
const modalMode = ref('add');
const currentSchemaId = ref(null);

const schemaFormRef = ref(null);
const schemaForm = reactive({
  eventName: '',
  displayName: '', // Add new property
  remark: '',      // Add new property
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

// Modify the existing formatParameterSchema method to be used for display table data
const formatParameterSchemaForDisplay = (jsonString) => {
  if (!jsonString) return [];
  try {
    const schema = JSON.parse(jsonString);
    // Ensure schema is an object before processing
    if (typeof schema !== 'object' || schema === null) {
      console.error("Parsed parameter schema is not an object:", schema);
      return [];
    }
    // Transform the schema object into an array of parameter objects
    return Object.keys(schema).map(key => {
      const paramDef = schema[key];
      // Handle both simple string type and complex object with type/required
      if (typeof paramDef === 'string') {
        return { name: key, displayName: key, type: paramDef, required: false };
      } else if (typeof paramDef === 'object' && paramDef !== null) {
        return {
          name: key,
          displayName: paramDef.displayName || '', // Load displayName
          type: paramDef.type || '',
          required: paramDef.required || false
          // Add other fields like description if you add them to your schema
          // description: paramDef.description || ''
        };
      }
      return { name: key, type: '', required: false }; // Fallback for unexpected format
    });
  } catch (e) {
    console.error('Failed to parse parameter schema JSON for display table:', e);
    return []; // Return empty array on error
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
    schemaForm.displayName = schema.displayName || ''; // Load displayName
    schemaForm.remark = schema.remark || '';
    if (schema.parameterSchema) {
      try {
        const params = JSON.parse(schema.parameterSchema);
        schemaForm.parameters = Object.keys(params).map(key => {
          const paramDef = params[key];
          if (typeof paramDef === 'string') {
            return {name: key, displayName: key, type: paramDef, required: false};
          } else if (typeof paramDef === 'object' && paramDef !== null) {
            return {
              name: key,
              displayName: paramDef.displayName || '', // Load displayName
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
  schemaForm.displayName = ''; // Reset displayName
  schemaForm.remark = '';      // Reset remark
  schemaForm.parameters = [];
  if (schemaFormRef.value) {
    schemaFormRef.value.resetFields();
    // Manual reset for fields not included in rules or not reactive enough for resetFields
    schemaForm.displayName = '';
    schemaForm.remark = '';
    schemaForm.parameters = []; // Ensure parameters are cleared
  }
};

const addParameter = () => {
  schemaForm.parameters.push({name: '', displayName: '', type: '', required: false});
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
        if (param.name && param.displayName && param.type) {
          if (param.required) {
            parameterSchemaJson[param.name] = {displayName: param.displayName, type: param.type, required: true};
          } else {
            parameterSchemaJson[param.name] = param.type;
          }
        }
      });

      const schemaData = {
        eventName: schemaForm.eventName,
        displayName: schemaForm.displayName, // Include displayName
        remark: schemaForm.remark,          // Include remark
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
  properties: [] // [{ name: '', displayName: '', type: '', required: false }] // Update structure comment
});
// Note: User property schema doesn't have an eventName field in the form

// Computed property to format user property schema for ElTable display
const userPropertySchemaListForDisplay = computed(() => {
  if (!userPropertySchema.value || !userPropertySchema.value.propertySchema) {
    return [];
  }
  try {
    const schema = JSON.parse(userPropertySchema.value.propertySchema);
    // Ensure schema is an object before processing
    if (typeof schema !== 'object' || schema === null) {
      console.error("Parsed user property schema is not an object:", schema);
      return [];
    }
    return Object.keys(schema).map(key => {
      const propDef = schema[key];
      // Handle both simple string type and complex object with type/required/displayName
      if (typeof propDef === 'string') {
        return { name: key, displayName: key, type: propDef, required: false }; // Default displayName if not in JSON
      } else if (typeof propDef === 'object' && propDef !== null) {
        return {
          name: key,
          displayName: propDef.displayName || key, // Use displayName from JSON or default to name
          type: propDef.type || '',
          required: propDef.required || false
        };
      }
      return { name: key, displayName: key, type: '', required: false }; // Fallback for unexpected format
    });
  } catch (e) {
    console.error('Failed to parse user property schema JSON for display:', e);
    return [];
  }
});

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
      // Ensure properties is an object before processing
      if (typeof properties !== 'object' || properties === null) {
        console.error("Parsed user property schema for modal is not an object:", properties);
        userPropertySchemaForm.properties = [];
        userPropertySchemaModalVisible.value = true;
        return;
      }
      userPropertySchemaForm.properties = Object.keys(properties).map(key => {
        const propDef = properties[key];
        // Handle both simple string type and complex object with type/required/displayName
        if (typeof propDef === 'string') {
          return { name: key, displayName: key, type: propDef, required: false }; // Default displayName if not in JSON
        } else if (typeof propDef === 'object' && propDef !== null) {
          return {
            name: key,
            displayName: propDef.displayName || '', // Load displayName from JSON
            type: propDef.type || '',
            required: propDef.required || false
          };
        }
        return { name: key, displayName: '', type: '', required: false }; // Handle unexpected format
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
  userPropertySchemaForm.properties = []; // Clears name, displayName, type, required for all items
  if (userPropertySchemaFormRef.value) {
    // resetFields might not work as expected for reactive properties and array items.
    // The reassignment above is the main way to clear.
  }
};

// Add a new user property field to the form
const addUserProperty = () => {
  userPropertySchemaForm.properties.push({ name: '', displayName: '', type: '', required: false }); // Initialize displayName
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
    // Only include properties with a name and type
    if (prop.name && prop.type) {
      // Construct the property definition object including displayName and required
      const propDefinition = {
        displayName: prop.displayName || '', // Include displayName, default to empty string if not set
        type: prop.type,
        required: prop.required || false // Default to false if not set
      };
      // Optional: If you want to omit displayName or required if they are default values (empty/false)
      // if (!propDefinition.displayName) delete propDefinition.displayName;
      // if (!propDefinition.required) delete propDefinition.required;


      // Decide whether to use the simple string format or complex object format in the JSON
      // Backend validation logic should support this.
      // Based on validateProperties method, backend expects complex object if required or displayName is present,
      // or if it's explicitly an object with type. Let's stick to the complex object format for consistency.
      propertySchemaJson[prop.name] = propDefinition;

      /*
      // Previous logic: simple string if not required, complex object if required
      if (prop.required) {
        propertySchemaJson[prop.name] = { type: prop.type, required: true };
      } else {
        propertySchemaJson[prop.name] = prop.type;
      }
      // This previous logic doesn't support displayName in both cases.
      // The new structure should always be the complex object to include displayName.
      */
    }
  });

  const schemaData = {
    propertySchema: Object.keys(propertySchemaJson).length > 0 ? JSON.stringify(propertySchemaJson) : null
  };

  try {
    const response = await api.put('/api/schemas/user-properties', schemaData);
    userPropertySchema.value = response.data; // Update displayed schema with the new structure
    ElMessage.success('用户属性结构保存成功');
    userPropertySchemaModalVisible.value = false; // Close modal
    console.log("User property schema saved:", userPropertySchema.value);
  } catch (error) {
    console.error('Failed to save user property schema:', error);
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
  if (newValue === 'data-management-metrics') { // Add this condition
    fetchMetrics(); // Fetch metrics when the metrics page is selected
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

// --- Access Guide Code Example Content ---
// Store the C# code as a string
const csharpCodeExample = `
// 导入必要的命名空间
using System;
using System.Collections.Generic;
using UnityEngine.Networking;
using System.Text;
// 如果使用 Newtonsoft.Json (推荐)
using Newtonsoft.Json;
// 如果只使用 Unity 内置的 JsonUtility，则参数字典需要更复杂的处理或定义具体的参数类

public class GameEventData // 对应后端 DTO
{
    public string userId;
    public string deviceId;
    public long timestamp; // 毫秒时间戳
    public string eventName;
    public int isDebug;
    public Dictionary<string, object> parameters;
    public Dictionary<string, object> userProperties;
}

public class EventReporter // 示例类
{
    // 替换为你的后端事件上报完整 URL (例如: "http://localhost:8080/api/data/event")
    private string eventEndpoint = "[你的后端事件上报完整 URL]";

    // 假设你已经有了获取 userId 和 deviceId 的方法
    private string GetUserId() { /* ... */ return "test_user_123"; }
    private string GetDeviceId() { /* ... */ return "test_device_abc"; }

    // 假设你维护了一个当前用户属性的字典
    private Dictionary<string, object> currentUserProperties = new Dictionary<string, object>();

    public void SetUserProperties(Dictionary<string, object> properties)
    {
        if (properties == null) return;
        foreach (var kvp in properties)
        {
            currentUserProperties[kvp.Key] = kvp.Value;
        }
    }

    public void TrackEvent(string eventName, Dictionary<string, object> eventParameters = null, bool isDebugMode = false)
    {
        GameEventData eventData = new GameEventData
        {
            userId = GetUserId(),
            deviceId = GetDeviceId(),
            timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(), // 获取当前毫秒时间戳
            eventName = eventName,
            isDebug = isDebugMode ? 1 : 0,
            parameters = eventParameters ?? new Dictionary<string, object>(),
            userProperties = new Dictionary<string, object>(currentUserProperties) // 附加当前用户属性的副本
        };

        // 使用 Newtonsoft.Json 序列化
        string jsonBody = JsonConvert.SerializeObject(eventData);

        // 发送 POST 请求 (在 Unity 中通常使用协程)
        // StartCoroutine(SendRequestCoroutine(eventEndpoint, jsonBody));
    }

    // 简单的发送请求协程示例
    /*
    private IEnumerator SendRequestCoroutine(string url, string jsonBody)
    {
        using (UnityWebRequest www = new UnityWebRequest(url, "POST"))
        {
            byte[] bodyRaw = Encoding.UTF8.GetBytes(jsonBody);
            www.uploadHandler = new UploadHandlerRaw(bodyRaw);
            www.downloadHandler = new DownloadHandlerBuffer();
            www.SetRequestHeader("Content-Type", "application/json");

            yield return www.SendWebRequest();

            if (www.result != UnityWebRequest.Result.Success)
            {
                Debug.LogError($"Event send failed: {www.error}");
            }
            else
            {
                Debug.Log("Event sent successfully!");
            }
        }
    }
    */
}

// 示例用法
/*
// 在应用启动时设置用户属性
var reporter = new EventReporter();
reporter.SetUserProperties(new Dictionary<string, object>
{
    { "app_version", "1.0.0" },
    { "device_model", UnityEngine.SystemInfo.deviceModel }
});

// 在某个事件发生时上报
reporter.TrackEvent("level_start", new Dictionary<string, object>
{
    { "level_id", 1 },
    { "difficulty", "easy" }
});

// 更新用户属性
reporter.SetUserProperties(new Dictionary<string, object>
{
    { "coin", 100 },
    { "level", 5 }
});

// 上报另一个事件
reporter.TrackEvent("item_purchase", new Dictionary<string, object>
{
    { "item_name", "sword" },
    { "price", 50 }
});
*/
`; // End of csharpCodeExample string

// Method to download the code example file
const downloadCodeExample = (filename, codeContent) => {
  const blob = new Blob([codeContent], { type: 'text/plain' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url); // Clean up the URL object
};
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

/* Add some spacing for the guide container */
.guide-container {
  padding: 0; /* Match the main content padding */
}

/* Style for the ElCard components */
.guide-card {
  margin-bottom: 20px; /* Space between cards */
  /* Optional: Add some box-shadow or border if desired */
  /* box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1); */
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133; /* Element Plus primary text color */
}

/* Style for inline code */
code {
  background-color: #f9f2f4;
  color: #c7254e;
  padding: 2px 4px;
  border-radius: 4px;
  font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
}

/* Style for the address code block */
pre.address-code {
  background-color: #e9e9eb; /* Light gray background */
  color: #5e6d82; /* Darker text */
  padding: 10px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  line-height: 1.4;
  white-space: pre-wrap;
  word-wrap: break-word;
}

/* Enhanced style for code blocks */
pre.code-block {
  background-color: #2b2b2b; /* Dark background for code */
  color: #a9b7c6; /* Light text color */
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: Consolas, Monaco, 'Andale Mono', 'Ubuntu Mono', monospace;
  font-size: 0.9em;
  line-height: 1.4;
  tab-size: 4;
  white-space: pre-wrap; /* Allow wrapping */
  word-wrap: break-word; /* Break long words */
}

/* Style for definition list (data structure) */
.data-structure-list {
  margin-top: 10px;
  margin-bottom: 10px;
  padding-left: 20px; /* Indent the list */
}

.data-structure-list dt {
  font-weight: bold;
  margin-top: 8px; /* Space above each term */
  color: #606266; /* Element Plus regular text color */
}

.data-structure-list dd {
  margin-left: 20px; /* Indent definition */
  margin-bottom: 8px; /* Space below each definition */
  color: #909399; /* Element Plus secondary text color */
}

/* Add some margin below headings and paragraphs */
.guide-container h2,
.guide-container h3,
.guide-container h4,
.guide-container p,
.guide-container ul {
  margin-bottom: 15px;
}

.guide-container ul li {
  margin-bottom: 5px;
}

/* Adjust margin for the last paragraph */
.guide-container p:last-child {
  margin-bottom: 0;
}

.code-example-download {
  display: inline-flex; /* Use flex to align content and icon */
  align-items: center;
  background-color: #e9e9eb; /* Light gray background */
  color: #5e6d82; /* Darker text color */
  padding: 10px 15px;
  border-radius: 4px;
  cursor: pointer; /* Indicate it's clickable */
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif; /* Match Element Plus font */
  font-size: 14px;
  margin-top: 10px; /* Space above the element */
  border: 1px solid #dcdfe6; /* Add a slight border */
  transition: background-color 0.2s, border-color 0.2s; /* Smooth transition on hover */
}

.code-example-download:hover {
  background-color: #d3d6db; /* Darker background on hover */
  border-color: #c6c9ce;
}

.code-example-download .el-icon {
  margin-left: 8px; /* Space between text and icon */
  font-size: 16px;
}

/* Optional: Adjust spacing within nested tables if needed */
.el-table .el-table__cell {
  padding: 5px 0; /* Reduce padding in nested table cells */
}

/* Optional: Add a slight background color to nested table rows for better separation */
.el-table .el-table__row {
  background-color: #fafafa; /* Lighter background for nested rows */
}

/* Ensure text within nested table cells wraps */
.el-table .el-table__cell .cell {
  white-space: normal;
  word-break: break-all;
}

/* Style for displaying metric definition in the table */
.definition-display {
  white-space: pre-wrap; /* Preserve whitespace and wrap lines */
  word-break: break-word; /* Break long words */
  font-size: 12px;
  color: #606266;
  max-height: 80px; /* Limit height in table cell */
  overflow-y: auto; /* Add scrollbar if definition is long */
  background-color: #f9f9f9; /* Subtle background */
  padding: 5px;
  border-radius: 4px;
  border: 1px solid #eee;
}
</style>
