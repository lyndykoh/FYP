# ---- Build Stage ----
FROM node:20-alpine AS builder
WORKDIR /app

# Copy dependency files and install them
# This layer is cached unless package.json or lockfile changes, speeding up rebuilds
COPY package*.json ./
RUN npm ci

# Copy the rest of the application source code
COPY . .

ARG NEXT_PUBLIC_API_URL=http://localhost:8081
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ARG NEXT_PUBLIC_LEARNING_API_URL=http://localhost:8083
ENV NEXT_PUBLIC_LEARNING_API_URL=${NEXT_PUBLIC_LEARNING_API_URL}
ARG NEXT_PUBLIC_GRADING_API_URL=http://localhost:8082
ENV NEXT_PUBLIC_GRADING_API_URL=${NEXT_PUBLIC_GRADING_API_URL}
ARG ENFORCE_HTTPS=false
ENV ENFORCE_HTTPS=${ENFORCE_HTTPS}

# Build the Next.js application, which will generate the .next/standalone output
RUN npm run build

# ---- Runtime Stage ----
FROM node:20-alpine AS runner
WORKDIR /app

# Set production environment variables
ENV NODE_ENV=production
# Essential for listening to connections inside the container
ENV HOSTNAME="0.0.0.0"
ENV PORT=3000

# Create a non-root user for security
RUN addgroup --system --gid 1001 nodejs && \
    adduser --system --uid 1001 nextjs

# Copy the necessary build output from the builder stage
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

# Switch to the non-root user
USER nextjs

# Expose the port the app runs on
EXPOSE 3000

# Start the server
CMD ["node", "server.js"]
