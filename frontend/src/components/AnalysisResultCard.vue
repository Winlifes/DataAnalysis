<script setup>
import {ref, onMounted, onUnmounted, watch, nextTick, computed} from 'vue';
import {
  ElCard,
  ElIcon,
  ElButton,
  ElTable,
  ElTableColumn,
  ElLoading, // Import ElLoading
  ElMessage // Import ElMessage for errors
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

import {Rank, PieChart, TrendCharts, Download, DataBoard, Refresh, Setting, Close } from '@element-plus/icons-vue';

// Define props received from the parent (Dashboard.vue)
const props = defineProps({
  analysisData: {
    type: Array, // The array of data objects from the backend analysis endpoint
    default: () => []
  },
  config: {
    type: Object, // The saved analysis configuration object for this item
    required: true
  },
  title: {
    type: String, // Title for this analysis card
    default: '分析结果'
  },
  isLoading: {
    type: Boolean, // Loading state for this specific analysis item
    default: false
  },
  error: {
    type: [Object, String], // Error object or message if analysis failed for this item
    default: null
  },
  size:{
    type: String,
    default: 'small'
  },
  selectedChartType:{
    type: String,
    default: 'bar'
  },
  id:{
    type: Number,
    default: 1
  }
});

// Define events emitted by this component
const emit = defineEmits(['refresh', 'remove', 'settings', 'change-chart-type', 'toggle-size', 'export']);

// --- State Variables ---
const chartContainer = ref(null); // Ref for the chart DOM element
let myChart = null; // Echarts instance for this card

const dynamicTableColumns = ref([]);
const dynamicTableData = ref([]);

// --- Computed Properties ---
// Determine the chart type from the config
const chartType = computed(() => props.selectedChartType || 'bar'); // Default to 'bar'
const cardSize = computed(() => props.size || 'small');

// Helper function to get the data type of an attribute (adapted from Analysis.vue)
// This is needed here to determine how to format data or which aggregations are relevant (though aggregations are in config)
const getAttributeType = (attributePath) => {
  if (attributePath === 'eventCount') return 'number';
  if (attributePath === 'uniqueUserCount') return 'number';
  if (attributePath === 'averageCountPerUser') return 'number';
  if (attributePath === 'userId') return 'string';
  if (attributePath === 'deviceId') return 'string';

  // Note: This component doesn't have direct access to globalUserPropertySchema or selectedEventSchema.
  // Ideally, relevant type info would be part of the saved 'config' or passed down.
  // For now, we'll make assumptions or rely on common types.
  // A more robust solution would include schema snippets in the saved config.

  // Simple check based on common parameter/property names or patterns
  if (attributePath.startsWith('parameter.') || attributePath.startsWith('userProperty.')) {
    const name = attributePath.split('.')[1];
    // Basic guess based on name
    if (name.toLowerCase().includes('count') || name.toLowerCase().includes('amount') || name.toLowerCase().includes('value') || name.toLowerCase().includes('level') || name.toLowerCase().includes('duration')) {
      return 'number';
    }
  }

  return 'string'; // Default to string
};

// Helper function to get available aggregation options (adapted, used for label lookup)
const getAggregationOptions = (attributePath) => {
  const type = getAttributeType(attributePath);
  const options = [
    { label: '去重数', value: 'distinctCount' }
  ];

  if (['integer', 'long', 'float', 'double', 'number'].includes(type)) {
    options.unshift(
      { label: '总和', value: 'sum' },
      { label: '均值', value: 'avg' },
      { label: '最大值', value: 'max' },
      { label: '最小值', value: 'min' }
    );
  }
  return options;
};


// --- Methods ---

/**
 * Renders the analysis results as a chart using Echarts.
 * @param results - The analysis results (List<Map<String, Object>>).
 * @param config - The saved analysis configuration object.
 */
const renderChart = (results, config) => {
  if (!chartContainer.value) {
    console.error("AnalysisResultCard: Chart container element not found. Cannot render chart.");
    return;
  }

  // Initialize chart if not already initialized OR if the instance is attached to a different DOM element
  if (!myChart || myChart.getDom() !== chartContainer.value) {
    console.log("AnalysisResultCard: Echarts instance not initialized or container mismatch. Initializing.");
    if (myChart) {
      console.log("AnalysisResultCard: Disposing previous Echarts instance.");
      myChart.dispose();
      window.removeEventListener('resize', resizeChart);
    }
    try {
      myChart = echarts.init(chartContainer.value);
      console.log("AnalysisResultCard: Echarts initialized successfully.");
      window.addEventListener('resize', resizeChart);
    } catch (e) {
      console.error("AnalysisResultCard: Failed to initialize Echarts:", e);
      myChart = null;
      window.removeEventListener('resize', resizeChart);
      return;
    }
  } else {
    console.log("AnalysisResultCard: Echarts instance already initialized on the correct container.");
  }


  if (!results || results.length === 0) {
    console.log("AnalysisResultCard: No results to display, clearing chart.");
    myChart.setOption({ series: [], xAxis: [] }); // Clear chart
    return;
  }
  console.log("AnalysisResultCard: Rendering chart with results.");

  // ** Calculate 'averageCountPerUser' if selected in the saved config **
  const processedResults = results.map(row => {
    const newRow = { ...row }; // Create a copy
    const eventCount = newRow['eventCount'] || 0;
    const uniqueUserCount = newRow['uniqueUserCount'] || 0;
    newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
    if (isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
      newRow['averageCountPerUser'] = 0;
    }
    return newRow;
  });


  // Determine the grouping alias used in the backend result
  let groupingAlias = config.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + config.groupingAttribute.substring(5);
  }
  // Fallback check against processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    // Need a way to reliably get the grouping alias from the results
    // If the first column is always the grouping column, could use Object.keys(processedResults[0])[0]
    // Otherwise, needs a clear mapping from groupingAttribute to backend alias.
    // Assuming backend returns the alias generated by buildAnalysisQuery if no custom logic
    // This fallback logic might need adjustment based on actual backend response structure.
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      // Filter out keys that match expected calculation aliases
      // Need to reconstruct expected aliases from config
      !getExpectedCalculationAliases(config).includes(key)
    );

    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`AnalysisResultCard: Grouping alias "${config.groupingAttribute}" not found in processed results keys, using fallback key "${groupingAlias}" (likely the first column).`);
    } else if (processedResults.length > 0) {
      groupingAlias = Object.keys(processedResults[0])[0];
      console.warn(`AnalysisResultCard: Could not reliably determine grouping key alias. Using first column key "${groupingAlias}".`);

    } else {
      console.error(`AnalysisResultCard: Could not determine grouping key alias from processed results for attribute: ${config.groupingAttribute}. No results or key not found.`);
      myChart.setOption({ series: [], xAxis: [], title: { text: '数据格式错误或无分组数据', left: 'center' } });
      return;
    }
  }
  console.log("AnalysisResultCard: Determined grouping alias:", groupingAlias);


  // Determine the aliases to be charted (including implicit and aggregated)
  // This list should match the keys we expect in processedResults.
  const calculationAliases = getExpectedCalculationAliases(config);
  console.log("AnalysisResultCard: calculation aliases for series:", calculationAliases);

  // Filter out aliases that are not actually present in the results (can happen with backend issues)
  const validCalculationAliases = calculationAliases.filter(alias =>
    processedResults.length > 0 && processedResults[0].hasOwnProperty(alias)
  );

  if (validCalculationAliases.length === 0) {
    console.error("AnalysisResultCard: No valid calculation aliases found in processed results. Cannot render chart series.");
    myChart.setOption({ series: [], xAxis: [] }); // Clear chart, no title needed here
    return;
  }
  console.log("AnalysisResultCard: Valid calculation aliases for series:", validCalculationAliases);


  // Extract grouping values for the X-axis from processed results
  const xAxisData = processedResults.map(row => row[groupingAlias]); // Use the determined grouping alias

  // Create series for each calculation attribute from processed results
  const series = validCalculationAliases.map(calcAlias => {
    // Find the corresponding label for the legend
    let legendLabel = calcAlias; // Default label is the alias

    if (calcAlias === 'eventCount') {
      legendLabel = '事件计数';
    } else if (calcAlias === 'uniqueUserCount') {
      legendLabel = '活跃用户数';
    } else if (calcAlias === 'averageCountPerUser') {
      legendLabel = '人均次数';
    } else {
      // Look up in config.calculations to find the original attribute and aggregation type
      const matchingCalc = config.calculationAttributes.find(calc =>
        `${calc.replace('.', '_')}_${calc.aggregationType}` === calcAlias
      );
      if (matchingCalc) {
        // We don't have attributeOptions here directly, need to reconstruct label
        const attributeName = matchingCalc.attribute.split('.').pop(); // Get name after '.'
        const attributePrefix = matchingCalc.attribute.startsWith('parameter.') ? '参数' : (matchingCalc.attribute.startsWith('userProperty.') ? '用户属性' : '');
        const aggregationLabel = getAggregationOptions(matchingCalc.attribute).find(agg => agg.value === matchingCalc.aggregationType)?.label || matchingCalc.aggregationType;
        legendLabel = `${attributePrefix ? attributePrefix + ': ' : ''}${attributeName} (${aggregationLabel})`;
      }
    }


    return {
      name: legendLabel,
      type: chartType.value, // Use the computed chart type
      data: processedResults.map(row => row[calcAlias]),
      smooth: chartType.value === 'line',
    };
  });

  const legendData = series.map(s => s.name);

  const options = {
    title: {
      text: "", // Use the card title prop
      left: 'center',
      textStyle: {
        fontSize: 14 // Smaller title in card
      }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: chartType.value === 'bar' ? 'shadow' : 'line'
      },
      formatter: function (params) {
        let tooltipContent = `${params[0].name}<br/>`;
        params.forEach(param => {
          const value = typeof param.value === 'number' ? parseFloat(param.value).toFixed(2) : param.value;
          tooltipContent += `<span style="display:inline-block;margin-right:4px;border-radius:10px;width:10px;height:10px;background-color:${param.color};"></span>${param.seriesName}: ${value}<br/>`;
        });
        return tooltipContent;
      }
    },
    legend: {
      data: legendData,
      top: 30,
      left: 'center',
      type: 'scroll',
      textStyle: {
        fontSize: 10 // Smaller legend text
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
      data: xAxisData,
      axisLabel: {
        interval: 0,
        rotate: xAxisData.some(label => String(label).length > 5) ? 30 : 0, // Rotate if labels are long
        fontSize: 10 // Smaller axis labels
      }
    },
    yAxis: {
      type: 'value',
      name: '值',
      nameTextStyle: {
        fontSize: 10
      },
      axisLabel: {
        fontSize: 10
      }
    },
    series: series
  };

  try {
    myChart.setOption(options, true); // Use true for `notMerge`
    console.log("AnalysisResultCard: Echarts setOption called successfully.");
  } catch (e) {
    console.error("AnalysisResultCard: Failed to setOption on Echarts instance:", e);
    // Display an error message on the card?
  }
};

