<script setup lang="ts">
import { useRoute } from 'vue-router'
import ChatPanel from '../components/ChatPanel.vue'

const embed = useRoute().path === '/embed'
</script>

<template>
  <ChatPanel :layout="embed ? 'embed' : 'page'" />
</template>
