# Frontend PTutor — multi-stage: build Vite rồi phục vụ bằng nginx (thêm mới cho demo trợ lý).
# /api được nginx chuyển sang backend nên VITE_API_BASE_URL để trống (cùng origin, không cần CORS).
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
ARG VITE_ASSISTANT_ENABLED=true
ENV VITE_API_BASE_URL="" VITE_ASSISTANT_ENABLED=$VITE_ASSISTANT_ENABLED
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
