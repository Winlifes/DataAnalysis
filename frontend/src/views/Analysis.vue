<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch, nextTick } from 'vue';
import TopNav from "@/components/TopNav.vue";
import {
  ElCard,
  ElIcon,
  ElButton,
  ElDatePicker,
  ElSelect,
  ElOption,
  ElForm,
  ElFormItem,
  ElInput,
  ElLoading,
  ElMessage,
  ElRow,
  ElCol,
  ElTable,
  ElTableColumn,
  ElCascader,
  ElInputNumber,
} from 'element-plus';

import * as echarts from 'echarts/core';
import {
  BarChart,
  LineChart
} from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent
} from 'echarts/components';
import {
  CanvasRenderer
} from 'echarts/renderers';

echarts.use([
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  TitleComponent,
  LegendComponent,
  DatasetComponent,
  CanvasRenderer
]);

import { DataAnalysis, Calendar, Filter, Operation, Plus, Delete } from '@element-plus/icons-vue';

import api from "@/api/index.js";

// --- State Variables ---
const loading = ref(false);
const loadingEvents = ref(false);
const loadingEventSchema = ref(false);
// State for global user property schema
const globalUserPropertySchema = ref(null); // Stores the global user property schema
// Loading state for fetching global user property schema
const loadingGlobalUserProperties = ref(false);


// Analysis Query Parameters
const analysisForm = reactive({
  dateRange: [Date.now() - 3600 * 1000 * 24 * 30, Date.now()],
  selectedEvent: '',
  calculationAttributes: [],
  groupingAttribute: '',
  globalFilters: [],
});

// Options for dropdowns
// ** UPDATED: availableEvents will now store list of EventSchema objects **
const availableEvents = ref([]); // List of all EventSchema objects from backend

const chartTypeOptions = ref([
  { label: '柱状图', value: 'bar' },
  { label: '折线图', 'value': 'line' },
  { label: '表格', value: 'table' },
]);
const selectedChartType = ref('bar');

// Analysis Results
const analysisResults = ref(null); // Stores the raw result from the backend (List<Map<String, Object>>根据后端返回的实际类型调整)

// Echarts Instance and Container
const chartContainer = ref(null); // Ref for chart DOM element
let myChart = null; // Echarts instance

const dynamicTableColumns = ref([]);
const dynamicTableData = ref([]);


// Event Schema for the selected event
const selectedEventSchema = ref(null);


// --- Computed Properties ---

const attributeOptions = computed(() => {
  const options = [];
  // Add implicit options
  options.push({ label: '事件计数', value: 'eventCount' });
  options.push({ label: '活跃用户数', value: 'uniqueUserCount' });
  // ** NEW: Add Average Count per User **
  options.push({ label: '人均次数', value: 'averageCountPerUser' });


  // Add parameter options from selected event schema
  if (selectedEventSchema.value && selectedEventSchema.value.parameterSchema) {
    try {
      const params = JSON.parse(selectedEventSchema.value.parameterSchema);
      if (typeof params === 'object' && params !== null) {
        Object.keys(params).forEach(key => {
          let label = `事件属性: ${key}`;
          const propDef = params[key];
          if (typeof propDef === 'object' && propDef !== null) {
            if (propDef.displayName) {
              label = `事件属性: ${propDef.displayName}`;
            } else {
              label = `事件属性: ${key}`; // Default if no displayName in object definition
            }
            // if (propDef.type) {
            //   label += ` (${propDef.type})`; // Add type if available
            // }

          } else if (typeof propDef === 'string') { // Handle simple string schema like "integer" or "string"
            label = `事件属性: ${key} (${propDef})`;
          } else {
            // Fallback for unexpected schema format
            label = `事件属性: ${key}`;
          }
          options.push({ label: label, value: `parameter.${key}` });
        });
      }
    } catch (e) {
      console.error('Failed to parse parameter schema:', e);
    }
  }

  // Add user property options from global user property schema
  // ** Access propertySchema field **
  if (globalUserPropertySchema.value && globalUserPropertySchema.value.propertySchema) {
    try {
      const userProps = JSON.parse(globalUserPropertySchema.value.propertySchema); // Parse the propertySchema JSON string
      if (typeof userProps === 'object' && userProps !== null) {
        Object.keys(userProps).forEach(key => {
          // Note: user property schema might contain displayName and type, use them for label
          let label = `用户属性: ${key}`;
          const propDef = userProps[key];
          if (typeof propDef === 'object' && propDef !== null) {
            if (propDef.displayName) {
              label = `用户属性: ${propDef.displayName}`;
            } else {
              label = `用户属性: ${key}`; // Default if no displayName in object definition
            }
            // if (propDef.type) {
            //   label += ` (${propDef.type})`; // Add type if available
            // }

          } else if (typeof propDef === 'string') { // Handle simple string schema like "integer" or "string"
            label = `用户属性: ${key} (${propDef})`;
          } else {
            // Fallback for unexpected schema format
            label = `用户属性: ${key}`;
          }
          options.push({ label: label, value: `userProperty.${key}` }); // Value format for backend
        });
      }
    } catch (e) {
      console.error('Failed to parse global user property schema JSON:', e);
    }
  }


  return options;
});

