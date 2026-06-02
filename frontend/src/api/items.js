import client from './client'

export const getItems = (category, page = 0, size = 20) => {
  const params = { page, size }
  if (category) params.category = category
  return client.get('/items', { params })
}