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
  ElCheckbox // Import ElCheckbox
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
  // ** UPDATED: Use a new structure for calculations **
  calculations: [], // Array of { attribute: string, aggregationType: string }
  // ** Add state for implicit metrics **
  includeEventCount: true,
  includeUniqueUserCount: true,
  includeAverageCountPerUser: false, // Derived metric calculated on frontend
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

// This computed property now provides options for selecting the *attribute* part of a calculation
const attributeOptions = computed(() => {
  const options = [];

  // Add parameter options from selected event schema
  if (selectedEventSchema.value && selectedEventSchema.value.parameterSchema) {
    try {
      const params = JSON.parse(selectedEventSchema.value.parameterSchema);
      if (typeof params === 'object' && params !== null) {
        Object.keys(params).forEach(key => {
          // Store type information if available in schema
          const paramDef = params[key];
          let type = typeof paramDef === 'string' ? paramDef : (paramDef && paramDef.type ? paramDef.type : 'unknown');
          options.push({ label: `参数: ${key}`, value: `parameter.${key}`, type: type });
        });
      }
    } catch (e) {
      console.error('Failed to parse parameter schema:', e);
    }
  }

  // Add user property options from global user property schema
  if (globalUserPropertySchema.value && globalUserPropertySchema.value.propertySchema) {
    try {
      const userProps = JSON.parse(globalUserPropertySchema.value.propertySchema); // Parse the propertySchema JSON string
      if (typeof userProps === 'object' && userProps !== null) {
        Object.keys(userProps).forEach(key => {
          const propDef = userProps[key];
          let label = `用户属性: ${key}`;
          let type = 'unknown';

          if (typeof propDef === 'object' && propDef !== null) {
            if (propDef.displayName) {
              label = `用户属性: ${propDef.displayName} (${key})`;
            }
            if (propDef.type) {
              type = propDef.type;
            }
          } else if (typeof propDef === 'string') { // Handle simple string schema like "integer" or "string"
            label = `用户属性: ${key} (${propDef})`;
            type = propDef;
          } else {
            // Fallback for unexpected schema format
            label = `用户属性: ${key}`;
          }
          options.push({ label: label, value: `userProperty.${key}`, type: type }); // Include type in the option object
        });
      }
    } catch (e) {
      console.error('Failed to parse global user property schema JSON:', e);
    }
  }

  // Filter out attributes that are primarily for grouping/filtering or implicit counts
  // Add special handling for userId/deviceId if they can be aggregated (e.g., distinct count)
  return options.filter(opt =>
      opt.value !== 'eventCount' &&
      opt.value !== 'uniqueUserCount' &&
      opt.value !== 'averageCountPerUser' // These are handled by checkboxes
    // && opt.value !== 'userId' // Keep userId if we want to count distinct users by user ID field
    // && opt.value !== 'deviceId' // Keep deviceId if we want to count distinct devices by device ID field
  );
});

// Helper function to get the data type of an attribute
const getAttributeType = (attributePath) => {
  // Check implicit counts and derived metric first (though they are filtered from selectableAttributes)
  if (attributePath === 'eventCount') return 'number'; // Conceptual type
  if (attributePath === 'uniqueUserCount') return 'number'; // Conceptual type
  if (attributePath === 'averageCountPerUser') return 'number'; // Conceptual type (frontend calculated)
  if (attributePath === 'userId') return 'string'; // userId is typically a string ID
  if (attributePath === 'deviceId') return 'string'; // deviceId is typically a string ID


  // Lookup in selected event parameters
  if (attributePath.startsWith('parameter.') && selectedEventSchema.value && selectedEventSchema.value.parameterSchema) {
    const paramName = attributePath.substring(10);
    try {
      const params = JSON.parse(selectedEventSchema.value.parameterSchema);
      const paramDef = params[paramName];
      if (typeof paramDef === 'object' && paramDef !== null && paramDef.type) {
        return paramDef.type.toLowerCase(); // e.g., "integer", "string", "boolean"
      } else if (typeof paramDef === 'string') {
        return paramDef.toLowerCase(); // e.g., "integer", "string"
      }
    } catch (e) {
      console.error('Failed to parse parameter schema for type lookup:', e);
    }
  }

  // Lookup in global user properties
  if (attributePath.startsWith('userProperty.') && globalUserPropertySchema.value && globalUserPropertySchema.value.propertySchema) {
    const propName = attributePath.substring(13);
    try {
      const userProps = JSON.parse(globalUserPropertySchema.value.propertySchema);
      const propDef = userProps[propName];
      if (typeof propDef === 'object' && propDef !== null && propDef.type) {
        return propDef.type.toLowerCase();
      } else if (typeof propDef === 'string') {
        return propDef.toLowerCase();
      }
    } catch (e) {
      console.error('Failed to parse user property schema for type lookup:', e);
    }
  }

  // Default or fallback type
  return 'string'; // Default to string if type is unknown
};


