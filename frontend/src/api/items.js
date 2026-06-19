import client from './client'

export const getItems = (category, page = 0, size = 20, q = '') => {
  const params = { page, size }
  if (category) params.category = category
  if (q && q.trim()) params.q = q.trim()
  return client.get('/items', { params })
}

export const addItem = (url) => client.post('/items', { url })

// 텍스트(메모) 직접 추가 → TEXT 아이템 (설계결정 #35)
export const addText = (text) => client.post('/items/text', { text })

// 이미지 파일 직접 업로드 → IMAGE 아이템 (비전 캡션 자동). FormData면 axios가 multipart 헤더를 자동 설정.
export const addImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return client.post('/items/image', form)
}

// 제목/메모/카테고리 부분 수정 ({ title?, memo?, category? })
export const updateItem = (id, body) => client.patch(`/items/${id}`, body)

// 아이템 태그 추가/제거 (성공 시 갱신된 아이템 반환)
export const addTag = (id, name) => client.post(`/items/${id}/tags`, { name })
export const removeTag = (id, name) => client.delete(`/items/${id}/tags`, { params: { name } })

export const recrawlItem = (id) => client.post(`/items/${id}/recrawl`)

export const deleteItem = (id) => client.delete(`/items/${id}`)