const calculationAttributeOptions = computed(() => {
  return attributeOptions.value;
});

const groupingAttributeOptions = computed(() => {
  const timeGranularityOptions = [
    { label: '按天', value: 'time.day' },
    { label: '按周', value: 'time.week' },
    { label: '按月', value: 'time.month' },
  ];
  // ** Filter out implicit calculations from grouping options (excluding 'averageCountPerUser' if not meant for grouping) **
  const attributeOnlyOptions = attributeOptions.value.filter(opt =>
    opt.value !== 'eventCount' &&
    opt.value !== 'uniqueUserCount' &&
    opt.value !== 'averageCountPerUser' // Assuming averageCountPerUser is not a valid grouping attribute
  );
  return [...timeGranularityOptions, ...attributeOnlyOptions];
});


// --- Methods ---

/**
 * Fetches the list of all EventSchema objects from the backend.
 * ** UPDATED: Call GET /api/schemas/events instead of /api/schemas/events/names **
 */
const fetchAvailableEvents = async () => {
  loadingEvents.value = true;
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
    loadingEvents.value = false;
  }
};

/**
 * Fetches the global user property schema from the backend.
 */
const fetchGlobalUserPropertySchema = async () => {
  loadingGlobalUserProperties.value = true;
  globalUserPropertySchema.value = null;
  try {
    // Assuming backend endpoint is GET /api/user-properties
    const response = await api.get('/api/schemas/user-properties'); // Using the provided endpoint
    globalUserPropertySchema.value = response.data;
    console.log("Fetched global user property schema:", globalUserPropertySchema.value);
  } catch (error) {
    console.error('Failed to fetch global user property schema:', error);
    ElMessage.error('加载用户属性结构失败');
    globalUserPropertySchema.value = null;
  } finally {
    loadingGlobalUserProperties.value = false;
  }
};


/**
 * Fetches the parameter and user property schema for a specific event name.
 * ** NOTE: This method still uses GET /api/schemas/events/{eventName}, assuming it exists.
 * If you only use GET /api/schemas/events, you would need to find the schema in the fetched list.**
 * @param eventName The name of the event.
 */
const fetchEventSchemaByName = async (eventName) => {
  if (!eventName) {
    selectedEventSchema.value = null;
    return;
  }
  loadingEventSchema.value = true;
  selectedEventSchema.value = null;
  analysisForm.calculationAttributes = [];
  analysisForm.groupingAttribute = '';

  try {
    // Assuming backend endpoint is GET /api/schemas/events/{eventName}
    // If this endpoint doesn't exist, you would need to search in availableEvents.value
    const response = await api.get(`/api/schemas/events/name/${eventName}`);
    selectedEventSchema.value = response.data;
    console.log(`Fetched schema for event ${eventName}:`, selectedEventSchema.value);
  } catch (error) {
    console.error(`Failed to fetch schema for event ${eventName}:`, error);
    // If the specific endpoint fails, try finding it in the full list if already fetched
    const foundSchema = availableEvents.value.find(event => event.eventName === eventName);
    if (foundSchema) {
      selectedEventSchema.value = foundSchema;
      console.warn(`Could not fetch schema by name, using schema found in full list for event ${eventName}.`);
    } else {
      ElMessage.error(`加载事件 "${eventName}" 的结构失败`);
      selectedEventSchema.value = null;
    }
  } finally {
    loadingEventSchema.value = false;
  }
};

const addGlobalFilter = () => {
  analysisForm.globalFilters.push({
    attribute: '',
    operator: '=',
    value: null,
  });
};

const removeGlobalFilter = (index) => {
  analysisForm.globalFilters.splice(index, 1);
};


const buildAnalysisQuery = () => {
  const query = {
    startTime: analysisForm.dateRange && analysisForm.dateRange.length === 2 ? analysisForm.dateRange[0] : null,
    endTime: analysisForm.dateRange && analysisForm.dateRange.length === 2 ? analysisForm.dateRange[1] : null,
    eventName: analysisForm.selectedEvent, // This will hold the eventName string
    // ** Pass all selected calculation attributes, including 'averageCountPerUser' **
    calculationAttributes: analysisForm.calculationAttributes,
    groupingAttribute: analysisForm.groupingAttribute,
    globalFilters: analysisForm.globalFilters,
  };

  if (!query.eventName) {
    return { error: '请选择一个事件进行分析' };
  }
  if (!query.startTime || !query.endTime) {
    return { error: '请选择分析的时间范围' };
  }
  if (!query.groupingAttribute) {
    return { error: '请选择一个分组方式或分组项' };
  }
  if (!query.calculationAttributes || query.calculationAttributes.length === 0) {
    return { error: '请选择至少一个计算属性或指标' };
  }
  // TODO: Add more detailed validation for filters

  return query;
};


