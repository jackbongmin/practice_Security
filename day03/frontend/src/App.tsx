import { useEffect, useState } from 'react'

const API = 'http://localhost:8080'

type Post = { id: number; title: string; author: string }
type Result = { req: string; status: string; body: string }

export default function App() {
  const [username, setUsername] = useState('user')
  const [password, setPassword] = useState('1234')
  const [me, setMe] = useState('(로그인 안 함)')
  const [posts, setPosts] = useState<Post[]>([])
  const [title, setTitle] = useState('')
  const [results, setResults] = useState<Result[]>([])

  // 세션 쿠키(JSESSIONID)를 함께 보낸다
  async function send(method: string, path: string, body?: { type: string; data: string }) {
    const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
    if (body) headers['Content-Type'] = body.type
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

  async function loadPosts() {
    const r = await send('GET', '/api/posts')
    if (r.status === '200') setPosts(JSON.parse(r.body))
  }

  async function login() {
    const r = await send('POST', '/api/login', {
      type: 'application/x-www-form-urlencoded',
      data: new URLSearchParams({ username, password }).toString(),
    })
    if (r.status === '200') setMe(username)
  }

  async function logout() {
    await send('POST', '/api/logout')
    setMe('(로그인 안 함)')
  }

  async function edit(p: Post) {
    const t = prompt('새 제목', p.title)
    if (t) {
      await send('PUT', `/api/posts/${p.id}`, json({ title: t }))
      loadPosts()
    }
  }

  useEffect(() => {
    loadPosts()
  }, [])

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 860, margin: '40px auto' }}>
      <h1>Spring Security 3일차 — 게시판</h1>
      <section>
        <input placeholder="아이디" value={username} onChange={(e) => setUsername(e.target.value)} />{' '}
        <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />{' '}
        <button onClick={login}>로그인</button> <button onClick={logout}>로그아웃</button>{' '}
        <b>현재: {me}</b>
        <div style={{ color: '#666', fontSize: 13 }}>계정: user · kim (USER) / admin (ADMIN), 비밀번호 모두 1234</div>
      </section>
      <section style={{ margin: '16px 0' }}>
        <input placeholder="새 글 제목" value={title} onChange={(e) => setTitle(e.target.value)} />{' '}
        <button onClick={async () => { await send('POST', '/api/posts', json({ title })); loadPosts() }}>글쓰기</button>{' '}
        <button onClick={loadPosts}>새로고침</button>{' '}
        <button onClick={async () => { await send('DELETE', '/api/posts'); loadPosts() }}>전체 삭제</button>{' '}
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
              <td>{p.title}</td>
              <td>{p.author}</td>
              <td>
                <button onClick={() => edit(p)}>수정</button>{' '}
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
