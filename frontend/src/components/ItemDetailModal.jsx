import { useState, useEffect } from 'react'
import { updateItem, addTag, removeTag } from '../api/items'

const TYPE_LABEL = { LINK: '링크', IMAGE: '이미지', TEXT: '메모' }

// 사용자가 고를 수 있는 카테고리(미분류/전체 제외) — AI 오분류 정정용(설계결정 #33)
const CATEGORY_OPTIONS = [
  { key: 'DEVELOPMENT', label: '개발' },
  { key: 'CAREER', label: '커리어/취업' },
  { key: 'ETC', label: '기타' },
]

function getDomain(url) {
  try { return new URL(url).hostname.replace('www.', '') }
  catch { return '' }
}

// 카드 탭 시 뜨는 상세 바텀시트. 제목/메모 수정 + 원문 열기.
// 썸네일/원문 이동은 명시 버튼으로 분리해 실수 이동을 줄인다.
function ItemDetailModal({ item, onClose, onSaved }) {
  const isText = item.type === 'TEXT'
  const [title, setTitle] = useState(item.title || '')
  const [content, setContent] = useState(item.content || '')
  const [memo, setMemo] = useState(item.memo || '')
  const [category, setCategory] = useState(item.category || null)
  const [tags, setTags] = useState(item.tags || [])
  const [tagInput, setTagInput] = useState('')
  const [tagBusy, setTagBusy] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  // iOS 키보드가 바텀시트를 가리지 않게 VisualViewport로 띄운다(AddLinkModal과 동일).
  const [kbOffset, setKbOffset] = useState(0)

  useEffect(() => {
    const vv = window.visualViewport
    if (!vv) return
    const update = () => {
      const offset = window.innerHeight - vv.height - vv.offsetTop
      setKbOffset(offset > 0 ? offset : 0)
    }
    vv.addEventListener('resize', update)
    vv.addEventListener('scroll', update)
    update()
    return () => {
      vv.removeEventListener('resize', update)
      vv.removeEventListener('scroll', update)
    }
  }, [])

  const source = getDomain(item.originalUrl) || TYPE_LABEL[item.type] || '아이템'
  const trimmedTitle = title.trim()
  const trimmedContent = content.trim()
  const dirty = trimmedTitle !== (item.title || '').trim()
    || memo.trim() !== (item.memo || '').trim()
    || category !== (item.category || null)
    || (isText && trimmedContent !== (item.content || '').trim())
  // TEXT는 본문이 비면 저장 불가(빈 메모 아이템 방지).
  const canSave = dirty && trimmedTitle.length > 0 && (!isText || trimmedContent.length > 0) && !saving

  const handleOpenOriginal = () => {
    if (item.originalUrl) window.open(item.originalUrl, '_blank', 'noopener')
  }

  const handleSave = async () => {
    if (!canSave) return
    setSaving(true)
    setError('')
    const body = {}
    if (trimmedTitle !== (item.title || '').trim()) body.title = trimmedTitle
    if (memo.trim() !== (item.memo || '').trim()) body.memo = memo.trim()
    if (category && category !== (item.category || null)) body.category = category
    if (isText && trimmedContent !== (item.content || '').trim()) body.content = trimmedContent
    try {
      const res = await updateItem(item.id, body)
      onSaved(res.data)
      onClose()
    } catch (err) {
      setError(err?.message || '저장하지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  // 태그는 추가/삭제가 각각 즉시 반영(목록도 onSaved로 갱신). 모달은 닫지 않는다.
  const handleAddTag = async () => {
    const name = tagInput.trim()
    if (!name || tagBusy) return
    if (tags.includes(name)) { setTagInput(''); return }
    setTagBusy(true)
    setError('')
    try {
      const res = await addTag(item.id, name)
      setTags(res.data.tags)
      setTagInput('')
      onSaved(res.data)
    } catch (err) {
      setError(err?.message || '태그를 추가하지 못했어요.')
    } finally {
      setTagBusy(false)
    }
  }

  const handleRemoveTag = async (name) => {
    if (tagBusy) return
    setTagBusy(true)
    setError('')
    try {
      const res = await removeTag(item.id, name)
      setTags(res.data.tags)
      onSaved(res.data)
    } catch (err) {
      setError(err?.message || '태그를 빼지 못했어요.')
    } finally {
      setTagBusy(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/30 backdrop-blur-sm flex items-end sm:items-center justify-center z-50"
      onClick={onClose}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        style={{ marginBottom: kbOffset }}
        className="bg-white w-full sm:w-[26rem] max-h-[88vh] overflow-y-auto rounded-t-3xl sm:rounded-3xl p-6 pb-7 shadow-2xl animate-[slideup_0.25s_ease] transition-[margin] duration-200 ease-out"
      >
        <div className="mx-auto mb-5 h-1 w-10 rounded-full bg-[#e5e8eb] sm:hidden" />

        <p className="text-xs text-[#8b95a1] mb-3 truncate">{source}</p>

        {/* 썸네일 — 탭하면 원문 열기 */}
        {item.thumbnailUrl && (
          <button
            type="button"
            onClick={handleOpenOriginal}
            className="block w-full mb-4 overflow-hidden rounded-2xl bg-[#f2f4f6]"
          >
            <img
              src={item.thumbnailUrl}
              alt=""
              className="w-full max-h-60 object-cover"
              onError={(e) => { e.currentTarget.parentElement.style.display = 'none' }}
            />
          </button>
        )}

        {/* 제목 수정 */}
        <label className="block text-xs font-semibold text-[#8b95a1] mb-1.5">제목</label>
        <input
          type="text"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="제목을 입력하세요"
          className="w-full bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-[15px] font-semibold text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
        />

        {/* 본문 — TEXT 아이템만 편집 가능(설계결정 #35) */}
        {isText && (
          <>
            <label className="block text-xs font-semibold text-[#8b95a1] mt-4 mb-1.5">내용</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              placeholder="내용을 입력하세요"
              rows={6}
              className="w-full resize-none bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-sm text-[#191f28] leading-relaxed placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
            />
          </>
        )}

        {/* 메모 */}
        <label className="block text-xs font-semibold text-[#8b95a1] mt-4 mb-1.5">메모</label>
        <textarea
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
          placeholder="간단한 메모나 한마디를 남겨보세요"
          rows={3}
          className="w-full resize-none bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-sm text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
        />

        {/* 카테고리 — AI 분류를 사용자가 정정(저장 시 반영) */}
        <label className="block text-xs font-semibold text-[#8b95a1] mt-4 mb-1.5">카테고리</label>
        <div className="flex flex-wrap gap-1.5">
          {CATEGORY_OPTIONS.map(({ key, label }) => {
            const active = category === key
            return (
              <button
                key={key}
                type="button"
                onClick={() => setCategory(key)}
                className={`text-xs font-semibold px-3 py-1.5 rounded-lg transition-colors ${
                  active
                    ? 'bg-[#3182f6] text-white'
                    : 'bg-[#f2f4f6] text-[#4e5968] hover:bg-[#e5e8eb]'
                }`}
              >
                {label}
              </button>
            )
          })}
        </div>

        {/* 태그 — 추가/삭제 즉시 반영 */}
        <label className="block text-xs font-semibold text-[#8b95a1] mt-4 mb-1.5">태그</label>
        {tags.length > 0 && (
          <div className="flex flex-wrap gap-1.5 mb-2">
            {tags.map((tag) => (
              <span key={tag} className="flex items-center gap-1 text-xs font-medium text-[#4e5968] bg-[#f2f4f6] pl-2.5 pr-1.5 py-1 rounded-lg">
                #{tag}
                <button
                  type="button"
                  onClick={() => handleRemoveTag(tag)}
                  disabled={tagBusy}
                  aria-label={`${tag} 태그 삭제`}
                  className="text-[#b0b8c1] hover:text-[#4e5968] disabled:opacity-50 leading-none"
                >
                  <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
                    <path d="M6 6l12 12M18 6 6 18" />
                  </svg>
                </button>
              </span>
            ))}
          </div>
        )}
        <div className="flex gap-2">
          <input
            type="text"
            value={tagInput}
            onChange={(e) => setTagInput(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleAddTag() } }}
            placeholder="태그 추가"
            maxLength={50}
            className="flex-1 bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-2.5 text-sm text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
          />
          <button
            type="button"
            onClick={handleAddTag}
            disabled={tagBusy || !tagInput.trim()}
            className="shrink-0 bg-[#f2f4f6] hover:bg-[#e5e8eb] disabled:opacity-50 text-[#4e5968] text-sm font-semibold px-4 rounded-xl transition-colors"
          >
            추가
          </button>
        </div>

        {error && <p className="text-sm text-[#f04452] mt-3">{error}</p>}

        {/* 액션 */}
        <div className="mt-5 space-y-2">
          <button
            type="button"
            onClick={handleSave}
            disabled={!canSave}
            className="w-full bg-[#191f28] hover:bg-[#333d4b] disabled:bg-[#d1d6db] text-white text-sm font-bold py-3.5 rounded-2xl transition-colors"
          >
            {saving ? '저장 중...' : '저장'}
          </button>
          {item.originalUrl && (
            <button
              type="button"
              onClick={handleOpenOriginal}
              className="w-full flex items-center justify-center gap-1.5 bg-[#f2f4f6] hover:bg-[#e5e8eb] text-[#4e5968] text-sm font-semibold py-3.5 rounded-2xl transition-colors"
            >
              원문 열기
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M7 17 17 7M9 7h8v8" />
              </svg>
            </button>
          )}
          <button
            type="button"
            onClick={onClose}
            className="w-full text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] py-2 transition-colors"
          >
            닫기
          </button>
        </div>
      </div>
    </div>
  )
}

export default ItemDetailModal