// Helper function to get available aggregation options for a given attribute type
const getAggregationOptions = (attributePath) => {
  const type = getAttributeType(attributePath);
  const options = [
    { label: '去重数', value: 'distinctCount' }
  ];

  // Assuming numeric types include: integer, long, float, double, number
  if (['integer', 'long', 'float', 'double', 'number'].includes(type)) {
    options.unshift( // Add to the beginning
      { label: '总和', value: 'sum' },
      { label: '均值', value: 'avg' },
      { label: '最大值', value: 'max' },
      { label: '最小值', value: 'min' }
      // Median might be complex in SQL, skip for now
      // { label: '中位数', value: 'median' }
    );
    // '人均值' is a special derived metric, not a standard aggregation on a single attribute.
    // We'll handle it via the checkbox, not as an aggregation option here.
  }

  // Special case: userId and deviceId might primarily support distinct count
  if (attributePath === 'userId' || attributePath === 'deviceId') {
    // Ensure distinctCount is the primary option, potentially remove sum/avg etc if type is string
    // Based on the type lookup, userId/deviceId are strings, so only distinctCount will be added
    // If we wanted to allow other aggregations on them (e.g., if they could be numeric IDs),
    // we would need to adjust the type logic or add specific checks here.
  }


  return options;
};

// Add a new calculation item to the form
const addCalculation = () => {
  analysisForm.calculations.push({
    attribute: '', // Selected attribute path
    aggregationType: '', // Selected aggregation type (e.g., 'sum', 'distinctCount')
  });
};

// Remove a calculation item from the form
const removeCalculation = (index) => {
  analysisForm.calculations.splice(index, 1);
};


const calculationAttributeOptions = computed(() => {
  // This computed property is no longer directly used for the main calculation select.
  // It might still be useful for displaying selected calculations or generating aliases.
  // For now, it can return the full list including implicit counts/derived metrics.
  const options = [];
  options.push({ label: '事件计数', value: 'eventCount' });
  options.push({ label: '活跃用户数', value: 'uniqueUserCount' });
  options.push({ label: '人均次数', value: 'averageCountPerUser' });

  // Add attributes with aggregation types - this is the new structure
  analysisForm.calculations.forEach(calc => {
    if (calc.attribute && calc.aggregationType) {
      const attrOption = attributeOptions.value.find(opt => opt.value === calc.attribute);
      const attributeLabel = attrOption ? attrOption.label.replace(/^(参数|用户属性): /, '') : calc.attribute; // Get base name/label
      const aggregationLabel = getAggregationOptions(calc.attribute).find(agg => agg.value === calc.aggregationType)?.label || calc.aggregationType;
      // Generate a unique value/alias for this calculation
      const alias = `${calc.attribute.replace('.', '_')}_${calc.aggregationType}`;
      options.push({ label: `${attributeLabel} (${aggregationLabel})`, value: alias, originalAttribute: calc.attribute, aggregationType: calc.aggregationType });
    }
  });


  return options;
});


