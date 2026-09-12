import request from '@/utils/axios'
import type { ChatRequest, ChatResponse, ChatSessionVO } from '@/types'

export const chatApi = {
  chat(data: ChatRequest) {
    return request.post<ChatResponse>('/chat/completions', data)
  },

  getSessions() {
    return request.get<ChatSessionVO[]>('/chat/sessions')
  },

  getMessages(sessionId: string) {
    return request.get(`/chat/sessions/${sessionId}/messages`)
  },

  deleteSession(sessionId: string) {
    return request.delete(`/chat/sessions/${sessionId}`)
  },
}
