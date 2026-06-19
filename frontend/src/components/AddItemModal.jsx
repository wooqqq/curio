import { useState, useEffect } from 'react'
import { addItem, addText, addImage } from '../api/items'

const TABS = [
  { key: 'link', label: '링크' },
  { key: 'text', label: '텍스트' },
  { key: 'image', label: '이미지' },
]

const MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 백엔드 상한과 동일(10MB)

// 웹에서 링크/텍스트/이미지 직접 추가 — 바텀시트. 성공 시 onAdded(item)로 피드에 즉시 반영(설계결정 #35).
function AddItemModal({ onClose, onAdded }) {
  const [tab, setTab] = useState('link')
  const [url, setUrl] = useState('')
  const [text, setText] = useState('')
  const [file, setFile] = useState(null)
  const [preview, setPreview] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  // iOS 키보드가 바텀시트를 가리지 않게 VisualViewport로 띄운다(기존 AddLinkModal과 동일).
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

  // 미리보기 objectURL 정리
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  const switchTab = (key) => {
    setTab(key)
    setError('')
  }

  const handlePaste = async () => {
    try {
      const t = await navigator.clipboard.readText()
      if (t) setUrl(t.trim())
    } catch { /* 권한 거부 등은 무시 */ }
  }

  const handleFile = (e) => {
    const f = e.target.files?.[0]
    if (!f) return
    if (!f.type.startsWith('image/')) { setError('이미지 파일만 올릴 수 있어요.'); return }
    if (f.size > MAX_IMAGE_BYTES) { setError('이미지는 10MB 이하만 올릴 수 있어요.'); return }
    setError('')
    setFile(f)
    if (preview) URL.revokeObjectURL(preview)
    setPreview(URL.createObjectURL(f))
  }

  const canSubmit =
    !loading && (
      (tab === 'link' && url.trim()) ||
      (tab === 'text' && text.trim()) ||
      (tab === 'image' && file)
    )

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!canSubmit) return
    setLoading(true)
    setError('')
    try {
      let res
      if (tab === 'link') res = await addItem(url.trim())
      else if (tab === 'text') res = await addText(text.trim())
      else res = await addImage(file)
      onAdded(res.data)
      onClose()
    } catch (err) {
      if (err?.code === 'DUPLICATE_URL') setError('이미 저장된 링크예요.')
      else if (err?.code === 'INVALID_IMAGE') setError('이미지 파일이 올바르지 않거나 너무 커요(10MB).')
      else setError(err?.message || '추가하지 못했어요.')
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
        <h2 className="text-lg font-bold text-[#191f28] mb-3">추가하기</h2>

        {/* 타입 선택 */}
        <div className="flex gap-1 p-1 bg-[#f2f4f6] rounded-xl mb-4">
          {TABS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => switchTab(key)}
              className={`flex-1 text-sm font-semibold py-2 rounded-lg transition-colors ${
                tab === key ? 'bg-white text-[#191f28] shadow-sm' : 'text-[#8b95a1] hover:text-[#4e5968]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === 'link' && (
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
        )}

        {tab === 'text' && (
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="저장할 내용을 적어보세요. 첫 줄이 제목이 돼요."
            rows={4}
            autoFocus
            className="w-full resize-none bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-sm text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors"
          />
        )}

        {tab === 'image' && (
          <div>
            <label className="block cursor-pointer">
              <input type="file" accept="image/*" onChange={handleFile} className="hidden" />
              {preview ? (
                <img src={preview} alt="" className="w-full max-h-52 object-cover rounded-xl" />
              ) : (
                <div className="flex flex-col items-center justify-center gap-1 bg-[#f9fafb] border border-dashed border-[#d1d6db] rounded-xl py-10 text-[#8b95a1] hover:bg-[#f2f4f6] transition-colors">
                  <span className="text-2xl">🖼️</span>
                  <span className="text-sm font-medium">이미지 선택 (최대 10MB)</span>
                </div>
              )}
            </label>
            {file && <p className="mt-2 text-xs text-[#8b95a1] truncate">{file.name}</p>}
          </div>
        )}

        {error && <p className="text-sm text-[#f04452] mt-2">{error}</p>}

        <button
          type="submit"
          disabled={!canSubmit}
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

export default AddItemModal
