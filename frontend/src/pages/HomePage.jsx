import { useState } from 'react'
import client from '../api/client'
import useAuthStore from '../store/authStore'
import { logout } from '../api/auth'

function HomePage() {
  const [linkCode, setLinkCode] = useState(null)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState(null)
  const logoutStore = useAuthStore((state) => state.logout)

  const handleGetLinkCode = async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await client.get('/user/link-code')
      // interceptor가 response.data를 반환하므로 res = { code, data, message }
      setLinkCode(res.data.code)
    } catch (e) {
      setError('코드 발급에 실패했습니다. 백엔드 서버가 실행 중인지 확인하세요.')
    } finally {
      setLoading(false)
    }
  }

  const handleCopy = () => {
    if (!linkCode) return
    navigator.clipboard.writeText(linkCode).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  const handleLogout = async () => {
    try {
      await logout()
    } catch (_) {}
    logoutStore()
    window.location.href = '/login'
  }

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-10">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">Curio</h1>
          <p className="text-gray-500 text-sm">카톡으로 보내기만 하면 AI가 자동 정리</p>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 mb-4">
          <h2 className="text-base font-semibold text-gray-800 mb-1">카카오톡 봇 연동</h2>
          <p className="text-sm text-gray-500 mb-4">
            연동 코드를 발급받아 카카오 채널에 전송하면 봇이 연결됩니다.
          </p>

          {error && (
            <p className="text-sm text-red-500 mb-3">{error}</p>
          )}

          {!linkCode ? (
            <button
              onClick={handleGetLinkCode}
              disabled={loading}
              className="w-full bg-[#FEE500] hover:bg-[#F0D800] disabled:opacity-50 text-gray-900 font-medium py-3 px-4 rounded-xl transition-colors"
            >
              {loading ? '발급 중...' : '연동 코드 발급받기'}
            </button>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-2 bg-gray-50 rounded-xl px-4 py-3 border border-gray-200">
                <span className="flex-1 text-center text-2xl font-bold tracking-widest text-gray-900">
                  {linkCode}
                </span>
                <button
                  onClick={handleCopy}
                  className="text-sm text-gray-500 hover:text-gray-800 transition-colors whitespace-nowrap"
                >
                  {copied ? '복사됨' : '복사'}
                </button>
              </div>
              <p className="text-xs text-gray-400 text-center">
                10분 내에 카카오 채널에 이 코드를 전송하세요
              </p>
              <button
                onClick={handleGetLinkCode}
                disabled={loading}
                className="w-full text-sm text-gray-500 hover:text-gray-700 py-2 transition-colors"
              >
                재발급
              </button>
            </div>
          )}
        </div>

        <button
          onClick={handleLogout}
          className="w-full text-sm text-gray-400 hover:text-gray-600 py-2 transition-colors"
        >
          로그아웃
        </button>
      </div>
    </div>
  )
}

export default HomePage