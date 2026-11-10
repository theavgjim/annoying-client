# Annoying Client cosmetics server
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /cosmetics . && mkdir /data

FROM gcr.io/distroless/static-debian12
COPY --from=build /cosmetics /cosmetics
# The server runs as user 65532, so it must own the database folder.
COPY --from=build --chown=65532:65532 /data /data
ENV PORT=8080 DATA=/data/cosmetics.json ADMINS=theavgjim_
VOLUME /data
EXPOSE 8080
USER 65532
ENTRYPOINT ["/cosmetics"]
