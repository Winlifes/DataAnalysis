<template>
  <el-aside :width="asideWidth" class="sidebar">
    <div class="sidebar-header">
      <el-input v-show="!isCollapsed" v-model="sidebarSearch" placeholder="搜索看板" :prefix-icon="Search" clearable size="small" class="search-input"/>
      <el-tooltip content="搜索" placement="right" :disabled="!isCollapsed"><el-button v-show="isCollapsed" :icon="Search" size="small" circle class="search-icon-collapsed" @click="focusSearchWhenExpanded"/></el-tooltip>

      <el-dropdown @command="handleHeaderDropdownCommand" trigger="click" placement="bottom-end">
        <el-button :icon="Operation" :circle="isCollapsed" size="small" class="header-action-dropdown-trigger">
          <span v-show="!isCollapsed" style="margin-left: 4px;">新建 / 管理</span>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="add-dashboard" :icon="DocumentAdd">新建看板</el-dropdown-item>
            <el-dropdown-item command="add-folder" :icon="FolderAdd">新建文件夹</el-dropdown-item>
            <el-dropdown-item command="toggle-manage" :icon="Setting" divided>
                    <span style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
                       管理模式
                       <el-icon v-if="managementModeActive" style="color: var(--el-color-success); margin-left: 8px;"><Select /></el-icon>
                    </span>
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="currentDashboardId"
        class="sidebar-menu"
        :collapse="isCollapsed"
        :collapse-transition="false"
        @select="handleMenuSelect"
        unique-opened
      >
        <template v-for="(section) in filteredMenuData" :key="section.id">
          <li class="menu-section-title" v-if="!isCollapsed && section.title">{{ section.title }}</li>
          <template v-for="folder in section.children" :key="folder.id">
            <el-sub-menu :index="folder.id" class="folder-submenu">
              <template #title>
                <el-icon v-if="folder.icon"><component :is="folder.icon" /></el-icon>
                <span class="item-title">{{ folder.title }}</span>
                <span v-if="managementModeActive" class="management-icons">
                           <el-icon title="重命名" @click.stop="editItem(folder, 'folder')"><EditPen /></el-icon>
                           <el-icon title="删除" @click.stop="deleteItem(folder, 'folder')" class="delete-icon"><Delete /></el-icon>
                      </span>
              </template>
              <el-menu-item v-for="dashboard in folder.children" :key="dashboard.id" :index="dashboard.id" class="dashboard-menu-item">
                <span class="item-title">{{ dashboard.title }}</span>
                <span v-if="managementModeActive" class="management-icons">
                           <el-icon title="重命名" @click.stop="editItem(dashboard, 'dashboard')"><EditPen /></el-icon>
                           <el-icon title="删除" @click.stop="deleteItem(dashboard, 'dashboard')" class="delete-icon"><Delete /></el-icon>
                      </span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar-footer" @click="toggleCollapse"><el-icon><component :is="isCollapsed ? DArrowRight : DArrowLeft" /></el-icon></div>
  </el-aside>
</template>

<script setup>
import { ref, computed, watch, shallowRef, onMounted } from 'vue';
// Element Plus components used
import { ElAside, ElInput, ElButton, ElScrollbar, ElMenu, ElSubMenu, ElMenuItem, ElIcon, ElTooltip, ElDropdown, ElDropdownMenu, ElDropdownItem, ElMessage } from 'element-plus';
// Icons (adjust imports as needed)
import { Search, Plus, FolderAdd, Setting, Star, Folder, DataAnalysis, Memo, User as UserIcon, DataLine, DArrowLeft, DArrowRight, HomeFilled, Briefcase, Warning, Document, EditPen, Delete, Operation, DocumentAdd, Select } from '@element-plus/icons-vue';
import api from "@/api/index.js"; // 引入 axios

// Props (remain the same)
const props = defineProps({
  isCollapsed: { type: Boolean, default: false },
  currentDashboardId: { type: String, default: '' },
  // menuData: { type: Array, default: () => [] } // 注释掉 menuData prop
});