/**
 * Renders the analysis results as an ElTable.
 * @param results - The analysis results (List<Map<String, Object>>).
 * @param config - The saved analysis configuration object.
 */
const renderTable = (results, config) => {
  if (!results || results.length === 0) {
    console.log("AnalysisResultCard: No results to display, clearing table.");
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
    return;
  }
  console.log("AnalysisResultCard: Rendering table with results.");


  // ** Calculate 'averageCountPerUser' if selected in the saved config **
  const processedResults = results.map(row => {
    const newRow = { ...row };
    const eventCount = newRow['eventCount'] || 0;
    const uniqueUserCount = newRow['uniqueUserCount'] || 0;
    newRow['averageCountPerUser'] = uniqueUserCount > 0 ? eventCount / uniqueUserCount : 0;
    if(isNaN(newRow['averageCountPerUser']) || !isFinite(newRow['averageCountPerUser'])) {
      newRow['averageCountPerUser'] = 0;
    }
    return newRow;
  });


  // Determine the grouping alias used in the backend result
  let groupingAlias = config.groupingAttribute;
  if (groupingAlias.includes('.')) {
    groupingAlias = groupingAlias.replace('.', '_');
  }
  if (groupingAlias.startsWith('time.')) {
    groupingAlias = 'time_' + config.groupingAttribute.substring(5);
  }
  // Fallback check against processed results keys
  if (processedResults.length > 0 && !processedResults[0].hasOwnProperty(groupingAlias)) {
    const potentialGroupingKeys = Object.keys(processedResults[0]).filter(key =>
      !getExpectedCalculationAliases(config).includes(key)
    );

    if (potentialGroupingKeys.length === 1) {
      groupingAlias = potentialGroupingKeys[0];
      console.warn(`AnalysisResultCard: Grouping alias "${config.groupingAttribute}" not found in processed results keys, using fallback key "${groupingAlias}" (likely the first column).`);
    } else if (processedResults.length > 0) {
      groupingAlias = Object.keys(processedResults[0])[0];
      console.warn(`AnalysisResultCard: Could not reliably determine grouping key alias. Using first column key "${groupingAlias}".`);

    } else {
      console.error(`AnalysisResultCard: Could not determine grouping key alias from processed results for attribute: ${config.groupingAttribute}. No results or key not found.`);
      dynamicTableColumns.value = [];
      dynamicTableData.value = [];
      return;
    }
  }
  console.log("AnalysisResultCard: Determined grouping alias:", groupingAlias);


  // Determine the aliases to be displayed in the table (including implicit and aggregated)
  const calculationAliases = getExpectedCalculationAliases(config);
  console.log(calculationAliases)

  // Filter out aliases that are not actually present in the results (can happen with backend issues)
  const validCalculationAliases = calculationAliases.filter(alias =>
    processedResults.length > 0 && processedResults[0].hasOwnProperty(alias)
  );

  if (validCalculationAliases.length === 0 && !groupingAlias) { // Allow just grouping column if no calcs
    console.error("AnalysisResultCard: No valid calculation aliases found in processed results, and no grouping alias determined. Cannot render table columns.");
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
    return;
  }
  console.log("AnalysisResultCard: Valid calculation aliases for table columns:", validCalculationAliases);


  // Create column definitions for ElTable
  const tableColumns = [];

  // Add grouping column if determined
  if(groupingAlias) {
    // Need to reconstruct grouping label from config.groupingAttribute
    let groupingLabel = groupingAlias;
    if (config.groupingAttribute.startsWith('time.')) {
      const timeUnit = config.groupingAttribute.substring(5);
      groupingLabel = `按${timeUnit === 'day' ? '天' : timeUnit === 'week' ? '周' : timeUnit === 'month' ? '月' : timeUnit}分组`; // Simple label
    } else if (config.groupingAttribute.startsWith('parameter.')) {
      groupingLabel = `参数: ${config.groupingAttribute.substring(10)}`;
    } else if (config.groupingAttribute.startsWith('userProperty.')) {
      groupingLabel = `用户属性: ${config.groupingAttribute.substring(13)}`;
    } else if (config.groupingAttribute === 'userId') {
      groupingLabel = '用户ID';
    } else if (config.groupingAttribute === 'deviceId') {
      groupingLabel = '设备ID';
    }

    tableColumns.push({ prop: groupingAlias, label: groupingLabel, width: 180 });
  }

  // Add calculation columns
  validCalculationAliases.forEach(calcAlias => {
    let calcLabel = calcAlias;
    console.log("label:" + calcLabel);
    if (calcAlias === 'eventCount') {
      calcLabel = '事件计数';
    } else if (calcAlias === 'uniqueUserCount') {
      calcLabel = '活跃用户数';
    } else if (calcAlias === 'averageCountPerUser') {
      calcLabel = '人均次数';
    } else {
      const matchingCalc = config.calculationAttributes.find(calc =>
        `${calc.replace('.', '_')}_${calc.aggregationType}` === calcAlias
      );
      if (matchingCalc) {
        const attributeName = matchingCalc.attribute.split('.').pop();
        const attributePrefix = matchingCalc.attribute.startsWith('parameter.') ? '参数' : (matchingCalc.attribute.startsWith('userProperty.') ? '用户属性' : '');
        const aggregationLabel = getAggregationOptions(matchingCalc.attribute).find(agg => agg.value === matchingCalc.aggregationType)?.label || matchingCalc.aggregationType;
        calcLabel = `${attributePrefix ? attributePrefix + ': ' : ''}${attributeName} (${aggregationLabel})`;
      }
    }


    if (calcAlias === 'averageCountPerUser') {
      tableColumns.push({
        prop: calcAlias,
        label: calcLabel,
        formatter: (row, column, cellValue) => {
          return typeof cellValue === 'number' ? parseFloat(cellValue).toFixed(2) : cellValue;
        }
      });
    } else if (calcAlias === 'eventCount' || calcAlias === 'uniqueUserCount') {
      tableColumns.push({
        prop: calcAlias,
        label: calcLabel,
        formatter: (row, column, cellValue) => {
          return typeof cellValue === 'number' ? Math.round(cellValue) : cellValue;
        }
      });
    }
    else {
      tableColumns.push({ prop: calcAlias, label: calcLabel });
    }
  });

  dynamicTableColumns.value = tableColumns;
  dynamicTableData.value = processedResults;
};

