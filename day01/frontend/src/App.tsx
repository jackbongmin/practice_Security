import { useState } from 'react'

const API = 'http://localhost:8080'

const ENDPOINTS = [
  { label: '공개 공지', path: '/api/public/notices' },
  { label: '내 정보', path: '/api/me' },
  { label: '관리자 통계', path: '/api/admin/stats' },
]

type Result = { path: string; status: string; body: string }

export default function App() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [results, setResults] = useState<Result[]>([])

  async function call(path: string) {
    // 아이디를 비우면 로그인 정보 없이 요청한다
    const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' }
    if (username) headers.Authorization = 'Basic ' + btoa(`${username}:${password}`)

    let result: Result
    try {
      const res = await fetch(API + path, { headers })
      result = { path, status: `${res.status}`, body: await res.text() }
    } catch {
      // 서버가 꺼져 있거나 CORS로 막히면 응답을 읽을 수 없다
      result = { path, status: '요청 실패', body: '네트워크 오류 또는 CORS 차단 — 브라우저 개발자 도구 Console 탭을 확인하세요' }
    }
    setResults((prev) => [result, ...prev])
  }

  return (
    <main style={{ fontFamily: 'sans-serif', maxWidth: 720, margin: '40px auto' }}>
      <h1>Spring Security 1일차</h1>
      <section>
        <input placeholder="아이디 (비우면 로그인 없이)" value={username} onChange={(e) => setUsername(e.target.value)} />{' '}
        <input placeholder="비밀번호" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </section>
      <section style={{ margin: '16px 0' }}>
        {ENDPOINTS.map((e) => (
          <button key={e.path} onClick={() => call(e.path)} style={{ marginRight: 8 }}>
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
              <td>{r.path}</td>
              <td>{r.status}</td>
              <td><code>{r.body}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
    </main>
  )
}
