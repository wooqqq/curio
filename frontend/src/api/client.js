import axios from 'axios'
import useAuthStore from '../store/authStore'

const client = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // refresh 쿠키 전송용
})

client.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    const originalRequest = error.config

    // _skipAuthRetry: reissue 요청 자체가 401일 때 재귀 호출되는 것을 막는다.
    if (
      error.response?.status === 401 &&
      !originalRequest._retry &&
      !originalRequest._skipAuthRetry
    ) {
      originalRequest._retry = true
      try {
        // refresh 쿠키는 브라우저가 자동 전송 → body 불필요
        const res = await client.post('/auth/reissue', null, { _skipAuthRetry: true })
        const newAccessToken = res.data.accessToken
        useAuthStore.getState().setAccessToken(newAccessToken)
        originalRequest.headers.Authorization = `Bearer ${newAccessToken}`
        return client(originalRequest)
      } catch {
        useAuthStore.getState().logout()
        window.location.href = '/login'
      }
    }

    return Promise.reject(error.response?.data ?? error)
  }
)

export default client