const runAnalysis = async () => {
  const query = buildAnalysisQuery();

  if (query.error) {
    ElMessage.warning(query.error);
    return;
  }

  loading.value = true;
  analysisResults.value = null; // Clear previous results

  try {
    // Assume backend endpoint is POST /api/analysis/event and returns List<Map<String, Object>>
    const response = await api.post('/api/analysis/event', query);

    analysisResults.value = response.data;
    console.log("Analysis results:", analysisResults.value);

    // The analysisResults watcher will handle the rendering

  } catch (error) {
    console.error('Failed to run analysis:', error);
    if (error.response && error.response.headers['x-error-reason']) {
      ElMessage.error(`分析查询失败: ${error.response.headers['x-error-reason']}`);
    }
    else if (error.response && error.response.data && error.response.data.message) {
      ElMessage.error(`分析查询失败: ${error.response.data.message}`);
    }
    else {
      ElMessage.error(`分析查询失败: ${error.message || '未知错误'}`);
    }
    analysisResults.value = null; // Clear results on error
    // The analysisResults watcher will handle clearing chart/table on error

  } finally {
    loading.value = false;
  }
};


// --- Rendering Methods ---

/**
 * Renders the analysis results as a chart using Echarts.
 * @param results - The analysis results (List<Map<String, Object>>).
 * @param chartType - The type of chart ('bar' or 'line').
 * @param query - The original query definition (useful for axis labels, legends).
 */
