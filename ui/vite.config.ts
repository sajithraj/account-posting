import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],
    server: {
        port: 3000,
        proxy: {
            // Forward /v2 calls to the account-posting backend during local dev
            '/v3': {
                target: 'http://localhost:4566',
                changeOrigin: true,
            },
        },
    },
});
