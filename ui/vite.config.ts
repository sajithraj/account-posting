import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

const localstackProxyTarget =
    process.env.VITE_LOCALSTACK_PROXY_TARGET
    ?? 'http://localhost:4566/restapis/q81zemjzgp/dev-local/_user_request_';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            // Forward /v3 calls to the LocalStack API Gateway during local dev.
            '/v3': {
                target: localstackProxyTarget,
                changeOrigin: true,
            },
        },
    },
});
