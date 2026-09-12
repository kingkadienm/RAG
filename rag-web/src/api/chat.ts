import request from '@/utils/axios'
import type { ChatRequest, ChatResponse, ChatSessionVO } from '@/types'
import { useAuthStore } from '@/stores/auth'

export const chatApi = {
  chat(data: ChatRequest) {
    return request.post<ChatResponse>('/chat/completions', data)
  },

  /**
   * 流式 SSE 对话
   * 使用原生 fetch 以支持 ReadableStream，不经过 axios 拦截器
   * SSE 格式：data: <content>\n\n
   * @param onChunk 实时接收到文本块时的回调（用于前端逐字显示）
   */
  async chatStream(data: ChatRequest, onChunk?: (chunk: string) => void): Promise<string> {
    const authStore = useAuthStore()
    const token = authStore.token

    const response = await fetch('/api/chat/completions/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(data),
    })

    if (!response.ok) {
      const text = await response.text()
      throw new Error(text || `HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('浏览器不支持 ReadableStream')
    }

    const decoder = new TextDecoder()
    let fullContent = ''
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按 SSE 格式解析：data: <content>\n\n
      const lines = buffer.split('\n')
      // 保留最后一个可能不完整的行
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed || !trimmed.startsWith('data: ')) continue
        const content = trimmed.slice(6) // 去掉 "data: " 前缀
        // 忽略 SSE 错误事件
        if (content.includes('"error"')) continue
        fullContent += content
        onChunk?.(content)
      }
    }

    // 处理 buffer 中剩余的最后一行
    const lastLine = buffer.trim()
    if (lastLine.startsWith('data: ') && !lastLine.includes('"error"')) {
      const content = lastLine.slice(6)
      fullContent += content
      onChunk?.(content)
    }

    return fullContent
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
