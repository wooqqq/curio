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
    <div className="min-h-screen flex flex-col bg-[#f2f4f6]">
      {/* 히어로 */}
      <div className="flex-1 flex flex-col items-center justify-center px-6">
        <div className="w-full max-w-sm text-center">
          <div className="mx-auto mb-7 flex h-16 w-16 items-center justify-center rounded-[18px] bg-[#191f28] shadow-sm">
            <span className="text-3xl font-extrabold text-white">C</span>
          </div>
          <h1 className="text-[34px] font-extrabold text-[#191f28] tracking-tight">Curio</h1>
          <p className="mt-3 text-[15px] text-[#4e5968] leading-relaxed">
            카카오톡으로 보내기만 하면<br />
            AI가 자동으로 정리해주는 개인 아카이브
          </p>
        </div>
      </div>

      {/* 하단 로그인 버튼 */}
      <div className="px-6 pb-12 pt-4">
        <div className="mx-auto w-full max-w-sm">
          <button
            onClick={handleKakaoLogin}
            disabled={!loginUrl}
            className="w-full flex items-center justify-center gap-2 bg-[#FEE500] hover:bg-[#F5DB00] active:bg-[#EBCD00] disabled:opacity-50 text-[#191f28] font-bold py-3.5 rounded-2xl shadow-sm transition-colors"
          >
            <span>💬</span>
            <span>{loginUrl ? '카카오로 시작하기' : '준비 중...'}</span>
          </button>
          <p className="mt-4 text-center text-xs text-[#8b95a1]">
            카카오 계정으로 간편하게 시작하세요
          </p>
        </div>
      </div>
    </div>
  )
}

export default LoginPage