const renderChart = (results, chartType, query) => {
  if (!chartContainer.value) {
    console.error("renderChart: Chart container element not found. Cannot render chart.");
    // This should ideally not happen with the container always in DOM and check in watcher
    return; // Cannot render if container is null
  }

  // Initialize chart if not already initialized OR if the instance is attached to a different DOM element
  // This check ensures myChart is ready and attached to chartContainer.value
  if (!myChart || myChart.getDom() !== chartContainer.value) {
    console.log("renderChart: Echarts instance not initialized or container mismatch. Initializing.");
    // Dispose previous instance if it exists on a different container or needs re-init
    if (myChart) {
      console.log("renderChart: Disposing previous Echarts instance.");
      myChart.dispose();
      window.removeEventListener('resize', resizeChart); // Remove old listener
    }
    try {
      myChart = echarts.init(chartContainer.value);
      console.log("renderChart: Echarts initialized successfully.");
      // Add resize listener only once during initial init for this container
      window.addEventListener('resize', resizeChart);
    } catch (e) {
      console.error("renderChart: Failed to initialize Echarts:", e);
      myChart = null; // Ensure myChart is null if initialization fails
      window.removeEventListener('resize', resizeChart); // Clean up listener
      return; // Cannot proceed if initialization failed
    }
  } else {
    console.log("renderChart: Echarts instance already initialized on the correct container.");
  }


  if (!results || results.length === 0) {
    console.log("renderChart: No results to display, clearing chart.");
    myChart.setOption({ series: [], xAxis: [] }); // Clear chart
    return;
  }
  console.log("renderChart: Rendering chart with results.");

  // ** Calculate 'averageCountPerUser' if selected in the query **
  const processedResults = results.map(row => {
    const newRow = { ...row }; // Create a copy to avoid modifying original results
    if (query.calculationAttributes.includes('averageCountPerUser')) {
      // Backend is adapted to return eventCount and uniqueUserCount when averageCountPerUser is requested.
      // Frontend calculates the average here.
      const eventCount = newRow['eventCount'] || 0; // Get eventCount (use 0 if null/undefined)
      const uniqueUserCount = newRow['uniqueUserCount'] || 0; // Get uniqueUserCount (use 0 if null/undefined)
      // Calculate average, handle division by zero
      newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
      // Ensure the calculated value is a number
      if(isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
        newRow['averageCountPerUser'] = 0; // Handle potential NaN/Infinity
      }
    }
    return newRow;
  });


  // Determine the grouping alias used in the backend result
  let groupingAlias = query.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + query.groupingAttribute.substring('time.'.length());
  }
  // Fallback check if generated alias doesn't exist in processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      // Filter out aliases that were originally requested calculation attributes
      !query.calculationAttributes.map(attr => {
        let alias = attr.replace('.', '_');
        if (attr === 'eventCount') alias = 'eventCount';
        if (attr === 'uniqueUserCount') alias = 'uniqueUserCount';
        if (attr === 'averageCountPerUser') alias = 'averageCountPerUser'; // Include new alias
        return alias;
      }).includes(key)
    );
    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`renderChart: Grouping alias "${query.groupingAttribute}" not found in processed results, using fallback key "${groupingAlias}".`);
    } else {
      console.error(`renderChart: Could not reliably determine grouping key alias from processed results for attribute: ${query.groupingAttribute}. Generated alias "${groupingAlias}" not found.`);
      myChart.setOption({ series: [], xAxis: [], title: { text: '数据格式错误或无分组数据', left: 'center' } });
      return;
    }
  }


  // Determine the calculation aliases to be charted (including 'averageCountPerUser' if calculated)
  const calculationAliases = query.calculationAttributes.map(attr => {
    let alias = attr.replace('.', '_');
    if (attr === 'eventCount') alias = 'eventCount';
    if (attr === 'uniqueUserCount') alias = 'uniqueUserCount';
    if (attr === 'averageCountPerUser') alias = 'averageCountPerUser'; // Use the alias we calculated with

    // Check if this alias exists in the processed row keys
    if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(alias)) {
      // Fallback: Try original attribute name if backend didn't alias or aliased differently
      if (processedResults[0].hasOwnProperty(attr)) {
        console.warn(`renderChart: Calculation alias "${alias}" generated from "${attr}" not found in processed result keys, using original attribute name "${attr}" as alias.`);
        alias = attr;
      } else {
        console.error(`renderChart: Could not reliably determine calculation alias from processed result keys for attribute: ${attr}. Generated alias "${alias}" not found.`);
        return null; // Exclude this attribute
      }
    }
    return alias;
  }).filter(alias => alias !== null); // Remove null entries


  if (calculationAliases.length === 0) {
    console.error("renderChart: No valid calculation aliases found in processed results. Cannot render chart series.");
    myChart.setOption({ series: [], xAxis: [], title: { text: '无计算属性数据', left: 'center' } });
    return;
  }


  // Extract grouping values for the X-axis from processed results
  const xAxisData = processedResults.map(row => row[groupingAlias]); // Use the determined grouping alias

  // Create series for each calculation attribute from processed results
  const series = calculationAliases.map(calcAlias => {
    const originalAttr = query.calculationAttributes.find(attr => {
      let generatedAlias = attr.replace('.', '_');
      if (attr === 'eventCount') generatedAlias = 'eventCount';
      if (attr === 'uniqueUserCount') generatedAlias = 'uniqueUserCount';
      if (attr === 'averageCountPerUser') generatedAlias = 'averageCountPerUser';
      return generatedAlias === calcAlias || attr === calcAlias; // Match generated alias or original name
    });
    // Get the label for the legend (including the new '人均次数' label)
    const legendLabel = attributeOptions.value.find(opt => opt.value === originalAttr)?.label || originalAttr || calcAlias;


    return {
      name: legendLabel,
      type: chartType,
      data: processedResults.map(row => row[calcAlias]), // Use calculated/aliased value from processedResults
      smooth: chartType === 'line',
      // Optional: Item style, emphasis, etc.
    };
  });

  const legendData = series.map(s => s.name);

  const options = {
    title: {
      text: `事件分析 (${query.eventName})`,
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: chartType === 'bar' ? 'shadow' : 'line'
      },
      // Optional: Formatter to display all values, including calculated average
      formatter: function (params) {
        let tooltipContent = `${params[0].name}<br/>`; // Grouping value
        params.forEach(param => {
          const value = typeof param.value === 'number' ? parseFloat(param.value).toFixed(2) : param.value; // Format numbers with 2 decimal places
          tooltipContent += `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${param.color};"></span>${param.seriesName}: ${value}<br/>`;
        });
        return tooltipContent;
      }
    },
    legend: {
      data: legendData,
      top: 30,
      left: 'center',
      type: 'scroll'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: xAxisData,
      axisLabel: {
        interval: 0,
        rotate: xAxisData.some(label => String(label).length > 8) ? 30 : 0
      }
    },
    yAxis: {
      type: 'value',
      name: '值',
      // Min interval is 1 for counts, but averages can be non-integers. Remove minInterval or make it conditional.
      // minInterval: 1
    },
    series: series
  };

  try {
    myChart.setOption(options, true); // Use true for `notMerge` to prevent issues with data/axis changes
    console.log("renderChart: Echarts setOption called successfully.");
  } catch (e) {
    console.error("renderChart: Failed to setOption on Echarts instance:", e);
  }
};

/**
 * Renders the analysis results as an ElTable.
 * @param results - The analysis results (List<Map<String, Object>>).
 * @param query - The original query definition (useful for column headers).
 */
