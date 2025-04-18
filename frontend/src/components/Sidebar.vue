<template>
  <el-aside :width="asideWidth" class="sidebar">
    <div class="sidebar-header">
      <el-input
        v-show="!isCollapsed"
        v-model="sidebarSearch"
        placeholder="搜索看板"
        :prefix-icon="Search"
        clearable
        size="small"
        class="search-input"
      />
      <el-tooltip content="搜索" placement="right" :disabled="!isCollapsed">
        <el-button
          v-show="isCollapsed"
          :icon="Search"
          size="small"
          circle
          class="search-icon-collapsed"
          @click="focusSearchWhenExpanded"
        />
      </el-tooltip>

      <el-dropdown @command="handleHeaderDropdownCommand" trigger="click" placement="bottom-end">
        <el-button
          :icon="Operation"
          :circle="isCollapsed"
          size="small"
          class="header-action-dropdown-trigger"
        >
          <span v-show="!isCollapsed" style="margin-left: 4px;">新建 / 管理</span>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="add-dashboard" :icon="DocumentAdd">新建看板</el-dropdown-item>
            <el-dropdown-item command="add-folder" :icon="FolderAdd">新建文件夹</el-dropdown-item>
            <el-dropdown-item command="toggle-manage" :icon="Setting" divided>
              <span
                style="display: flex; align-items: center; justify-content: space-between; width: 100%;"
              >
                管理模式
                <el-icon
                  v-if="managementModeActive"
                  style="color: var(--el-color-success); margin-left: 8px;"
                >
                  <Select />
                </el-icon>
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
        <template v-for="section in filteredMenuData" :key="section.id">
          <li v-if="!isCollapsed && section.title" class="menu-section-title">{{ section.title }}</li>
          <template v-for="folder in section.children" :key="folder.id">
            <el-sub-menu :index="folder.id" class="folder-submenu">
              <template #title>
                <el-icon v-if="folder.icon"><component :is="folder.icon" /></el-icon>
                <span class="item-title">{{ folder.title }}</span>
                <!-- 文件夹操作下拉 -->
                <el-dropdown
                  v-if="managementModeActive && !isCollapsed"
                  @command="cmd => onItemCommand(folder, 'folder', cmd)"
                  trigger="click"
                  placement="bottom-end"
                >
                  <span class="item-actions">
                    <el-icon><MoreFilled /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </template>

              <el-menu-item
                v-for="dashboard in folder.children"
                :key="dashboard.id"
                :index="dashboard.id"
                class="dashboard-menu-item"
              >
                <span class="item-title">{{ dashboard.title }}</span>
                <!-- 仪表板操作下拉 -->
                <el-dropdown
                  v-if="managementModeActive && !isCollapsed"
                  @command="cmd => onItemCommand(dashboard, 'dashboard', cmd)"
                  trigger="click"
                  placement="bottom-end"
                >
                  <span class="item-actions">
                    <el-icon><MoreFilled /></el-icon>
                  </span>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item command="move">移动</el-dropdown-item>
                      <el-dropdown-item command="rename">重命名</el-dropdown-item>
                      <el-dropdown-item command="delete">删除</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </template>
      </el-menu>
    </el-scrollbar>

    <div class="sidebar-footer" @click="toggleCollapse">
      <el-icon><component :is="isCollapsed ? DArrowRight : DArrowLeft" /></el-icon>
    </div>
  </el-aside>
</template>

<script setup>
import { ref, computed } from 'vue';
import {
  ElAside,
  ElInput,
  ElButton,
  ElScrollbar,
  ElMenu,
  ElSubMenu,
  ElMenuItem,
  ElIcon,
  ElTooltip,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem
} from 'element-plus';
import {
  Search,
  FolderAdd,
  Setting,
  DArrowLeft,
  DArrowRight,
  DocumentAdd,
  Operation,
  MoreFilled,
  Select
} from '@element-plus/icons-vue';

