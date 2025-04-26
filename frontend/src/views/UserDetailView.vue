<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import { useRoute } from 'vue-router';

import TopNav from "@/components/TopNav.vue";
import {
  ElContainer,
  ElAside,
  ElMain,
  ElCard,
  ElIcon,
  ElButton,
  ElDatePicker,
  ElTable,
  ElTableColumn,
  ElLoading,
  ElMessage,
  ElScrollbar,
  ElTag,
  // Removed ElSelect and ElOption as granularity dropdown is removed
} from 'element-plus';

import * as echarts from 'echarts/core';
import {
  BarChart
} from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  TitleComponent,
  // Removed LegendComponent as there's only one series now
} from 'echarts/components';
import {
  CanvasRenderer
} from 'echarts/renderers';

echarts.use([
  BarChart,
  GridComponent,
  TooltipComponent,
  TitleComponent,
  // Removed LegendComponent
  CanvasRenderer
]);


import { Calendar, DataAnalysis, List, ArrowDown, ArrowRight } from '@element-plus/icons-vue';

import api from "@/api/index.js";

// --- Get User ID from Route ---
const route = useRoute();
const userId = computed(() => route.params.userId);

// --- State Variables ---
const loadingData = ref(true);
const loadingStats = ref(false);
const loadingSequence = ref(false);

// User Data (left panel)
const playerDataSource = ref(null);

// Event Statistics (right panel - chart)
const statsDateRange = ref([Date.now() - 3600 * 1000 * 24 * 30, Date.now()]); // Default to last 30 days
// Removed selectedGranularity and granularityOptions
// eventStatistics will now store data in [{ eventName, count }] format
const eventStatistics = ref([]);

const chartContainer = ref(null);
let myChart = null;

const userPropertySchema = ref(null);
const availableEvents = ref([]); // List of all EventSchema objects from backend

// Event Sequence (right panel - list)
const eventSequence = ref([]);
const eventSequencePagination = reactive({
  currentPage: 0,
  pageSize: 30,
  total: 0,
  loadingMore: false
});

const expandedEventId = ref(null);


// --- Computed Properties ---
const formattedUserProperties = computed(() => {
  if (!playerDataSource.value || !playerDataSource.value.userProperties) {
    return [];
  }
  try {
    const properties = JSON.parse(playerDataSource.value.userProperties);
    if (typeof properties === 'object' && properties !== null) {
      return Object.keys(properties).map(key => {
        let value = properties[key];
        if (value === null || value === undefined) {
          value = 'null';
        } else if (typeof value === 'object') {
          try {
            value = JSON.stringify(value, null, 2);
          } catch (e) {
            value = '[Complex Object]';
          }
        }
        const selectedProp = userPropertyOptions.value.find(opt => opt.value === key);
        return { label: selectedProp ? selectedProp.label : key, value: value };
      });
    }
    return [];
  } catch (e) {
    console.error('Failed to parse user properties JSON for display:', e);
    return [];
  }
});

const userPropertyOptions = computed(() => {
  if (!userPropertySchema.value || !userPropertySchema.value.propertySchema) {
    return [];
  }
  try {
    const schema = JSON.parse(userPropertySchema.value.propertySchema);
    if (typeof schema === 'object' && schema !== null) {
      return Object.keys(schema).map(key => {
        const propDef = schema[key];
        let displayName = key; // Default to key if displayName is not found

        if (typeof propDef === 'object' && propDef !== null && propDef.displayName) {
          displayName = propDef.displayName;
        }
        // Return an object with 'label' for display and 'value' for the actual key
        return { label: displayName, value: key };
      }).sort((a, b) => a.label.localeCompare(b.label)); // Optional: sort alphabetically by label
    }
    return [];
  } catch (e) {
    console.error('Failed to parse user property schema JSON for options:', e);
    return [];
  }
});


// --- Methods ---
const fetchUserPropertySchema = async () => {
  userPropertySchema.value = null;
  try {
    const response = await api.get('/api/schemas/user-properties');
    userPropertySchema.value = response.data;
    console.log("Fetched user property schema:", userPropertySchema.value);
  } catch (error) {
    console.error('Failed to fetch user property schema:', error);
    ElMessage.error('加载用户属性结构失败，用户属性查找可能不可用');
    userPropertySchema.value = null;
  } finally {

  }
};

