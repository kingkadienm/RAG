import request from '@/utils/axios'
import type {ChatRequest, ChatResponse, ChatSessionVO, ChatStreamResult} from '@/types'
import {useAuthStore} from '@/stores/auth'

export const chatApi = {
    chat(data: ChatRequest) {
        return request.post<ChatResponse>('/chat/completions', data)
    },

    async chatStream(
        data: ChatRequest,
        onChunk?: (chunk: string) => void
    ): Promise<ChatStreamResult> {
        const authStore = useAuthStore()
        const token = authStore.token

        const response = await fetch('/api/chat/completions/stream', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'text/event-stream',
                ...(token ? { Authorization: `Bearer ${token}` } : {}),
            },
            body: JSON.stringify(data),
        })

        if (!response.ok) {
            const text = await response.text()
            throw new Error(text || `HTTP ${response.status}`)
        }

        if (!response.body) {
            throw new Error('浏览器不支持 ReadableStream')
        }

        const reader = response.body.getReader()
        const decoder = new TextDecoder('utf-8')

        let buffer = ''
        let fullContent = ''
        let sessionId = data.sessionId || ''
        let refChunksData: any = null

        const processEvent = (event: string) => {
            const lines = event.split(/\r?\n/)

            const dataLines: string[] = []

            for (const line of lines) {
                if (line.startsWith('data:')) {
                    // 只去掉 data:，不要 trim 正文
                    let content = line.slice(5)

                    // SSE 规范允许 data: 后面跟一个空格
                    if (content.startsWith(' ')) {
                        content = content.slice(1)
                    }

                    dataLines.push(content)
                }
            }

            if (dataLines.length === 0) {
                return
            }

            const content = dataLines.join('\n')

            // 检查是否是参考文档数据
            const refChunksMatch = content.match(/^\[REF_CHUNKS:(.+)\]$/)
            if (refChunksMatch) {
                // 提取参考文档 JSON 数据
                try {
                    refChunksData = JSON.parse(refChunksMatch[1])
                } catch (e) {
                    console.error('[Chat] 解析参考文档数据失败:', e)
                }
                return
            }

            if (content === '[DONE]') {
                return
            }

            const sessionMatch = content.match(
                /^\[SESSION_ID:([^\]]+)\]$/
            )

            if (sessionMatch) {
                sessionId = sessionMatch[1]
                return
            }

            fullContent += content
            onChunk?.(content)
        }

        while (true) {
            const { done, value } = await reader.read()

            if (done) {
                break
            }

            buffer += decoder.decode(value, { stream: true })

            // SSE event 之间使用空行分隔
            const events = buffer.split(/\r?\n\r?\n/)

            // 最后一段可能是不完整的 event
            buffer = events.pop() || ''

            for (const event of events) {
                processEvent(event)
            }
        }

        // 处理 decoder 剩余内容
        buffer += decoder.decode()

        if (buffer.trim()) {
            processEvent(buffer)
        }

        return {
            content: fullContent,
            sessionId,
            refChunks: refChunksData
        }
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
