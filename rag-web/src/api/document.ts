import request from '@/utils/axios'
import type {
  Document,
  DocumentListParams,
  DocumentListResponse,
  UploadResponse,
  ChunkVO,
} from '@/types'

export const documentApi = {
  list(kbId: number, params: DocumentListParams) {
    return request.get<DocumentListResponse>(`/knowledge-base/documents/${kbId}`, { params })
  },

  getById(id: number) {
    return request.get<Document>(`/doc/${id}`)
  },

  getChunks(id: number) {
    return request.get<ChunkVO[]>(`/doc/${id}/chunks`)
  },

  delete(id: number) {
    return request.delete(`/doc/${id}`)
  },

  retry(id: number) {
    return request.post(`/doc/${id}/retry`)
  },

  upload(file: File, kbId: number): Promise<UploadResponse> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kbId', String(kbId))
    return request.post('/upload/file', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
