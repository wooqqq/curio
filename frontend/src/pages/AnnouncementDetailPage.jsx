import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getAnnouncement } from '../api/notice'
import { checkAdmin, deleteAnnouncement } from '../api/admin'

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
  const [isAdmin, setIsAdmin] = useState(false)

  useEffect(() => {
    setLoading(true)
    getAnnouncement(id)
      .then((res) => setAnnouncement(res.data))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [id])

  // 관리자에게만 수정/삭제·팝업 만들기 노출 (백엔드도 독립 검증)
  useEffect(() => {
    checkAdmin().then((r) => setIsAdmin(r.data.admin)).catch(() => {})
  }, [])

  const handleDelete = async () => {
    if (!window.confirm('이 공지를 삭제할까요?')) return
    try {
      await deleteAnnouncement(id)
      navigate('/announcements')
    } catch (err) {
      alert(err?.message || '삭제하지 못했어요.')
    }
  }

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
          <article className="bg-white rounded-2xl px-6 pt-6 pb-4 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_4px_16px_rgba(0,0,0,0.04)]">
            <h1 className="text-2xl font-extrabold text-[#191f28] tracking-tight leading-snug">
              {announcement.title}
            </h1>
            <p className="text-xs text-[#8b95a1] mt-2">{formatDate(announcement.createdAt)}</p>
            <div className="mt-6 text-[15px] text-[#333d4b] leading-relaxed whitespace-pre-wrap">
              {announcement.content}
            </div>

            {isAdmin && (
              <div className="mt-7 pt-5 border-t border-[#f2f4f6] flex items-center justify-between">
                <button
                  onClick={() => navigate(`/admin?tab=popups&link=/announcements/${id}`)}
                  className="text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] py-2 transition-colors"
                >
                  이 공지로 팝업 만들기
                </button>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => navigate(`/admin?tab=announcements&edit=${id}`)}
                    className="text-sm font-semibold text-[#4e5968] bg-[#f2f4f6] hover:bg-[#e5e8eb] px-4 py-2.5 rounded-xl transition-colors"
                  >
                    수정
                  </button>
                  <button
                    onClick={handleDelete}
                    className="text-sm font-semibold text-[#f04452] bg-[#f04452]/10 hover:bg-[#f04452]/15 px-4 py-2.5 rounded-xl transition-colors"
                  >
                    삭제
                  </button>
                </div>
              </div>
            )}
          </article>
        )}
      </main>
    </div>
  )
}

export default AnnouncementDetailPage
