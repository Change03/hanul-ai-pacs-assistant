FROM node:20-alpine

WORKDIR /app
RUN apk add --no-cache curl
COPY package.json ./
RUN npm install
COPY . .
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

EXPOSE 3000
CMD ["npm", "start"]
