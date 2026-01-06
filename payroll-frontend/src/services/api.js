import axios from "axios";
import keycloak from "./keycloak";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080",
});

// agrega el Bearer token automáticamente (y refresca si está por expirar)
api.interceptors.request.use(async (config) => {
  try {
    if (keycloak?.token) {
      // refresca si expira en < 30s
      await keycloak.updateToken(30);
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    }
  } catch (e) {
    // si no pudo refrescar, forzar login
    // (opcional: comenta esto si te molesta que redirija)
    keycloak.login();
  }
  return config;
});

export default api;
