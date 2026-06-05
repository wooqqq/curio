import { create } from 'zustand'

// access 토큰은 메모리에만 보관한다 (localStorage 사용 안 함 → XSS로 탈취 불가).
// refresh 토큰은 백엔드의 httpOnly 쿠키에 있어 JS가 접근하지 못한다.
const useAuthStore = create((set) => ({
  user: null,
  accessToken: null,
  // 새로고침 시 쿠키로 세션 복구(reissue)가 끝나기 전까지 라우팅 판단을 보류하기 위한 플래그
  bootstrapped: false,

  setAuth: (user, accessToken) => set({ user, accessToken }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setBootstrapped: (bootstrapped) => set({ bootstrapped }),

  logout: () => set({ user: null, accessToken: null }),
}))

export default useAuthStore