// Helper to get the list of expected calculation aliases based on config
const getExpectedCalculationAliases = (config) => {
  const aliases = [];
  if (config.includeEventCount) aliases.push('eventCount');
  if (config.includeUniqueUserCount || config.includeAverageCountPerUser) {
    aliases.push('uniqueUserCount');
  }
  if (config.includeAverageCountPerUser) {
    aliases.push('averageCountPerUser'); // This is the calculated key on frontend
  }

  if (Array.isArray(config.calculationAttributes)) {
    config.calculationAttributes.forEach(calc => {
      if(calc.includes("@"))
      {
         aliases.push(calc.replace('.', '_').replace('@', '_'));
      }
      else {
        aliases.push(calc);
        if(calc === "uniqueUserCount"){
          aliases.push("averageCountPerUser");
        }
      }
      // if (calc.attribute && calc.aggregationType) {
      //   const alias = `${calc.attribute.replace('.', '_')}_${calc.aggregationType}`;
      //   aliases.push(alias);
      // }
    });
  }
  return [...new Set(aliases)];
};


const resizeChart = () => {
  if (myChart && myChart.getDom() && chartType.value !== 'table') {
    console.log("AnalysisResultCard: Resizing chart.");
    myChart.resize();
  }
};

// --- Lifecycle Hooks and Watchers ---

