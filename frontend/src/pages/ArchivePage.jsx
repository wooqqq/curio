import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { getItems, recrawlItem, deleteItem } from '../api/items'
import { logout } from '../api/auth'
import { checkAdmin } from '../api/admin'
import client from '../api/client'
import useAuthStore from '../store/authStore'
import PopupModal from '../components/PopupModal'
import AddItemModal from '../components/AddItemModal'
import ItemDetailModal from '../components/ItemDetailModal'

const TYPE_ICON = {
  LINK: '🔗',
  IMAGE: '🖼️',
  TEXT: '📝',
}

const SOURCE_LABEL = {
  IMAGE: '이미지',
  TEXT: '메모',
}

const CATEGORIES = [
  { key: null, label: '전체' },
  { key: 'DEVELOPMENT', label: '개발' },
  { key: 'CAREER', label: '커리어/취업' },
  { key: 'ETC', label: '기타' },
]

function getDomain(url) {
  try { return new URL(url).hostname.replace('www.', '') }
  catch { return '' }
}

function timeAgo(dateStr) {
  const diff = Math.floor((Date.now() - new Date(dateStr)) / 1000)
  if (diff < 60) return '방금 전'
  if (diff < 3600) return `${Math.floor(diff / 60)}분 전`
  if (diff < 86400) return `${Math.floor(diff / 3600)}시간 전`
  if (diff < 604800) return `${Math.floor(diff / 86400)}일 전`
  return new Date(dateStr).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
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
    <div
      className="fixed inset-0 bg-black/30 backdrop-blur-sm flex items-end sm:items-center justify-center z-50"
      onClick={onClose}
    >
      <div
        className="bg-white w-full sm:w-[22rem] rounded-t-3xl sm:rounded-3xl p-6 pb-8 shadow-2xl animate-[slideup_0.25s_ease]"
        onClick={e => e.stopPropagation()}
      >
        <div className="mx-auto mb-5 h-1 w-10 rounded-full bg-[#e5e8eb] sm:hidden" />
        <h2 className="text-lg font-bold text-[#191f28] mb-1">카카오톡 봇 연동</h2>
        <p className="text-sm text-[#8b95a1] mb-5 leading-relaxed">
          아래 코드를 카카오 채널에 전송하면<br />봇이 내 계정과 연결돼요.
        </p>

        {error && <p className="text-sm text-red-500 mb-3">{error}</p>}

        {linkCode ? (
          <div className="space-y-3">
            <div className="flex items-center gap-3 bg-[#f2f4f6] rounded-2xl px-5 py-4">
              <span className="flex-1 text-center text-3xl font-bold tracking-[0.3em] text-[#191f28] pl-[0.3em]">
                {linkCode}
              </span>
              <button
                onClick={handleCopy}
                className="shrink-0 text-sm font-semibold text-[#3182f6] hover:text-[#1b64da] transition-colors"
              >
                {copied ? '복사됨' : '복사'}
              </button>
            </div>
            <p className="text-xs text-[#8b95a1] text-center">10분 안에 카카오 채널에 전송하세요</p>
            <button
              onClick={handleGet}
              disabled={loading}
              className="w-full text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] py-1 transition-colors"
            >
              재발급
            </button>
          </div>
        ) : (
          <p className="text-center text-[#8b95a1] py-6">{loading ? '발급 중...' : '-'}</p>
        )}

        <button
          onClick={onClose}
          className="mt-4 w-full bg-[#f2f4f6] hover:bg-[#e5e8eb] text-[#4e5968] text-sm font-semibold py-3.5 rounded-2xl transition-colors"
        >
          닫기
        </button>
      </div>
    </div>
  )
}

