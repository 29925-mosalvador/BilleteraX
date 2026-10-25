// Pages uses the public service. The integrated Debian web stays on its own origin.
window.BILLETERAX_CONFIG = {
  apiBase: window.location.hostname === "29925-mosalvador.github.io"
    ? "https://billeterax-access.onrender.com"
    : window.location.origin,
  managed: true
};
