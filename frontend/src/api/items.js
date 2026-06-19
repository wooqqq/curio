import client from './client'

export const getItems = (category, page = 0, size = 20, q = '') => {
  const params = { page, size }
  if (category) params.category = category
  if (q && q.trim()) params.q = q.trim()
  return client.get('/items', { params })
}

export const addItem = (url) => client.post('/items', { url })

// 제목/메모/카테고리 부분 수정 ({ title?, memo?, category? })
export const updateItem = (id, body) => client.patch(`/items/${id}`, body)

// 아이템 태그 추가/제거 (성공 시 갱신된 아이템 반환)
export const addTag = (id, name) => client.post(`/items/${id}/tags`, { name })
export const removeTag = (id, name) => client.delete(`/items/${id}/tags`, { params: { name } })

export const recrawlItem = (id) => client.post(`/items/${id}/recrawl`)

export const deleteItem = (id) => client.delete(`/items/${id}`)
