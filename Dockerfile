# P18: backend image (pinned base, SBOM via syft in release job).
# go.mod 1.26 ister; eski imaj build'i kırıyordu.
FROM golang:1.26-alpine AS build
WORKDIR /src
COPY . .
RUN go build -o /out/pocket-agent-backend ./backend/cmd/server
FROM alpine:3.20
COPY --from=build /out/pocket-agent-backend /usr/local/bin/
EXPOSE 8080
ENTRYPOINT ["pocket-agent-backend"]
