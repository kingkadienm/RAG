import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api/auth'
import type { LoginRequest, LoginResponse } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('token') || '')
  const userInfo = ref<Partial<LoginResponse> | null>(null)

  const isLoggedIn = () => !!token.value

  const setToken = (t: string) => {
    token.value = t
    localStorage.setItem('token', t)
  }

  const setUserInfo = (info: Partial<LoginResponse>) => {
    userInfo.value = info
  }

  const login = async (data: LoginRequest) => {
    // axios interceptor returns ApiResult as the resolved value
    const res = await authApi.login(data)
    // res is ApiResult<{ token, userId, username, nickname, role }>
    const data_ = res.data
    setToken(data_.token)
    setUserInfo(data_)
    return data_
  }

  const logout = () => {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
  }

  return { token, userInfo, isLoggedIn, setToken, setUserInfo, login, logout }
})
