const TOKEN_KEY = "ticketing.accessToken";
const EMAIL_KEY = "ticketing.email";
const EVENT_KEY = "ticketing.eventId";

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(EMAIL_KEY);
}

function getSelectedEventId() {
  return localStorage.getItem(EVENT_KEY) || "1";
}

function setSelectedEventId(eventId) {
  localStorage.setItem(EVENT_KEY, String(eventId));
}

function setEmail(email) {
  localStorage.setItem(EMAIL_KEY, email);
}

function getEmail() {
  return localStorage.getItem(EMAIL_KEY) || "";
}

async function api(path, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {}),
  };

  const token = getToken();
  if (options.auth !== false && token) {
    headers.Authorization = `Bearer ${token}`;
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    throw new Error(data?.message || `HTTP ${response.status}`);
  }

  return data;
}

function formatDate(value) {
  if (!value) {
    return "-";
  }
  return new Date(value).toLocaleString("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function setMessage(node, text, kind = "") {
  if (!node) {
    return;
  }
  node.textContent = text;
  node.className = `message${kind ? ` ${kind}` : ""}`;
}

function requireLogin(next = "/login.html") {
  if (!getToken()) {
    location.href = `${next}?next=${encodeURIComponent(location.pathname)}`;
    return false;
  }
  return true;
}

function bindLogout(buttonId = "logoutButton") {
  const button = document.getElementById(buttonId);
  if (!button) {
    return;
  }
  button.addEventListener("click", () => {
    clearToken();
    location.href = "/login.html";
  });
}