// Emits (remove add-new, keep specific add types)
const emit = defineEmits([
  'toggle-collapse',
  'add-folder',
  'add-dashboard',
  'select-dashboard',
  'edit-item',
  'delete-item'
]);

// --- State ---
const sidebarSearch = ref('');
const managementModeActive = ref(false); // Keep state for management mode
const localMenuData = ref([]); // 用于存储处理后的菜单数据

// --- Computed Properties (remain the same) ---
const asideWidth = computed(() => (props.isCollapsed ? '64px' : '240px'));
const filteredMenuData = computed(() => {
  const search = sidebarSearch.value.toLowerCase().trim();
  if (!search) return localMenuData.value; // 使用 localMenuData

  return localMenuData.value.map(section => {
    const filteredFolders = section.children.map(folder => {
      const folderTitleMatch = folder.title.toLowerCase().includes(search);
      const filteredDashboards = folder.children.filter(dashboard => dashboard.title.toLowerCase().includes(search));
      if (folderTitleMatch || filteredDashboards.length > 0) {
        return { ...folder, children: folderTitleMatch ? folder.children : filteredDashboards };
      }
      return null;
    }).filter(folder => folder !== null && folder.children.length > 0);

    if (filteredFolders.length > 0) {
      return { ...section, children: filteredFolders };
    }
    return null;
  }).filter(section => section !== null);
});

// --- Methods ---
const toggleCollapse = () => emit('toggle-collapse');
const focusSearchWhenExpanded = () => { if (props.isCollapsed) emit('toggle-collapse'); };
const handleMenuSelect = (index, indexPath) => { /* ... same logic to check if dashboard and emit ... */
  let isDashboard = false;
  localMenuData.value.forEach(section => { // 使用 localMenuData
    section.children.forEach(folder => {
      if (folder.children.some(db => db.id === index)) {
        isDashboard = true;
      }
    });
  });
  if (isDashboard) {
    emit('select-dashboard', index);
  }
};

// --- Consolidated Header Dropdown Handler ---
const handleHeaderDropdownCommand = (command) => {
  switch (command) {
    case 'add-dashboard':
      emit('add-dashboard');
      break;
    case 'add-folder':
      emit('add-folder');
      break;
    case 'toggle-manage':
      managementModeActive.value = !managementModeActive.value;
      break;
  }
};

// --- Management Mode Item Handlers ---
const editItem = (item, type) => { emit('edit-item', { id: item.id, type: type, name: item.title }); };
const deleteItem = (item, type) => { emit('delete-item', { id: item.id, type: type, name: item.title }); };
let res = null;
// --- Fetch and Process Menu Data ---
const fetchMenuData = async () => {
  try {
    const response = await api.get('/api/menu/list');
    res = response.data;
    const rawMenuData = response.data;
    const processedMenuData = [];

    const myDashboardsSection = {
      id: 'section-my',
      type: 'section',
      title: '我的看板',
      children: []
    };

    const projectSpaceSection = {
      id: 'section-project',
      type: 'section',
      title: '项目空间',
      children: []
    };

    rawMenuData.forEach(item => {
      console.log(item.icon);
      if (item.icon === 'section-my') {
        item.icon = shallowRef(Star)
        myDashboardsSection.children.push(item); // 设置我的看板下的文件夹图标
      } else if (item.icon === 'section-project') {
        item.icon = shallowRef(Folder)
        projectSpaceSection.children.push(item); // 设置项目空间下的文件夹图标
        }
    });

    processedMenuData.push(myDashboardsSection);
    processedMenuData.push(projectSpaceSection);

    localMenuData.value = processedMenuData;
  } catch (error) {
    console.error('Failed to fetch menu data:', error);
    ElMessage.error('加载菜单数据失败');
  }
};

onMounted(fetchMenuData);

</script>

