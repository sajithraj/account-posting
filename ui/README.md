# ui — React Frontend

React 18 · TypeScript · Vite · TanStack Query · Axios

---

## Run Commands

```bash
cd ui

npm install       # first time only
npm run dev       # dev server — http://localhost:3000
npm run build     # production build (output: dist/)
npm run preview   # preview the production build locally
```

Vite proxies all `/api` requests to `http://localhost:8080` in dev mode — no CORS configuration needed. Make sure the
Spring Boot API is running before starting the UI.

---

## Pages

| Route                  | Page                | Description                                                |
|------------------------|---------------------|------------------------------------------------------------|
| `/`                    | `PostingListPage`   | Search form + paginated posting table + retry buttons      |
| `/postings/:postingId` | `PostingDetailPage` | Full posting detail + leg table + manual leg status update |
| `/dashboard`           | `DashboardPage`     | High-level overview                                        |
| `/config`              | `ConfigPage`        | CRUD for `posting_config` routing rules                    |

---

## Structure

```
src/
├── main.tsx                  React entry — QueryClientProvider setup
├── App.tsx                   Router + route definitions + NavLink navigation
├── types/posting.ts          TypeScript interfaces matching backend DTOs
├── api/postingApi.ts         All axios calls — unwraps ApiResponse<T>, exports getErrorMessage()
├── pages/
│   ├── PostingListPage.tsx   Search, pagination, inline leg expansion, retry (all / selected / single)
│   ├── PostingDetailPage.tsx Single posting view + LegTable + manual status update
│   ├── CreatePostingPage.tsx New posting form
│   └── ConfigPage.tsx        Routing config management (create / update / delete / cache flush)
└── components/
    ├── StatusBadge.tsx       Coloured badge — PENDING=amber, SUCCESS=green, FAILED=red
    └── LegTable.tsx          Renders LegResponse[] — shows target system, operation, mode, status
```

---

## Key Dependencies

| Package               | Version | Purpose                               |
|-----------------------|---------|---------------------------------------|
| react                 | 18      | UI framework                          |
| react-router-dom      | 6       | Client-side routing                   |
| @tanstack/react-query | 5       | Server state — useQuery / useMutation |
| axios                 | 1.7     | HTTP client — all API calls           |
| typescript            | 5       | Type safety                           |
| vite                  | 5       | Build tool + dev server               |

---

## API Client

`src/api/postingApi.ts` wraps every backend call. It:

- Uses `axios` with `baseURL: '/api'`
- Unwraps the `ApiResponse<T>` envelope — callers get the data directly
- Exports `getErrorMessage(err)` — extracts the human-readable message from any error shape

All requests use **snake_case** JSON bodies, matching the backend Jackson configuration.
