FROM node:24-bookworm-slim

ENV NODE_ENV=production
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

COPY src ./src
COPY README.md ./README.md

RUN mkdir -p /app/reports \
    && chown -R node:node /app

USER node
ENTRYPOINT ["node", "src/cli.js"]
