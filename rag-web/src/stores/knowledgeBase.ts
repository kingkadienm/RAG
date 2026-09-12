import { defineStore } from 'pinia'
import { ref } from 'vue'
import { kbApi } from '@/api/knowledgeBase'
import type { KnowledgeBase } from '@/types'

export const useKbStore = defineStore('kb', () => {
  const knowledgeBases = ref<KnowledgeBase[]>([])
  const loading = ref(false)

  const fetchList = async () => {
    loading.value = true
    try {
      knowledgeBases.value = await kbApi.list()
    } finally {
      loading.value = false
    }
  }

  const create = async (data: { name: string; description?: string }) => {
    const kb = await kbApi.create(data)
    knowledgeBases.value.push(kb)
    return kb
  }

  const remove = async (id: number) => {
    await kbApi.delete(id)
    knowledgeBases.value = knowledgeBases.value.filter((kb) => kb.id !== id)
  }

  const getById = (id: number) => {
    return knowledgeBases.value.find((kb) => kb.id === id)
  }

  return { knowledgeBases, loading, fetchList, create, remove, getById }
})
