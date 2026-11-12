FROM node:22-alpine

WORKDIR /app
COPY server.js ./

# Каталог состояния должен быть доступен пользователю node:
# в него монтируется named volume, при первой инициализации он наследует владельца.
RUN mkdir -p /data && chown node:node /data

USER node
ENV NODE_ENV=production
EXPOSE 3000

CMD ["node", "server.js"]
