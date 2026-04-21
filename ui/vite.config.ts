import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            // Forward /v2 calls to the account-posting backend during local dev
            '/v3': {
                target: 'http://localhost:4566/restapis/9r2xy1g5ow/dev-local/_user_request_',
                changeOrigin: true,
            },
        },
    },
});
