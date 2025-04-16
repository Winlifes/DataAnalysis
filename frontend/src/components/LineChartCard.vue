<template>
  <el-card shadow="hover" class="report-card" :body-style="{ padding: '15px' }">
    <div class="card-row header-row">
      <div class="header-left"><a href="#" @click.prevent="handleTitleClick" class="report-title-link" :title="`进入 ${title} 配置`">{{ title }}</a></div>
      <div class="header-right">
        <el-button :icon="Refresh" circle size="small" title="刷新" @click="handleRefresh" />
        <el-button :icon="FullScreen" circle size="small" title="全屏" @click="handleFullscreen" />
        <el-dropdown @command="handleDropdownCommand" trigger="click" placement="bottom-end">
          <el-button :icon="MoreFilled" circle size="small" title="更多选项" class="more-options-trigger"/>
          <template #dropdown> <el-dropdown-menu><el-dropdown-item :command="'toggle-size'" :icon="currentSize === 'small' ? FullScreen : Rank">{{ currentSize === 'small' ? '切换大图' : '切换小图' }}</el-dropdown-item><el-dropdown-item :command="'settings'" :icon="Setting">报表设置</el-dropdown-item><el-dropdown-item :command="'export'" :icon="Download">数据导出 (CSV)</el-dropdown-item><el-dropdown-item :command="'remove'" :icon="Delete" divided class="danger-item">移除</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </div>
    </div>

    <div class="card-row controls-row">
      <div class="controls-left">
        <el-dropdown size="small" @command="handleGranularityChange" trigger="click"> <el-button size="small" plain>{{ granularityLabels[currentGranularity] }}<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button><template #dropdown><el-dropdown-menu><el-dropdown-item v-for="(label, value) in granularityLabels" :key="value" :command="value" :disabled="currentGranularity === value">{{ label }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        <el-date-picker v-model="dateRange" type="daterange" unlink-panels range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" :shortcuts="dateShortcuts" size="small" style="width: 240px;" @change="handleDateRangeChange"/>
        <el-switch v-model="comparisonEnabled" size="small" active-text="日期对比" inactive-text="日期对比" inline-prompt @change="handleComparisonToggle" style="--el-switch-on-color: var(--el-color-primary); --el-switch-off-color: #bbb"/>
      </div>
      <div class="controls-right">
        <el-button
          :icon="showLabels ? Hide : View"
          circle
          size="small"
          :title="showLabels ? '隐藏数值' : '显示数值'"
          @click="showLabels = !showLabels"
        />
        <el-dropdown size="small" @command="handleChartTypeChange" trigger="click">
          <el-button size="small" plain>
            <el-icon style="margin-right: 5px"><component :is="chartTypeIcons[currentChartType]" /></el-icon>
            {{ chartTypeLabels[currentChartType] }}<el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown> <el-dropdown-menu>
            <el-dropdown-item v-for="(label, type) in chartTypeLabels" :key="type" :command="type" :disabled="currentChartType === type">
              <el-icon style="margin-right: 5px"><component :is="chartTypeIcons[type]" /></el-icon> {{ label }}
            </el-dropdown-item>
          </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <div class="card-row content-row"><el-row :gutter="20" style="width: 100%"><el-col :span="6"> <div v-if="dateLabel" class="date-label">{{ dateLabel }}</div><div class="main-metric">{{ mainMetric }}<span v-if="metricUnit" class="metric-unit">{{ metricUnit }}</span></div><div v-for="(comp, index) in comparisons" :key="index" class="comparison"><el-icon v-if="comp.icon" :size="12"><component :is="comp.icon" /></el-icon>{{ comp.label }}<span v-if="comp.value !== undefined" :class="comp.trend || (comp.value >= 0 ? 'up' : 'down')">{{ comp.valueFormatted !== undefined ? comp.valueFormatted : formatComparisonValue(comp.value) }}</span></div><div v-if="average !== undefined" class="average">均值 {{ average }}</div></el-col><el-col :span="18"> <div v-show="currentChartType !== 'table'" ref="chartRef" class="chart-container"></div><div v-if="currentChartType === 'table'" class="table-container"><el-table :data="tableData" stripe size="small" height="180px" border><el-table-column prop="date" label="Date" sortable min-width="110"></el-table-column><el-table-column prop="value" :label="chartData.legend || 'Value'" sortable align="right"></el-table-column></el-table></div></el-col></el-row></div>

  </el-card>
</template>