onMounted(() => {
  console.log("AnalysisResultCard mounted. Config:", props.config);
  // Initial render based on initial props
  if (props.analysisData && props.analysisData.length > 0) {
    if (chartType.value !== 'table') {
      // Wait for nextTick to ensure ref is available before rendering chart
      nextTick(() => {
        renderChart(props.analysisData, props.config);
      });
    } else {
      renderTable(props.analysisData, props.config);
    }
  }
});

onUnmounted(() => {
  console.log("AnalysisResultCard unmounted.");
  if (myChart) {
    console.log("AnalysisResultCard: Disposing Echarts instance on unmount.");
    myChart.dispose();
    myChart = null;
    window.removeEventListener('resize', resizeChart);
  }
});

// Watch for changes in analysisData or config to re-render
watch(() => [props.analysisData, props.config, props.isLoading, props.error, props.selectedChartType, props.size], ([newData, newConfig, newIsLoading, newError, newChartType, newSize], [oldData, oldConfig, oldIsLoading, oldError, oldChartType, oldSize]) => {
  console.log("AnalysisResultCard: Props updated.");
  // Only re-render if data or relevant config changes, and it's not currently loading
  if (!newIsLoading && (newData !== oldData || newConfig !== oldConfig || newError !== oldError || newChartType !== oldChartType || newSize !== oldSize)) {
    console.log("AnalysisResultCard: Data or config changed, and not loading. Re-rendering.");
    if (newError) {
      // If there's an error, clear displays
      if (myChart) myChart.setOption({ series: [], xAxis: [] });
      dynamicTableColumns.value = [];
      dynamicTableData.value = [];
    } else if ((newData && newData.length > 0) || newChartType || newSize) {
      // If there is data, render based on the current chart type
      if (chartType.value !== 'table') {
        // Wait for nextTick to ensure the chart container is visible (if v-show is used)
        nextTick(() => {
          renderChart(newData, newConfig);
        });
      } else {
        renderTable(newData, newConfig);
      }
    } else {
      // If no data and no error, clear displays
      if (myChart) myChart.setOption({ series: [], xAxis: [] });
      dynamicTableColumns.value = [];
      dynamicTableData.value = [];
    }
  } else if (newIsLoading) {
    console.log("AnalysisResultCard: Loading state is true, clearing display.");
    // If loading starts, clear the display
    if (myChart) myChart.setOption({ series: [], xAxis: [] });
    dynamicTableColumns.value = [];
    dynamicTableData.value = [];
  } else {
    console.log("AnalysisResultCard: Props updated, but no re-render needed.");
  }
}, { deep: true }); // Deep watch config in case its internal properties change


