import client from './client'

export const getItems = (category, page = 0, size = 20, q = '') => {
  const params = { page, size }
  if (category) params.category = category
  if (q && q.trim()) params.q = q.trim()
  return client.get('/items', { params })
}
