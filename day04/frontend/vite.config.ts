import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 프록시를 쓰지 않는다: 브라우저가 3000 → 8080으로 직접 요청해야 CORS 동작이 보인다
export default defineConfig({
  plugins: [react()],
  server: { port: 3000 },
})
