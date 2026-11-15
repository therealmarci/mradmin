// The phone layout (since 1.7): phones and tablets held upright get their own frame - a bar at the top with the big
// title, a tab bar at the bottom, sheets from below (App.svelte, components/mobile/). Tablets held sideways and computers
// keep the sidebar.
//
// device.mobile: the phone frame is on; device.standalone: started from the home screen (installed app).
// page: the head of the open page (PageHead fills it): title and the way back - the bar at the top shows them.

const PHONE = '(max-width: 759px), (max-width: 1100px) and (orientation: portrait)';

const phone = typeof matchMedia === 'function' ? matchMedia(PHONE) : null;
const installed = typeof matchMedia === 'function' ? matchMedia('(display-mode: standalone)') : null;

export const device = $state({
  mobile: phone?.matches ?? false,
  standalone: (installed?.matches ?? false) || navigator.standalone === true,
  ios: /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1),
});

function mark() {
  document.documentElement.classList.toggle('m', device.mobile);
  document.documentElement.classList.toggle('standalone', device.standalone);
}
mark();
phone?.addEventListener('change', (e) => {
  device.mobile = e.matches;
  mark();
});
installed?.addEventListener('change', (e) => {
  device.standalone = e.matches;
  mark();
});

// ------------------------------------------------------------------ the head of the open page

// A stack: the page heads that are open right now (the last one counts - a page inside a page).
const heads = [];
export const page = $state({ title: '', back: null });

function show() {
  const top = heads[heads.length - 1];
  page.title = top?.title ?? '';
  page.back = top?.back ?? null;
}

/** A page head registers itself (title, back = {label, path, onnav?}); the result removes it again. */
export function claimHead(entry) {
  heads.push(entry);
  show();
  return {
    update(next) {
      Object.assign(entry, next);
      show();
    },
    release() {
      const i = heads.indexOf(entry);
      if (i >= 0) heads.splice(i, 1);
      show();
    },
  };
}

// ------------------------------------------------------------------ windows as sheets

// On the phone every window of the pages (.overlay > panel, app.css) is a sheet from below: pulling it down at the top
// (or anywhere while it is scrolled to the top) closes it - like a tap next to it, which the pages already handle.
(() => {
  let panel = null, y0 = 0, dy = 0;
  addEventListener('touchstart', (e) => {
    if (!device.mobile || e.touches.length !== 1) return;
    const p = e.target.closest?.('.overlay > *');
    if (!p || e.target.closest('input, textarea, select, canvas, [data-nodrag]')) return;
    const top = e.touches[0].clientY - p.getBoundingClientRect().top;
    if (p.scrollTop > 0 && top > 40) return;
    panel = p;
    y0 = e.touches[0].clientY;
    dy = 0;
  }, { passive: true });
  addEventListener('touchmove', (e) => {
    if (!panel) return;
    dy = Math.max(0, e.touches[0].clientY - y0);
    if (dy > 0 && e.cancelable) e.preventDefault();
    panel.classList.add('m-dragging');
    panel.style.transform = dy ? `translateY(${dy}px)` : '';
  }, { passive: false });
  addEventListener('touchend', () => {
    if (!panel) return;
    const p = panel;
    panel = null;
    p.classList.remove('m-dragging');
    p.style.transform = '';
    if (dy > 90) p.parentElement.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  }, { passive: true });
})();

// ------------------------------------------------------------------ install as an app

// Android/Chrome: the browser's own install question, kept for the button of the hint (InstallHint.svelte).
export const install = $state({ prompt: null });
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  install.prompt = e;
});
window.addEventListener('appinstalled', () => {
  install.prompt = null;
  device.standalone = true;
  mark();
});

// ------------------------------------------------------------------ the tab bar of the administration

const TABS_KEY = 'mradmin.mobile.tabs';
export const DEFAULT_ADMIN_TABS = ['overview', 'tickets', 'players', 'main'];

/** The four places the person chose for the tab bar of the administration (stored in this browser). */
export function chosenTabs() {
  try {
    const list = JSON.parse(localStorage.getItem(TABS_KEY) ?? 'null');
    if (Array.isArray(list) && list.every((x) => typeof x === 'string')) return list.slice(0, 4);
  } catch {
    // private mode or broken value
  }
  return DEFAULT_ADMIN_TABS;
}

export const tabChoice = $state({ ids: chosenTabs() });

export function chooseTabs(ids) {
  tabChoice.ids = ids.slice(0, 4);
  try {
    if (ids === DEFAULT_ADMIN_TABS) localStorage.removeItem(TABS_KEY);
    else localStorage.setItem(TABS_KEY, JSON.stringify(tabChoice.ids));
  } catch {
    // private mode: only for this visit
  }
}
