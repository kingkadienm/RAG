import request from '@/utils/axios'
import type {
  KnowledgeBase,
  KnowledgeBaseCreateDTO,
  KnowledgeBaseUpdateDTO,
} from '@/types'

export const kbApi = {
  list() {
    return request.get<KnowledgeBase[]>('/knowledge-base/list')
  },

  getById(id: number) {
    return request.get<KnowledgeBase>(`/knowledge-base/${id}`)
  },

  create(data: KnowledgeBaseCreateDTO) {
    return request.post<KnowledgeBase>('/knowledge-base', data)
  },

  update(id: number, data: KnowledgeBaseUpdateDTO) {
    return request.put(`/knowledge-base/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/knowledge-base/${id}`)
  },
}
