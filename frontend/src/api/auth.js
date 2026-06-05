import client from './client'

export const reissueToken = () =>
  client.post('/auth/reissue', null, { _skipAuthRetry: true })

export const logout = () =>
  client.post('/auth/logout')
