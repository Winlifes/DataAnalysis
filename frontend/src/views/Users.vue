<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue';
import TopNav from "@/components/TopNav.vue";
import {
  ElInput,
  ElButton,
  ElTable,
  ElTableColumn,
  ElLoading,
  ElMessage,
  ElCard,
  ElIcon,
  ElSelect,
  ElOption
} from 'element-plus';
import api from "@/api/index.js";
import { User, DataBoard, Coin, Key } from '@element-plus/icons-vue';
import router from "@/router/index.js";

// --- State Variables ---
const searchType = ref('userId');
const searchIdValue = ref('');
const searchUserPropertyKey = ref(''); // This will now store the actual property key (name)
const searchUserPropertyValue = ref('');

const playerDataResults = ref([]);
const loading = ref(false);

const userPropertySchema = ref(null);
const loadingSchema = ref(false);

// --- Computed Properties ---
const searchTypeOptions = ref([
  { label: '用户ID', value: 'userId', icon: User },
  { label: '设备ID', value: 'deviceId', icon: DataBoard },
  { label: '用户属性', value: 'userProperty', icon: Coin }
]);

const searchInputPlaceholder = computed(() => {
  switch (searchType.value) {
    case 'userId':
      return '请输入用户ID';
    case 'deviceId':
      return '请输入设备ID';
    case 'userProperty':
      return '请输入属性值';
    default:
      return '请输入查找内容';
  }
});

// Modify: Extract user property names AND display names from the schema
// This computed property will return an array of objects for the ElSelect options
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


const searchTypeIcon = computed(() => {
  switch (searchType.value) {
    case 'userId':
      return User;
    case 'deviceId':
      return DataBoard;
    case 'userProperty':
      return Coin;
    default:
      return null;
  }
});


// --- Methods ---

const fetchUserPropertySchema = async () => {
  loadingSchema.value = true;
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
    loadingSchema.value = false;
  }
};


const searchPlayerData = async () => {
  const params = {};
  let validationError = null;

  if (searchType.value === 'userId' || searchType.value === 'deviceId') {
    if (!searchIdValue.value) {
      validationError = `请输入要查找的${searchTypeOptions.value.find(opt => opt.value === searchType.value)?.label}`;
    } else {
      params[searchType.value] = searchIdValue.value;
    }
  } else if (searchType.value === 'userProperty') {
    // Now searchUserPropertyKey holds the actual key (name)
    if (!searchUserPropertyKey.value || !searchUserPropertyValue.value) {
      validationError = '请选择属性名并输入属性值进行查找';
    } else {
      params.userPropertyKey = searchUserPropertyKey.value; // Send the actual key (name)
      params.userPropertyValue = searchUserPropertyValue.value;
    }
  }

  if (validationError) {
    ElMessage.warning(validationError);
    return;
  }


  loading.value = true;
  playerDataResults.value = [];

  try {
    const response = await api.get('/api/data/player', { params });

    if (response.data) {
      playerDataResults.value = Array.isArray(response.data) ? response.data : [response.data];

      if (playerDataResults.value.length === 0) {
        let searchCriteria = '';
        if (searchType.value === 'userId' || searchType.value === 'deviceId') {
          searchCriteria = `${searchTypeOptions.value.find(opt => opt.value === searchType.value)?.label}: ${searchIdValue.value}`;
        } else if (searchType.value === 'userProperty') {
          // Display displayName in the info message if available
          const selectedProp = userPropertyOptions.value.find(opt => opt.value === searchUserPropertyKey.value);
          const propLabel = selectedProp ? selectedProp.label : searchUserPropertyKey.value;
          searchCriteria = `用户属性 ${propLabel}=${searchUserPropertyValue.value}`;
        }
        ElMessage.info(`未找到匹配的玩家数据 (${searchCriteria})`);
      }

    } else {
      playerDataResults.value = [];
      let searchCriteria = '';
      if (searchType.value === 'userId' || searchType.value === 'deviceId') {
        searchCriteria = `${searchTypeOptions.value.find(opt => opt.value === searchType.value)?.label}: ${searchIdValue.value}`;
      } else if (searchType.value === 'userProperty') {
        const selectedProp = userPropertyOptions.value.find(opt => opt.value === searchUserPropertyKey.value);
        const propLabel = selectedProp ? selectedProp.label : searchUserPropertyKey.value;
        searchCriteria = `用户属性 ${propLabel}=${searchUserPropertyValue.value}`;
      }
      ElMessage.info(`未找到匹配的玩家数据 (${searchCriteria})`);
    }

    console.log("Player data search results:", playerDataResults.value);

  } catch (error) {
    console.error('Failed to search player data:', error);
    let searchCriteria = '';
    if (searchType.value === 'userId' || searchType.value === 'deviceId') {
      searchCriteria = `${searchTypeOptions.value.find(opt => opt.value === searchType.value)?.label}: ${searchIdValue.value}`;
    } else if (searchType.value === 'userProperty') {
      const selectedProp = userPropertyOptions.value.find(opt => opt.value === searchUserPropertyKey.value);
      const propLabel = selectedProp ? selectedProp.label : searchUserPropertyKey.value;
      searchCriteria = `用户属性 ${propLabel}=${searchUserPropertyValue.value}`;
    }
    if (error.response && error.response.status === 404) {
      ElMessage.info(`未找到匹配的玩家数据 (${searchCriteria})`);
    } else {
      ElMessage.error('查找玩家数据失败');
    }
  } finally {
    loading.value = false;
  }
};

