import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: '/api/:path*',
        destination: 'http://localhost:8080/:path*', // 代理到 Spring Boot 后端
      },
    ];
  },
};

export default nextConfig;