const fetchAvailableEvents = async () => {
  availableEvents.value = []; // Clear previous data
  try {
    // Assuming backend endpoint for all event schemas is GET /api/schemas/events
    const response = await api.get('/api/schemas/events'); // Use the provided endpoint
    availableEvents.value = response.data || [];
    console.log("Fetched available EventSchemas:", availableEvents.value);
  } catch (error) {
    console.error('Failed to fetch available events:', error);
    ElMessage.error('加载事件列表失败');
    availableEvents.value = [];
  } finally {
  }
};

const fetchInitialData = async () => {
  if (!userId.value) {
    ElMessage.error('用户ID未提供');
    loadingData.value = false;
    return;
  }
  loadingData.value = true;
  try {
    const playerResponse = await api.get(`/api/data/player/${userId.value}`);
    playerDataSource.value = playerResponse.data;
    console.log("Fetched PlayerData:", playerDataSource.value);

    await fetchEventSequence(true);

    // Fetch initial Event Statistics with default date range
    await fetchEventStatistics();

  } catch (error) {
    console.error('Failed to fetch initial user data:', error);
    ElMessage.error('加载用户数据失败');
    playerDataSource.value = null;
    eventSequence.value = [];
    eventStatistics.value = [];
  } finally {
    loadingData.value = false;
  }
};


const fetchEventStatistics = async () => {
  if (!userId.value) return;
  if (!statsDateRange.value || statsDateRange.value.length !== 2) {
    ElMessage.warning('请选择有效的统计时间范围');
    return;
  }

  loadingStats.value = true;
  eventStatistics.value = [];

  try {
    const startTime = statsDateRange.value[0];
    const endTime = statsDateRange.value[1];

    const response = await api.get(`/api/data/events/statistics/${userId.value}`, {
      params: {
        startTime: startTime,
        endTime: endTime,
      }
    });

    eventStatistics.value = response.data ? response.data.sort((a, b) => b.count - a.count) : [];
    console.log("Fetched Event Statistics:", eventStatistics.value);

    if (myChart) {
      updateChart(eventStatistics.value);
    } else {
      initChart(eventStatistics.value);
    }


  } catch (error) {
    console.error('Failed to fetch event statistics:', error);
    ElMessage.error('加载事件统计数据失败');
    eventStatistics.value = [];
    if (myChart) {
      updateChart([]);
    }
  } finally {
    loadingStats.value = false;
  }
};


const fetchEventSequence = async (resetPagination = false) => {
  if (!userId.value) return;

  if (resetPagination) {
    eventSequencePagination.currentPage = 0;
    eventSequence.value = [];
    eventSequencePagination.total = 0;
    expandedEventId.value = null;
  }

  if (eventSequencePagination.loadingMore && !resetPagination) {
    console.log("Already loading more events, skipping.");
    return;
  }

  if (!resetPagination && eventSequencePagination.total > 0 &&
    eventSequencePagination.currentPage * eventSequencePagination.pageSize >= eventSequencePagination.total) {
    console.log("Already loaded all pages.");
    eventSequencePagination.loadingMore = false;
    return;
  }


  loadingSequence.value = !resetPagination;
  eventSequencePagination.loadingMore = !resetPagination;

  try {
    const response = await api.get(`/api/data/events/sequence/${userId.value}`, {
      params: {
        page: eventSequencePagination.currentPage,
        size: eventSequencePagination.pageSize
      }
    });

    if (response.data && Array.isArray(response.data.content)) {
      if (resetPagination) {
        eventSequence.value = response.data.content;
      } else {
        eventSequence.value = [...eventSequence.value, ...response.data.content];
      }

      eventSequencePagination.total = response.data.totalElements || eventSequence.value.length;

      if (response.data.content.length === eventSequencePagination.pageSize &&
        response.data.totalPages > eventSequencePagination.currentPage + 1) {
        eventSequencePagination.currentPage++;
      } else if (response.data.content.length < eventSequencePagination.pageSize) {
        eventSequencePagination.currentPage = response.data.totalPages || eventSequencePagination.currentPage + 1;
      } else {
        eventSequencePagination.currentPage++;
      }


    } else {
      if (resetPagination) {
        eventSequence.value = [];
        eventSequencePagination.total = 0;
      } else {
        eventSequencePagination.total = eventSequence.value.length;
      }
      if (!resetPagination) {
        ElMessage.info('没有更多行为序列数据了');
      } else {
        ElMessage.info('未找到用户行为序列数据');
      }
    }

    console.log("Fetched Event Sequence:", response.data);


  } catch (error) {
    console.error('Failed to fetch event sequence:', error);
    if (resetPagination) {
      ElMessage.error('加载用户行为序列失败');
      eventSequence.value = [];
      eventSequencePagination.total = 0;
    } else {
      ElMessage.error('加载更多行为序列失败');
    }
    eventSequencePagination.total = eventSequence.value.length;

  } finally {
    loadingSequence.value = false;
    eventSequencePagination.loadingMore = false;
  }
};