// --- Event Handlers for Card Actions ---
const handleRefreshClick = () => {
  console.log("AnalysisResultCard: Refresh clicked.");
  emit('refresh'); // Emit refresh event to parent
};

const handleSettingsClick = () => {
  console.log("AnalysisResultCard: Settings clicked.");
  emit('settings'); // Emit settings event to parent
};

const handleRemoveClick = () => {
  console.log("AnalysisResultCard: Remove clicked.");
  emit('remove'); // Emit remove event to parent
};

// ** NEW: Handlers for Dropdown Menu Items **
const handleCommand = (command) => {
  console.log("AnalysisResultCard: Dropdown command received:", command);
  if (command === 'export') {
    // Trigger export logic within this component
    handleExport();
  } else if (command === 'toggle-size') {
    // Emit event to parent to toggle size
    emit('toggle-size');
  } else {
    // Assume it's a chart type change command ('bar', 'line', 'table')
    // Emit event to parent to change chart type
    emit('change-chart-type', command);
  }
};

const handleExport = () => {
  console.log("AnalysisResultCard: Export initiated.");
  // Check if there's data to export
  if (!props.analysisData || props.analysisData.length === 0) {
    ElMessage.warning("没有分析结果可以导出。");
    return;
  }

  const isExport = localStorage.getItem("isExport");
  if(!isExport || isExport === "false")
  {
    ElMessage.warning("没有导出权限。");
    return;
  }

  if (chartType.value !== 'table') {
    // Export Chart
    if (myChart && chartContainer.value) {
      try {
        console.log("AnalysisResultCard: Attempting to export chart as PNG.");
        const dataURL = myChart.getDataURL({
          type: 'png',
          pixelRatio: 2, // Increase resolution
          backgroundColor: '#fff' // Set background color
        });

        // Create a temporary link element to trigger download
        const link = document.createElement('a');
        link.href = dataURL;
        // Generate a filename based on title and type
        const filename = `${props.title || '分析结果'}_图表_${Date.now()}.png`;
        link.download = filename;

        // Append link to body and click it
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        console.log("AnalysisResultCard: Chart export initiated.");
        ElMessage.success("图表导出成功！");

      } catch (e) {
        console.error("AnalysisResultCard: Failed to export chart:", e);
        ElMessage.error("导出图表失败。");
      }
    } else {
      console.warn("AnalysisResultCard: Export button clicked for chart type, but myChart instance or container not found.");
      ElMessage.warning("图表未完全加载，无法导出。");
    }
  } else {
    // Export Table Data (e.g., as CSV)
    console.log("AnalysisResultCard: Attempting to export table data as CSV.");
    try {
      if (!dynamicTableData.value || dynamicTableData.value.length === 0 || !dynamicTableColumns.value || dynamicTableColumns.value.length === 0) {
        ElMessage.warning("没有表格数据可以导出。");
        return;
      }

      // Build CSV header from dynamicTableColumns labels
      const headers = dynamicTableColumns.value.map(col => col.label).join(',');

      // Build CSV rows from dynamicTableData
      const rows = dynamicTableData.value.map(row => {
        return dynamicTableColumns.value.map(col => {
          let cellValue = row[col.prop];
          // Apply formatter if exists (e.g., for average or counts)
          if (col.formatter && typeof col.formatter === 'function') {
            cellValue = col.formatter(row, col, cellValue); // Pass index if needed by formatter
          }
          // Handle potential null/undefined values and escape commas/quotes
          cellValue = cellValue == null ? '' : String(cellValue);
          if (cellValue.includes(',') || cellValue.includes('"') || cellValue.includes('\n')) {
            // Escape double quotes by doubling them, then wrap field in double quotes
            cellValue = '"' + cellValue.replace(/"/g, '""') + '"';
          }
          return cellValue;
        }).join(',');
      }).join('\n');

      const csvContent = headers + '\n' + rows;

      // Create a Blob from the CSV content
      const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });

      // Create a link element
      const link = document.createElement('a');
      const url = URL.createObjectURL(blob);

      link.href = url;
      // Generate a filename
      const filename = `${props.title || '分析结果'}_表格数据_${Date.now()}.csv`;
      link.download = filename;

      // Append link to body, click it, and clean up
      document.body.appendChild(link);
      link.click();

      // Release the object URL
      URL.revokeObjectURL(url);
      document.body.removeChild(link);

      console.log("AnalysisResultCard: Table data export initiated.");
      ElMessage.success("表格数据导出成功！");

    } catch (e) {
      console.error("AnalysisResultCard: Failed to export table data:", e);
      ElMessage.error("导出表格数据失败。");
    }
  }
};

