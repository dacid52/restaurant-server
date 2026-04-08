// Render navbar vào element có id="navbar"
function renderNavbar() {
  const user = getUser();
  const el = document.getElementById('navbar');
  if (!el) return;

  const currentPath = window.location.pathname;
  const isActive = (pathPrefix) => currentPath === pathPrefix || currentPath.startsWith(pathPrefix);

  const menuLinks = `
    <a href="/menu/" class="${isActive('/menu/') ? 'text-orange-600 font-semibold' : 'text-gray-600'} hover:text-orange-600 transition">Thực đơn</a>
    <a href="/booking/" class="${isActive('/booking/') ? 'text-orange-600 font-semibold' : 'text-gray-600'} hover:text-orange-600 transition">Đặt bàn</a>
    ${user ? `<a href="/my-reservations/" class="${isActive('/my-reservations/') ? 'text-orange-600 font-semibold' : 'text-gray-600'} hover:text-orange-600 transition">Lịch sử</a>` : ''}
  `;

  el.innerHTML = `
    <nav class="bg-white border-b shadow-sm sticky top-0 z-50">
      <div class="max-w-6xl mx-auto px-3 sm:px-4 lg:px-6 h-14 sm:h-16 flex items-center justify-between">
        <a href="/" class="font-bold text-orange-600 text-lg">🍽️ Nhà hàng</a>

        <div class="hidden md:flex items-center gap-5 text-sm">
          ${menuLinks}
          ${user ? `
            <span class="text-gray-400">|</span>
            <span class="text-gray-700 font-medium max-w-[180px] truncate" title="${escHtml(user.fullName || user.username)}">${escHtml(user.fullName || user.username)}</span>
            <button onclick="logout()" class="text-red-500 hover:text-red-700 transition">Đăng xuất</button>
          ` : `
            <a href="/login/" class="text-gray-600 hover:text-orange-600 transition">Đăng nhập</a>
            <a href="/register/" class="bg-orange-600 text-white px-4 py-1.5 rounded-lg hover:bg-orange-700 transition">Đăng ký</a>
          `}
        </div>

        <button
          id="mobile-menu-toggle"
          type="button"
          aria-label="Mở menu"
          aria-expanded="false"
          class="md:hidden inline-flex items-center justify-center w-10 h-10 rounded-lg border border-gray-200 text-gray-700 hover:bg-gray-50"
        >
          <svg id="icon-open" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18M3 12h18M3 18h18"/>
          </svg>
          <svg id="icon-close" class="hidden" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </button>
      </div>
    </nav>

    <div id="mobile-menu-panel" class="md:hidden hidden border-b border-gray-200 bg-white shadow-sm">
      <div class="max-w-6xl mx-auto px-3 py-3 space-y-2 text-sm">
        <div class="flex flex-col gap-2">
          ${menuLinks}
        </div>
        <div class="h-px bg-gray-100 my-2"></div>
        ${user ? `
          <div class="text-gray-700 text-sm">Xin chào, <span class="font-semibold">${escHtml(user.fullName || user.username)}</span></div>
          <button onclick="logout()" class="w-full text-left text-red-500 hover:text-red-700 transition">Đăng xuất</button>
        ` : `
          <div class="flex items-center gap-3">
            <a href="/login/" class="text-gray-600 hover:text-orange-600 transition">Đăng nhập</a>
            <a href="/register/" class="bg-orange-600 text-white px-3 py-1.5 rounded-lg hover:bg-orange-700 transition">Đăng ký</a>
          </div>
        `}
      </div>
    </div>`;

  const toggleBtn = document.getElementById('mobile-menu-toggle');
  const panel = document.getElementById('mobile-menu-panel');
  const iconOpen = document.getElementById('icon-open');
  const iconClose = document.getElementById('icon-close');

  if (!toggleBtn || !panel || !iconOpen || !iconClose) return;

  const closeMenu = () => {
    panel.classList.add('hidden');
    iconOpen.classList.remove('hidden');
    iconClose.classList.add('hidden');
    toggleBtn.setAttribute('aria-expanded', 'false');
    document.body.classList.remove('overflow-hidden');
  };

  const openMenu = () => {
    panel.classList.remove('hidden');
    iconOpen.classList.add('hidden');
    iconClose.classList.remove('hidden');
    toggleBtn.setAttribute('aria-expanded', 'true');
    document.body.classList.add('overflow-hidden');
  };

  toggleBtn.addEventListener('click', () => {
    const isHidden = panel.classList.contains('hidden');
    if (isHidden) {
      openMenu();
    } else {
      closeMenu();
    }
  });

  panel.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => closeMenu());
  });

  window.addEventListener('resize', () => {
    if (window.innerWidth >= 768) {
      closeMenu();
    }
  });
}

function logout() {
  clearAuth();
  window.location.href = '/login/';
}

function escHtml(str) {
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
