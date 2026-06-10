import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAnnouncement } from '../api/notice'

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric',
  })
}

function AnnouncementDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [announcement, setAnnouncement] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  useEffect(() => {
    setLoading(true)
    getAnnouncement(id)
      .then((res) => setAnnouncement(res.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [id])

  return (
    <div className="min-h-screen bg-[#f2f4f6]">
      <header className="sticky top-0 z-20 bg-[#f2f4f6]/80 backdrop-blur-md border-b border-black/[0.04]">
        <div className="max-w-2xl mx-auto px-5 h-14 flex items-center gap-2">
          <button
            onClick={() => navigate(-1)}
            aria-label="뒤로"
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#4e5968] hover:bg-black/[0.04] transition-colors"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <span className="text-base font-bold text-[#191f28]">공지사항</span>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-5 pb-16 pt-8">
        {loading ? (
          <p className="text-center text-[#8b95a1] text-sm py-24">불러오는 중...</p>
        ) : error ? (
          <div className="text-center py-24 text-[#8b95a1]">
            <p className="text-5xl mb-4">📭</p>
            <p className="text-[15px] font-semibold text-[#4e5968]">공지를 찾을 수 없어요.</p>
            <button
              onClick={() => navigate('/')}
              className="mt-5 text-sm font-semibold text-[#3182f6] hover:text-[#1b64da]"
            >
              아카이브로 돌아가기
            </button>
          </div>
        ) : (
          <article className="bg-white rounded-2xl p-7 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_4px_16px_rgba(0,0,0,0.04)]">
            <h1 className="text-2xl font-extrabold text-[#191f28] tracking-tight leading-snug">
              {announcement.title}
            </h1>
            <p className="text-xs text-[#8b95a1] mt-2">{formatDate(announcement.createdAt)}</p>
            <div className="mt-6 text-[15px] text-[#333d4b] leading-relaxed whitespace-pre-wrap">
              {announcement.content}
            </div>
          </article>
        )}
      </main>
    </div>
  )
}

export default AnnouncementDetailPage