const loadMoreEvents = () => {
  if (!loadingSequence.value && !eventSequencePagination.loadingMore) {
    fetchEventSequence(false);
  }
};

const displayNameByEvent = (eventName) => {
  const event = availableEvents.value.find(e => e.eventName === eventName);
  return event ? event.displayName : eventName;
}

const formatTimestamp = (timestamp) => {
  if (!timestamp) return '-';
  return new Date(timestamp).toLocaleString();
};

const toggleEventDetails = (eventId) => {
  if (expandedEventId.value === eventId) {
    expandedEventId.value = null;
  } else {
    expandedEventId.value = eventId;
  }
};

const parseJsonPropertiesForDisplay = (jsonString) => {
  if (!jsonString) return [];
  try {
    const properties = JSON.parse(jsonString);
    if (typeof properties === 'object' && properties !== null && !Array.isArray(properties)) {
      return Object.keys(properties).map(key => {
        let value = properties[key];
        if (value === null || value === undefined) {
          value = 'null';
        } else if (typeof value === 'object') {
          try {
            value = JSON.stringify(value, null, 2);
          } catch (e) {
            value = '[Complex Object]';
          }
        }
        const selectedProp = userPropertyOptions.value.find(opt => opt.value === key);
        return { label: selectedProp ? selectedProp.label : key, value: value };
      });
    }
    console.warn("Expected JSON object for properties, but received:", properties);
    return [];

  } catch (e) {
    console.error('Failed to parse JSON for display:', e);
    return [{ label: '解析错误', value: '无效的 JSON 数据' }];
  }
};


// --- Echarts Methods ---

const initChart = (statsData) => {
  if (chartContainer.value) {
    myChart = echarts.init(chartContainer.value);
    updateChart(statsData);
    window.addEventListener('resize', resizeChart);
  }
};

const updateChart = (statsData) => {
  if (!myChart) {
    initChart(statsData);
    return;
  }

  if (!statsData || statsData.length === 0) {
    myChart.setOption({ series: [] });
    return;
  }

  const eventNames = statsData.map(item => displayNameByEvent(item.eventName));
  const eventCounts = statsData.map(item => item.count);

  const options = {
    title: {
      text: '用户行为总量统计',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: eventNames,
      axisLabel: {
        interval: 0,
        rotate: eventNames.some(name => name && name.length > 5) ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      name: '次数',
      minInterval: 1
    },
    series: [
      {
        name: '上报次数',
        type: 'bar',
        data: eventCounts,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#83bff6' },
            { offset: 0.5, color: '#188df0' },
            { offset: 1, color: '#188df0' }
          ])
        },
        emphasis: {
          itemStyle: {
            color: '#2378f7'
          }
        },
        barWidth: '60%',
      }
    ]
  };

  myChart.setOption(options);
};

const resizeChart = () => {
  if (myChart) {
    myChart.resize();
  }
};


// --- Lifecycle Hooks ---
onMounted(async () => {
  fetchUserPropertySchema(); // Fetch schema on mount
  fetchAvailableEvents();
  if (userId.value) {
    await nextTick();
    initChart([]);
    fetchInitialData();
  } else {
    loadingData.value = false;
    ElMessage.error('未检测到用户ID');
  }
});

onUnmounted(() => {
  if (myChart) {
    myChart.dispose();
    myChart = null;
    window.removeEventListener('resize', resizeChart);
  }
});

// --- Watcher ---
watch(statsDateRange, (newRange, oldRange) => {
  if (newRange && newRange.length === 2 && oldRange && oldRange.length === 2 &&
    (newRange[0] !== oldRange[0] || newRange[1] !== oldRange[1])) {
    fetchEventStatistics();
  } else if (newRange && newRange.length === 2 && (!oldRange || oldRange.length !== 2)) {
    fetchEventStatistics();
  }
});


