// src/services/api.js
import axios from "axios";
import keycloak from "./keycloak";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8080",
  headers: { "Content-Type": "application/json" },
});

// Adjunta token y lo refresca si expira pronto
api.interceptors.request.use(async (config) => {
  // Si keycloak aún no está inicializado, igual deja pasar (puede dar 401 en endpoints protegidos)
  if (!keycloak) return config;

  // Si hay token, refrescar si queda poco y agregar Authorization
  if (keycloak.token) {
    try {
      await keycloak.updateToken(30); // refresh si expira en <30s
      config.headers.Authorization = `Bearer ${keycloak.token}`;
    } catch (e) {
      // No fuerces login acá si te molesta el redirect durante pruebas
      // keycloak.login();
      console.warn("[api] token refresh failed", e);
    }
  }

  return config;
});

export default api;
