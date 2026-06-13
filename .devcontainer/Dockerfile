FROM mcr.microsoft.com/devcontainers/universal:2

RUN set -eux; \
	if [ -d /etc/apt/sources.list.d ]; then \
		find /etc/apt/sources.list.d -type f \( -name '*yarn*' -o -name '*.list' -o -name '*.sources' \) -print0 | xargs -0 -r rm -f; \
	fi; \
	sed -i '/dl\.yarnpkg\.com/d' /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources 2>/dev/null || true