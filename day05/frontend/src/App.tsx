import { useState } from 'react'

const API = 'http://localhost:8080'

type Result = { req: string; status: string; body: string }

export default function App() {
  const [username, setUsername] = useState('user')
  const [password, setPassword] = useState('1234')
  // 토큰 방식: 토큰을 메모리(React 상태)에만 둔다. 새로고침하면 사라진다
  const [token, setToken] = useState('')
  const [results, setResults] = useState<Result[]>([])

  // 4일차: 쿠키를 쓰는 POST에는 CSRF 토큰을 싣는다
  async function csrfHeader(): Promise<Record<string, string>> {
    const t = await (await fetch(API + '/api/public/csrf', { credentials: 'include' })).json()
    return { [t.headerName]: t.token }
  }

  async function send(label: string, method: string, path: string, init: RequestInit = {}) {
    let result: Result
    try {
      const res = await fetch(API + path, { method, ...init })
      result = { req: `[${label}] ${method} ${path}`, status: `${res.status}`, body: await res.text() }
    } catch {
      result = { req: `[${label}] ${method} ${path}`, status: '요청 실패', body: '네트워크 오류 또는 CORS 차단 — Console 탭 확인' }
    }
    setResults((prev) => [result, ...prev])
    return result
  }

  const xhr = { 'X-Requested-With': 'XMLHttpRequest' }
  const loginJson = () => JSON.stringify({ username, password })

  // ① 세션
  const sessionLogin = async () =>
    send('세션', 'POST', '/api/login', {
      credentials: 'include',
      headers: { ...xhr, 'Content-Type': 'application/x-www-form-urlencoded', ...(await csrfHeader()) },
      body: new URLSearchParams({ username, password }).toString(),
    })
  const sessionMe = () => send('세션', 'GET', '/api/me', { credentials: 'include', headers: xhr })
  const sessionLogout = async () =>
    send('세션', 'POST', '/api/logout', { credentials: 'include', headers: { ...xhr, ...(await csrfHeader()) } })

  // ② 순진한 쿠키
  const naiveLogin = async () =>
    send('쿠키', 'POST', '/api/public/naive/login', {
      credentials: 'include',
      headers: { ...xhr, 'Content-Type': 'application/json', ...(await csrfHeader()) },
      body: loginJson(),
    })
  const naiveMe = () => send('쿠키', 'GET', '/api/public/naive/me', { credentials: 'include', headers: xhr })

  // ③ 토큰 — 쿠키를 보내지 않고 Authorization 헤더에 싣는다
  async function tokenLogin() {
    const r = await send('토큰', 'POST', '/api/token/login', {
      headers: { 'Content-Type': 'application/json' },
      body: loginJson(),
    })
    if (r.status === '200') setToken(JSON.parse(r.body).token)
  }
  const bearer = (): RequestInit => ({ headers: token ? { Authorization: `Bearer ${token}` } : {} })
  const tokenMe = () => send('토큰', 'GET', '/api/token/me', bearer())
  const tokenAdmin = () => send('토큰', 'GET', '/api/token/admin', bearer())
  // 토큰 가운데 조각(payload)은 암호화가 아니라 Base64URL — 누구나 읽을 수 있다
  const payload = token ? atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')) : ''

  const row = { margin: '10px 0', padding: 10, border: '1px solid #ccc' }

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 900, margin: '40px auto' }}>
      <h1>Spring Security 5일차 — 쿠키 · 세션 · 토큰</h1>
      <section>
        <input placeholder="아이디" value={username} onChange={(e) => setUsername(e.target.value)} />{' '}
        <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <span style={{ color: '#666', fontSize: 13 }}> 계정: user · kim (USER) / admin (ADMIN), 비밀번호 1234</span>
      </section>
      <section style={row}>
        <b>① 세션</b>{' '}
        <button onClick={sessionLogin}>로그인</button> <button onClick={sessionMe}>내 정보</button>{' '}
        <button onClick={sessionLogout}>로그아웃</button>
      </section>
      <section style={row}>
        <b>② 순진한 쿠키</b>{' '}
        <button onClick={naiveLogin}>로그인</button> <button onClick={naiveMe}>내 정보</button>
      </section>
      <section style={row}>
        <b>③ 토큰(JWT)</b>{' '}
        <button onClick={tokenLogin}>로그인</button> <button onClick={tokenMe}>내 정보</button>{' '}
        <button onClick={tokenAdmin}>관리자 API</button> <button onClick={() => setToken('')}>로그아웃(토큰 버리기)</button>
        <div style={{ fontSize: 13, wordBreak: 'break-all', marginTop: 6 }}>
          <div>토큰: <code>{token || '(없음)'}</code></div>
          {payload && <div>내용(payload): <code>{payload}</code></div>}
        </div>
      </section>
      <button onClick={() => setResults([])}>결과 지우기</button>
      <table border={1} cellPadding={6} style={{ borderCollapse: 'collapse', width: '100%', marginTop: 10 }}>
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
