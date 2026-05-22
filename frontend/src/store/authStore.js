import { create } from 'zustand'

const useAuthStore = create((set) => ({
  user: null,
  accessToken: localStorage.getItem('accessToken') ?? null,

  setAuth: (user, accessToken) => {
    localStorage.setItem('accessToken', accessToken)
    set({ user, accessToken })
  },

  logout: () => {
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    set({ user: null, accessToken: null })
  },
}))

export default useAuthStore
