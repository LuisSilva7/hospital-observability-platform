import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // build mínimo autocontido para a imagem Docker (node server.js)
  output: "standalone",
};

export default nextConfig;