</script>

<template>
  <el-card class="analysis-result-card" shadow="hover" v-loading="isLoading">
    <template #header>
      <div class="card-header">
        <span class="card-title">{{ props.title }}</span>
        <div class="card-actions">
          <el-button :icon="Refresh" circle size="small" title="刷新" @click="handleRefreshClick"></el-button>
          <el-dropdown trigger="click" @command="handleCommand">
            <el-button :icon="Setting" circle size="small" title="设置"></el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="toggle-size" :icon="Rank">
                  切换大小图 (当前: {{ cardSize === 'small' ? '小图' : '大图' }})
                </el-dropdown-item>
                <el-dropdown-item command="bar" :icon="PieChart" :disabled="chartType === 'bar'">
                  切换柱状图
                </el-dropdown-item>
                <el-dropdown-item command="line" :icon="TrendCharts" :disabled="chartType === 'line'">
                  切换折线图
                </el-dropdown-item>
                <el-dropdown-item command="table" :icon="DataBoard" :disabled="chartType === 'table'">
                  切换表格
                </el-dropdown-item>
                <el-dropdown-item divided command="export" :icon="Download">
                  导出图表/数据
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button :icon="Close" circle size="small" title="移除" @click="handleRemoveClick"></el-button>
        </div>
      </div>
    </template>

    <div class="card-body">
      <div v-if="error" class="error-message">
        <p>加载分析数据失败:</p>
        <p>{{ error.message || error }}</p>
        <el-button type="primary" link size="small" @click="handleRefreshClick">重试</el-button>
      </div>
      <div v-else-if="!isLoading && (!analysisData || analysisData.length === 0)" class="empty-message">
        <p>无数据</p>
      </div>
      <div
        v-else-if="chartType !== 'table'"
        ref="chartContainer"
        class="chart-container"
        style="width: 100%; height: 300px;"
      >
      </div>
      <div v-else class="table-container">
        <el-table :data="dynamicTableData" style="width: 100%;" max-height="300">
          <template v-for="column in dynamicTableColumns" :key="column.prop">
            <el-table-column
              :prop="column.prop"
              :label="column.label"
              :width="column.width"
              :formatter="column.formatter"
            ></el-table-column>
          </template>
          <template #empty>
            <span>无数据</span>
          </template>
        </el-table>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.analysis-result-card .el-card__header {
  padding: 10px 15px;
}

