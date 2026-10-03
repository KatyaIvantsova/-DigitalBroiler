import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** @type {import('next').NextConfig} */
const nextConfig = {
  turbopack: {
    root: __dirname,
  },
  // Самодостаточная сборка для Docker-образа (node server.js)
  output: 'standalone',
  images: {
    unoptimized: true,
  },
  allowedDevOrigins: ['192.168.56.1', 'localhost', '127.0.0.1'],
}

export default nextConfig
