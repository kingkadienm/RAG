/* ========== 用户相关 ========== */
export interface LoginRequest {
  username: string
  password: string
}

/** 后端返回: ApiResult<data: { token, userId, username, nickname, role }> */
export interface LoginResponse {
  token: string
  userId: number
  username: string
  nickname: string
  role: number
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  role: number
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

/* ========== 知识库相关 ========== */
export interface KnowledgeBase {
  id: number
  name: string
  description: string
  creatorId: number
  status: number
  createdTime: string
  updatedTime: string
  deleted: number
  // documentCount?: number
}

export interface KnowledgeBaseCreateDTO {
  name: string
  description?: string
}

export interface KnowledgeBaseUpdateDTO {
  name?: string
  description?: string
  status?: number
}

/* ========== 文档相关 ========== */
export type ParseStatus = 0 | 1 | 2 | 3
export type VectorStatus = 0 | 1 | 2 | 3

export const PARSE_STATUS_MAP: Record<number, { label: string; type: string }> = {
  0: { label: '待解析', type: 'info' },
  1: { label: '解析中', type: 'warning' },
  2: { label: '解析成功', type: 'success' },
  3: { label: '解析失败', type: 'danger' },
}

export const VECTOR_STATUS_MAP: Record<number, { label: string; type: string }> = {
  0: { label: '待向量化', type: 'info' },
  1: { label: '向量化中', type: 'warning' },
  2: { label: '向量化完成', type: 'success' },
  3: { label: '向量化失败', type: 'danger' },
}

export interface Document {
  id: number
  kbId: number
  title: string
  fileName: string
  fileType: string
  fileSize: number
  filePath: string
  fileMd5: string
  parseStatus: ParseStatus
  vectorStatus: VectorStatus
  chunkCount: number
  vectorCount: number
  errorMsg: string
  version: number
  creatorId: number
  createdTime: string
  updatedTime: string
  deleted: number
}

export interface DocumentListParams {
  kbId?: number
  pageNum?: number
  pageSize?: number
}

export interface DocumentListResponse {
  list: Document[]
  total: number
}

export interface ChunkVO {
  index: number
  content: string
  length: number
}

/* ========== 上传相关 ========== */
export interface UploadResponse {
  docId: number
  fileName: string
  fileSize: number
  parseStatus: number
  message: string
}

export interface FileUploadVO {
  fileName: string
  filePath: string
  fileSize: number
}

/* ========== 聊天相关 ========== */
export interface ChatRequest {
  sessionId?: string
  question: string
  kbIds?: number[]
}

/** 后端 ChatResponse: sessionId, answer, references[] */
export interface ChatResponse {
  sessionId: string
  answer: string
  references?: ChatReference[]
}

export interface ChatReference {
  docId: number
  fileName: string
  content: string
  score: number
}

export interface ChatStreamResult {
  content: string
  sessionId: string
  refChunks?: ChatReference[] | null
}

export interface ChatMessageVO {
  id: number
  sessionId: string
  role: number
  content: string
  refChunks?:  ChatReference[] | null
  tokenCount?: number
  createdTime: string
}

export interface ChatSessionVO {
  id: number
  sessionId: string
  kbId: number
  title: string
  messageCount: number
  createdTime: string
  updatedTime: string
}

/* ========== 统一响应格式（后端 ApiResult） ========== */
export interface ApiResult<T = any> {
  code: number
  message: string
  data: T
}

/* ========== 系统配置相关 ========== */
export interface ConfigItem {
  id: number
  configKey: string
  configValue: string
  valueType: string
  description: string
  isSystem: number
  createdTime: string
  updatedTime: string
  deleted: number
  /** @internal 前端编辑用，不参与序列化 */
  _editingValue?: string
}
