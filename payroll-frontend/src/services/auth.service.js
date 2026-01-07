import api from "./api";

export function getMe() {
  return api.get("/users/me").then((r) => r.data);
}

