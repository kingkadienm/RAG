import request from '@/utils/axios'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  UserInfo,
} from '@/types'

export const authApi = {
  login(data: LoginRequest) {
    return request.post<LoginResponse>('/auth/login', data)
  },

  register(data: RegisterRequest) {
    return request.post('/auth/register', data)
  },

  getUserInfo() {
    return request.get<UserInfo>('/auth/me')
  },
}
