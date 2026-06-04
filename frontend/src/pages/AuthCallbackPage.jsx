import { useEffect, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'

function AuthCallbackPage() {
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)
  const processed = useRef(false)

  useEffect(() => {
    if (processed.current) return
    processed.current = true

    const params = new URLSearchParams(window.location.search)
    const accessToken = params.get('accessToken')
    const refreshToken = params.get('refreshToken')

    if (accessToken && refreshToken) {
      localStorage.setItem('refreshToken', refreshToken)
      setAuth(null, accessToken)
      navigate('/', { replace: true })
    } else {
      navigate('/login', { replace: true })
    }
  }, [])

  return (
    <div className="min-h-screen flex flex-col items-center justify-center gap-4 bg-[#f2f4f6]">
      <div className="h-8 w-8 rounded-full border-[3px] border-[#e5e8eb] border-t-[#191f28] animate-spin" />
      <p className="text-sm font-medium text-[#8b95a1]">로그인 중...</p>
    </div>
  )
}

export default AuthCallbackPage