const renderTable = (results, query) => {
  if (!results || results.length === 0) {
    console.log("renderTable: No results to display, clearing table.");
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
    return;
  }
  console.log("renderTable: Rendering table with results.");

  // ** Calculate 'averageCountPerUser' if selected in the query **
  const processedResults = results.map(row => {
    const newRow = { ...row };
    if (query.calculationAttributes.includes('averageCountPerUser')) {
      const eventCount = newRow['eventCount'] || 0;
      const uniqueUserCount = newRow['uniqueUserCount'] || 0;
      newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
      if(isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
        newRow['averageCountPerUser'] = 0;
      }
    }
    return newRow;
  });


  // Find the mapping from backend aliases back to frontend labels for headers
  let groupingAlias = query.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + query.groupingAttribute.substring('time.'.length());
  }
  // Fallback check against processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      !query.calculationAttributes.map(attr => {
        let alias = attr.replace('.', '_');
        if (attr === 'eventCount') alias = 'eventCount';
        if (attr === 'uniqueUserCount') alias = 'uniqueUserCount';
        if (attr === 'averageCountPerUser') alias = 'averageCountPerUser';
        return alias;
      }).includes(key)
    );
    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`renderTable: Grouping alias "${query.groupingAttribute}" not found in processed results, using fallback key "${groupingAlias}".`);
    } else {
      console.error(`renderTable: Could not reliably determine grouping key alias from processed results for attribute: ${query.groupingAttribute}. Generated alias "${groupingAlias}" not found.`);
      dynamicTableColumns.value = [];
      dynamicTableData.value = [];
      return;
    }
  }


  // Determine the calculation aliases to be displayed in the table (including 'averageCountPerUser')
  const calculationAliases = query.calculationAttributes.map(attr => {
    let alias = attr.replace('.', '_');
    if (attr === 'eventCount') alias = 'eventCount';
    if (attr === 'uniqueUserCount') alias = 'uniqueUserCount';
    if (attr === 'averageCountPerUser') alias = 'averageCountPerUser'; // Use the alias we calculated with
    // Check if this alias exists in the processed result keys
    if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(alias)) {
      if (processedResults[0].hasOwnProperty(attr)) {
        console.warn(`renderTable: Calculation alias "${alias}" generated from "${attr}" not found in processed result keys, using original attribute name "${attr}" as alias.`);
        alias = attr;
      } else {
        console.error(`renderTable: Could not reliably determine calculation alias from processed result keys for attribute: ${attr}. Generated alias "${alias}" not found.`);
        return null; // Exclude this attribute
      }
    }
    return alias;
  }).filter(alias => alias !== null);


  // Create column definitions for ElTable
  const tableColumns = [];

  // Add grouping column
  const groupingLabel = groupingAttributeOptions.value.find(opt => opt.value === query.groupingAttribute)?.label || query.groupingAttribute || groupingAlias;
  tableColumns.push({ prop: groupingAlias, label: groupingLabel, width: 180 });

  // Add calculation columns
  calculationAliases.forEach(calcAlias => {
    const originalAttr = query.calculationAttributes.find(attr => {
      let generatedAlias = attr.replace('.', '_');
      if (attr === 'eventCount') generatedAlias = 'eventCount';
      if (attr === 'uniqueUserCount') generatedAlias = 'uniqueUserCount';
      if (attr === 'averageCountPerUser') generatedAlias = 'averageCountPerUser';
      return generatedAlias === calcAlias || attr === calcAlias;
    });
    const calcLabel = attributeOptions.value.find(opt => opt.value === originalAttr)?.label || originalAttr || calcAlias;
    // ** Add formatter for 'averageCountPerUser' to display with decimals **
    if (calcAlias === 'averageCountPerUser') {
      tableColumns.push({
        prop: calcAlias,
        label: calcLabel,
        formatter: (row, column, cellValue) => {
          return typeof cellValue === 'number' ? parseFloat(cellValue).toFixed(2) : cellValue; // Format numbers with 2 decimal places
        }
      });
    } else {
      tableColumns.push({ prop: calcAlias, label: calcLabel });
    }
  });

  dynamicTableColumns.value = tableColumns;
  dynamicTableData.value = processedResults; // Use processedResults for table data
};


const resizeChart = () => {
  if (myChart && myChart.getDom()) { // Check if myChart exists and has a DOM element
    console.log("Resizing chart.");
    myChart.resize();
  }
};

// --- Initialization ---
// initChart is now primarily called from renderChart when needed
const initChart = () => {
  // This function's primary purpose is now just to serve as a place
  // for the myChart initialization logic that can be called by renderChart.
  // The actual initialization happens within renderChart after container check.
  console.log("initChart called. Container:", chartContainer.value, "Instance:", myChart);
};


// --- Lifecycle Hook ---
onMounted(async () => {
  console.log("Component mounted.");
  // ** UPDATED: Fetch all EventSchema objects instead of just names **
  fetchAvailableEvents();
  // Fetch global user property schema on mount
  fetchGlobalUserPropertySchema();
  // Initial chart rendering will be handled by the analysisResults watcher
  // when the initial data (empty or from saved state) is processed, or when
  // the first analysis results arrive.
});

onUnmounted(() => {
  console.log("Component unmounted.");
  if (myChart) {
    console.log("Disposing Echarts instance on unmount.");
    myChart.dispose();
    myChart = null;
    window.removeEventListener('resize', resizeChart);
    console.log("Echarts disposed.");
  }
});