<style scoped>
/* Keep existing styles, adjust header */
.sidebar { background-color: #ffffff; border-right: 1px solid var(--el-border-color-lighter); display: flex; flex-direction: column; height: 100%; box-sizing: border-box; transition: width 0.3s ease; overflow: hidden; }
.sidebar-header {
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 10px; /* Adjust gap */
  flex-shrink: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  overflow: hidden;
}
.search-input { flex-grow: 1; }
.search-icon-collapsed { /* Styles remain */ }
.header-action-dropdown-trigger {
  margin-left: auto; /* Push dropdown trigger to the right */
  flex-shrink: 0;
}
/* Ensure button doesn't have extra margin when only icon is shown */
.header-action-dropdown-trigger.is-circle {
  margin-left: auto;
}

.menu-scrollbar { flex-grow: 1; height: 0; }
.sidebar-menu:not(.el-menu--collapse) { padding: 0 10px; width: 100%; }
.sidebar-menu { border-right: none; }
.menu-section-title { padding: 10px 20px 5px 20px; font-size: 12px; color: #909399; font-weight: 500; line-height: normal; list-style: none; }
.sidebar-menu.el-menu--collapse .menu-section-title { display: none; }
.sidebar-menu .el-menu-item, .sidebar-menu :deep(.el-sub-menu__title) { height: 40px; line-height: 40px; font-size: 14px; border-radius: 4px; margin: 4px 0; padding-left: 20px !important; display: flex; align-items: center; position: relative; }
.sidebar-menu .el-icon { margin-right: 8px; position: relative; top: 0px; width: 1em; }
.sidebar-menu .el-sub-menu .el-menu-item { height: 36px; line-height: 36px; font-size: 13px; padding-left: 35px !important; }
.sidebar-menu .el-menu-item.is-active { background-color: var(--el-color-primary-light-9); color: var(--el-color-primary); }
.sidebar-menu .el-menu-item:hover, .sidebar-menu :deep(.el-sub-menu__title):hover { background-color: var(--el-fill-color-light); }
/* Collapsed styles remain the same */
.sidebar-menu.el-menu--collapse { width: 100%; }
.sidebar-menu.el-menu--collapse .el-menu-item, .sidebar-menu.el-menu--collapse :deep(.el-sub-menu__title) { justify-content: center; padding-left: 0 !important; padding: 0; }
.sidebar-menu.el-menu--collapse .el-icon:first-child { margin-right: 0; font-size: 18px; margin: 0; }
.sidebar-menu.el-menu--collapse .item-title, .sidebar-menu.el-menu--collapse span:not(.el-tooltip__trigger), .sidebar-menu.el-menu--collapse :deep(.el-sub-menu__icon-arrow) { display: none; }
.sidebar-menu.el-menu--collapse .el-tooltip__trigger { padding: 0 !important; justify-content: center; display: flex; align-items: center; }
/* Management Icons Styling (remains the same) */
.management-icons { position: absolute; right: 5px; top: 50%; transform: translateY(-50%); display: none; gap: 3px; background-color: #fff; padding: 0 3px; border-radius: 3px; z-index: 5; /* Ensure above title */}
.sidebar-menu .el-menu-item:hover .management-icons, .sidebar-menu :deep(.el-sub-menu__title):hover .management-icons { display: inline-flex; align-items: center; }
.sidebar-menu.el-menu--collapse .management-icons { display: none !important; }
.management-icons .el-icon { cursor: pointer; font-size: 12px; padding: 2px; border-radius: 3px; color: var(--el-text-color-secondary); margin: 0 !important; }
.management-icons .el-icon:hover { background-color: var(--el-color-info-light-8); color: var(--el-text-color-primary); }
.management-icons .delete-icon:hover { background-color: var(--el-color-danger-light-8); color: var(--el-color-danger); }
.item-title { flex-grow: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 45px; } /* Ensure space */
:deep(.el-sub-menu__title) .item-title { margin-right: 25px;}
/* Footer */
.sidebar-footer { padding: 10px 0; text-align: center; border-top: 1px solid var(--el-border-color-lighter); flex-shrink: 0; cursor: pointer; color: var(--el-text-color-secondary); line-height: 1; }
.sidebar-footer:hover { color: var(--el-color-primary); background-color: var(--el-fill-color-light); }
.sidebar-footer .el-icon { font-size: 16px; vertical-align: middle; }
</style>