// Props
const props = defineProps({
  isCollapsed: { type: Boolean, default: false },
  currentDashboardId: { type: String, default: '' },
  menuData: { type: Array, default: () => [] }
});

// Emits
const emit = defineEmits([
  'toggle-collapse',
  'add-folder',
  'add-dashboard',
  'select-dashboard',
  'edit-item',
  'delete-item',
  'move-item'
]);

// State
const sidebarSearch = ref('');
const managementModeActive = ref(false);

// Computed
const asideWidth = computed(() => (props.isCollapsed ? '64px' : '240px'));
const filteredMenuData = computed(() => {
  const search = sidebarSearch.value.toLowerCase().trim();
  if (!search) return props.menuData;
  return props.menuData
    .map(section => {
      const children = section.children
        .map(folder => {
          const matchFolder = folder.title.toLowerCase().includes(search);
          const filteredDashboards = folder.children.filter(db =>
            db.title.toLowerCase().includes(search)
          );
          if (matchFolder || filteredDashboards.length) {
            return { ...folder, children: matchFolder ? folder.children : filteredDashboards };
          }
          return null;
        })
        .filter(f => f && f.children.length);
      return children.length ? { ...section, children } : null;
    })
    .filter(s => s);
});

// Methods
const toggleCollapse = () => emit('toggle-collapse');
const focusSearchWhenExpanded = () => {
  if (props.isCollapsed) emit('toggle-collapse');
};
const handleMenuSelect = index => {
  let isDashboard = false;
  props.menuData.forEach(section =>
    section.children.forEach(folder =>
      folder.children.some(db => {
        if (db.id === index) isDashboard = true;
      })
    )
  );
  if (isDashboard) emit('select-dashboard', index);
};
const handleHeaderDropdownCommand = cmd => {
  if (cmd === 'add-dashboard') emit('add-dashboard');
  else if (cmd === 'add-folder') emit('add-folder');
  else if (cmd === 'toggle-manage') managementModeActive.value = !managementModeActive.value;
};
const onItemCommand = (item, type, command) => {
  if (command === 'move') emit('move-item', { id: item.id, type, name: item.title });
  else if (command === 'rename') emit('edit-item', { id: item.id, type, name: item.title });
  else if (command === 'delete') emit('delete-item', { id: item.id, type, name: item.title });
};
</script>

<style scoped>
.sidebar {
  background-color: #ffffff;
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  height: 100%;
  transition: width 0.3s ease;
  overflow: hidden;
}
.sidebar-header {
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.search-input {
  flex-grow: 1;
}
.header-action-dropdown-trigger {
  margin-left: auto;
}
.menu-scrollbar {
  flex-grow: 1;
  height: 0;
}
.sidebar-menu {
  border-right: none;
  padding: 0 10px;
}
.menu-section-title {
  padding: 10px 20px 5px;
  font-size: 12px;
  color: #909399;
  font-weight: 500;
  list-style: none;
}
.dashboard-menu-item,
.sidebar-menu :deep(.el-sub-menu__title) {
  height: 40px;
  line-height: 40px;
  border-radius: 4px;
  margin: 4px 0;
  padding-left: 10px;
  display: flex;
  align-items: center;
  position: relative;
}
.sidebar-menu .el-icon {
  margin-right: 8px;
}
.sidebar-menu .el-sub-menu .el-menu-item {
  height: 36px;
  line-height: 36px;
  padding-left: 35px !important;
}
.el-menu-item.is-active {
  background-color: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.el-menu-item:hover,
.sidebar-menu :deep(.el-sub-menu__title):hover {
  background-color: var(--el-fill-color-light);
}

/* 调整后的更多按钮样式 */
.item-actions {
  position: absolute;
  right: -40px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px; /* 增加点击区域 */
}
.item-actions .el-icon {
  font-size: 16px;
}

.sidebar-footer {
  padding: 10px 0;
  text-align: center;
  border-top: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  color: var(--el-text-color-secondary);
}
.sidebar-footer:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}
.sidebar-footer .el-icon {
  font-size: 16px;
  vertical-align: middle;
}
</style>