</script>

<template>
  <TopNav></TopNav>
  <div class="user-detail-container" v-loading="loadingData">
    <el-container class="detail-container">
      <el-aside width="300px" class="left-panel">
        <el-card class="box-card">
          <template #header>
            <div class="card-header">
              <span>用户属性</span>
              <span v-if="playerDataSource && playerDataSource.userId"> ({{ playerDataSource.userId }})</span>
            </div>
          </template>
          <el-scrollbar class="card-body-scrollbar" :height="playerDataSource && (playerDataSource.userProperties || formattedUserProperties.length > 0) ? 'calc(100vh - 230px)' : 'auto'">
            <div v-if="playerDataSource && (playerDataSource.userProperties || formattedUserProperties.length > 0)" class="user-properties-list">
              <div v-for="(prop, index) in formattedUserProperties" :key="index" class="property-item">
                <span class="property-label">{{ prop.label }}:</span>
                <span class="property-value">{{ prop.value }}</span>
              </div>
            </div>
            <div v-else-if="!loadingData && playerDataSource && (!playerDataSource.userProperties || formattedUserProperties.length === 0)">
              <p>该用户无用户属性。</p>
            </div>
            <div v-else-if="!loadingData && !playerDataSource">
              <p>无法加载用户属性信息。</p>
            </div>
            <div v-else>
              <p>加载中...</p>
            </div>
          </el-scrollbar>
        </el-card>
      </el-aside>

      <el-main class="right-panel">
        <el-scrollbar class="main-content-scrollbar" height="calc(100vh - 100px)">
          <el-card class="box-card statistics-card">
            <template #header>
              <div class="card-header">
                <span>用户行为总量统计</span>
              </div>
            </template>
            <div class="statistics-controls">
              <el-date-picker
                v-model="statsDateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始日期时间"
                end-placeholder="结束日期时间"
                value-format="x"
                @change="fetchEventStatistics"
                style="width: 350px;"
              />
              <el-button type="primary" :loading="loadingStats" @click="fetchEventStatistics" style="margin-left: 10px;">
                <el-icon><DataAnalysis /></el-icon> 查询统计
              </el-button>
            </div>
            <div v-loading="loadingStats" class="chart-container">
              <div ref="chartContainer" style="width: 100%; height: 300px;"></div>
              <div v-if="!loadingStats && (!eventStatistics || eventStatistics.length === 0)" class="chart-empty-message">
                <p>暂无统计数据</p>
              </div>
            </div>
          </el-card>

          <el-card class="box-card event-sequence-card">
            <template #header>
              <div class="card-header">
                <span>用户行为序列</span>
              </div>
            </template>
            <el-table
              :data="eventSequence || []"
              v-loading="loadingSequence && eventSequence.length === 0"
              style="width: 100%"
              :show-header="false"
              :row-class-name="({row}) => row.id === expandedEventId ? 'expanded-row' : ''"
            >
              <el-table-column label="事件信息">
                <template #default="scope">
                  <div @click="toggleEventDetails(scope.row.id)" class="event-sequence-item">
                    <span class="event-timestamp">{{ formatTimestamp(scope.row.timestamp) }}</span>
                    <span class="event-name">{{ displayNameByEvent(scope.row.eventName) }}</span>
                    <el-icon class="expand-icon" :class="{'is-expanded': scope.row.id === expandedEventId}">
                      <component :is="scope.row.id === expandedEventId ? ArrowDown : ArrowRight" />
                    </el-icon>
                  </div>
                  <div v-if="scope.row.id === expandedEventId" class="event-details-expanded">
                    <p><strong>事件名称:</strong> {{ displayNameByEvent(scope.row.eventName) }}</p>
                    <p><strong>用户ID:</strong> {{ scope.row.userId }}</p>
                    <p><strong>设备ID:</strong> {{ scope.row.deviceId }}</p>
                    <p><strong>时间戳:</strong> {{ formatTimestamp(scope.row.timestamp) }}</p>
                    <p><strong>是否调试事件:</strong> {{ scope.row.isDebug === 1 ? '是' : '否' }}</p>

                    <h4>事件参数 (Parameters):</h4>
                    <div v-if="parseJsonPropertiesForDisplay(scope.row.parameters).length > 0" class="property-list-inline">
                      <div v-for="(prop, propIndex) in parseJsonPropertiesForDisplay(scope.row.parameters)" :key="propIndex" class="property-item-inline">
                        <span class="property-label-inline">{{ prop.label }}:</span>
                        <span class="property-value-inline">{{ prop.value }}</span>
                      </div>
                    </div>
                    <div v-else>无参数</div>


                    <h4>事件发生时用户属性快照 (User Properties):</h4>
                    <div v-if="parseJsonPropertiesForDisplay(scope.row.userProperties).length > 0" class="property-list-inline">
                      <div v-for="(prop, propIndex) in parseJsonPropertiesForDisplay(scope.row.userProperties)" :key="'userProp'+propIndex" class="property-item-inline">
                        <span class="property-label-inline">{{ prop.label }}:</span>
                        <span class="property-value-inline">{{ prop.value }}</span>
                      </div>
                    </div>
                    <div v-else>无用户属性快照</div>


                    <div v-if="scope.row.validationError">
                      <h4>验证错误:</h4>
                      <p class="validation-error-message">{{ scope.row.validationError }}</p>
                    </div>
                  </div>
                </template>
              </el-table-column>
            </el-table>
            <div v-if="(eventSequence || []).length < eventSequencePagination.total" class="load-more-container">
              <el-button
                :loading="eventSequencePagination.loadingMore"
                @click="loadMoreEvents"
                :disabled="eventSequencePagination.loadingMore"
              >
                加载更多 (已加载 {{ eventSequence.value.length }} / {{ eventSequencePagination.total }})
              </el-button>
            </div>
            <div v-else-if="!loadingSequence && eventSequence.length > 0 && eventSequence.length === eventSequencePagination.total" class="load-more-container">
              <p class="text-center text-muted">已加载全部行为序列数据。</p>
            </div>
            <div v-else-if="!loadingSequence && eventSequence.length === 0 && !loadingData && !loadingStats" class="load-more-container">
              <p class="text-center text-muted">暂无行为序列数据。</p>
            </div>
          </el-card>
        </el-scrollbar>
      </el-main>
    </el-container>

  </div>