<script setup>
// Imports (keep existing)
import { ref, watch, onMounted, onBeforeUnmount, computed, nextTick, shallowRef } from 'vue';
import * as echarts from 'echarts/core';
import { LineChart, BarChart } from 'echarts/charts';
import { GridComponent, TitleComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { ElCard, ElRow, ElCol, ElRadioGroup, ElRadioButton, ElSwitch, ElIcon, ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton, ElDatePicker, ElTable, ElTableColumn, ElMessage } from 'element-plus';
// Icons (Add View, Hide; remove unused if any)
import { ArrowDown, FullScreen, Rank, MoreFilled, Setting, Download, Delete, Refresh, TrendCharts, Finished, Tickets, Histogram, DataLine as CumulativeIcon, View, Hide } from '@element-plus/icons-vue';

// ECharts Registration (keep existing)
echarts.use([ GridComponent, TitleComponent, TooltipComponent, LegendComponent, LineChart, BarChart, CanvasRenderer ]);

// Props definition (keep existing)
const props = defineProps({ cardId: { type: [String, Number], required: true }, title: { type: String, required: true }, reportId: [String, Number], dateLabel: String, mainMetric: { type: [String, Number], required: true }, metricUnit: String, comparisons: { type: Array, default: () => [] }, average: [String, Number], chartData: { type: Object, required: true }, chartColor: { type: String, default: '#409EFF' }, currentSize: { type: String, default: 'small' }, initialGranularity: { type: String, default: 'day' }, initialDateRange: { type: Array, default: null }, initialComparison: { type: Boolean, default: false }, initialShowLabels: { type: Boolean, default: false }, initialChartType: { type: String, default: 'line' }, availableChartTypes: { type: Array, default: () => ['line'] }, showAreaStyle: { type: Boolean, default: false } /* Removed radioOptions/initialRadioValue as they are not used */ });

// Emits definition (keep existing)
const emit = defineEmits([ 'toggle-size', 'settings', 'export', 'remove', 'refresh', 'fullscreen', 'title-click', 'granularity-change', 'daterange-change', 'comparison-toggle']);

// Internal Reactive State (keep existing)
const showLabels = ref(props.initialShowLabels); const chartRef = ref(null); let chartInstance = null; const currentChartType = ref(props.initialChartType); const currentGranularity = ref(props.initialGranularity); const dateRange = ref(props.initialDateRange); const comparisonEnabled = ref(props.initialComparison);

// --- Updated Labels and Icons ---
const granularityLabels = { hour: '按小时', day: '按天', week: '按周', month: '按月' };
const chartTypeLabels = { line: '趋势图', cumulative: '累计图', bar: '柱状图', table: '表格' }; // Renamed 'distribution' key to 'bar', label to '柱状图'
const chartTypeIcons = {
  line: TrendCharts,
  cumulative: CumulativeIcon,
  bar: Histogram,
  table: Tickets
};
// Date Picker Shortcuts (keep existing)
const dateShortcuts = [ { text: '最近7天', value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 7); return [start, end] } }, { text: '最近30天', value: () => { const end = new Date(); const start = new Date(); start.setTime(start.getTime() - 3600 * 1000 * 24 * 30); return [start, end] } }, { text: '本周', value: () => { const end = new Date(); const start = new Date(); const dayOfWeek = start.getDay() || 7; start.setDate(start.getDate() - dayOfWeek + 1); return [start, end] } }, { text: '上周', value: () => { const end = new Date(); const start = new Date(); const dayOfWeek = start.getDay() || 7; end.setDate(end.getDate() - dayOfWeek); start.setDate(end.getDate() - 6); return [start, end]} }, { text: '本月', value: () => { const end = new Date(); const start = new Date(end.getFullYear(), end.getMonth(), 1); return [start, end] } }, { text: '上月', value: () => { const end = new Date(); end.setDate(0); const start = new Date(end.getFullYear(), end.getMonth(), 1); return [start, end] } }, ];
if (!dateRange.value) { dateRange.value = dateShortcuts[0].value(); }

