import request from '@/utils/axios'
import type { ConfigItem } from '@/types'

export interface ConfigItem {
  id: number
  configKey: string
  configValue: string
  valueType: string
  description: string
  isSystem: number
  createdTime: string
  updatedTime: string
}

export const configApi = {
  /**
   * 查询所有配置
   */
  list() {
    return request.get<ConfigItem[]>('/settings')
  },

  /**
   * 按 key 查询
   */
  getByKey(key: string) {
    return request.get<ConfigItem>(`/settings/${encodeURIComponent(key)}`)
  },

  /**
   * 修改配置值
   */
  update(key: string, configValue: string) {
    return request.put(`/settings/${encodeURIComponent(key)}`, { configValue })
  },

  /**
   * 新增配置项
   */
  create(data: Omit<ConfigItem, 'id' | 'createdTime' | 'updatedTime'>) {
    return request.post<ConfigItem>('/settings', data)
  },

  /**
   * 删除配置项
   */
  delete(id: number) {
    return request.delete(`/settings/${id}`)
  },

  /**
   * 手动刷新配置缓存
   */
  refresh() {
    return request.post('/settings/refresh')
  },
}