</template>

<style scoped>
/* Overall container and layout */
.user-detail-container {
  padding: 20px 20px 0;
  max-height: calc(100vh - 100px); /* Adjust based on TopNav height */
  display: flex; /* Use flex for main layout */
  background-color: #f0f2f5; /* Light background for the whole page */
}

.detail-container {
  flex-grow: 1; /* Allow container to fill available height */
  border-radius: 8px; /* Rounded corners for the main container */
  overflow: hidden; /* Hide overflow from rounded corners */
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05); /* Subtle shadow */
  background-color: #fff; /* White background within the container */
}

/* Panel padding and flex behavior */
.left-panel {
  padding: 20px; /* Add padding inside panels */
  padding-right: 10px; /* Adjust gap between panels */
  border-right: 1px solid #ebeef5; /* Separator line */
}

.right-panel {
  padding: 20px; /* Add padding inside panels */
  padding-left: 10px; /* Adjust gap between panels */
  flex-grow: 1; /* Right panel takes remaining space */
}

/* Card styling */
.box-card {
  margin-bottom: 20px;
  border-radius: 6px;
  box-shadow: 0 1px 6px rgba(0, 0, 0, 0.02);
}

.box-card:last-child {
  margin-bottom: 0; /* Remove bottom margin from the last card in scrollable area */
}


.card-header {
  font-size: 16px; /* Slightly smaller header font */
  font-weight: bold;
  color: #303133;
  padding-bottom: 10px; /* Space below header text */
  border-bottom: 1px solid #ebeef5; /* Separator below header */
  margin-bottom: 15px; /* Space between header and card body */
}

/* Adjust card body padding if needed (Element Plus default is 20px) */
/* .el-card__body { padding: 15px; } */


/* User Properties List (Left Panel) */
.user-properties-list {
  font-size: 14px;
}

.user-properties-list .property-item {
  margin-bottom: 10px; /* Increased space between items */
  padding-bottom: 10px;
  border-bottom: 1px dashed #dcdfe6; /* Slightly more visible separator */
  word-break: break-all;
}

.user-properties-list .property-item:last-child {
  border-bottom: none;
  margin-bottom: 0;
  padding-bottom: 0;
}

