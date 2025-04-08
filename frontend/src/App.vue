<script setup>
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="app-container">

    <main class="content">
      <RouterView />
    </main>

  </div>
</template>

<style scoped>
.app-container {
  display: flex;
  flex-direction: column;
}

</style>
