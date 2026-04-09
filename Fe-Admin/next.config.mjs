/** @type {import('next').NextConfig} */
const gatewayUrl = process.env.NEXT_PUBLIC_API_URL
  ? process.env.NEXT_PUBLIC_API_URL.replace('/api', '')
  : 'http://localhost:3000';

const nextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  images: {
    unoptimized: true,
  },
  allowedDevOrigins: ['restaurant-server.site'],
  async rewrites() {
    return [
      {
        source: '/api/images/:path*',
        destination: `${gatewayUrl}/api/images/:path*`,
      },
    ]
  },
}

export default nextConfig