// --- Watcher ---
// Watch for changes in the selected event to fetch its schema
watch(() => analysisForm.selectedEvent, (newValue, oldValue) => {
  console.log(`Selected event changed from "${oldValue}" to "${newValue}"`);
  if (newValue && newValue !== oldValue) {
    // ** fetchEventSchemaByName is still needed to get parameterSchema for attributeOptions **
    fetchEventSchemaByName(newValue);
  } else if (!newValue) {
    selectedEventSchema.value = null;
    analysisForm.calculationAttributes = [];
    analysisForm.groupingAttribute = '';
  }
  // Clear results and displays when event changes
  analysisResults.value = null;
  // Clearing display will be handled by analysisResults watcher when it becomes null
  // if (myChart) myChart.setOption({ series: [], xAxis: [] }); // Redundant
  // dynamicTableColumns.value = []; // Redundant
  // dynamicTableData.value = []; // Redundant
});

// Watch for changes in chart type to switch rendering mode
watch(selectedChartType, async (newValue, oldValue) => {
  console.log(`Chart type changed from ${oldValue} to ${newValue}`);
  if (newValue !== oldValue) {
    // Wait for nextTick to ensure Vue has potentially swapped v-show elements
    await nextTick();
    console.log(`After nextTick in selectedChartType watcher. chartContainer.value: ${chartContainer.value}`);

    if (newValue !== 'table') {
      // Switching to chart type (bar or line)
      // The analysisResults watcher will handle initialization and rendering if results exist
      console.log("Chart type switched to chart. analysisResults:", analysisResults.value);
      if (analysisResults.value && analysisResults.value.length > 0) {
        console.log("Results exist, triggering render via analysisResults watcher logic.");
        // Manually trigger the analysisResults watcher logic if results are already there
        // This ensures initialization and rendering logic from that watcher is used.
        const currentResults = analysisResults.value;
        analysisResults.value = null; // Temporarily clear to force re-trigger
        await nextTick(); // Wait for the clear to potentially update DOM/refs
        analysisResults.value = currentResults; // Restore results to trigger rendering
      } else {
        // If switching to chart but no results, clear chart if instance exists
        console.log("Chart type switched to chart, no results. Clearing chart if instance exists.");
        if (myChart) myChart.setOption({ series: [], xAxis: [] });
      }

    } else {
      // Switching to table
      // Dispose chart if it exists
      if (myChart) {
        console.log("Chart type switched to table, disposing Echarts instance.");
        myChart.dispose();
        myChart = null;
        window.removeEventListener('resize', resizeChart);
      }
      // The analysisResults watcher will handle rendering the table if results exist
      console.log("Chart type switched to table. analysisResults:", analysisResults.value);
      if (analysisResults.value && analysisResults.value.length > 0) {
        console.log("Results exist, triggering render via analysisResults watcher logic.");
        // Manually trigger the analysisResults watcher logic if results are already there
        const currentResults = analysisResults.value;
        analysisResults.value = null; // Temporarily clear to force re-trigger
        await nextTick(); // Wait for the clear to potentially update DOM/refs
        analysisResults.value = currentResults; // Restore results to trigger rendering
      } else {
        // If switching to table but no results, clear table columns/data
        console.log("Chart type switched to table, no results. Clearing table data/columns.");
        dynamicTableColumns.value = [];
        dynamicTableData.value = [];
      }
    }
  }
});

// Watch for analysisResults changes to trigger rendering of the CURRENTLY selected type
watch(analysisResults, async (newResults, oldResults) => {
  console.log(`Analysis results watcher triggered. Results count: ${newResults ? newResults.length : 0}. Current chart type: ${selectedChartType.value}`);

  // Always clear displays initially when results change (before potential re-render)
  if (selectedChartType.value !== 'table') {
    if (myChart) myChart.setOption({ series: [], xAxis: [] });
  } else {
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
  }


  // Only proceed to render if new results are populated
  if (newResults && newResults.length > 0) {
    // Wait for nextTick to ensure the correct container is in the DOM and visible (if chart)
    await nextTick();
    console.log(`After nextTick in analysisResults watcher (results populated). chartContainer.value: ${chartContainer.value}`);

    const query = buildAnalysisQuery(); // Get current query for rendering

    if (selectedChartType.value !== 'table') {
      // If current type is chart, render chart
      // ** Explicitly wait for chartContainer.value to be non-null **
      let attempts = 0;
      const maxAttempts = 10; // Try up to 10 times
      const delay = 50; // Wait 50ms between attempts

      while (!chartContainer.value && attempts < maxAttempts) {
        console.warn(`analysisResults watcher: chartContainer.value is null (Attempt ${attempts + 1}/${maxAttempts}). Waiting...`);
        await new Promise(resolve => setTimeout(resolve, delay));
        await nextTick(); // Wait for Vue's next DOM update cycle
        attempts++;
      }

      if (chartContainer.value) {
        console.log("analysisResults watcher: chartContainer.value is now available. Proceeding with chart render.");
        // renderChart will handle initialization if necessary
        renderChart(newResults, selectedChartType.value, query);
      } else {
        console.error(`analysisResults watcher: chartContainer.value is still null after ${maxAttempts} attempts. Cannot render chart.`);
        // If we still fail, ensure chart is cleared if an instance exists
        if(myChart) myChart.setOption({ series: [], xAxis: [] });
        ElMessage.error("无法显示图表，图表容器未准备就绪。");
      }

    } else {
      // If current type is table, render table immediately after nextTick
      console.log("analysisResults watcher: Results available, type is table. Rendering table.");
      renderTable(newResults, query);
    }
  } else {
    // Results are null or empty. Displays are already cleared at the start of the watcher.
    console.log("analysisResults watcher: Results are null or empty. Displays cleared.");
  }
}, { deep: true });


