<template>
  <div ref="chartRef" style="width: 100%; height: 400px;"></div>
</template>

<script setup>
import * as echarts from 'echarts'
import { onMounted, ref } from 'vue'

const chartRef = ref(null)

onMounted(() => {
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '每日新增玩家' },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: ['2025-03-26', '2025-03-27', '2025-03-28', '2025-03-29', '2025-03-30', '2025-03-31', '2025-04-01']
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '新增玩家数',
        type: 'line',
        data: [15, 12, 6, 7, 8, 5, 10]
      }
    ]
  })
})
</script>
