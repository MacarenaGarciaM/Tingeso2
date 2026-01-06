import axios from "axios";
import keycloak from "./keycloak";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080",
});

// agrega el Bearer token automáticamente
api.interceptors.request.use((config) => {
  const token = keycloak?.token;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;