</script>

<template>
  <TopNav />

  <el-main class="analysis-main">
    <el-card class="analysis-card" shadow="hover">
      <template #header>
        <div class="analysis-card-header">
          <h2>事件分析</h2>
        </div>
      </template>



      <el-form :model="analysisForm" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="时间范围">
              <el-date-picker
                v-model="analysisForm.dateRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始日期时间"
                end-placeholder="结束日期时间"
                value-format="x"
                style="width: 100%;"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="选择事件">
              <el-select
                v-model="analysisForm.selectedEvent"
                placeholder="请选择要分析的事件"
                style="width: 100%;"
                filterable
                clearable
                :loading="loadingEvents"
              >
                <el-option
                  v-for="item in availableEvents"
                  :key="item.eventName"
                  :label="item.displayName || item.eventName" :value="item.eventName" ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <template v-if="analysisForm.selectedEvent && !loadingEventSchema && selectedEventSchema">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="计算属性">
                <el-select
                  v-model="analysisForm.calculationAttributes"
                  multiple
                  placeholder="请选择要计算的属性/指标"
                  style="width: 100%;"
                  filterable
                >
                  <el-option
                    v-for="attr in calculationAttributeOptions"
                    :key="attr.value"
                    :label="attr.label"
                    :value="attr.value"
                  ></el-option>
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="分组项">
                <el-select
                  v-model="analysisForm.groupingAttribute"
                  placeholder="请选择分组方式或分组项"
                  style="width: 100%;"
                  filterable
                  clearable
                >
                  <el-option
                    v-for="attr in groupingAttributeOptions"
                    :key="attr.value"
                    :label="attr.label"
                    :value="attr.value"
                  ></el-option>
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="全局筛选">
            <div class="filter-builder-container">
              <div v-for="(filter, index) in analysisForm.globalFilters" :key="index" class="filter-item">
                <el-select v-model="filter.attribute" placeholder="属性" style="width: 180px; margin-right: 10px;" filterable size="small">
                  <el-option
                    v-for="attr in attributeOptions.filter(opt => !opt.value.startsWith('eventCount') && !opt.value.startsWith('uniqueUserCount') && opt.value !== 'averageCountPerUser')"
                  :key="attr.value"
                  :label="attr.label"
                  :value="attr.value"
                  ></el-option>
                </el-select>
                <el-select v-model="filter.operator" placeholder="操作符" style="width: 100px; margin-right: 10px;" size="small">
                  <el-option label="=" value="="></el-option>
                  <el-option label="!=" value="!="></el-option>
                  <el-option label=">" value=">"></el-option>
                  <el-option label="<" value="<"></el-option>
                  <el-option label=">=" value=">="></el-option>
                  <el-option label="<=" value="<="></el-option>
                  <el-option label="包含" value="contains"></el-option>
                  <el-option label="不包含" value="not contains"></el-option>
                  <el-option label="为空" value="isNull"></el-option>
                  <el-option label="不为空" value="isNotNull"></el-option>
                </el-select>
                <el-input v-model="filter.value" placeholder="值" style="width: 150px; margin-right: 10px;" size="small"
                          :disabled="filter.operator === 'isNull' || filter.operator === 'isNotNull'"
                ></el-input>
                <el-button type="danger" :icon="Delete" circle size="small" @click="removeGlobalFilter(index)"></el-button>
              </div>
              <el-button type="primary" link :icon="Plus" @click="addGlobalFilter">添加筛选条件</el-button>
            </div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="计算属性">
            <el-select placeholder="请先选择一个事件" disabled style="width: 100%;"></el-select>
          </el-form-item>
          <el-form-item label="分组项">
            <el-select placeholder="请先选择一个事件" disabled style="width: 100%;"></el-select>
          </el-form-item>
          <el-form-item label="全局筛选">
            <div class="filter-builder-container">
              <p style="color: #909399; text-align: center;">请先选择一个事件来定义筛选条件</p>
            </div>
            </el-form-item>
            <el-form-item v-if="loadingEventSchema" label="">
              <span style="color: #909399;">加载事件结构中...</span>
            </el-form-item>
        </template>


        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="图表类型">
              <el-select v-model="selectedChartType" placeholder="请选择图表类型" style="width: 100%;">
                <el-option
                  v-for="item in chartTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                ></el-option>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="">
              <el-button type="primary" :icon="DataAnalysis" :loading="loading || loadingEventSchema" @click="runAnalysis" style="width: 150px;"
                         :disabled="loadingEventSchema || !analysisForm.selectedEvent || !analysisForm.groupingAttribute || analysisForm.calculationAttributes.length === 0"
              >
                运行分析
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>

      </el-form>
    </el-card>

    <el-card class="box-card analysis-results-card" v-loading="loading">
      <template #header>
        <div class="card-header">
          <span>分析结果</span>
        </div>
      </template>
      <div ref="chartContainer" style="width: 100%; height: 400px;" v-show="selectedChartType !== 'table'">