const goToUserDetail = (userId) => {
  if (!userId) {
    console.warn("Attempted to navigate with empty userId.");
    return;
  }
  console.log(`Navigating to detailed page for User ID: ${userId}`);

  // Use router.push to navigate
  // Assuming your user detail route is named 'UserDetail' and accepts a 'userId' param
  router.push({ name: 'UserDetail', params: { userId: userId } });

  // Alternatively, if you don't use named routes but know the path structure:
  // router.push(`/user/${userId}`);
};

const formatUserPropertiesDisplay = (userPropertiesJson) => {
  if (!userPropertiesJson) return '无属性';
  try {
    const properties = JSON.parse(userPropertiesJson);
    if (typeof properties === 'object' && properties !== null) {
      return Object.keys(properties)
        .map(key => {
          let value = properties[key];
          if (typeof value === 'string' && value.length > 50) {
            value = value.substring(0, 50) + '...';
          }
          if (value === null || value === undefined) {
            value = 'null';
          }
          // Find the display name for the key if schema is available
          const propOption = userPropertyOptions.value.find(opt => opt.value === key);
          const label = propOption ? propOption.label : key; // Use display name or key
          return `${label}: ${value}`;
        })
        .join(', ');
    }
    return userPropertiesJson;
  } catch (e) {
    console.error('Failed to parse user properties JSON for display:', e);
    return '无效的属性 JSON';
  }
};

// --- Lifecycle Hook ---
onMounted(() => {
  fetchUserPropertySchema(); // Fetch schema on mount
});

// --- Watcher ---
// Watch for changes in searchType to clear irrelevant input fields
watch(searchType, (newValue, oldValue) => {
  if (newValue === 'userId' || newValue === 'deviceId') {
    searchUserPropertyKey.value = '';
    searchUserPropertyValue.value = '';
  } else if (newValue === 'userProperty') {
    searchIdValue.value = '';
    // Ensure userPropertyKey is cleared if the old type wasn't userProperty
    if (oldValue !== 'userProperty') {
      searchUserPropertyKey.value = '';
    }
  }
  // Also clear search results when search type changes
  playerDataResults.value = [];
});


</script>

<template>
  <TopNav></TopNav>
  <div class="user-lookup-container">
    <el-card class="search-card">
      <template #header>
        <div class="card-header">
          <span>玩家数据细查</span>
        </div>
      </template>
      <div class="search-controls">
        <el-select v-model="searchType" placeholder="选择查找方式" style="width: 150px;">
          <el-option
            v-for="item in searchTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <el-icon style="vertical-align: middle;"><component :is="item.icon" /></el-icon>
            <span style="vertical-align: middle; margin-left: 5px;">{{ item.label }}</span>
          </el-option>
        </el-select>


        <template v-if="searchType === 'userId' || searchType === 'deviceId'">
          <el-input
            v-model="searchIdValue"
            :placeholder="searchInputPlaceholder"
            clearable
            style="width: 250px; margin-left: 10px;"
            @keyup.enter="searchPlayerData"
          />
        </template>
        <template v-else-if="searchType === 'userProperty'">
          <el-select
            v-model="searchUserPropertyKey"
            placeholder="选择属性名"
            clearable
            filterable
            style="width: 180px; margin-left: 10px;"
            :loading="loadingSchema"
            :disabled="userPropertyOptions.length === 0 && !loadingSchema" >
            <el-option
              v-for="prop in userPropertyOptions"
              :key="prop.value" :label="prop.label" :value="prop.value" ></el-option>
          </el-select>
          <el-input
            v-model="searchUserPropertyValue"
            :placeholder="searchInputPlaceholder"
            clearable
            style="width: 250px; margin-left: 10px;"
            @keyup.enter="searchPlayerData"
          />
        </template>


        <el-button
          type="primary"
          style="margin-left: 10px;"
          :loading="loading || loadingSchema"
          @click="searchPlayerData"
          :disabled="loadingSchema || (searchType === 'userProperty' && userPropertyOptions.length === 0)" >
          查找
        </el-button>
      </div>
    </el-card>

    <el-card class="results-card" v-if="playerDataResults.length > 0 || loading">
      <template #header>
        <div class="card-header">
          <span>查找结果</span>
        </div>
      </template>
      <el-table :data="playerDataResults" v-loading="loading" style="width: 100%">
        <el-table-column prop="userId" label="用户ID" width="300">
          <template #default="scope">
            <el-button link type="primary" @click="goToUserDetail(scope.row.userId)">
              {{ scope.row.userId }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="deviceId" label="设备ID" width="200"></el-table-column>
        <el-table-column label="用户属性">
          <template #default="scope">
            {{ formatUserPropertiesDisplay(scope.row.userProperties) }}
          </template>
        </el-table-column>
        <el-table-column prop="lastUpdatedTimestamp" label="最后更新时间" width="180">
          <template #default="scope">
            {{ scope.row.lastUpdatedTimestamp ? new Date(scope.row.lastUpdatedTimestamp).toLocaleString() : '-' }}
          </template>
        </el-table-column>
      </el-table>
    </el-card>

  </div>
</template>

<style scoped>
.user-lookup-container {
  padding: 20px;
}

.search-card, .results-card {
  margin-bottom: 20px;
}

.search-controls {
  display: flex;
  align-items: center;
}

/* Adjust margin for the first input or select after the search type select */
.search-controls .el-input:first-of-type,
.search-controls .el-select:first-of-type {
  margin-left: 10px;
}


.card-header {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

/* Optional: Style for user properties display in table if it gets too long */
/* You might need a tooltip or popover for full JSON */
</style>
