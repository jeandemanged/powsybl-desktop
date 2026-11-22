# Sandbox for running Claude Code (and building/testing PowSyBl Desktop) in a dev container.
# Adapted from https://github.com/anthropics/claude-code/tree/main/.devcontainer
FROM maven:3.10.0-eclipse-temurin-25-noble

ARG TZ=Etc/UTC
ENV TZ="$TZ"

# Dev tools, firewall tooling, and the native libraries JavaFX (prism sw, WebView) loads in the headless tests
RUN apt-get update && apt-get install -y --no-install-recommends \
    sudo less git procps man-db unzip gnupg2 gh jq nano vim ripgrep \
    iptables ipset iproute2 dnsutils aggregate curl ca-certificates \
    libgtk-3-0t64 libxtst6 libxxf86vm1 libgl1 libasound2t64 libxslt1.1 libxml2 \
    fonts-dejavu-core fontconfig \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# The noble image ships an "ubuntu" user with uid 1000, matching the usual host (WSL/Linux) user
ARG USERNAME=ubuntu

# Persistent shell history, workspace, Claude config and Maven repository (the last two are volumes)
RUN mkdir -p /commandhistory /workspace /home/$USERNAME/.claude /home/$USERNAME/.m2 \
  && touch /commandhistory/.bash_history \
  && chown -R $USERNAME:$USERNAME /commandhistory /workspace /home/$USERNAME/.claude /home/$USERNAME/.m2 \
  && echo "export PROMPT_COMMAND='history -a' HISTFILE=/commandhistory/.bash_history" >> /home/$USERNAME/.bashrc

ENV DEVCONTAINER=true
# The maven image points its config at /root and copies settings through an entrypoint: neither applies here
ENV MAVEN_CONFIG=/home/$USERNAME/.m2
ENTRYPOINT []
ENV EDITOR=nano VISUAL=nano

# Firewall script, runnable as root by the container user (and nothing else via sudo)
COPY init-firewall.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/init-firewall.sh \
  && echo "$USERNAME ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/$USERNAME-firewall \
  && chmod 0440 /etc/sudoers.d/$USERNAME-firewall

USER $USERNAME
WORKDIR /workspace

# Claude Code, native installer. Updates are disabled (the firewall blocks them): rebuild the container instead.
RUN curl -fsSL https://claude.ai/install.sh | bash
ENV PATH="/home/$USERNAME/.local/bin:$PATH"
ENV DISABLE_AUTOUPDATER=1
