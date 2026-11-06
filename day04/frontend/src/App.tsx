import { useEffect, useState } from 'react'

const API = 'http://localhost:8080'

type Post = { id: number; title: string; author: string }
type Result = { req: string; status: string; body: string }
type Body = { type: string; data: string }

export default function App() {
  const [username, setUsername] = useState('user')
  const [password, setPassword] = useState('1234')
  const [me, setMe] = useState('(로그인 안 함)')
  const [posts, setPosts] = useState<Post[]>([])
  const [title, setTitle] = useState('')
  const [results, setResults] = useState<Result[]>([])

  // 3단계: POST·PUT·DELETE 전에 CSRF 토큰을 받아 헤더에 싣는다. 서버에 토큰 API가 없으면(시작 코드) 그냥 보낸다
  async function csrfHeader(): Promise<Record<string, string>> {
    try {
      const res = await fetch(API + '/api/public/csrf', { credentials: 'include' })
      if (!res.ok) return {}
      const t = await res.json()
      return { [t.headerName]: t.token }
    } catch {
      return {}
    }
  }

  async function send(method: string, path: string, body?: Body) {
    const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
    if (body) headers['Content-Type'] = body.type
    if (method !== 'GET') Object.assign(headers, await csrfHeader())
    let result: Result
    try {
      const res = await fetch(API + path, { method, headers, body: body?.data, credentials: 'include' })
      result = { req: `${method} ${path}`, status: `${res.status}`, body: await res.text() }
    } catch {
      result = { req: `${method} ${path}`, status: '요청 실패', body: '네트워크 오류 또는 CORS 차단 — 브라우저 개발자 도구 Console 탭을 확인하세요' }
    }
    setResults((prev) => [result, ...prev])
    return result
  }

  const json = (data: object) => ({ type: 'application/json', data: JSON.stringify(data) })
  const form = (data: Record<string, string>) => ({
    type: 'application/x-www-form-urlencoded',
    data: new URLSearchParams(data).toString(),
  })

  async function loadPosts() {
    const r = await send('GET', '/api/posts')
    if (r.status === '200') setPosts(JSON.parse(r.body))
  }

  async function login() {
    const r = await send('POST', '/api/login', form({ username, password }))
    if (r.status === '200') setMe(username)
  }

  async function logout() {
    await send('POST', '/api/logout')
    setMe('(로그인 안 함)')
  }

  useEffect(() => {
    loadPosts()
  }, [])

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 860, margin: '40px auto' }}>
      <h1>Spring Security 4일차 — 게시판</h1>
      <section>
        <input placeholder="아이디" value={username} onChange={(e) => setUsername(e.target.value)} />{' '}
        <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />{' '}
        <button onClick={login}>로그인</button> <button onClick={logout}>로그아웃</button>{' '}
        <b>현재: {me}</b>
        <div style={{ color: '#666', fontSize: 13 }}>계정: user · kim (USER) / admin (ADMIN), 비밀번호 모두 1234</div>
      </section>
      <section style={{ margin: '16px 0' }}>
        <input placeholder="새 글 제목 (HTML 가능: <b>굵게</b>)" size={40} value={title} onChange={(e) => setTitle(e.target.value)} />{' '}
        <button onClick={async () => { await send('POST', '/api/posts', json({ title })); loadPosts() }}>글쓰기</button>{' '}
        <button onClick={loadPosts}>새로고침</button>{' '}
        <button onClick={() => setResults([])}>결과 지우기</button>
      </section>
      <table border={1} cellPadding={6} style={{ borderCollapse: 'collapse', width: '100%', marginBottom: 16 }}>
        <thead>
          <tr><th>번호</th><th>제목</th><th>작성자</th><th></th></tr>
        </thead>
        <tbody>
          {posts.map((p) => (
            <tr key={p.id}>
              <td>{p.id}</td>
              {/*/!* 1단계: 제목의 HTML을 그대로 화면에 넣는다 (XSS 구멍) *!/*/}
              {/*<td dangerouslySetInnerHTML={{ __html: p.title }} />*/}
              {/* 후: { }는 값을 글자로 넣음 (이스케이프) → <img ...>가 그림이 아니라 글자로 보임 */}
              <td>{p.title}</td>
              <td>{p.author}</td>
              <td>
                <button onClick={async () => { await send('DELETE', `/api/posts/${p.id}`); loadPosts() }}>삭제</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
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