const groupingAttributeOptions = computed(() => {
  const timeGranularityOptions = [
    { label: '按天', value: 'time.day' },
    { label: '按周', value: 'time.week' },
    { label: '按月', value: 'time.month' },
  ];
  // Grouping can be by attribute (parameter, user property, userId, deviceId) or time.
  // Filter out implicit counts and derived metrics.
  const attributeOnlyOptions = attributeOptions.value.filter(opt =>
    opt.value !== 'eventCount' &&
    opt.value !== 'uniqueUserCount' &&
    opt.value !== 'averageCountPerUser'
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
    // ** Clear existing calculations when event changes **
    analysisForm.calculations = [];
    analysisForm.includeEventCount = true; // Reset implicit counts
    analysisForm.includeUniqueUserCount = true;
    analysisForm.includeAverageCountPerUser = false;
    return;
  }
  loadingEventSchema.value = true;
  selectedEventSchema.value = null;
  // ** Clear existing calculations when event changes **
  analysisForm.calculations = [];
  analysisForm.includeEventCount = true; // Reset implicit counts
  analysisForm.includeUniqueUserCount = true;
  analysisForm.includeAverageCountPerUser = false;
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
    // ** UPDATED: Build calculationAttributes list from new state structure **
    calculationAttributes: [],
    groupingAttribute: analysisForm.groupingAttribute,
    globalFilters: analysisForm.globalFilters,
  };

  // Add implicit metrics if selected
  if (analysisForm.includeEventCount) {
    query.calculationAttributes.push('eventCount'); // Backend alias
  }
  if (analysisForm.includeUniqueUserCount || analysisForm.includeAverageCountPerUser) { // UniqueUserCount needed for Average
    // Backend needs uniqueUserCount to calculate average, so request it if either is needed
    query.calculationAttributes.push('uniqueUserCount'); // Backend alias
  }
  // Note: 'averageCountPerUser' is calculated on frontend, but frontend tells backend it was 'requested'
  // by including eventCount and uniqueUserCount. The frontend rendering logic then calculates the average.
  // If backend was calculating it, we'd send something like { name: 'averageCountPerUser' } in the DTO.
  // Given the previous backend implementation, sending the raw counts and calculating on frontend is simpler.
  // However, if backend expects 'averageCountPerUser' in the list to trigger its own calculation, adjust here.
  // Let's assume for now, backend just needs eventCount and uniqueUserCount if frontend wants average.
  // The frontend calculation logic will use the received eventCount and uniqueUserCount.
  // If the backend was adapted to accept 'averageCountPerUser' and return it, we would add it here:
  // if (analysisForm.includeAverageCountPerUser) {
  //      query.calculationAttributes.push('averageCountPerUser'); // Backend alias
  // }


  // Add selected attribute aggregations
  analysisForm.calculations.forEach(calc => {
    if (calc.attribute && calc.aggregationType) {
      // Generate an alias for the backend, e.g., parameter_level_sum
      const alias = `${calc.attribute}@${calc.aggregationType}`;
      query.calculationAttributes.push(alias); // Add generated alias to the list
      // Backend will need to know the original attribute and aggregation type based on this alias
      // or the DTO structure needs to change. Let's assume backend can parse alias or DTO changes.
      // If DTO changes, we would add { attribute: calc.attribute, aggregationType: calc.aggregationType }
      // to a list of 'attributeAggregations' in the query DTO.
    }
  });


  if (!query.eventName) {
    return { error: '请选择一个事件进行分析' };
  }
  if (!query.startTime || !query.endTime) {
    return { error: '请选择分析的时间范围' };
  }
  if (!query.groupingAttribute) {
    return { error: '请选择一个分组方式或分组项' };
  }
  // Check if at least one calculation is selected (either implicit or attribute aggregation)
  if (query.calculationAttributes.length === 0 && !analysisForm.includeEventCount && !analysisForm.includeUniqueUserCount && !analysisForm.includeAverageCountPerUser) {
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
    return;
  }

  // Initialize chart if not already initialized OR if the instance is attached to a different DOM element
  if (!myChart || myChart.getDom() !== chartContainer.value) {
    console.log("renderChart: Echarts instance not initialized or container mismatch. Initializing.");
    if (myChart) {
      console.log("renderChart: Disposing previous Echarts instance.");
      myChart.dispose();
      window.removeEventListener('resize', resizeChart);
    }
    try {
      myChart = echarts.init(chartContainer.value);
      console.log("renderChart: Echarts initialized successfully.");
      window.addEventListener('resize', resizeChart);
    } catch (e) {
      console.error("renderChart: Failed to initialize Echarts:", e);
      myChart = null;
      window.removeEventListener('resize', resizeChart);
      return;
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
    // Check if 'averageCountPerUser' checkbox was selected in the form state
    if (analysisForm.includeAverageCountPerUser) {
      // Backend is adapted to return eventCount and uniqueUserCount when averageCountPerUser is effectively requested.
      // Frontend calculates the average here.
      const eventCount = newRow['eventCount'] || 0;
      const uniqueUserCount = newRow['uniqueUserCount'] || 0;
      newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
      if(isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
        newRow['averageCountPerUser'] = 0;
      }
    }

    // Process other calculated attributes if needed, matching backend aliases
    // If backend aliases are complex (e.g., parameter_level_sum), the frontend
    // needs to know these aliases to retrieve data from the row.
    // The alias generation logic in buildAnalysisQuery must match backend's output.

    return newRow;
  });


  // Determine the grouping alias used in the backend result
  let groupingAlias = query.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + query.groupingAttribute.substring(5);
  }
  // Fallback check if generated alias doesn't exist in processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    // Need a way to reliably get the grouping alias from the results
    // If the first column is always the grouping column, could use Object.keys(processedResults[0])[0]
    // Otherwise, needs a clear mapping from groupingAttribute to backend alias.
    // Assuming backend returns the alias generated by buildAnalysisQuery if no custom logic
    // This fallback logic might need adjustment based on actual backend response structure.
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      // Filter out keys that match expected calculation aliases
      !query.calculationAttributes.includes(key) // Check against the list of aliases sent to backend
    );

    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`renderChart: Grouping alias "${query.groupingAttribute}" not found in processed results keys, using fallback key "${groupingAlias}" (likely the first column).`);
    } else if (processedResults.length > 0) {
      // If multiple keys remain or none, and results exist, assume first key is grouping. Risky.
      groupingAlias = Object.keys(processedResults[0])[0];
      console.warn(`renderChart: Could not reliably determine grouping key alias. Using first column key "${groupingAlias}".`);

    } else {
      console.error(`renderChart: Could not determine grouping key alias from processed results for attribute: ${query.groupingAttribute}. No results or key not found.`);
      myChart.setOption({ series: [], xAxis: [], title: { text: '数据格式错误或无分组数据', left: 'center' } });
      return;
    }
  }
  console.log("renderChart: Determined grouping alias:", groupingAlias);


  // Determine the aliases to be charted (including implicit and aggregated)
  // This list should match the keys we expect in processedResults.
  const calculationAliases = [];
  if (analysisForm.includeEventCount) calculationAliases.push('eventCount');
  if (analysisForm.includeUniqueUserCount) calculationAliases.push('uniqueUserCount'); // Needs to be included if average is selected
  if (analysisForm.includeAverageCountPerUser) calculationAliases.push('averageCountPerUser'); // This is the calculated key on frontend

  // Add aliases for attribute aggregations from the form state
  analysisForm.calculations.forEach(calc => {
    if (calc.attribute && calc.aggregationType) {
      const alias = `${calc.attribute.replace('.','_')}`;
      calculationAliases.push(alias); // Use the generated alias
    }
  });

  console.log("renderChart: Calculation aliases:", calculationAliases);
  console.log("renderChart: Processed results:", processedResults);

  // Filter out aliases that are not actually present in the results (can happen with backend issues)
  const validCalculationAliases = calculationAliases.filter(alias =>
    processedResults.length > 0 && processedResults[0].hasOwnProperty(alias)
  );

  if (validCalculationAliases.length === 0) {
    console.error("renderChart: No valid calculation aliases found in processed results. Cannot render chart series.");
    myChart.setOption({ series: [], xAxis: [], title: { text: '无计算属性数据或别名不匹配', left: 'center' } });
    return;
  }
  console.log("renderChart: Valid calculation aliases for series:", validCalculationAliases);


  // Extract grouping values for the X-axis from processed results
  const xAxisData = processedResults.map(row => row[groupingAlias]); // Use the determined grouping alias

  // Create series for each calculation attribute from processed results
  const series = validCalculationAliases.map(calcAlias => {
    // Find the corresponding label for the legend
    let legendLabel = calcAlias; // Default label is the alias

    // Try to find the original calculation definition to get a human-readable label
    if (calcAlias === 'eventCount') {
      legendLabel = '事件计数';
    } else if (calcAlias === 'uniqueUserCount') {
      legendLabel = '活跃用户数';
    } else if (calcAlias === 'averageCountPerUser') {
      legendLabel = '人均次数';
    } else {
      // Look up in analysisForm.calculations to find the original attribute and aggregation type
      const matchingCalc = analysisForm.calculations.find(calc =>
        `${calc.attribute.replace('.', '_')}_${calc.aggregationType}` === calcAlias
      );
      if (matchingCalc) {
        const attrOption = attributeOptions.value.find(opt => opt.value === matchingCalc.attribute);
        const attributeLabel = attrOption ? attrOption.label.replace(/^(参数|用户属性): /, '') : matchingCalc.attribute;
        const aggregationLabel = getAggregationOptions(matchingCalc.attribute).find(agg => agg.value === matchingCalc.aggregationType)?.label || matchingCalc.aggregationType;
        legendLabel = `${attributeLabel} (${aggregationLabel})`;
      }
    }


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
      // Remove minInterval for counts as averages are non-integers
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


  // ** Calculate 'averageCountPerUser' if selected in the form state **
  const processedResults = results.map(row => {
    const newRow = { ...row };
    if (analysisForm.includeAverageCountPerUser) {
      const eventCount = newRow['eventCount'] || 0;
      const uniqueUserCount = newRow['uniqueUserCount'] || 0;
      newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
      if(isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
        newRow['averageCountPerUser'] = 0;
      }
    }
    // Process other calculated attributes if needed, matching backend aliases
    // If backend aliases are complex (e.g., parameter_level_sum), the frontend
    // needs to know these aliases to retrieve data from the row.
    // The alias generation logic in buildAnalysisQuery must match backend's output.

    return newRow;
  });


  // Find the mapping from backend aliases back to frontend labels for headers
  let groupingAlias = query.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + query.groupingAttribute.substring(5);
  }
  // Fallback check against processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    // Need a way to reliably get the grouping alias from the results
    // If the first column is always the grouping column, could use Object.keys(processedResults[0])[0]
    // Otherwise, needs a clear mapping from groupingAttribute to backend alias.
    // Assuming backend returns the alias generated by buildAnalysisQuery if no custom logic
    // This fallback logic might need adjustment based on actual backend response structure.
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      !query.calculationAttributes.includes(key) // Check against the list of aliases sent to backend
    );

    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`renderTable: Grouping alias "${query.groupingAttribute}" not found in processed results keys, using fallback key "${groupingAlias}" (likely the first column).`);
    } else if (processedResults.length > 0) {
      // If multiple keys remain or none, and results exist, assume first key is grouping. Risky.
      groupingAlias = Object.keys(processedResults[0])[0];
      console.warn(`renderTable: Could not reliably determine grouping key alias. Using first column key "${groupingAlias}".`);

    } else {
      console.error(`renderTable: Could not determine grouping key alias from processed results for attribute: ${query.groupingAttribute}. No results or key not found.`);
      dynamicTableColumns.value = [];
      dynamicTableData.value = [];
      return;
    }
  }
  console.log("renderTable: Determined grouping alias:", groupingAlias);


  // Determine the aliases to be displayed in the table (including implicit and aggregated)
  const calculationAliases = [];
  if (analysisForm.includeEventCount) calculationAliases.push('eventCount');
  if (analysisForm.includeUniqueUserCount) calculationAliases.push('uniqueUserCount'); // Needs to be included if average is selected
  if (analysisForm.includeAverageCountPerUser) calculationAliases.push('averageCountPerUser'); // This is the calculated key on frontend

  // Add aliases for attribute aggregations from the form state
  analysisForm.calculations.forEach(calc => {
    if (calc.attribute && calc.aggregationType) {
      const alias = `${calc.attribute.replace('.', '_')}`;
      calculationAliases.push(alias); // Use the generated alias
    }
  });

  // Filter out aliases that are not actually present in the results (can happen with backend issues)
  const validCalculationAliases = calculationAliases.filter(alias =>
    processedResults.length > 0 && processedResults[0].hasOwnProperty(alias)
  );

  if (validCalculationAliases.length === 0 && !groupingAlias) { // Allow just grouping column if no calcs
    console.error("renderTable: No valid calculation aliases found in processed results, and no grouping alias determined. Cannot render table columns.");
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
    return;
  }
  console.log("renderTable: Valid calculation aliases for table columns:", validCalculationAliases);


  // Create column definitions for ElTable
  const tableColumns = [];

  // Add grouping column if determined
  if(groupingAlias) {
    const groupingLabel = groupingAttributeOptions.value.find(opt => opt.value === query.groupingAttribute)?.label || query.groupingAttribute || groupingAlias;
    tableColumns.push({ prop: groupingAlias, label: groupingLabel, width: 180 });
    console.log('add column' + groupingAlias);
  }

  console.log('valid' + validCalculationAliases);
  // Add calculation columns
  validCalculationAliases.forEach(calcAlias => {
    // Find the corresponding label for the column header
    let calcLabel = calcAlias; // Default label is the alias

    if (calcAlias === 'eventCount') {
      calcLabel = '事件计数';
    } else if (calcAlias === 'uniqueUserCount') {
      calcLabel = '活跃用户数';
    } else if (calcAlias === 'averageCountPerUser') {
      calcLabel = '人均次数';
    } else {
      // Look up in analysisForm.calculations to find the original attribute and aggregation type
      const matchingCalc = analysisForm.calculations.find(calc =>
        `${calc.attribute.replace('.', '_')}` === calcAlias
      );
      if (matchingCalc) {
        const attrOption = attributeOptions.value.find(opt => opt.value === matchingCalc.attribute);
        const attributeLabel = attrOption ? attrOption.label.replace(/^(参数|用户属性): /, '') : matchingCalc.attribute;
        const aggregationLabel = getAggregationOptions(matchingCalc.attribute).find(agg => agg.value === matchingCalc.aggregationType)?.label || matchingCalc.aggregationType;
        calcLabel = `${attributeLabel} (${aggregationLabel})`;
      }
    }


    // ** Add formatter for 'averageCountPerUser' to display with decimals **
    if (calcAlias === 'averageCountPerUser') {
      tableColumns.push({
        prop: calcAlias,
        label: calcLabel,
        formatter: (row, column, cellValue) => {
          return typeof cellValue === 'number' ? parseFloat(cellValue).toFixed(2) : cellValue; // Format numbers with 2 decimal places
        }
      });
    } else if (calcAlias === 'eventCount' || calcAlias === 'uniqueUserCount') {
      // Formatter for integer counts
      tableColumns.push({
        prop: calcAlias,
        label: calcLabel,
        formatter: (row, column, cellValue) => {
          return typeof cellValue === 'number' ? Math.round(cellValue) : cellValue; // Round counts to nearest integer
        }
      });
    }
    else {
      // Default column definition
      console.log('add column' + calcAlias);
      tableColumns.push({ prop: calcAlias, label: calcLabel });
    }
  });

  dynamicTableColumns.value = tableColumns;
  dynamicTableData.value = processedResults; // Use processedResults for table data
};