.property-label {
  font-weight: bold;
  margin-right: 8px; /* Increased space */
  color: #606266;
  display: inline-block; /* Allows better control */
  min-width: 80px; /* Optional: align labels */
  vertical-align: top; /* Align label with value */
}

.property-value {
  color: #303133;
  vertical-align: top;
}

/* Statistics Chart Controls */
.statistics-controls {
  display: flex;
  align-items: center;
  margin-bottom: 15px; /* Reduced margin */
}

.chart-container {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 15px; /* Increased padding */
  background-color: #fff;
  min-height: 320px;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;
  overflow: hidden; /* Ensure chart stays within bounds */
}

.chart-container > div[ref="chartContainer"] {
  width: 100%;
  height: 300px;
}

.chart-empty-message {
  text-align: center;
  margin-top: 50px;
  color: #909399;
  font-size: 14px;
}


/* Event Sequence List (Right Panel) */
.event-sequence-item {
  padding: 12px 5px; /* Adjusted padding */
  border-bottom: 1px solid #ebeef5;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: background-color 0.2s ease;
}

.event-sequence-item:hover {
  background-color: #f5f7fa;
}

/* Add a slight indicator for expanded items */
.el-table__row.expanded-row .event-sequence-item {
  background-color: #e6f0ff; /* Light blue background */
  border-bottom-color: transparent; /* Hide border when expanded */
}


.event-timestamp {
  font-size: 12px;
  color: #909399;
  margin-right: 15px;
  flex-shrink: 0;
}

.event-name {
  font-weight: bold;
  color: #303133;
  flex-grow: 1;
  word-break: break-all; /* Allow long event names to break */
}

.event-sequence-item .expand-icon {
  margin-left: auto;
  color: #c0c4cc;
  transition: transform 0.2s ease;
  font-size: 14px;
  flex-shrink: 0; /* Prevent icon from shrinking */
}

.event-sequence-item .expand-icon.is-expanded {
  transform: rotate(180deg);
  color: #409eff; /* Primary color when expanded */
}

/* Expanded Details Area */
.event-details-expanded {
  margin-top: 0; /* No margin above */
  padding: 15px 10px; /* Padding inside */
  background-color: #f9f9f9;
  border-top: 1px solid #eee;
  font-size: 13px;
  color: #555;
  border-bottom: 1px solid #ebeef5; /* Add bottom border */
}

.event-details-expanded p {
  margin-bottom: 8px;
  word-break: break-all;
}

.event-details-expanded h4 {
  margin-top: 15px; /* Increased margin */
  margin-bottom: 8px; /* Increased margin */
  font-size: 14px;
  color: #333;
  padding-bottom: 5px;
  border-bottom: 1px dashed #ddd; /* Separator below heading */
}
.event-details-expanded h4:first-of-type {
  margin-top: 0; /* No top margin for the first h4 */
}


/* Style for the list of properties within expanded details */
.property-list-inline {
  margin-top: 10px;
  padding-left: 10px; /* Indent the list */
  border-left: 3px solid #409eff; /* Color border for visual grouping */
  background-color: #ecf5ff; /* Light background for the list */
  padding: 10px;
  border-radius: 4px;
}

.property-list-inline .property-item-inline {
  margin-bottom: 6px; /* Adjusted margin */
  word-break: break-all;
  line-height: 1.4;
}

.property-item-inline:last-child {
  margin-bottom: 0;
}

.property-label-inline {
  font-weight: bold;
  margin-right: 8px; /* Increased space */
  color: #606266;
  display: inline-block;
  min-width: 80px; /* Optional: align labels */
  /* vertical-align: top; */
}

.property-value-inline {
  color: #303133;
  /* vertical-align: top; */
}

/* Style for the "Load More" container */
.load-more-container {
  text-align: center;
  margin-top: 20px;
  padding-top: 10px; /* Add some space */
  border-top: 1px solid #ebeef5; /* Separator line */
}

.text-center { text-align: center; }
.text-muted { color: #909399; }
.validation-error-message { color: red; word-break: break-all; font-size: 13px; margin-top: 8px;}

/* Adjust Scrollbar heights if needed */
.card-body-scrollbar, .main-content-scrollbar {
  /* You might need to fine-tune these heights based on your exact layout */
  /* Example: If TopNav is 60px, and card header + padding is ~50px, 100vh - 60 - 50 - 20(container top/bottom padding) = 100vh - 130px for left panel card body scrollbar */
  /* The current values are estimates. */
}
</style>
