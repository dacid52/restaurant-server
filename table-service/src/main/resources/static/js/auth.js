// Quản lý token và thông tin user trong localStorage
const TOKEN_KEY = 'customer_token';
const USER_KEY  = 'customer_user';

function saveAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY));
  } catch {
    return null;
  }
}

function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function isLoggedIn() {
  return !!getToken();
}

// Gọi trước khi render trang cần đăng nhập
function requireAuth() {
  if (!isLoggedIn()) {
    window.location.href = '/login/?redirect=' + encodeURIComponent(window.location.pathname);
  }
}

// Gọi API với token tự động
async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (token) headers['Authorization'] = 'Bearer ' + token;

  const res = await fetch(API_BASE + path, { ...options, headers });

  if (res.status === 401) {
    clearAuth();
    window.location.href = '/login/';
    return;
  }

  if (!res.ok) {
    const data = await res.json().catch(() => ({}));
    throw new Error(data.message || 'Lỗi ' + res.status);
  }

  const text = await res.text();
  return text ? JSON.parse(text) : null;
}