function ItemCard({ item, onRecrawl, onDelete, onOpenDetail }) {
  const [recrawling, setRecrawling] = useState(false)
  const [recrawlError, setRecrawlError] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const domain = getDomain(item.originalUrl)
  const source = domain || SOURCE_LABEL[item.type] || '링크'
  // 저장 당시 크롤링 실패로 제목 자리에 raw URL이 들어간 경우
  const titleBroken = item.type === 'LINK' && /^https?:\/\//i.test(item.title || '')

  // 본문 탭 = 상세 시트(제목·메모 수정), 썸네일 탭 = 원문 바로 열기
  const handleClick = () => onOpenDetail(item)

  const handleThumbClick = (e) => {
    e.stopPropagation()
    if (item.originalUrl) window.open(item.originalUrl, '_blank', 'noopener')
  }

  const handleRecrawl = async (e) => {
    e.stopPropagation()
    if (recrawling) return
    setRecrawling(true)
    setRecrawlError(false)
    try {
      await onRecrawl(item.id)
    } catch {
      setRecrawlError(true)
    } finally {
      setRecrawling(false)
    }
  }

  const handleDelete = async (e) => {
    e.stopPropagation()
    if (deleting) return
    if (!window.confirm('이 아이템을 삭제할까요?')) return
    setDeleting(true)
    try {
      await onDelete(item.id)
      // 성공 시 부모가 목록에서 제거 → 카드 언마운트 (상태 복구 불필요)
    } catch {
      setDeleting(false)
      alert('삭제하지 못했어요. 잠시 후 다시 시도해주세요.')
    }
  }

  return (
    <div
      onClick={handleClick}
      className="group bg-white rounded-2xl flex items-start gap-3 p-4 shadow-[0_1px_2px_rgba(0,0,0,0.04),0_4px_16px_rgba(0,0,0,0.04)] transition-all duration-200 cursor-pointer hover:-translate-y-0.5 hover:shadow-[0_2px_4px_rgba(0,0,0,0.06),0_12px_28px_rgba(0,0,0,0.08)]"
    >
      {/* 본문 */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5 mb-1.5 text-xs text-[#8b95a1]">
          <span>{TYPE_ICON[item.type]}</span>
          <span className="truncate">{source}</span>
          <span className="text-[#d1d6db]">·</span>
          <span className="shrink-0">{timeAgo(item.createdAt)}</span>
        </div>

        <p className="text-[15px] font-bold text-[#191f28] leading-snug line-clamp-2 mb-1">
          {titleBroken ? (domain || '제목 없음') : (item.title || item.content || '제목 없음')}
        </p>

        {item.type === 'TEXT' && item.content && (
          <p className="text-[13px] text-[#6b7684] leading-snug line-clamp-2 mb-1.5 whitespace-pre-wrap">
            {item.content}
          </p>
        )}

        {item.memo && (
          <p className="flex items-center gap-1 text-[13px] text-[#6b7684] line-clamp-1 mb-1.5">
            <span className="shrink-0">🗒️</span>
            <span className="truncate">{item.memo}</span>
          </p>
        )}

        {item.tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            {item.tags.slice(0, 3).map(tag => (
              <span
                key={tag}
                className="text-xs font-medium text-[#8b95a1] bg-[#f2f4f6] px-2.5 py-1 rounded-lg"
              >
                #{tag}
              </span>
            ))}
          </div>
        )}

        {titleBroken && (
          <div className="mt-2 flex items-center gap-2">
            <button
              onClick={handleRecrawl}
              disabled={recrawling}
              className="inline-flex items-center gap-1 text-xs font-semibold text-[#3182f6] hover:text-[#1b64da] disabled:text-[#8b95a1] transition-colors"
            >
              <span className={recrawling ? 'inline-block animate-spin' : ''}>↻</span>
              {recrawling ? '불러오는 중...' : '제목 다시 불러오기'}
            </button>
            {recrawlError && <span className="text-xs text-red-400">다시 불러오지 못했어요</span>}
          </div>
        )}
      </div>

      {/* 썸네일 — 탭하면 원문 바로 열기(본문 탭=상세와 구분) */}
      {item.thumbnailUrl && (
        <img
          src={item.thumbnailUrl}
          alt=""
          onClick={handleThumbClick}
          className="shrink-0 w-16 h-16 object-cover rounded-xl bg-[#f2f4f6] cursor-pointer"
          onError={e => { e.currentTarget.style.display = 'none' }}
        />
      )}

      {/* 삭제 — 썸네일 유무와 무관하게 카드 우상단 고정 */}
      <button
        onClick={handleDelete}
        disabled={deleting}
        aria-label="삭제"
        className="shrink-0 -mr-1 flex h-7 w-7 items-center justify-center rounded-full text-[#b0b8c1] hover:text-[#f04452] hover:bg-[#f04452]/10 disabled:opacity-40 transition-colors"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M3 6h18M8 6V4a1 1 0 0 1 1-1h6a1 1 0 0 1 1 1v2m2 0v14a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6" />
          <path d="M10 11v6M14 11v6" />
        </svg>
      </button>
    </div>
  )
}

function ArchivePage() {
  const [items, setItems] = useState([])
  const [totalCount, setTotalCount] = useState(0)
  const [category, setCategory] = useState(null)
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(true)
  const [loading, setLoading] = useState(false)
  const [showModal, setShowModal] = useState(false)
  const [showAddModal, setShowAddModal] = useState(false)
  const [detailItem, setDetailItem] = useState(null)
  const [isAdmin, setIsAdmin] = useState(false)
  const logoutStore = useAuthStore(s => s.logout)

  // 관리자면 헤더에 관리자 페이지 링크 노출 (백엔드도 /admin 접근을 별도 검증)
  useEffect(() => {
    checkAdmin().then(res => setIsAdmin(res.data.admin)).catch(() => {})
  }, [])

  const fetchItems = useCallback(async (pg, cat, query) => {
    setLoading(true)
    try {
      const res = await getItems(cat, pg, 20, query)
      const pageData = res.data
      setItems(prev => (pg === 0 ? pageData.content : [...prev, ...pageData.content]))
      setHasMore(!pageData.last)
      setTotalCount(pageData.totalElements ?? 0)
    } finally {
      setLoading(false)
    }
  }, [])

  // 검색어 입력 디바운스 (300ms)
  useEffect(() => {
    const t = setTimeout(() => setSearch(searchInput.trim()), 300)
    return () => clearTimeout(t)
  }, [searchInput])

  // 카테고리 또는 검색어 변경 시 첫 페이지부터 다시 조회
  useEffect(() => {
    setPage(0)
    fetchItems(0, category, search)
  }, [category, search, fetchItems])

  const handleLoadMore = () => {
    const next = page + 1
    setPage(next)
    fetchItems(next, category, search)
  }

  const handleRecrawl = useCallback(async (id) => {
    const res = await recrawlItem(id)
    const updated = res.data
    setItems(prev => prev.map(it => (it.id === id ? updated : it)))
  }, [])

  const handleDelete = useCallback(async (id) => {
    await deleteItem(id)
    setItems(prev => prev.filter(it => it.id !== id))
    setTotalCount(c => Math.max(0, c - 1))
  }, [])

  // 웹에서 링크 추가 성공 → 피드 맨 앞에 즉시 반영
  const handleAdded = useCallback((item) => {
    setItems(prev => [item, ...prev])
    setTotalCount(c => c + 1)
  }, [])

  // 상세에서 제목/메모 저장 → 피드 카드 즉시 갱신
  const handleSaved = useCallback((updated) => {
    setItems(prev => prev.map(it => (it.id === updated.id ? updated : it)))
  }, [])

  const handleLogout = async () => {
    try { await logout() } catch (_) {}
    logoutStore()
    window.location.href = '/login'
  }

  return (
    <div className="min-h-screen bg-[#f2f4f6]">
      {showModal && <LinkCodeModal onClose={() => setShowModal(false)} />}
      {showAddModal && <AddItemModal onClose={() => setShowAddModal(false)} onAdded={handleAdded} />}
      {detailItem && (
        <ItemDetailModal
          item={detailItem}
          onClose={() => setDetailItem(null)}
          onSaved={handleSaved}
        />
      )}
      <PopupModal />

      {/* 헤더 (frosted) */}
      <header className="sticky top-0 z-20 bg-[#f2f4f6]/80 backdrop-blur-md border-b border-black/[0.04]">
        <div className="max-w-2xl mx-auto px-5 h-14 flex items-center justify-between">
          <span className="text-lg font-extrabold text-[#191f28] tracking-tight">Curio</span>
          <div className="flex items-center gap-1.5">
            <Link
              to="/announcements"
              className="text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] px-3 py-2 rounded-full hover:bg-black/[0.03] transition-colors"
            >
              공지
            </Link>
            {isAdmin && (
              <Link
                to="/admin"
                className="text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] px-3 py-2 rounded-full hover:bg-black/[0.03] transition-colors"
              >
                관리자
              </Link>
            )}
            <button
              onClick={() => setShowModal(true)}
              className="flex items-center gap-1.5 bg-[#FEE500] hover:bg-[#F5DB00] text-[#191f28] text-sm font-bold px-3.5 py-2 rounded-full transition-colors"
            >
              <span>💬</span>
              <span>봇 연동</span>
            </button>
            <button
              onClick={handleLogout}
              className="text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] px-3 py-2 rounded-full hover:bg-black/[0.03] transition-colors"
            >
              로그아웃
            </button>
          </div>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-5 pb-16">
        {/* 타이틀 */}
        <div className="pt-8 pb-5">
          <h1 className="text-[26px] font-extrabold text-[#191f28] tracking-tight">내 아카이브</h1>
          <p className="text-sm text-[#8b95a1] mt-1">
            {search ? `검색 결과 ${totalCount}개` : `저장한 글 ${totalCount}개`}
          </p>

          {/* 검색바 */}
          <div className="relative mt-5">
            <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-[15px] text-[#8b95a1]">🔍</span>
            <input
              type="text"
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              placeholder="제목, 내용, 태그 검색"
              className="w-full bg-white rounded-2xl pl-11 pr-10 py-3 text-sm text-[#191f28] placeholder:text-[#8b95a1] shadow-[0_1px_2px_rgba(0,0,0,0.04)] outline-none focus:ring-2 focus:ring-[#191f28]/10 transition-shadow"
            />
            {searchInput && (
              <button
                onClick={() => setSearchInput('')}
                aria-label="검색어 지우기"
                className="absolute right-3 top-1/2 -translate-y-1/2 flex h-6 w-6 items-center justify-center rounded-full text-[#8b95a1] hover:bg-[#f2f4f6] transition-colors"
              >
                ✕
              </button>
            )}
          </div>
        </div>

        {/* 카테고리 필터 (sticky) */}
        <div className="sticky top-14 z-10 -mx-5 px-5 py-2.5 bg-[#f2f4f6]/85 backdrop-blur-md">
          <div className="flex gap-2 overflow-x-auto no-scrollbar">
            {CATEGORIES.map(({ key, label }) => {
              const active = key === category
              return (
                <button
                  key={label}
                  onClick={() => setCategory(key)}
                  className={`shrink-0 px-4 py-2 rounded-full text-sm font-bold transition-colors ${
                    active
                      ? 'bg-[#191f28] text-white'
                      : 'bg-white text-[#4e5968] hover:bg-[#f2f4f6] border border-[#e5e8eb]'
                  }`}
                >
                  {label}
                </button>
              )
            })}
          </div>
        </div>

        {/* 리스트 */}
        <div className="space-y-3 pt-3">
          {items.length === 0 && !loading ? (
            <div className="text-center py-24 text-[#8b95a1]">
              <p className="text-5xl mb-4">{search ? '🔍' : '📭'}</p>
              <p className="text-[15px] font-semibold text-[#4e5968]">
                {search
                  ? `'${search}' 검색 결과가 없어요.`
                  : category
                    ? '이 카테고리엔 저장된 글이 없어요.'
                    : '아직 저장된 아이템이 없어요.'}
              </p>
              <p className="text-sm mt-1.5">
                {search ? '다른 키워드로 검색해보세요.' : '카카오톡 채널에 링크나 텍스트를 보내보세요!'}
              </p>
            </div>
          ) : (
            items.map(item => <ItemCard key={item.id} item={item} onRecrawl={handleRecrawl} onDelete={handleDelete} onOpenDetail={setDetailItem} />)
          )}

          {loading && (
            <p className="text-center text-[#8b95a1] text-sm py-8">불러오는 중...</p>
          )}

          {!loading && hasMore && items.length > 0 && (
            <button
              onClick={handleLoadMore}
              className="w-full py-3.5 text-sm font-semibold text-[#4e5968] bg-white hover:bg-[#f2f4f6] rounded-2xl shadow-[0_1px_2px_rgba(0,0,0,0.04)] transition-colors"
            >
              더 보기
            </button>
          )}
        </div>
      </main>

      {/* 링크 추가 FAB (우하단 고정) */}
      <button
        onClick={() => setShowAddModal(true)}
        aria-label="링크 추가"
        className="fixed bottom-6 right-6 z-30 flex h-14 w-14 items-center justify-center rounded-full bg-[#191f28] text-white shadow-[0_4px_16px_rgba(0,0,0,0.18)] hover:bg-[#333d4b] active:scale-95 transition-all"
      >
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
    </div>
  )
}

export default ArchivePage