.analysis-result-card .el-card__body {
  padding: 15px;
  min-height: 300px; /* Ensure minimum height */
  display: flex;
  flex-direction: column;
  justify-content: center; /* Center content vertically */
  align-items: center; /* Center content horizontally */
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
  flex-grow: 1; /* Allow title to take available space */
  margin-right: 10px; /* Space between title and actions */
  overflow: hidden; /* Prevent long titles from overflowing */
  text-overflow: ellipsis; /* Show ellipsis for long titles */
  white-space: nowrap; /* Prevent wrapping */
}

.card-actions {
  display: flex;
  align-items: center;
  gap: 5px; /* Space between buttons */
}

.card-body {
  width: 100%;
  height: 100%; /* Take full height of the card body */
  display: flex;
  flex-direction: column;
  justify-content: center; /* Center content vertically */
  align-items: center; /* Center content horizontally */
}

.chart-container {
  /* Echarts will manage its own size within this container */
  width: 100%;
  height: 100%; /* Fill the card body height */
}

.table-container {
  width: 100%;
  max-height: 300px; /* Limit table height with scroll */
  overflow-y: auto;
}

.error-message, .empty-message {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.error-message p, .empty-message p {
  margin: 5px 0;
}

/* Adjust loading mask style if needed */
.analysis-result-card.is-loading .el-card__body {
  /* Additional styles for loading state if needed */
}
</style>
