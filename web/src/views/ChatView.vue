<script setup lang="ts">
import { useRoute } from 'vue-router'
import ChatPanel from '../components/ChatPanel.vue'

const embed = useRoute().path === '/embed'
</script>

<template>
  <div class="h-full min-h-0">
    <ChatPanel class="h-full min-h-0" :layout="embed ? 'embed' : 'page'" />
  </div>
</template>