// --- Methods (keep existing) ---
const formatComparisonValue = (value) => { if (value === undefined || value === null) return ''; return `${Math.abs(value)}%`;};
const handleTitleClick = () => emit('title-click', props.reportId || props.cardId);
const handleRefresh = () => emit('refresh', props.cardId);
const handleFullscreen = () => emit('fullscreen', props.cardId);
const handleGranularityChange = (command) => { currentGranularity.value = command; emit('granularity-change', { cardId: props.cardId, granularity: command }); };
const handleDateRangeChange = (newRange) => { dateRange.value = newRange; emit('daterange-change', { cardId: props.cardId, range: newRange }); };
const handleComparisonToggle = (enabled) => { comparisonEnabled.value = enabled; emit('comparison-toggle', { cardId: props.cardId, enabled: enabled }); };
const handleChartTypeChange = (command) => { if (command && command !== currentChartType.value) currentChartType.value = command; };
const handleDropdownCommand = (command) => { switch (command) { case 'toggle-size': emit('toggle-size', props.cardId); break; case 'settings': emit('settings', props.cardId); break; case 'export': exportDataAsCsv(); break; case 'remove': emit('remove', props.cardId); break; } };
const exportDataAsCsv = () => { /* ... keep exact same CSV export logic ... */ if (!props.chartData || !props.chartData.xData || !props.chartData.yData || props.chartData.xData.length === 0) { ElMessage.error('无法导出：图表数据缺失或无效'); return; } const { xData, yData, legend } = props.chartData; const safeTitle = props.title.replace(/[^a-z0-9]/gi, '_').toLowerCase(); const filename = `${safeTitle || 'chart'}_data_${new Date().toISOString().slice(0,10)}.csv`; const headers = ['Date', legend || 'Value']; let csvContent = '\uFEFF'; const escapeCsvCell = (cell) => { if (cell === undefined || cell === null) return ''; let cellString = String(cell); if (cellString.includes(',') || cellString.includes('\n') || cellString.includes('"')) { cellString = cellString.replace(/"/g, '""'); return `"${cellString}"`; } return cellString; }; csvContent += headers.map(escapeCsvCell).join(',') + '\r\n'; for (let i = 0; i < xData.length; i++) { const yValue = (yData && yData.length > i) ? yData[i] : ''; const row = [xData[i], yValue]; csvContent += row.map(escapeCsvCell).join(',') + '\r\n'; } const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' }); const link = document.createElement("a"); if (link.download !== undefined) { const url = URL.createObjectURL(blob); link.setAttribute("href", url); link.setAttribute("download", filename); link.style.visibility = 'hidden'; document.body.appendChild(link); link.click(); document.body.removeChild(link); URL.revokeObjectURL(url); ElMessage.success(`数据已导出为 ${filename}`); } else { ElMessage.error('您的浏览器不支持直接下载文件'); } };

// --- Watchers (keep existing) ---
watch(showLabels, (newValue) => { if(currentChartType.value !== 'table') chartInstance?.setOption({ series: [{ label: { show: newValue } }] }); });
watch(() => props.chartData, (newData) => { if(currentChartType.value !== 'table') { if (chartInstance && newData) { chartInstance.setOption({ xAxis: { data: newData.xData }, series: [{ data: newData.yData, name: newData.legend }], legend: { data: [newData.legend] } }); } } }, { deep: true });
watch(currentChartType, (newType, oldType) => { if (newType === 'table') { chartInstance?.dispose(); chartInstance = null; window.removeEventListener('resize', handleResize); } else if (oldType === 'table' && newType !== 'table') { nextTick(initializeChart); } else if (chartInstance) { chartInstance.setOption(chartOptions.value, true); } });
watch(() => props.currentSize, async () => { await nextTick(); if(currentChartType.value !== 'table') chartInstance?.resize(); });

// --- Table Data Computed Property (keep existing) ---
const tableData = computed(() => { if (!props.chartData || !props.chartData.xData || !props.chartData.yData) return []; return props.chartData.xData.map((date, index) => ({ date: date, value: props.chartData.yData[index] ?? null })); });

// --- Chart Options Computed Property (keep existing, logic already handles 'bar') ---
const chartOptions = computed(() => { if (currentChartType.value === 'table') return {}; const type = currentChartType.value === 'cumulative' ? 'line' : currentChartType.value; const isBar = type === 'bar'; const isLine = type === 'line'; let displayYData = props.chartData.yData || []; if (currentChartType.value === 'cumulative') { displayYData = (props.chartData.yData || []).reduce((acc, val, i) => { acc.push((acc[i-1] || 0) + (val || 0)); return acc; }, []); } return { tooltip: { trigger: 'axis' }, grid: { left: '3%', right: '4%', bottom: '12%', top: '10%', containLabel: true }, xAxis: { type: 'category', boundaryGap: isBar, data: props.chartData.xData || [], axisLine: { lineStyle: { color: '#cccccc' } }, axisLabel: { color: '#666666' } }, yAxis: { type: 'value', axisLabel: { color: '#666666' }, splitLine: { lineStyle: { color: '#eeeeee', type: 'dashed' } } }, legend: { data: [props.chartData.legend], bottom: 0, textStyle: { color: '#666666' } }, series: [ { name: props.chartData.legend, type: type, smooth: false, symbol: 'none', barWidth: isBar ? '60%' : undefined, data: displayYData, lineStyle: { color: props.chartColor, width: 2 }, itemStyle: { color: props.chartColor }, label: { show: showLabels.value, position: isBar ? 'insideTop' : 'top', color: isBar ? '#fff' : '#333', fontSize: 10, formatter: '{c}' }, ...(props.showAreaStyle && isLine && currentChartType.value !== 'cumulative' && { areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [ { offset: 0, color: props.chartColor + '33' }, { offset: 1, color: props.chartColor + '05' } ]) } }) } ] }; });

// --- Initialize Chart Function (keep existing) ---
const initializeChart = () => { if (chartRef.value && currentChartType.value !== 'table') { chartInstance?.dispose(); chartInstance = echarts.init(chartRef.value); chartInstance.setOption(chartOptions.value); window.addEventListener('resize', handleResize); } }

// --- Lifecycle Hooks (keep existing) ---
onMounted(() => { initializeChart(); });
onBeforeUnmount(() => { chartInstance?.dispose(); window.removeEventListener('resize', handleResize); });

// --- Resize Handler (keep existing) ---
const handleResize = () => { if(currentChartType.value !== 'table') chartInstance?.resize(); };

</script>

<style scoped>
/* Keep existing styles, ensure icon button style is good */
.report-card { border: 1px solid var(--el-border-color-lighter); background-color: #fff; border-radius: 4px; transition: box-shadow 0.3s; }
.report-card:hover { box-shadow: var(--el-box-shadow-light); }
.card-row { display: flex; align-items: center; padding: 5px 0; }
/* Header Row */
.header-row { justify-content: space-between; margin-bottom: 10px; padding-bottom: 10px; border-bottom: 1px solid var(--el-border-color-lighter); }
.header-left .report-title-link { font-size: 16px; font-weight: 500; color: var(--el-text-color-primary); text-decoration: none; }
.header-left .report-title-link:hover { color: var(--el-color-primary); }
.header-right { display: flex; align-items: center; gap: 5px; }
.header-right .el-button.is-circle { padding: 6px; }
.more-options-trigger { padding: 5px !important; font-size: 14px !important; border: none !important; background: transparent !important; color: var(--el-text-color-secondary); cursor: pointer; border-radius: 50%; margin-left: 5px; /* Add some space */ }
.more-options-trigger:hover { background-color: var(--el-fill-color-light) !important; color: var(--el-color-primary); }
/* Controls Row */
.controls-row { justify-content: space-between; margin-bottom: 15px; flex-wrap: wrap; gap: 10px; }
.controls-left { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.controls-right { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.controls-left .el-dropdown .el-button, .controls-right .el-dropdown .el-button { padding-left: 10px; padding-right: 10px; }
.controls-right .el-button.is-circle { padding: 6px; /* Ensure toggle button looks okay */ }
.el-icon--right { margin-left: 4px; }
/* Content Row */
.content-row { /* No extra styles needed usually */ }
/* Metrics Styles */
.date-label { font-size: 12px; color: #909399; margin-bottom: 5px; }
.main-metric { font-size: 28px; font-weight: bold; margin-bottom: 8px; line-height: 1.2; color: #303133; }
.metric-unit { font-size: 14px; font-weight: normal; margin-left: 4px; color: #606266; }
.comparison { font-size: 12px; color: #606266; margin-bottom: 4px; display: flex; align-items: center; gap: 4px; }
.comparison .el-icon { vertical-align: text-bottom; }
.comparison .down { color: #f56c6c; }
.comparison .up { color: #67c23a; }
.average { font-size: 12px; color: #909399; margin-top: 8px; }
/* Chart/Table Area */
.chart-container, .table-container { width: 100%; height: 200px; min-height: 180px; }
.report-table .el-table__cell { padding: 4px 0; }
/* Dropdown Styles */
.el-dropdown-menu__item.danger-item { color: var(--el-color-danger); }
.el-dropdown-menu__item.danger-item .el-icon { color: var(--el-color-danger); }
</style>
