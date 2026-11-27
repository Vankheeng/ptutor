# Build context: tmp/frontend_dist_current, produced by npm run build on this branch.
FROM ptutor-demo-ref-frontend:latest
COPY . /usr/share/nginx/html