const resizeChart = () => {
  if (myChart && myChart.getDom() && selectedChartType.value !== 'table') { // Only resize if chart is visible
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
  // It also handles disposing old instances.
  console.log("initChart called. Container:", chartContainer.value, "Instance:", myChart ? myChart.getDom() : null);

  // Initialization logic moved into renderChart
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
  // the first analysis results arrive. The watcher includes nextTick and container checks.
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
  // Also clear any pending timeouts or intervals if they were added
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
    // ** Clear existing calculations when event changes **
    analysisForm.calculations = [];
    analysisForm.includeEventCount = true; // Reset implicit counts
    analysisForm.includeUniqueUserCount = true;
    analysisForm.includeAverageCountPerUser = false;
  }
  // Clear results and displays when event changes
  analysisResults.value = null;
  // Clearing display will be handled by analysisResults watcher when it becomes null
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
      console.log("Chart type switched to chart. analysisResults:", analysisResults.value);
      if (analysisResults.value && analysisResults.value.length > 0) {
        console.log("Results exist, triggering chart render logic via analysisResults watcher.");
        // Manually trigger the analysisResults watcher logic if results are already there
        // Temporarily set analysisResults to null to force the watcher to run its full logic
        const currentResults = analysisResults.value;
        analysisResults.value = null;
        await nextTick(); // Allow DOM updates from clearing
        analysisResults.value = currentResults; // Restore to trigger render
      } else {
        // If switching to chart but no results, clear chart if instance exists
        console.log("Chart type switched to chart, no results. Clearing chart if instance exists.");
        if (myChart) {
          myChart.setOption({ series: [], xAxis: [] });
        }
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
        console.log("Results exist, triggering table render logic via analysisResults watcher.");
        // Manually trigger the analysisResults watcher logic if results are already there
        const currentResults = analysisResults.value;
        analysisResults.value = null; // Temporarily clear to force re-trigger
        await nextTick(); // Allow DOM updates from clearing
        analysisResults.value = currentResults; // Restore to trigger render
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
    if (myChart) {
      console.log("Analysis results watcher: Clearing chart display.");
      myChart.setOption({ series: [], xAxis: [] });
    }
  } else {
    console.log("Analysis results watcher: Clearing table display.");
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
      // ** Explicitly wait for chartContainer.value to be non-null with a retry mechanism **
      let attempts = 0;
      const maxAttempts = 20; // Increased attempts
      const delay = 50; // 50ms delay

      console.log(`Analysis results watcher: Starting wait loop for chartContainer.value. Current: ${chartContainer.value}`);

      while (!chartContainer.value && attempts < maxAttempts) {
        console.warn(`Analysis results watcher: chartContainer.value is null (Attempt ${attempts + 1}/${maxAttempts}). Waiting ${delay}ms...`);
        await new Promise(resolve => setTimeout(resolve, delay));
        await nextTick(); // Wait for Vue's next DOM update cycle potentially updating the ref
        attempts++;
      }

      if (chartContainer.value) {
        console.log("Analysis results watcher: chartContainer.value is now available. Proceeding with chart render.");
        // renderChart will handle initialization if necessary
        renderChart(newResults, selectedChartType.value, query);
      } else {
        console.error(`Analysis results watcher: chartContainer.value is still null after ${maxAttempts} attempts. Cannot render chart.`);
        // If we still fail, ensure chart is cleared if an instance exists
        if(myChart) myChart.setOption({ series: [], xAxis: [] });
        ElMessage.error("无法显示图表，图表容器未准备就绪。请尝试重新运行分析。");
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
}, { deep: true }); // Deep watch if analysisResults structure could change significantly


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
              <el-form-item label="计算指标">
                <div class="calculation-builder-container">
                  <div class="implicit-metrics-checkboxes">
                    <el-checkbox v-model="analysisForm.includeEventCount" label="事件计数" size="small" style="margin-right: 15px;"></el-checkbox>
                    <el-checkbox v-model="analysisForm.includeUniqueUserCount" label="活跃用户数" size="small" style="margin-right: 15px;"></el-checkbox>
                    <el-checkbox v-model="analysisForm.includeAverageCountPerUser" label="人均次数" size="small"
                                 :disabled="!analysisForm.includeUniqueUserCount || !analysisForm.includeEventCount"
                    ></el-checkbox>
                  </div>
                  <div v-for="(calc, index) in analysisForm.calculations" :key="index" class="calculation-item">
                    <el-select v-model="calc.attribute" placeholder="选择属性" style="width: 180px; margin-right: 10px;" filterable size="small">
                      <el-option
                        v-for="attr in attributeOptions"
                      :key="attr.value"
                      :label="attr.label"
                      :value="attr.value"
                      ></el-option>
                    </el-select>
                    <el-select
                      v-model="calc.aggregationType"
                      placeholder="选择聚合方式"
                      style="width: 120px; margin-right: 10px;"
                      size="small"
                      :disabled="!calc.attribute"
                    >
                    <el-option
                      v-for="agg in getAggregationOptions(calc.attribute)"
                    :key="agg.value"
                    :label="agg.label"
                    :value="agg.value"
                    ></el-option>
                    </el-select>
                    <el-button type="danger" :icon="Delete" circle size="small" @click="removeCalculation(index)"></el-button>
                  </div>
                  <el-button type="primary" link :icon="Plus" @click="addCalculation">添加属性计算</el-button>
                </div>
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
                          type="text"
                ></el-input>
                <el-button type="danger" :icon="Delete" circle size="small" @click="removeGlobalFilter(index)"></el-button>
              </div>
              <el-button type="primary" link :icon="Plus" @click="addGlobalFilter">添加筛选条件</el-button>
            </div>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="计算指标">
            <div class="calculation-builder-container">
              <p style="color: #909399; text-align: center;">请先选择一个事件</p>
            </div>
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
                         :disabled="loadingEventSchema || !analysisForm.selectedEvent || !analysisForm.groupingAttribute || (!analysisForm.includeEventCount && !analysisForm.includeUniqueUserCount && !analysisForm.includeAverageCountPerUser && analysisForm.calculations.length === 0)"
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
      <div>
        <el-table v-show="selectedChartType === 'table'" :data="dynamicTableData" style="width: 100%">
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

.el-form-item {
  margin-bottom: 18px;
}

.el-row .el-col .el-form-item:last-child {
  margin-bottom: 0;
}

/* Calculation Builder Styling */
.calculation-builder-container {
  border: 1px dashed #dcdfe6; /* Match Element Plus border color */
  padding: 15px;
  width: 100%;
  border-radius: 4px;
  background-color: #f4f4f5; /* Softer background color */
  margin-bottom: 18px; /* Add margin below the container */
}

.implicit-metrics-checkboxes {
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #ebeef5; /* Separator */
}

.calculation-item {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.calculation-item:last-child {
  margin-bottom: 0;
}

.calculation-item .el-select,
.calculation-item .el-input,
.calculation-item .el-input-number,
.calculation-item .el-cascader {
  margin-right: 10px;
}
/* Style for the add calculation button */
.calculation-builder-container > .el-button {
  margin-top: 5px; /* Add a small margin above the button */
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
  border: 1px dashed #dcdfe6; /* Match Element Plus border color */
  border-radius: 4px;
  overflow: hidden;
  position: relative; /* Needed for absolute positioning of empty message */
  /* Add flex properties to center content *only when there's no chart rendered* */
  display: flex;
  align-items: center;
  justify-content: center;
}

/* Remove flex properties and border when chart is rendered */
div[ref="chartContainer"]:has(canvas),
div[ref="chartContainer"]:has(svg) {
  border: none;
  display: block; /* Override flex when chart is rendered */
  align-items: initial;
  justify-content: initial;
}

/* Style for empty message inside chart container */
.chart-empty-message {
  text-align: center;
  width: 100%; /* Center text horizontally */
  /* Use position: static or relative when parent is flex, but we need absolute positioning when parent is flex */
  /* Let's keep position: absolute and ensure it's styled correctly */
  position: absolute;
  top: 50%;
  left: 0;
  transform: translateY(-50%);
  color: #909399; /* Match placeholder text color */
  font-size: 14px;
}
/* Hide empty message when chart is rendered */
div[ref="chartContainer"]:has(canvas) .chart-empty-message,
div[ref="chartContainer"]:has(svg) .chart-empty-message {
  display: none;
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

</style>
