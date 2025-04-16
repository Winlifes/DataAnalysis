// src/api/index.js
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 Axios 实例
const api = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 5000,
})

// 请求拦截器：自动带上 token
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 响应拦截器：统一错误处理
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 403) {
      ElMessage.error('登陆过期，请重新登录')
      //localStorage.removeItem('token')
      //localStorage.removeItem('nickname')
      //localStorage.removeItem("isSuperAdmin");
      //window.location.href = '/login'
    } else {
      //ElMessage.error('请求失败')
    }
    return Promise.reject(error)
  }
)

export default api
