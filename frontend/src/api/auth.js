import client from './client'

export const reissueToken = (refreshToken) =>
  client.post('/auth/reissue', { refreshToken })

export const logout = () =>
  client.post('/auth/logout')
