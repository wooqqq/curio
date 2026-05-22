import { useEffect, useState } from 'react'
import client from '../api/client'

function LoginPage() {
  const [loginUrl, setLoginUrl] = useState(null)

  useEffect(() => {
    client.get('/auth/kakao/login-url').then((res) => {
      setLoginUrl(res.data)
    })
  }, [])

  const handleKakaoLogin = () => {
    if (loginUrl) window.location.href = loginUrl
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-50">
      <div className="w-full max-w-sm px-6">
        <div className="text-center mb-10">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">Curio</h1>
          <p className="text-gray-500 text-sm">카톡으로 보내기만 하면 AI가 자동 정리</p>
        </div>

        <button
          onClick={handleKakaoLogin}
          disabled={!loginUrl}
          className="w-full flex items-center justify-center gap-3 bg-[#FEE500] hover:bg-[#F0D800] disabled:opacity-50 text-gray-900 font-medium py-3 px-4 rounded-xl transition-colors"
        >
          카카오로 시작하기
        </button>
      </div>
    </div>
  )
}

export default LoginPage
