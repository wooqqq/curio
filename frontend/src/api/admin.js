import client from './client'

// 현재 로그인 유저가 관리자인지 (res.data.admin)
export const checkAdmin = () => client.get('/admin/check')

// 공지 관리
export const createAnnouncement = (body) => client.post('/admin/announcements', body)
export const updateAnnouncement = (id, body) => client.put(`/admin/announcements/${id}`, body)
export const deleteAnnouncement = (id) => client.delete(`/admin/announcements/${id}`)

// 팝업 관리
export const getAdminPopups = () => client.get('/admin/popups')
export const createPopup = (body) => client.post('/admin/popups', body)
export const updatePopup = (id, body) => client.put(`/admin/popups/${id}`, body)
export const deletePopup = (id) => client.delete(`/admin/popups/${id}`)

// 팝업 배너 이미지 업로드 → res.data.url
export const uploadPopupImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return client.post('/admin/upload-image', form)
}
