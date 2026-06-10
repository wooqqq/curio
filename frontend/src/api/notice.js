import client from './client'

// 로그인 사용자 공통 — 공지 조회 + 활성 팝업 조회
export const getAnnouncements = () => client.get('/announcements')
export const getAnnouncement = (id) => client.get(`/announcements/${id}`)
export const getActivePopup = () => client.get('/popups/active')
