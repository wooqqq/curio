import { useState, useEffect, useCallback } from 'react'
import { getItems } from '../api/items'
import { logout } from '../api/auth'
import client from '../api/client'
import useAuthStore from '../store/authStore'

const TYPE_ICON = {
  LINK: '🔗',
  IMAGE: '🖼️',
  TEXT: '📝',
}

function LinkCodeModal({ onClose }) {
  const [linkCode, setLinkCode] = useState(null)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState(null)

  const handleGet = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await client.get('/user/link-code')
      setLinkCode(res.data.code)
    } catch {
      setError('발급에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { handleGet() }, [])

  const handleCopy = () => {
    if (!linkCode) return
    navigator.clipboard.writeText(linkCode).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={onClose}>
      <div className="bg-white rounded-2xl p-6 w-80 shadow-xl" onClick={e => e.stopPropagation()}>
        <h2 className="text-base font-semibold text-gray-800 mb-1">카카오톡 봇 연동</h2>
        <p className="text-sm text-gray-500 mb-4">코드를 카카오 채널에 전송하면 봇이 연결됩니다.</p>

        {error && <p className="text-sm text-red-500 mb-3">{error}</p>}

        {linkCode ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 bg-gray-50 rounded-xl px-4 py-3 border border-gray-200">
              <span className="flex-1 text-center text-2xl font-bold tracking-widest text-gray-900">{linkCode}</span>
              <button onClick={handleCopy} className="text-sm text-gray-500 hover:text-gray-800">
                {copied ? '복사됨' : '복사'}
              </button>
            </div>
            <p className="text-xs text-gray-400 text-center">10분 내에 카카오 채널에 전송하세요</p>
            <button onClick={handleGet} disabled={loading} className="w-full text-sm text-gray-400 hover:text-gray-600 py-1">재발급</button>
          </div>
        ) : (
          <p className="text-center text-gray-400 py-4">{loading ? '발급 중...' : '-'}</p>
        )}

        <button onClick={onClose} className="mt-4 w-full text-sm text-gray-400 hover:text-gray-600 py-1">닫기</button>
      </div>
    </div>
  )
}

function ItemCard({ item }) {
  const handleClick = () => {
    if (item.originalUrl) window.open(item.originalUrl, '_blank', 'noopener')
  }

  return (
    <div
      onClick={handleClick}
      className={`bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden ${item.originalUrl ? 'cursor-pointer hover:shadow-md transition-shadow' : ''}`}
    >
      {item.thumbnailUrl && (
        <img src={item.thumbnailUrl} alt={item.title} className="w-full h-40 object-cover" />
      )}
      <div className="p-4">
        <div className="mb-1">
          <span className="text-lg">{TYPE_ICON[item.type]}</span>
        </div>
        {item.title && (
          <p className="font-medium text-gray-900 text-sm leading-snug mb-1 line-clamp-2">{item.title}</p>
        )}
        {item.aiSummary && (
          <p className="text-xs text-gray-500 line-clamp-2 mb-2">{item.aiSummary}</p>
        )}
        {!item.title && item.content && (
          <p className="text-sm text-gray-700 line-clamp-3 mb-2">{item.content}</p>
        )}
        {item.tags.length > 0 && (
          <div className="flex flex-wrap gap-1 mt-2">
            {item.tags.map(tag => (
              <span key={tag} className="text-xs bg-yellow-50 text-yellow-700 px-2 py-0.5 rounded-full">#{tag}</span>
            ))}
          </div>
        )}
        <p className="text-xs text-gray-300 mt-2">
          {new Date(item.createdAt).toLocaleDateString('ko-KR')}
        </p>
      </div>
    </div>
  )
}

function ArchivePage() {
  const [items, setItems] = useState([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [showModal, setShowModal] = useState(false)
  const logoutStore = useAuthStore(s => s.logout)

  const fetchItems = useCallback(async (pg) => {
    setLoading(true)
    try {
      const res = await getItems(null, pg)
      const pageData = res.data
      if (pg === 0) {
        setItems(pageData.content)
      } else {
        setItems(prev => [...prev, ...pageData.content])
      }
      setHasMore(!pageData.last)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    setPage(0)
    setItems([])
    setHasMore(true)
    fetchItems(0)
  }, [])

  const handleLoadMore = () => {
    const next = page + 1
    setPage(next)
    fetchItems(next)
  }

  const handleLogout = async () => {
    try { await logout() } catch (_) {}
    logoutStore()
    window.location.href = '/login'
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {showModal && <LinkCodeModal onClose={() => setShowModal(false)} />}

      {/* 헤더 */}
      <header className="bg-white border-b border-gray-100 sticky top-0 z-10">
        <div className="max-w-2xl mx-auto px-4 h-14 flex items-center justify-between">
          <h1 className="text-xl font-bold text-gray-900">Curio</h1>
          <div className="flex items-center gap-3">
            <button
              onClick={() => setShowModal(true)}
              className="text-sm bg-[#FEE500] hover:bg-[#F0D800] text-gray-900 font-medium px-3 py-1.5 rounded-lg transition-colors"
            >
              봇 연동
            </button>
            <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-gray-600">로그아웃</button>
          </div>
        </div>
      </header>

      {/* 아이템 목록 */}
      <main className="max-w-2xl mx-auto px-4 py-6">
        {items.length === 0 && !loading ? (
          <div className="text-center py-20 text-gray-400">
            <p className="text-4xl mb-3">📭</p>
            <p className="text-sm">아직 저장된 아이템이 없어요.</p>
            <p className="text-sm">카카오톡 채널에 링크나 텍스트를 보내보세요!</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {items.map(item => <ItemCard key={item.id} item={item} />)}
          </div>
        )}

        {loading && (
          <p className="text-center text-gray-400 text-sm py-8">불러오는 중...</p>
        )}

        {!loading && hasMore && items.length > 0 && (
          <button
            onClick={handleLoadMore}
            className="w-full mt-6 py-3 text-sm text-gray-500 hover:text-gray-800 border border-gray-200 rounded-xl transition-colors"
          >
            더 보기
          </button>
        )}
      </main>
    </div>
  )
}

export default ArchivePage