# Annoying Client cosmetics server
FROM golang:1.24-alpine AS build
WORKDIR /src
COPY go.mod ./
COPY *.go ./
RUN CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /cosmetics .

FROM gcr.io/distroless/static-debian12
COPY --from=build /cosmetics /cosmetics
ENV PORT=8080 DATA=/data/cosmetics.json ADMINS=theavgjim_
VOLUME /data
EXPOSE 8080
USER 65532
ENTRYPOINT ["/cosmetics"]
