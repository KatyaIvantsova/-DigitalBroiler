FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm
# В базовом образе подключён apt-репозиторий yarn с просроченным ключом: из-за него
# apt-get update падает, и фича docker-in-docker не устанавливается. Yarn проекту не нужен.
RUN rm -f /etc/apt/sources.list.d/yarn.list
