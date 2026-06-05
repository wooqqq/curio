import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'

// 토큰은 URL에 실리지 않는다. 카카오 콜백으로 백엔드가 refresh 쿠키를 심어둔 상태이고,
// App 부팅 시 reissue로 access 토큰까지 복구된 뒤 이 화면이 렌더된다 → 상태만 보고 분기.
function AuthCallbackPage() {
  const navigate = useNavigate()
  const accessToken = useAuthStore((s) => s.accessToken)
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true
    navigate(accessToken ? '/' : '/login', { replace: true })
  }, [])

  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4 bg-[#f2f4f6]">
      <div className="h-8 w-8 rounded-full border-[3px] border-[#e5e8eb] border-t-[#191f28] animate-spin" />
      <p className="text-sm font-medium text-[#8b95a1]">로그인 중...</p>
    </div>
  )
}

export default AuthCallbackPage
