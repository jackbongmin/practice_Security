import { useState } from 'react'

const API = 'http://localhost:8080'

const ENDPOINTS = [
  { label: '공개 공지', path: '/api/public/notices' },
  { label: '내 정보', path: '/api/me' },
  { label: '관리자 통계', path: '/api/admin/stats' },
  { label: '회원 목록(관리자)', path: '/api/admin/members' },
]

type Mode = 'basic' | 'session'
type Result = { req: string; status: string; body: string }

export default function App() {
  const [username, setUsername] = useState('user')
  const [password, setPassword] = useState('1234')
  const [mode, setMode] = useState<Mode>('basic')
  const [results, setResults] = useState<Result[]>([])

  async function send(method: string, path: string, body?: { type: string; data: string }) {
    const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
    // Basic: 매 요청에 아이디·비밀번호를 싣는다 / 세션: 쿠키(JSESSIONID)를 싣는다
    if (mode === 'basic' && username) headers.Authorization = 'Basic ' + btoa(`${username}:${password}`)
    if (body) headers['Content-Type'] = body.type

    let result: Result
    try {
      const res = await fetch(API + path, {
        method,
        headers,
        body: body?.data,
        credentials: mode === 'session' ? 'include' : 'same-origin',
      })
      result = { req: `${method} ${path}`, status: `${res.status}`, body: await res.text() }
    } catch {
      // 서버가 꺼져 있거나 CORS로 막히면 응답을 읽을 수 없다
      result = { req: `${method} ${path}`, status: '요청 실패', body: '네트워크 오류 또는 CORS 차단 — 브라우저 개발자 도구 Console 탭을 확인하세요' }
    }
    setResults((prev) => [result, ...prev])
  }

  const signup = () =>
    send('POST', '/api/public/signup', { type: 'application/json', data: JSON.stringify({ username, password }) })
  const login = () =>
    send('POST', '/api/login', {
      type: 'application/x-www-form-urlencoded',
      data: new URLSearchParams({ username, password }).toString(),
    })

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 820, margin: '40px auto' }}>
      <h1>Spring Security 2일차</h1>
      <section>
        <input placeholder="아이디" value={username} onChange={(e) => setUsername(e.target.value)} />{' '}
        <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />{' '}
        <button onClick={signup}>회원가입</button>
      </section>
      <section style={{ margin: '16px 0' }}>
        <label>
          <input type="radio" checked={mode === 'basic'} onChange={() => setMode('basic')} /> Basic (요청마다 아이디·비밀번호)
        </label>{' '}
        <label>
          <input type="radio" checked={mode === 'session'} onChange={() => setMode('session')} /> 세션 (로그인 후 쿠키)
        </label>{' '}
        <button onClick={login} disabled={mode !== 'session'}>로그인</button>{' '}
        <button onClick={() => send('POST', '/api/logout')} disabled={mode !== 'session'}>로그아웃</button>
      </section>
      <section style={{ margin: '16px 0' }}>
        {ENDPOINTS.map((e) => (
          <button key={e.path} onClick={() => send('GET', e.path)} style={{ marginRight: 8 }}>
            {e.label}
          </button>
        ))}
        <button onClick={() => setResults([])}>결과 지우기</button>
      </section>
      <table border={1} cellPadding={6} style={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead>
          <tr><th>요청</th><th>상태</th><th>응답</th></tr>
        </thead>
        <tbody>
          {results.map((r, i) => (
            <tr key={i}>
              <td>{r.req}</td>
              <td>{r.status}</td>
              <td><code style={{ wordBreak: 'break-all' }}>{r.body}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
