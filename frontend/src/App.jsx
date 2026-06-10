import { useEffect, useRef } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import ArchivePage from './pages/ArchivePage'
import AdminPage from './pages/AdminPage'
import AnnouncementListPage from './pages/AnnouncementListPage'
import AnnouncementDetailPage from './pages/AnnouncementDetailPage'
import client from './api/client'
import useAuthStore from './store/authStore'

function PrivateRoute({ children }) {
  const accessToken = useAuthStore((state) => state.accessToken)
  return accessToken ? children : <Navigate to="/login" replace />
}

function FullScreenLoader() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4 bg-[#f2f4f6]">
      <div className="h-8 w-8 rounded-full border-[3px] border-[#e5e8eb] border-t-[#191f28] animate-spin" />
      <p className="text-sm font-medium text-[#8b95a1]">불러오는 중...</p>
    </div>
  )
}

function App() {
  const setAccessToken = useAuthStore((s) => s.setAccessToken)
  const setBootstrapped = useAuthStore((s) => s.setBootstrapped)
  const bootstrapped = useAuthStore((s) => s.bootstrapped)
  const started = useRef(false)

  // 앱 시작 시 refresh 쿠키로 access 토큰을 1회 복구한다.
  // access는 메모리에만 있어 새로고침하면 사라지므로, 쿠키가 살아있으면 여기서 로그인 상태가 복원된다.
  useEffect(() => {
    if (started.current) return // StrictMode 이중 실행 가드 (쿠키 회전 중복 방지)
    started.current = true

    client
      .post('/auth/reissue', null, { _skipAuthRetry: true })
      .then((res) => setAccessToken(res.data.accessToken))
      .catch(() => {}) // 쿠키 없음/만료 → 비로그인 상태로 진행
      .finally(() => setBootstrapped(true))
  }, [setAccessToken, setBootstrapped])

  if (!bootstrapped) {
    return <FullScreenLoader />
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/auth/callback" element={<AuthCallbackPage />} />
        <Route
          path="/"
          element={
            <PrivateRoute>
              <ArchivePage />
            </PrivateRoute>
          }
        />
        <Route
          path="/announcements"
          element={
            <PrivateRoute>
              <AnnouncementListPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/announcements/:id"
          element={
            <PrivateRoute>
              <AnnouncementDetailPage />
            </PrivateRoute>
          }
        />
        <Route
          path="/admin"
          element={
            <PrivateRoute>
              <AdminPage />
            </PrivateRoute>
          }
        />
      </Routes>
    </BrowserRouter>
  )
}

export default App
