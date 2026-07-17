/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  images: {
    remotePatterns: [
      { protocol: "http", hostname: "localhost", port: "9000" },
      { protocol: "https", hostname: "brain.aile.ir" },
      { protocol: "https", hostname: "aile.ir" },
      { protocol: "https", hostname: "**.amazonaws.com" },
    ],
  },
  experimental: {
    optimizePackageImports: [
      "@radix-ui/react-avatar",
      "@radix-ui/react-dialog",
      "@radix-ui/react-dropdown-menu",
      "@radix-ui/react-select",
      "@radix-ui/react-tabs",
      "lucide-react",
    ],
  },
};

export default nextConfig;
