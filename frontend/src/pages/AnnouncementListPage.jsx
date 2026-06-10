import { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { getAnnouncements } from '../api/notice'

function formatDate(dateStr) {
  return new Date(dateStr).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}

function AnnouncementListPage() {
  const navigate = useNavigate()
  const [list, setList] = useState([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    getAnnouncements()
      .then((res) => setList(res.data))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div className="min-h-screen bg-[#f2f4f6]">
      <header className="sticky top-0 z-20 bg-[#f2f4f6]/80 backdrop-blur-md border-b border-black/[0.04]">
        <div className="max-w-2xl mx-auto px-5 h-14 flex items-center gap-2">
          <button
            onClick={() => navigate('/')}
            aria-label="아카이브로"
            className="flex h-9 w-9 items-center justify-center rounded-full text-[#4e5968] hover:bg-black/[0.04] transition-colors"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M15 18l-6-6 6-6" />
            </svg>
          </button>
          <span className="text-base font-bold text-[#191f28]">공지사항</span>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-5 pb-16 pt-6">
        {loading ? (
          <p className="text-center text-sm text-[#8b95a1] py-24">불러오는 중...</p>
        ) : list.length === 0 ? (
          <div className="text-center py-24 text-[#8b95a1]">
            <p className="text-5xl mb-4">📭</p>
            <p className="text-[15px] font-semibold text-[#4e5968]">등록된 공지가 없어요.</p>
          </div>
        ) : (
          <div className="space-y-2.5">
            {list.map((a) => (
              <Link
                key={a.id}
                to={`/announcements/${a.id}`}
                className="block bg-white rounded-2xl px-4 py-3.5 shadow-[0_1px_2px_rgba(0,0,0,0.04)] hover:-translate-y-0.5 hover:shadow-[0_2px_4px_rgba(0,0,0,0.06),0_12px_28px_rgba(0,0,0,0.08)] transition-all duration-200"
              >
                <p className="text-[15px] font-bold text-[#191f28] truncate">{a.title}</p>
                <p className="text-xs text-[#8b95a1] mt-1">{formatDate(a.createdAt)}</p>
              </Link>
            ))}
          </div>
        )}
      </main>
    </div>
  )
}

export default AnnouncementListPage