<!--        <p v-show="!loading && selectedChartType !== 'table' && (!analysisResults || analysisResults.length === 0)" class="chart-empty-message">运行分析后将在此显示图表结果</p>-->
      </div>
      <div v-show="selectedChartType === 'table' && analysisResults && analysisResults.length !== 0">
        <el-table :data="dynamicTableData" style="width: 100%">
          <template v-for="column in dynamicTableColumns" :key="column.prop">
            <el-table-column
              :prop="column.prop"
              :label="column.label"
              :width="column.width"
              :formatter="column.formatter"
            ></el-table-column>
          </template>
          <template #empty>
            <span v-if="loading">加载中...</span>
            <span v-else>无数据或列定义错误</span>
          </template>
        </el-table>
      </div>
    </el-card>

  </el-main>
</template>

<style scoped>
.analysis-main {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px); /* Adjust min-height considering TopNav */
}
.analysis-card {
  margin-bottom: 20px; /* Added margin-bottom for separation */
  padding: 16px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05); /* Softer shadow */
}
.analysis-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 18px; /* Match card header font size */
  font-weight: bold; /* Match card header font weight */
  color: #303133; /* Match card header color */
  padding-bottom: 10px; /* Match card header padding */
  border-bottom: 1px solid #ebeef5; /* Match card header border */
  margin-bottom: 15px; /* Match card header margin */
}
/* Removed the duplicate .card-header and h2 styles below if analysis-card-header is used */

h2 {
    font-size: 22px;
    color: #303133;
    margin-bottom: 20px;
}

.box-card {
    margin-bottom: 20px;
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 15px;
}



.el-form-item {
  margin-bottom: 18px;
}

.el-row .el-col .el-form-item:last-child {
  margin-bottom: 0;
}

/* Filter Builder Styling */
.filter-builder-container {
  border: 1px dashed #dcdfe6; /* Match Element Plus border color */
  padding: 15px;
  width: 100%;
  border-radius: 4px;
  background-color: #f4f4f5; /* Softer background color */
  margin-bottom: 18px; /* Add margin below the container */
}

.filter-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.filter-item:last-child {
  margin-bottom: 0;
}

.filter-item .el-select,
.filter-item .el-input,
.filter-item .el-input_number,
.filter-item .el-cascader {
  margin-right: 10px;
}

/* Style for the add filter button */
.filter-builder-container > .el-button {
  margin-top: 5px; /* Add a small margin above the button */
}


/* Analysis Results Placeholder/Container */
.analysis-results-card .el-card__body {
  min-height: 200px;
  padding: 15px;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  justify-content: flex-start;
}

/* Style for chart container when visible */
div[ref="chartContainer"] {
  width: 100%;
  height: 400px;
  /* Remove display: flex, align-items, justify-content here so Echarts can use the space */
  /* These will be handled by the empty message paragraph inside */
  /* display: flex; */
  /* align-items: center; */
  /* justify-content: center; */
  border: 1px dashed #dcdfe6; /* Match Element Plus border color */
  border-radius: 4px;
  /* Removed color and font-size as these are for the empty message */
  /* color: #909399; */
  /* font-size: 14px; */
  overflow: hidden;
  position: relative; /* Needed for absolute positioning of empty message */
}

/* Remove placeholder border when chart is rendered */
div[ref="chartContainer"]:has(canvas),
div[ref="chartContainer"]:has(svg) {
  border: none;
}

/* Style for empty message inside chart container */
.chart-empty-message {
  text-align: center;
  width: 100%; /* Center text horizontally */
  position: absolute; /* Position over the empty chart area */
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  color: #909399; /* Match placeholder text color */
  font-size: 14px;
}


/* Style for table when empty */
.el-table {
  min-height: 200px;
}
/* Adjust ElTable__empty-block if needed */
.el-table__empty-block {
  /* Adjust alignment if needed, default is centered */
  /* justify-content: center; */
  /* align-items: center; */
}

/* Optional: Style for the analysis results header title */
.analysis-results-card .card-header span {
  /* Add specific styling if different from analysis-card-header */
}

</style>
