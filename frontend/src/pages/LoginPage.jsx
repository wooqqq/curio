function LoginPage() {
  const handleKakaoLogin = () => {
    window.location.href = 'http://localhost:8080/oauth2/authorization/kakao'
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
          className="w-full flex items-center justify-center gap-3 bg-[#FEE500] hover:bg-[#F0D800] text-gray-900 font-medium py-3 px-4 rounded-xl transition-colors"
        >
          <img
            src="https://developers.kakao.com/assets/img/about/logos/kakaolink/kakaolink_btn_medium.png"
            alt="kakao"
            className="w-5 h-5"
          />
          카카오로 시작하기
        </button>
      </div>
    </div>
  )
}

export default LoginPage
