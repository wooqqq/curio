import { useState, useEffect } from 'react'
import { addItem } from '../api/items'

// 웹에서 링크 직접 추가 — 바텀시트. 성공 시 onAdded(item)로 피드에 즉시 반영.
function AddLinkModal({ onClose, onAdded }) {
  const [url, setUrl] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  // iOS(사파리/크롬)는 키보드가 레이아웃 뷰포트를 줄이지 않고 위에 덮는다.
  // 그래서 바텀시트(items-end)가 키보드 뒤로 가려진다 → VisualViewport로 키보드 높이를
  // 감지해 시트를 그만큼 위로 띄운다(margin-bottom). 키보드 없으면 0이라 무해.
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

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText()
      if (text) setUrl(text.trim())
    } catch {
      /* 클립보드 권한 거부 등은 조용히 무시 (직접 입력 가능) */
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    const value = url.trim()
    if (!value || loading) return
    setLoading(true)
    setError('')
    try {
      const res = await addItem(value)
      onAdded(res.data)
      onClose()
    } catch (err) {
      setError(err?.code === 'DUPLICATE_URL' ? '이미 저장된 링크예요.' : (err?.message || '추가하지 못했어요.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 bg-black/30 backdrop-blur-sm flex items-end sm:items-center justify-center z-50"
      onClick={onClose}
    >
      <form
        onSubmit={handleSubmit}
        onClick={(e) => e.stopPropagation()}
        style={{ marginBottom: kbOffset }}
        className="bg-white w-full sm:w-[24rem] rounded-t-3xl sm:rounded-3xl p-6 pb-7 shadow-2xl animate-[slideup_0.25s_ease] transition-[margin] duration-200 ease-out"
      >
        <div className="mx-auto mb-5 h-1 w-10 rounded-full bg-[#e5e8eb] sm:hidden" />
        <h2 className="text-lg font-bold text-[#191f28] mb-1">링크 추가</h2>
        <p className="text-sm text-[#8b95a1] mb-4 leading-relaxed">저장할 링크를 붙여넣으세요.<br />AI가 자동으로 정리해드려요.</p>

        <div className="flex gap-2">
          <input
            type="url"
            inputMode="url"
            value={url}
            onChange={(e) => setUrl(e.target.value)}
            placeholder="https://..."
            autoFocus
            className="flex-1 min-w-0 bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-sm text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
          />
          <button
            type="button"
            onClick={handlePaste}
            className="shrink-0 text-sm font-semibold text-[#4e5968] bg-[#f2f4f6] hover:bg-[#e5e8eb] px-3 rounded-xl transition-colors"
          >
            붙여넣기
          </button>
        </div>

        {error && <p className="text-sm text-[#f04452] mt-2">{error}</p>}

        <button
          type="submit"
          disabled={loading || !url.trim()}
          className="mt-4 w-full bg-[#191f28] hover:bg-[#333d4b] disabled:bg-[#b0b8c1] text-white text-sm font-bold py-3.5 rounded-2xl transition-colors"
        >
          {loading ? '추가 중...' : '추가'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="mt-2 w-full text-sm font-medium text-[#8b95a1] hover:text-[#4e5968] py-2 transition-colors"
        >
          닫기
        </button>
      </form>
    </div>
  )
}

export default AddLinkModal
