import { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { getAnnouncements } from '../api/notice'
import {
  checkAdmin,
  createAnnouncement, updateAnnouncement, deleteAnnouncement,
  getAdminPopups, createPopup, updatePopup, deletePopup, uploadPopupImage,
} from '../api/admin'

const inputCls =
  'w-full bg-[#f9fafb] border border-[#e5e8eb] rounded-xl px-4 py-3 text-sm text-[#191f28] placeholder:text-[#b0b8c1] outline-none focus:border-[#3182f6] focus:bg-white transition-colors'
const labelCls = 'block text-[13px] font-semibold text-[#4e5968] mb-1.5'
const primaryBtn =
  'bg-[#191f28] hover:bg-[#333d4b] disabled:bg-[#b0b8c1] text-white text-sm font-bold px-5 py-3 rounded-xl transition-colors'
const ghostBtn =
  'text-sm font-semibold text-[#8b95a1] hover:text-[#4e5968] px-4 py-3 transition-colors'

function fmtDate(d) {
  return new Date(d).toLocaleDateString('ko-KR', { month: 'short', day: 'numeric' })
}

// ---------- 공지 관리 ----------
function AnnouncementsTab() {
  const [list, setList] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [saving, setSaving] = useState(false)

  const load = useCallback(() => {
    getAnnouncements().then((r) => setList(r.data)).catch(() => {})
  }, [])
  useEffect(() => { load() }, [load])

  const reset = () => { setEditingId(null); setTitle(''); setContent('') }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim() || !content.trim()) return
    setSaving(true)
    try {
      const body = { title: title.trim(), content: content.trim() }
      if (editingId) await updateAnnouncement(editingId, body)
      else await createAnnouncement(body)
      reset()
      load()
    } catch (err) {
      alert(err?.message || '저장하지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  const startEdit = (a) => {
    setEditingId(a.id); setTitle(a.title); setContent(a.content)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleDelete = async (id) => {
    if (!window.confirm('이 공지를 삭제할까요?')) return
    try { await deleteAnnouncement(id); load() }
    catch (err) { alert(err?.message || '삭제하지 못했어요.') }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={handleSubmit} className="bg-white rounded-2xl p-6 shadow-[0_1px_2px_rgba(0,0,0,0.04)] space-y-4">
        <h2 className="text-base font-bold text-[#191f28]">{editingId ? '공지 수정' : '새 공지 작성'}</h2>
        <div>
          <label className={labelCls}>제목</label>
          <input className={inputCls} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder="공지 제목" />
        </div>
        <div>
          <label className={labelCls}>내용</label>
          <textarea className={`${inputCls} min-h-[140px] resize-y`} value={content} onChange={(e) => setContent(e.target.value)} placeholder="공지 내용" />
        </div>
        <div className="flex items-center gap-2">
          <button type="submit" disabled={saving} className={primaryBtn}>
            {saving ? '저장 중...' : editingId ? '수정 저장' : '등록'}
          </button>
          {editingId && <button type="button" onClick={reset} className={ghostBtn}>취소</button>}
        </div>
      </form>

      <div className="space-y-2.5">
        {list.length === 0 ? (
          <p className="text-center text-sm text-[#8b95a1] py-10">등록된 공지가 없어요.</p>
        ) : (
          list.map((a) => (
            <div key={a.id} className="bg-white rounded-2xl px-5 py-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)] flex items-start gap-3">
              <div className="flex-1 min-w-0">
                <p className="text-[15px] font-bold text-[#191f28] truncate">{a.title}</p>
                <p className="text-xs text-[#8b95a1] mt-0.5">#{a.id} · {fmtDate(a.createdAt)}</p>
              </div>
              <div className="flex items-center gap-1 shrink-0">
                <button onClick={() => startEdit(a)} className="text-xs font-semibold text-[#3182f6] hover:text-[#1b64da] px-2 py-1">수정</button>
                <button onClick={() => handleDelete(a.id)} className="text-xs font-semibold text-[#f04452] hover:text-[#d63a46] px-2 py-1">삭제</button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

// ---------- 팝업 관리 ----------
function PopupsTab() {
  const [list, setList] = useState([])
  const [editingId, setEditingId] = useState(null)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [imageUrl, setImageUrl] = useState('')
  const [linkUrl, setLinkUrl] = useState('')
  const [active, setActive] = useState(true)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [announcements, setAnnouncements] = useState([]) // linkUrl을 공지에서 채우기 위한 목록

  const load = useCallback(() => {
    getAdminPopups().then((r) => setList(r.data)).catch(() => {})
  }, [])
  useEffect(() => { load() }, [load])
  useEffect(() => { getAnnouncements().then((r) => setAnnouncements(r.data)).catch(() => {}) }, [])

  const reset = () => {
    setEditingId(null); setTitle(''); setContent(''); setImageUrl(''); setLinkUrl(''); setActive(true); setUploadError('')
  }

  const handleUpload = async (e) => {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일 재선택 허용
    if (!file) return
    setUploading(true); setUploadError('')
    try {
      const res = await uploadPopupImage(file)
      setImageUrl(res.data.url)
    } catch (err) {
      setUploadError(err?.message || '업로드에 실패했어요.')
    } finally {
      setUploading(false)
    }
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!title.trim()) return
    setSaving(true)
    try {
      const body = {
        title: title.trim(),
        content: content.trim() || null,
        imageUrl: imageUrl || null,
        linkUrl: linkUrl.trim() || null,
        active,
      }
      if (editingId) await updatePopup(editingId, body)
      else await createPopup(body)
      reset()
      load()
    } catch (err) {
      alert(err?.message || '저장하지 못했어요.')
    } finally {
      setSaving(false)
    }
  }

  const startEdit = (p) => {
    setEditingId(p.id); setTitle(p.title); setContent(p.content || ''); setImageUrl(p.imageUrl || '')
    setLinkUrl(p.linkUrl || ''); setActive(p.active); setUploadError('')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleDelete = async (id) => {
    if (!window.confirm('이 팝업을 삭제할까요?')) return
    try { await deletePopup(id); load() }
    catch (err) { alert(err?.message || '삭제하지 못했어요.') }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={handleSubmit} className="bg-white rounded-2xl p-6 shadow-[0_1px_2px_rgba(0,0,0,0.04)] space-y-4">
        <h2 className="text-base font-bold text-[#191f28]">{editingId ? '팝업 수정' : '새 팝업 만들기'}</h2>

        <div>
          <label className={labelCls}>제목</label>
          <input className={inputCls} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder="팝업 제목" />
        </div>

        <div>
          <label className={labelCls}>내용 <span className="font-normal text-[#b0b8c1]">(선택 · 이미지 대신 텍스트)</span></label>
          <textarea className={`${inputCls} min-h-[80px] resize-y`} value={content} onChange={(e) => setContent(e.target.value)} placeholder="팝업에 표시할 텍스트" />
        </div>

        <div>
          <label className={labelCls}>이미지 <span className="font-normal text-[#b0b8c1]">(선택)</span></label>
          {imageUrl ? (
            <div className="relative inline-block">
              <img src={imageUrl} alt="" className="max-h-40 rounded-xl border border-[#e5e8eb]" />
              <button
                type="button"
                onClick={() => setImageUrl('')}
                className="absolute -top-2 -right-2 h-6 w-6 rounded-full bg-[#191f28] text-white text-xs flex items-center justify-center shadow"
                aria-label="이미지 제거"
              >✕</button>
            </div>
          ) : (
            <label className="inline-flex items-center gap-2 bg-[#f2f4f6] hover:bg-[#e5e8eb] text-[#4e5968] text-sm font-semibold px-4 py-2.5 rounded-xl cursor-pointer transition-colors">
              {uploading ? '업로드 중...' : '이미지 선택'}
              <input type="file" accept="image/*" onChange={handleUpload} disabled={uploading} className="hidden" />
            </label>
          )}
          {uploadError && <p className="text-xs text-[#f04452] mt-1.5">{uploadError}</p>}
        </div>

        <div>
          <label className={labelCls}>링크 URL <span className="font-normal text-[#b0b8c1]">(선택)</span></label>
          {announcements.length > 0 && (
            <select
              className={`${inputCls} mb-2`}
              value=""
              onChange={(e) => { if (e.target.value) setLinkUrl(`/announcements/${e.target.value}`) }}
            >
              <option value="">공지에서 가져오기…</option>
              {announcements.map((a) => (
                <option key={a.id} value={a.id}>#{a.id} · {a.title}</option>
              ))}
            </select>
          )}
          <input className={inputCls} value={linkUrl} onChange={(e) => setLinkUrl(e.target.value)} placeholder="/announcements/3 또는 https://..." />
          <p className="text-xs text-[#8b95a1] mt-1.5">위에서 공지를 고르면 자동 입력돼요. 외부 링크는 <code className="text-[#4e5968]">https://</code> 직접 입력</p>
        </div>

        <label className="flex items-center gap-2.5 cursor-pointer select-none">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4 accent-[#3182f6]" />
          <span className="text-sm font-medium text-[#4e5968]">지금 활성화 <span className="text-[#8b95a1]">(켜면 기존 활성 팝업은 자동으로 꺼져요 · 동시 1개)</span></span>
        </label>

        <div className="flex items-center gap-2">
          <button type="submit" disabled={saving || uploading} className={primaryBtn}>
            {saving ? '저장 중...' : editingId ? '수정 저장' : '등록'}
          </button>
          {editingId && <button type="button" onClick={reset} className={ghostBtn}>취소</button>}
        </div>
      </form>

      <div className="space-y-2.5">
        {list.length === 0 ? (
          <p className="text-center text-sm text-[#8b95a1] py-10">등록된 팝업이 없어요.</p>
        ) : (
          list.map((p) => (
            <div key={p.id} className="bg-white rounded-2xl px-5 py-4 shadow-[0_1px_2px_rgba(0,0,0,0.04)] flex items-center gap-3">
              {p.imageUrl && <img src={p.imageUrl} alt="" className="h-12 w-12 rounded-lg object-cover bg-[#f2f4f6] shrink-0" />}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-[15px] font-bold text-[#191f28] truncate">{p.title}</p>
                  {p.active && <span className="shrink-0 text-[11px] font-bold text-[#3182f6] bg-[#3182f6]/10 px-2 py-0.5 rounded-full">활성</span>}
                </div>
                <p className="text-xs text-[#8b95a1] mt-0.5 truncate">#{p.id}{p.linkUrl ? ` · ${p.linkUrl}` : ''}</p>
              </div>
              <div className="flex items-center gap-1 shrink-0">
                <button onClick={() => startEdit(p)} className="text-xs font-semibold text-[#3182f6] hover:text-[#1b64da] px-2 py-1">수정</button>
                <button onClick={() => handleDelete(p.id)} className="text-xs font-semibold text-[#f04452] hover:text-[#d63a46] px-2 py-1">삭제</button>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

function AdminPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState('announcements')
  const [allowed, setAllowed] = useState(null) // null=확인중, false=권한없음, true=관리자

  useEffect(() => {
    checkAdmin()
      .then((res) => {
        if (res.data.admin) setAllowed(true)
        else { setAllowed(false); navigate('/', { replace: true }) }
      })
      .catch(() => { setAllowed(false); navigate('/', { replace: true }) })
  }, [navigate])

  if (allowed !== true) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#f2f4f6]">
        <p className="text-sm text-[#8b95a1]">{allowed === false ? '권한이 없어요.' : '확인 중...'}</p>
      </div>
    )
  }

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
          <span className="text-base font-bold text-[#191f28]">관리자</span>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-5 pb-16 pt-6">
        <div className="flex gap-2 mb-6">
          {[
            { key: 'announcements', label: '공지 관리' },
            { key: 'popups', label: '팝업 관리' },
          ].map(({ key, label }) => (
            <button
              key={key}
              onClick={() => setTab(key)}
              className={`px-4 py-2 rounded-full text-sm font-bold transition-colors ${
                tab === key ? 'bg-[#191f28] text-white' : 'bg-white text-[#4e5968] border border-[#e5e8eb] hover:bg-[#f2f4f6]'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === 'announcements' ? <AnnouncementsTab /> : <PopupsTab />}
      </main>
    </div>
  )
}

export default AdminPage
