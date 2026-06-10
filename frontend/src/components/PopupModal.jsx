import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getActivePopup } from '../api/notice'

const DISMISS_KEY = 'curio_popup_dismiss'

function todayStr() {
  return new Date().toISOString().slice(0, 10) // YYYY-MM-DD
}

// 아카이브 진입 시 활성 팝업 1개를 노출. 이미지/내용 클릭 시 linkUrl로 이동.
// "오늘 하루 보지 않기"는 서버 read-state 없이 localStorage(팝업 id + 날짜)로 처리한다.
function PopupModal() {
  const [popup, setPopup] = useState(null)
  const navigate = useNavigate()

  useEffect(() => {
    getActivePopup()
      .then((res) => {
        const p = res.data
        if (!p) return
        const raw = localStorage.getItem(DISMISS_KEY)
        if (raw) {
          try {
            const { id, date } = JSON.parse(raw)
            if (id === p.id && date === todayStr()) return // 오늘 이미 닫음
          } catch { /* 파싱 실패 시 그냥 노출 */ }
        }
        setPopup(p)
      })
      .catch(() => {}) // 팝업 조회 실패는 조용히 무시 (아카이브 진입을 막지 않음)
  }, [])

  if (!popup) return null

  const close = () => setPopup(null)

  const dismissToday = () => {
    localStorage.setItem(DISMISS_KEY, JSON.stringify({ id: popup.id, date: todayStr() }))
    setPopup(null)
  }

  const hasLink = Boolean(popup.linkUrl)
  const goLink = () => {
    if (!hasLink) return
    if (/^https?:\/\//i.test(popup.linkUrl)) {
      window.open(popup.linkUrl, '_blank', 'noopener')
    } else {
      navigate(popup.linkUrl) // 내부 경로 (예: /announcements/3)
    }
    close()
  }

  return (
    <div
      className="fixed inset-0 bg-black/40 backdrop-blur-sm flex items-end sm:items-center justify-center z-50"
      onClick={close}
    >
      <div
        className="bg-white w-full sm:w-[28rem] rounded-t-3xl sm:rounded-3xl overflow-hidden shadow-2xl animate-[slideup_0.25s_ease]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 이미지·본문 어디를 눌러도 linkUrl로 이동 (linkUrl 없으면 클릭 비활성) */}
        {popup.imageUrl && (
          <div onClick={goLink} className={hasLink ? 'cursor-pointer' : ''}>
            <img
              src={popup.imageUrl}
              alt={popup.title}
              className="w-full max-h-[60vh] object-cover bg-[#f2f4f6]"
              onError={(e) => { e.currentTarget.style.display = 'none' }}
            />
          </div>
        )}

        <div className="px-6 pt-5 pb-1">
          <div className="mx-auto mb-4 h-1 w-10 rounded-full bg-[#e5e8eb] sm:hidden" />
          <div onClick={goLink} className={hasLink ? 'cursor-pointer' : ''}>
            <h2 className="text-base font-bold text-[#191f28] mb-1">{popup.title}</h2>
            {popup.content && (
              <p className="text-sm text-[#6b7684] leading-relaxed whitespace-pre-wrap">{popup.content}</p>
            )}
          </div>
        </div>

        <div className="flex items-center justify-between border-t border-[#f2f4f6] px-5 py-1.5">
          <button
            onClick={dismissToday}
            className="text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] py-2 transition-colors"
          >
            오늘 하루 보지 않기
          </button>
          <button
            onClick={close}
            className="text-sm font-semibold text-[#4e5968] hover:text-[#191f28] py-2 transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}

export default PopupModal
