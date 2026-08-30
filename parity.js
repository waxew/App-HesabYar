/*
 * Feature Parity layer for HesabYar Web.
 * این فایل قابلیت‌های اصلی نسخه Android را به نسخه مرورگر اضافه می‌کند بدون اینکه موتور پایه app.js تکرار شود.
 */

/* کلیدهای پایدار داده‌های محلی؛ در بروزرسانی PWA حفظ می‌شوند. */
const PARITY_KEYS = {
  cart: 'hesabyar_web_cart_v1',
  budget: 'hesabyar_web_budget_v1',
  priceBook: 'hesabyar_web_price_book_v1',
  marketplaces: 'hesabyar_web_marketplaces_v1',
  invoices: 'hesabyar_web_invoices_v1',
  businesses: 'hesabyar_web_businesses_v1',
  profileName: 'hesabyar_web_profile_name_v1',
  profileImage: 'hesabyar_web_profile_image_v1',
  favorites: 'hesabyar_web_pro_favorites_v1'
};

/* JSON را با fallback امن از LocalStorage می‌خواند. */
function parityLoad(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? fallback : JSON.parse(raw);
  } catch {
    return fallback;
  }
}

/* داده را به شکل JSON ذخیره می‌کند. */
function paritySave(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

/* State اختصاصی قابلیت‌های پیشرفته. */
const parityState = {
  cart: parityLoad(PARITY_KEYS.cart, []),
  budget: Number(localStorage.getItem(PARITY_KEYS.budget) || 0),
  priceBook: parityLoad(PARITY_KEYS.priceBook, []),
  marketplaces: parityLoad(PARITY_KEYS.marketplaces, [
    { id: 1, name: 'فروش مستقیم', feePercent: 0, fixedFee: 0 },
    { id: 2, name: 'مارکت‌پلیس نمونه', feePercent: 10, fixedFee: 0 }
  ]),
  invoices: parityLoad(PARITY_KEYS.invoices, []),
  businesses: parityLoad(PARITY_KEYS.businesses, []),
  profileName: localStorage.getItem(PARITY_KEYS.profileName) || 'کاربر حسابیار',
  profileImage: localStorage.getItem(PARITY_KEYS.profileImage) || '',
  favorites: new Set(parityLoad(PARITY_KEYS.favorites, []))
};

/* Toast کوتاه برای تأیید عملیات. */
function parityToast(message) {
  let toast = document.getElementById('parityToast');
  if (!toast) {
    toast = document.createElement('div');
    toast.id = 'parityToast';
    toast.className = 'web-toast';
    document.body.appendChild(toast);
  }
  toast.textContent = message;
  toast.classList.add('is-visible');
  clearTimeout(parityToast.timer);
  parityToast.timer = setTimeout(() => toast.classList.remove('is-visible'), 1800);
}

/* Download فایل متنی در مرورگر. */
function downloadText(filename, content, type = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/* داده ورودی را برای CSV امن می‌کند. */
function csvCell(value) {
  return `"${String(value ?? '').replace(/"/g, '""')}"`;
}

/* محاسبه قیمت‌گذاری فروشنده؛ هم‌راستا با AdvancedCalculationEngine.kt. */
function sellerPricing(values) {
  const nums = Object.values(values);
  if (nums.some(v => !Number.isFinite(v) || v < 0)) return null;
  const fixedCost = values.purchase + values.shipping + values.packaging + values.other;
  const variableRatePercent = values.advertising + values.platform + values.gateway + values.tax;
  const denominator = 1 - (variableRatePercent + values.margin) / 100;
  const breakEvenDenominator = 1 - variableRatePercent / 100;
  if (denominator <= 0 || breakEvenDenominator <= 0) return null;
  const suggestedPrice = fixedCost / denominator;
  const breakEvenPrice = fixedCost / breakEvenDenominator;
  const expectedProfit = suggestedPrice - fixedCost - suggestedPrice * variableRatePercent / 100;
  const expectedMarginPercent = suggestedPrice > 0 ? expectedProfit / suggestedPrice * 100 : 0;
  return { fixedCost, variableRatePercent, suggestedPrice, breakEvenPrice, expectedProfit, expectedMarginPercent };
}

/* سه سناریوی آماده نسخه Android. */
function pricingScenarios(base) {
  return [
    ['فروش سریع', 12],
    ['متعادل', 22],
    ['سود بیشتر', 32]
  ].map(([label, margin]) => {
    const result = sellerPricing({ ...base, margin });
    return result ? { label, margin, salePrice: result.suggestedPrice } : null;
  }).filter(Boolean);
}

/* خرید نقدی/اقساطی؛ هم‌راستا با V3Engine. */
function installmentResult(cashPrice, downPayment, installmentAmount, count) {
  if (cashPrice <= 0 || downPayment < 0 || installmentAmount < 0 || count <= 0) return null;
  const total = downPayment + installmentAmount * count;
  const extra = total - cashPrice;
  const financed = Math.max(cashPrice - downPayment, 1);
  const monthly = extra <= 0 ? 0 : extra / financed / count * 100;
  const annual = monthly <= 0 ? 0 : (Math.pow(1 + monthly / 100, 12) - 1) * 100;
  return { total, extra, extraPercent: extra / cashPrice * 100, monthly, annual };
}

/* What-if نسخه 3. */
function whatIfResult(baseSalePrice, landedCost, feePercent, discountPercent) {
  if (baseSalePrice <= 0 || landedCost < 0 || feePercent < 0 || discountPercent < 0 || discountPercent > 100) return null;
  const customerPrice = baseSalePrice * (1 - discountPercent / 100);
  const fees = customerPrice * feePercent / 100;
  const profit = customerPrice - landedCost - fees;
  const margin = customerPrice > 0 ? profit / customerPrice * 100 : -100;
  return { customerPrice, profit, margin, isLoss: profit < 0 };
}

/* جمع فاکتور. */
function invoiceTotals(lines, discountPercent, taxPercent) {
  if (lines.some(line => line.quantity < 0 || line.unitPrice < 0) || discountPercent < 0 || discountPercent > 100 || taxPercent < 0) return null;
  const subtotal = lines.reduce((sum, line) => sum + line.quantity * line.unitPrice, 0);
  const discountAmount = subtotal * discountPercent / 100;
  const taxable = subtotal - discountAmount;
  const taxAmount = taxable * taxPercent / 100;
  return { subtotal, discountAmount, taxAmount, finalTotal: taxable + taxAmount };
}

/* تبدیل واحدهای داخلی نسخه Android. */
function convertUnit(value, from, to) {
  if (value < 0) return null;
  const groups = [
    { mg: 0.001, g: 1, kg: 1000 },
    { mm: 0.001, cm: 0.01, m: 1, km: 1000 },
    { ml: 1, l: 1000 }
  ];
  const group = groups.find(g => Object.hasOwn(g, from) && Object.hasOwn(g, to));
  return group ? value * group[from] / group[to] : null;
}

/* Parser سبک فرمان فارسی؛ معادل V3Engine.parseSmartCommand. */
function parseSmartCommand(text) {
  const digitMap = { '۰':'0','۱':'1','۲':'2','۳':'3','۴':'4','۵':'5','۶':'6','۷':'7','۸':'8','۹':'9','٠':'0','١':'1','٢':'2','٣':'3','٤':'4','٥':'5','٦':'6','٧':'7','٨':'8','٩':'9' };
  const normalized = String(text || '').replace(/[۰-۹٠-٩]/g, ch => digitMap[ch]).replace(/[٬،,]/g, '');
  const numbers = [...normalized.matchAll(/\d+(?:\.\d+)?/g)].map(m => Number(m[0]));
  const percentages = [...normalized.matchAll(/(\d+(?:\.\d+)?)\s*(?:%|درصد)/g)].map(m => Number(m[1]));
  const cost = numbers[0] ?? null;
  const fee = normalized.includes('کارمزد') ? percentages[0] ?? null : null;
  const marginIndex = fee != null ? 1 : 0;
  const margin = /سود|مارجین|margin/i.test(normalized) ? percentages[marginIndex] ?? null : null;
  const discount = normalized.includes('تخفیف') ? percentages.at(-1) ?? null : null;
  return { cost, fee, margin, discount };
}

/* قیمت پیشنهادی فرمان متنی. */
function smartSuggestedPrice(command) {
  const cost = command.cost;
  const fee = command.fee ?? 0;
  const margin = command.margin;
  if (cost == null || margin == null || cost < 0 || fee < 0 || margin < 0 || fee + margin >= 100) return null;
  return cost / (1 - (fee + margin) / 100);
}

/* ------------------------------ Drawer -------------------------------- */

/* Drawer را یک بار به DOM اضافه می‌کند. */
function ensureParityDrawer() {
  if (document.getElementById('webDrawer')) return;

  const overlay = document.createElement('button');
  overlay.id = 'drawerOverlay';
  overlay.className = 'drawer-overlay';
  overlay.type = 'button';
  overlay.setAttribute('aria-label', 'بستن منو');

  const drawer = document.createElement('aside');
  drawer.id = 'webDrawer';
  drawer.className = 'web-drawer';
  drawer.setAttribute('aria-label', 'منوی حسابیار');
  drawer.innerHTML = `
    <div class="drawer-head"><strong>حسابیار</strong><button id="closeDrawer" class="icon-button" type="button">×</button></div>
    <section class="drawer-profile">
      <label id="profileAvatar" class="profile-avatar" for="profileImageInput">ح</label>
      <input id="profileImageInput" type="file" accept="image/*" hidden>
      <input id="profileNameInput" class="profile-name-input" maxlength="40" value="${escapeHtml(parityState.profileName)}" aria-label="نام کاربر">
      <small style="color:var(--muted)">برای تغییر تصویر روی آواتار بزن.</small>
    </section>
    <div class="drawer-section"><span class="drawer-label">دسترسی سریع</span><div class="drawer-list">
      ${drawerButton('home','⌂','خانه')}
      ${drawerButton('buyer','🛒','دستیار خرید')}
      ${drawerButton('seller','↗','دستیار فروشنده')}
      ${drawerButton('priceBook','₿','دفترچه قیمت')}
      ${drawerButton('scanner','▣','اسکن قیمت و بارکد')}
      ${drawerButton('pro','★','مرکز ابزارهای حرفه‌ای')}
      ${drawerButton('reports','▥','گزارش‌ها')}
      ${drawerButton('dataTools','⇩','خروجی و پشتیبان‌گیری')}
    </div></div>
    <div class="drawer-divider"></div>
    <div class="drawer-section"><span class="drawer-label">برنامه</span><div class="drawer-list">
      ${drawerButton('settings','⚙','تنظیمات')}
      ${drawerButton('about','ⓘ','درباره نرم‌افزار')}
      ${drawerButton('contact','✉','ارتباط با ما')}
      <button id="shareWebApp" class="drawer-item" type="button"><span class="drawer-icon">↗</span><span>اشتراک با دوستان</span></button>
    </div></div>
    <div class="drawer-divider"></div>
    <div class="drawer-footer"><strong>Develop by AS Team Group</strong><br>HesabYar 3.0.0 · Web PWA</div>`;

  document.body.append(overlay, drawer);

  overlay.addEventListener('click', closeParityDrawer);
  document.getElementById('closeDrawer').addEventListener('click', closeParityDrawer);
  drawer.querySelectorAll('[data-drawer-page]').forEach(button => button.addEventListener('click', () => {
    closeParityDrawer();
    navigate(button.dataset.drawerPage);
  }));

  document.getElementById('profileNameInput').addEventListener('change', event => {
    parityState.profileName = event.target.value.trim().slice(0, 40) || 'کاربر حسابیار';
    localStorage.setItem(PARITY_KEYS.profileName, parityState.profileName);
  });

  document.getElementById('profileImageInput').addEventListener('change', event => {
    const file = event.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      parityState.profileImage = String(reader.result || '');
      localStorage.setItem(PARITY_KEYS.profileImage, parityState.profileImage);
      updateDrawerAvatar();
    };
    reader.readAsDataURL(file);
  });

  document.getElementById('shareWebApp').addEventListener('click', async () => {
    const data = { title: 'حسابیار', text: 'نسخه وب حسابیار', url: location.href.split('#')[0] };
    if (navigator.share) await navigator.share(data).catch(() => {});
    else {
      await navigator.clipboard?.writeText(data.url).catch(() => {});
      parityToast('لینک نسخه وب کپی شد.');
    }
  });
  updateDrawerAvatar();
}

function drawerButton(page, icon, title) {
  return `<button class="drawer-item" type="button" data-drawer-page="${page}"><span class="drawer-icon">${icon}</span><span>${title}</span></button>`;
}

function updateDrawerAvatar() {
  const avatar = document.getElementById('profileAvatar');
  if (!avatar) return;
  avatar.innerHTML = parityState.profileImage ? `<img alt="تصویر پروفایل" src="${parityState.profileImage}">` : 'ح';
}

function openParityDrawer() {
  ensureParityDrawer();
  document.getElementById('webDrawer').classList.add('is-open');
  document.getElementById('drawerOverlay').classList.add('is-open');
}

function closeParityDrawer() {
  document.getElementById('webDrawer')?.classList.remove('is-open');
  document.getElementById('drawerOverlay')?.classList.remove('is-open');
}

/* ------------------------------ Home ---------------------------------- */

/* Home نسخه وب را با مقصدهای اصلی Android گسترش می‌دهد. */
renderHome = function renderParityHome() {
  root.innerHTML = `
    <section class="hero">
      <article class="hero-main">
        <span class="eyebrow">HesabYar Web · Android Parity</span>
        <h1>حسابیار؛ ابزار مالی و خرید و فروش.</h1>
        <p>نسخه وب بر اساس ساختار نسخه Android توسعه داده شده و علاوه بر ماشین‌حساب‌ها، دستیار خرید و فروش، دفترچه قیمت، اسکنر، گزارش، Backup و مرکز حرفه‌ای را ارائه می‌کند.</p>
      </article>
      <aside class="hero-side">
        <div class="status-row"><span>Web PWA</span><strong class="online-dot">فعال</strong></div>
        <div class="status-row"><span>واحد پول</span><strong>${escapeHtml(state.currency)}</strong></div>
        <div class="status-row"><span>تاریخچه</span><strong>${state.history.length} مورد</strong></div>
        <div class="status-row"><span>پروفایل</span><strong>${escapeHtml(parityState.profileName)}</strong></div>
      </aside>
    </section>

    <div class="section-heading"><div><h2>بخش‌های اصلی</h2><p>همان مقصدهای اصلی نسخه Android.</p></div></div>
    <section class="feature-grid">
      ${featureCard('buyer','🛒','دستیار خرید','بودجه، سبد خرید و هشدار کسری')}
      ${featureCard('seller','↗','دستیار فروشنده','قیمت امن فروش، Margin و تخفیف')}
      ${featureCard('priceBook','₿','دفترچه قیمت','ذخیره و مقایسه قیمت واحد')}
      ${featureCard('scanner','▣','اسکنر','بارکد/QR و قابلیت OCR مرورگر')}
      ${featureCard('pro','★','مرکز حرفه‌ای','۱۱ ابزار نسخه 3', true)}
      ${featureCard('dataTools','⇩','خروجی و Backup','CSV و پشتیبان JSON')}
    </section>

    <div class="section-heading"><div><h2>ماشین‌حساب‌ها</h2><p>موتور محاسباتی هم‌راستا با CalculationEngine.kt.</p></div></div>
    <section class="tool-grid">
      ${TOOLS.map(tool => `<button class="tool-card" type="button" data-tool="${tool.id}"><span class="tool-icon">${tool.icon}</span><strong>${tool.title}</strong><span>${tool.subtitle}</span></button>`).join('')}
    </section>`;

  document.querySelectorAll('[data-tool]').forEach(button => button.addEventListener('click', () => openTool(button.dataset.tool)));
  document.querySelectorAll('[data-feature-page]').forEach(button => button.addEventListener('click', () => navigate(button.dataset.featurePage)));
};

function featureCard(page, icon, title, subtitle, pro = false) {
  return `<button class="feature-card ${pro ? 'is-pro' : ''}" type="button" data-feature-page="${page}"><span class="feature-icon">${icon}</span><strong>${title}</strong><span>${subtitle}</span></button>`;
}

/* --------------------------- Buyer Assistant --------------------------- */

function renderBuyer() {
  const total = parityState.cart.reduce((sum, item) => sum + item.price * item.quantity, 0);
  const remaining = parityState.budget - total;
  root.innerHTML = `
    <section class="panel">
      <div class="panel-head"><div><h1>دستیار خرید</h1><p>بودجه و سبد خرید روی همین دستگاه نگهداری می‌شود.</p></div></div>
      <div class="panel-body">
        <div class="summary-grid">
          ${summaryCard('بودجه', `${money(parityState.budget)} ${state.currency}`)}
          ${summaryCard('جمع سبد', `${money(total)} ${state.currency}`)}
          ${summaryCard(parityState.budget > 0 && remaining < 0 ? 'کسری بودجه' : 'مانده بودجه', `${money(Math.abs(remaining))} ${state.currency}`)}
        </div>
        <div class="form-grid">
          ${numberField('buyerBudget', `بودجه (${state.currency})`, parityState.budget || '0')}
          <div class="field"><label for="buyerName">نام کالا</label><input id="buyerName" class="input" dir="rtl" style="text-align:right" placeholder="مثلاً برنج"></div>
          ${numberField('buyerPrice', `قیمت واحد (${state.currency})`)}
          ${numberField('buyerQuantity', 'تعداد', '1')}
        </div>
        <div class="result-actions"><button id="saveBudget" class="secondary-button" type="button">ذخیره بودجه</button><button id="addCartItem" class="primary-button" type="button">اضافه به سبد</button></div>
        <div class="data-list">${parityState.cart.length ? parityState.cart.map(item => `<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(item.name)}</strong><span>${clean(item.quantity)} × ${money(item.price)} = ${money(item.price * item.quantity)} ${state.currency}</span></div><div class="data-item-actions"><button class="small-button danger" data-remove-cart="${item.id}" type="button">حذف</button></div></div>`).join('') : '<div class="empty-state">سبد خرید خالی است.</div>'}</div>
      </div>
    </section>`;

  document.getElementById('saveBudget').addEventListener('click', () => {
    parityState.budget = Math.max(0, toNumber(document.getElementById('buyerBudget').value) ?? 0);
    localStorage.setItem(PARITY_KEYS.budget, String(parityState.budget));
    parityToast('بودجه ذخیره شد.'); renderBuyer();
  });
  document.getElementById('addCartItem').addEventListener('click', () => {
    const name = document.getElementById('buyerName').value.trim();
    const price = toNumber(document.getElementById('buyerPrice').value);
    const quantity = toNumber(document.getElementById('buyerQuantity').value);
    if (!name || price == null || price < 0 || quantity == null || quantity <= 0) return parityToast('اطلاعات کالا کامل نیست.');
    parityState.cart.unshift({ id: Date.now(), name, price, quantity });
    paritySave(PARITY_KEYS.cart, parityState.cart); renderBuyer();
  });
  document.querySelectorAll('[data-remove-cart]').forEach(button => button.addEventListener('click', () => {
    parityState.cart = parityState.cart.filter(item => String(item.id) !== button.dataset.removeCart);
    paritySave(PARITY_KEYS.cart, parityState.cart); renderBuyer();
  }));
}

function summaryCard(label, value) {
  return `<div class="summary-card"><span>${label}</span><strong>${value}</strong></div>`;
}

/* --------------------------- Seller Assistant -------------------------- */

function renderSeller() {
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>دستیار فروشنده</h1><p>هزینه ثابت، کارمزدها و Margin هدف را وارد کن.</p></div></div><div class="panel-body">
      <div class="form-grid">
        ${numberField('sPurchase', `قیمت خرید (${state.currency})`)}
        ${numberField('sShipping', `ارسال ورودی (${state.currency})`, '0')}
        ${numberField('sPackaging', `بسته‌بندی (${state.currency})`, '0')}
        ${numberField('sOther', `سایر هزینه ثابت (${state.currency})`, '0')}
        ${numberField('sAdvertising', 'تبلیغات (%)', '0')}
        ${numberField('sPlatform', 'کارمزد پلتفرم (%)', '0')}
        ${numberField('sGateway', 'کارمزد درگاه (%)', '0')}
        ${numberField('sTax', 'مالیات/عوارض (%)', '0')}
        ${numberField('sMargin', 'حاشیه سود هدف (%)', '25')}
      </div>
      <div class="result-actions"><button id="sellerCalculate" class="primary-button" type="button">محاسبه قیمت فروش</button></div>
      <div id="sellerResult"></div>
    </div></section>`;

  document.getElementById('sellerCalculate').addEventListener('click', () => {
    const values = {
      purchase: toNumber(document.getElementById('sPurchase').value) ?? -1,
      shipping: toNumber(document.getElementById('sShipping').value) ?? 0,
      packaging: toNumber(document.getElementById('sPackaging').value) ?? 0,
      other: toNumber(document.getElementById('sOther').value) ?? 0,
      advertising: toNumber(document.getElementById('sAdvertising').value) ?? 0,
      platform: toNumber(document.getElementById('sPlatform').value) ?? 0,
      gateway: toNumber(document.getElementById('sGateway').value) ?? 0,
      tax: toNumber(document.getElementById('sTax').value) ?? 0,
      margin: toNumber(document.getElementById('sMargin').value) ?? -1
    };
    const result = sellerPricing(values);
    const box = document.getElementById('sellerResult');
    if (!result) { box.innerHTML = '<div class="validation-message">مقادیر نامعتبرند یا مجموع کارمزدها و Margin به ۱۰۰٪ رسیده است.</div>'; return; }
    const scenarios = pricingScenarios(values);
    renderResult(box, {
      label: 'قیمت فروش پیشنهادی',
      value: `${money(result.suggestedPrice)} ${state.currency}`,
      rows: [
        ['هزینه ثابت واقعی', `${money(result.fixedCost)} ${state.currency}`],
        ['هزینه‌های درصدی', percent(result.variableRatePercent)],
        ['سود مورد انتظار', `${money(result.expectedProfit)} ${state.currency}`],
        ['Margin', percent(result.expectedMarginPercent)],
        ['قیمت سربه‌سر', `${money(result.breakEvenPrice)} ${state.currency}`]
      ],
      save: () => { saveHistory('قیمت‌گذاری فروشنده', `هزینه ${money(result.fixedCost)} / Margin ${percent(result.expectedMarginPercent)}`, `${money(result.suggestedPrice)} ${state.currency}`); parityToast('در تاریخچه ذخیره شد.'); }
    });
    box.insertAdjacentHTML('beforeend', `<div class="summary-grid">${scenarios.map(s => summaryCard(`${s.label} · ${s.margin}٪`, `${money(s.salePrice)} ${state.currency}`)).join('')}</div>`);
  });
}

/* ----------------------------- Price Book ------------------------------- */

function renderPriceBook() {
  const sorted = parityState.priceBook.filter(item => item.quantity > 0).slice().sort((a,b) => a.price/a.quantity - b.price/b.quantity);
  const cheapest = sorted[0];
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>دفترچه قیمت</h1><p>ثبت قیمت و مقایسه بر اساس قیمت هر واحد.</p></div></div><div class="panel-body">
      ${cheapest ? `<div class="result-card"><span class="result-label">به‌صرفه‌ترین ثبت</span><strong class="result-value" style="direction:rtl">${escapeHtml(cheapest.name)}</strong><div class="result-row"><span>قیمت هر ${escapeHtml(cheapest.unit)}</span><strong>${money(cheapest.price/cheapest.quantity)} ${state.currency}</strong></div></div>` : ''}
      <div class="form-grid">
        <div class="field"><label for="pbName">نام محصول</label><input id="pbName" class="input" dir="rtl" style="text-align:right"></div>
        ${numberField('pbPrice', `قیمت (${state.currency})`)}
        ${numberField('pbQuantity','مقدار / وزن / تعداد','1')}
        <div class="field"><label for="pbUnit">واحد</label><input id="pbUnit" class="input" dir="rtl" style="text-align:right" value="عدد"></div>
      </div>
      <div class="result-actions"><button id="addPriceRecord" class="primary-button" type="button">ثبت در دفترچه</button></div>
      <div class="table-wrap"><table class="web-table"><thead><tr><th>محصول</th><th>قیمت</th><th>مقدار</th><th>قیمت واحد</th><th></th></tr></thead><tbody>${parityState.priceBook.map(item => `<tr><td>${escapeHtml(item.name)}</td><td class="num">${money(item.price)}</td><td>${clean(item.quantity)} ${escapeHtml(item.unit)}</td><td class="num">${money(item.price/item.quantity)}</td><td><button class="small-button danger" data-remove-price="${item.id}">حذف</button></td></tr>`).join('')}</tbody></table></div>
    </div></section>`;

  document.getElementById('addPriceRecord').addEventListener('click', () => {
    const name = document.getElementById('pbName').value.trim();
    const price = toNumber(document.getElementById('pbPrice').value);
    const quantity = toNumber(document.getElementById('pbQuantity').value);
    const unit = document.getElementById('pbUnit').value.trim() || 'عدد';
    if (!name || price == null || price < 0 || quantity == null || quantity <= 0) return parityToast('اطلاعات قیمت کامل نیست.');
    parityState.priceBook.unshift({ id: Date.now(), name, price, quantity, unit, createdAt: Date.now() });
    paritySave(PARITY_KEYS.priceBook, parityState.priceBook); renderPriceBook();
  });
  document.querySelectorAll('[data-remove-price]').forEach(button => button.addEventListener('click', () => {
    parityState.priceBook = parityState.priceBook.filter(item => String(item.id) !== button.dataset.removePrice);
    paritySave(PARITY_KEYS.priceBook, parityState.priceBook); renderPriceBook();
  }));
}

/* ------------------------------- Scanner -------------------------------- */

function renderScanner() {
  const barcodeSupported = 'BarcodeDetector' in window;
  const textSupported = 'TextDetector' in window;
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>اسکن قیمت و بارکد</h1><p>معادل مرورگری Scanner نسخه Android.</p></div></div><div class="panel-body">
      <div class="capability-note">BarcodeDetector: <strong>${barcodeSupported ? 'پشتیبانی می‌شود' : 'در این مرورگر موجود نیست'}</strong> · OCR TextDetector: <strong>${textSupported ? 'پشتیبانی می‌شود' : 'در این مرورگر موجود نیست'}</strong>. در مرورگرهایی که API موجود نیست، تصویر همچنان قابل انتخاب است اما پردازش خودکار انجام نمی‌شود.</div>
      <div class="field" style="margin-top:12px"><label for="scanFile">عکس فاکتور / بارکد / QR</label><input id="scanFile" type="file" accept="image/*" capture="environment" class="input" style="padding:10px;direction:rtl;text-align:right"></div>
      <div id="scannerPreview" class="scanner-preview" style="margin-top:12px"><span>هنوز تصویری انتخاب نشده.</span></div>
      <div id="scannerResult" class="result-card is-hidden"></div>
    </div></section>`;

  document.getElementById('scanFile').addEventListener('change', async event => {
    const file = event.target.files?.[0];
    if (!file) return;
    const image = document.createElement('img');
    image.alt = 'تصویر انتخاب‌شده';
    image.src = URL.createObjectURL(file);
    await image.decode().catch(() => {});
    const preview = document.getElementById('scannerPreview');
    preview.innerHTML = '';
    preview.appendChild(image);
    const outputs = [];
    if (barcodeSupported) {
      try {
        const detector = new BarcodeDetector();
        const codes = await detector.detect(image);
        if (codes.length) outputs.push(`بارکد / QR:\n${codes.map(code => code.rawValue || code.rawData || 'کد').join('\n')}`);
      } catch {}
    }
    if (textSupported) {
      try {
        const detector = new TextDetector();
        const texts = await detector.detect(image);
        if (texts.length) outputs.push(`متن:\n${texts.map(item => item.rawValue || '').filter(Boolean).join('\n')}`);
      } catch {}
    }
    const result = document.getElementById('scannerResult');
    result.classList.remove('is-hidden');
    result.innerHTML = outputs.length ? `<span class="result-label">نتیجه شناسایی</span><pre style="white-space:pre-wrap;direction:rtl;line-height:1.9">${escapeHtml(outputs.join('\n\n'))}</pre>` : `<span class="result-label">نتیجه</span><p style="line-height:2;color:var(--muted)">API تشخیص موردنیاز در این مرورگر در دسترس نیست یا چیزی شناسایی نشد. نسخه Android همچنان از ML Kit برای OCR و Barcode استفاده می‌کند.</p>`;
  });
}

/* ------------------------------- Reports -------------------------------- */

function renderReportsParity() {
  const counts = state.history.reduce((map, item) => { map[item.title] = (map[item.title] || 0) + 1; return map; }, {});
  const entries = Object.entries(counts).sort((a,b) => b[1]-a[1]);
  const max = Math.max(1, ...entries.map(item => item[1]));
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>گزارش‌ها</h1><p>نمای کلی محاسبات ذخیره‌شده روی همین مرورگر.</p></div></div><div class="panel-body">
      <div class="summary-grid">${summaryCard('کل محاسبات', String(state.history.length))}${summaryCard('نوع ابزار', String(entries.length))}${summaryCard('اقلام سبد خرید', String(parityState.cart.length))}</div>
      ${entries.length ? entries.slice(0, 12).map(([title,count]) => `<div class="report-row"><div class="report-row-head"><span>${escapeHtml(title)}</span><strong>${count}</strong></div><div class="report-track"><div class="report-fill" style="width:${count/max*100}%"></div></div></div>`).join('') : '<div class="empty-state">هنوز داده‌ای برای گزارش وجود ندارد.</div>'}
    </div></section>`;
}

/* ---------------------------- Data Tools -------------------------------- */

function buildBackupObject() {
  return {
    format: 'HesabYar-Web-Backup',
    version: 1,
    createdAt: new Date().toISOString(),
    settings: { currency: state.currency, theme: state.theme, profileName: parityState.profileName, profileImage: parityState.profileImage },
    history: state.history,
    cart: parityState.cart,
    budget: parityState.budget,
    priceBook: parityState.priceBook,
    marketplaces: parityState.marketplaces,
    invoices: parityState.invoices,
    businesses: parityState.businesses,
    favorites: [...parityState.favorites]
  };
}

function renderDataTools() {
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>خروجی و پشتیبان‌گیری</h1><p>معادل مرورگری DataTools نسخه Android.</p></div></div><div class="panel-body settings-list">
      <div class="setting-row"><div><strong>CSV تاریخچه</strong><span>گزارش محاسبات برای Excel و نرم‌افزارهای جدول.</span></div><button id="exportCsv" class="secondary-button" type="button">دریافت CSV</button></div>
      <div class="setting-row"><div><strong>Backup کامل</strong><span>تنظیمات، تاریخچه، سبد خرید، دفترچه قیمت و داده‌های حرفه‌ای.</span></div><button id="exportBackup" class="primary-button" type="button">دریافت Backup</button></div>
      <div class="setting-row"><div><strong>Restore</strong><span>بازیابی فایل JSON ساخته‌شده توسط نسخه وب.</span></div><label class="secondary-button" style="display:grid;place-items:center;cursor:pointer"><input id="restoreBackup" type="file" accept="application/json,.json" hidden>انتخاب Backup</label></div>
      <div class="setting-row"><div><strong>ذخیره‌سازی وب</strong><span>LocalStorage مستقل مرورگر؛ حذف داده‌های سایت آن را پاک می‌کند.</span></div><strong>فعال</strong></div>
    </div></section>`;

  document.getElementById('exportCsv').addEventListener('click', () => {
    const rows = [['زمان','عنوان','جزئیات','نتیجه'], ...state.history.map(item => [item.createdAt,item.title,item.details,item.result])];
    const csv = '\ufeff' + rows.map(row => row.map(csvCell).join(',')).join('\n');
    downloadText('HesabYar-history.csv', csv, 'text/csv;charset=utf-8');
  });
  document.getElementById('exportBackup').addEventListener('click', () => downloadText('HesabYar-backup.json', JSON.stringify(buildBackupObject(), null, 2), 'application/json'));
  document.getElementById('restoreBackup').addEventListener('change', async event => {
    const file = event.target.files?.[0]; if (!file) return;
    try {
      const backup = JSON.parse(await file.text());
      if (backup.format !== 'HesabYar-Web-Backup') throw new Error('invalid');
      state.history = Array.isArray(backup.history) ? backup.history.slice(0,100) : [];
      persistHistory();
      state.currency = backup.settings?.currency || state.currency;
      state.theme = backup.settings?.theme || state.theme;
      localStorage.setItem(STORAGE_KEYS.currency, state.currency); localStorage.setItem(STORAGE_KEYS.theme, state.theme);
      parityState.profileName = backup.settings?.profileName || parityState.profileName;
      parityState.profileImage = backup.settings?.profileImage || '';
      localStorage.setItem(PARITY_KEYS.profileName, parityState.profileName); localStorage.setItem(PARITY_KEYS.profileImage, parityState.profileImage);
      parityState.cart = Array.isArray(backup.cart) ? backup.cart : []; paritySave(PARITY_KEYS.cart, parityState.cart);
      parityState.budget = Number(backup.budget || 0); localStorage.setItem(PARITY_KEYS.budget, String(parityState.budget));
      parityState.priceBook = Array.isArray(backup.priceBook) ? backup.priceBook : []; paritySave(PARITY_KEYS.priceBook, parityState.priceBook);
      parityState.marketplaces = Array.isArray(backup.marketplaces) ? backup.marketplaces : []; paritySave(PARITY_KEYS.marketplaces, parityState.marketplaces);
      parityState.invoices = Array.isArray(backup.invoices) ? backup.invoices : []; paritySave(PARITY_KEYS.invoices, parityState.invoices);
      parityState.businesses = Array.isArray(backup.businesses) ? backup.businesses : []; paritySave(PARITY_KEYS.businesses, parityState.businesses);
      parityState.favorites = new Set(Array.isArray(backup.favorites) ? backup.favorites : []); paritySave(PARITY_KEYS.favorites, [...parityState.favorites]);
      applyTheme(); updateDrawerAvatar(); parityToast('Backup بازیابی شد.');
    } catch { parityToast('فایل Backup معتبر نیست.'); }
  });
}

/* --------------------------- Professional Center ------------------------ */

const PRO_TOOLS = [
  ['marketplace','▦','مقایسه مارکت‌پلیس','کارمزد و سود خالص'],
  ['installment','▤','نقد یا اقساط','هزینه واقعی خرید قسطی'],
  ['analytics','⌁','تحلیل قیمت','بهترین قیمت و روند'],
  ['whatif','⌘','شبیه‌ساز What-if','تخفیف، سود و Margin'],
  ['invoice','▧','فاکتور آفلاین','اقلام، تخفیف و مالیات'],
  ['business','▣','پروفایل کاری','چند فروشگاه روی یک دستگاه'],
  ['import','⇧','Import محصولات','CSV به دفترچه قیمت'],
  ['smart','✦','فرمان متنی','محاسبه با جمله فارسی'],
  ['converter','⇄','تبدیل واحد و ارز','واحد و نرخ دستی'],
  ['backup','◆','Backup رمزدار','Export امن سمت مرورگر'],
  ['cash','¤','Cash Check','سربه‌سر ماهانه و سود هدف']
];

function renderProCenter() {
  root.innerHTML = `
    <section class="panel"><div class="panel-head"><div><h1>مرکز ابزارهای حرفه‌ای</h1><p>ابزارهای نسخه 3 حسابیار.</p></div></div><div class="panel-body">
      <div class="field"><label for="proSearch">جستجوی ابزار</label><input id="proSearch" class="input" dir="rtl" style="text-align:right" placeholder="نام ابزار..."></div>
      <div id="proGrid" class="pro-grid" style="margin-top:14px"></div>
    </div></section>`;
  const draw = () => {
    const q = document.getElementById('proSearch').value.trim().toLowerCase();
    const visible = PRO_TOOLS.filter(tool => !q || `${tool[2]} ${tool[3]}`.toLowerCase().includes(q)).sort((a,b) => Number(parityState.favorites.has(b[0])) - Number(parityState.favorites.has(a[0])));
    document.getElementById('proGrid').innerHTML = visible.map(tool => `<button class="pro-card" type="button" data-pro="${tool[0]}"><span>${tool[1]}</span><b>${tool[2]} ${parityState.favorites.has(tool[0]) ? '★' : ''}</b><span>${tool[3]}</span></button>`).join('');
    document.querySelectorAll('[data-pro]').forEach(button => button.addEventListener('click', () => renderProTool(button.dataset.pro)));
  };
  document.getElementById('proSearch').addEventListener('input', draw); draw();
}

function proShell(title, subtitle, body) {
  root.innerHTML = `<section class="panel"><div class="panel-head"><div><h1>${title}</h1><p>${subtitle}</p></div><button id="backPro" class="secondary-button" type="button">مرکز حرفه‌ای</button></div><div class="panel-body">${body}</div></section>`;
  document.getElementById('backPro').addEventListener('click', renderProCenter);
}

function renderProTool(id) {
  const handlers = {
    marketplace: renderMarketplace,
    installment: renderInstallment,
    analytics: renderAnalytics,
    whatif: renderWhatIf,
    invoice: renderInvoice,
    business: renderBusiness,
    import: renderImport,
    smart: renderSmartCommand,
    converter: renderConverter,
    backup: renderSecureBackup,
    cash: renderCashCheck
  };
  handlers[id]?.();
}

function renderMarketplace() {
  proShell('مقایسه مارکت‌پلیس','پروفایل کارمزد و سود خالص',`
    <div class="form-grid">${numberField('mpCost','هزینه تمام‌شده')}${numberField('mpSale','قیمت فروش')}<div class="field"><label>نام کانال</label><input id="mpName" class="input" dir="rtl" style="text-align:right"></div>${numberField('mpFee','کارمزد درصدی','0')}${numberField('mpFixed','کارمزد ثابت','0')}</div>
    <div class="result-actions"><button id="mpCompare" class="primary-button">مقایسه</button><button id="mpAdd" class="secondary-button">افزودن پروفایل</button></div><div id="mpResult"></div><div id="mpList" class="data-list"></div>`);
  const drawProfiles = () => { document.getElementById('mpList').innerHTML = parityState.marketplaces.map(p => `<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(p.name)}</strong><span>${clean(p.feePercent)}٪ + ${money(p.fixedFee)} ثابت</span></div><button class="small-button danger" data-del-mp="${p.id}">حذف</button></div>`).join(''); document.querySelectorAll('[data-del-mp]').forEach(b=>b.onclick=()=>{parityState.marketplaces=parityState.marketplaces.filter(p=>String(p.id)!==b.dataset.delMp);paritySave(PARITY_KEYS.marketplaces,parityState.marketplaces);drawProfiles();}); };
  document.getElementById('mpAdd').onclick=()=>{const name=document.getElementById('mpName').value.trim();const fee=toNumber(document.getElementById('mpFee').value)??0;const fixed=toNumber(document.getElementById('mpFixed').value)??0;if(!name||fee<0||fixed<0)return parityToast('پروفایل نامعتبر است.');parityState.marketplaces.push({id:Date.now(),name,feePercent:fee,fixedFee:fixed});paritySave(PARITY_KEYS.marketplaces,parityState.marketplaces);drawProfiles();};
  document.getElementById('mpCompare').onclick=()=>{const cost=toNumber(document.getElementById('mpCost').value),sale=toNumber(document.getElementById('mpSale').value);if(cost==null||sale==null||cost<0||sale<=0)return;const quotes=parityState.marketplaces.map(p=>{const fees=sale*p.feePercent/100+p.fixedFee;const profit=sale-cost-fees;return{...p,fees,profit,margin:profit/sale*100};}).sort((a,b)=>b.profit-a.profit);document.getElementById('mpResult').innerHTML=quotes.length?`<div class="result-card"><span class="result-label">بهترین کانال</span><strong class="result-value" style="direction:rtl">${escapeHtml(quotes[0].name)}</strong>${quotes.map(q=>`<div class="result-row"><span>${escapeHtml(q.name)}</span><strong>${money(q.profit)} · ${percent(q.margin)}</strong></div>`).join('')}</div>`:'';};drawProfiles();
}

function renderInstallment() {
  proShell('نقد یا اقساط','هزینه واقعی خرید قسطی',`<div class="form-grid">${numberField('iCash','قیمت نقدی')}${numberField('iDown','پیش‌پرداخت','0')}${numberField('iAmount','مبلغ هر قسط')}${numberField('iCount','تعداد اقساط','12')}</div><div class="result-actions"><button id="iCalc" class="primary-button">محاسبه</button></div><div id="iResult"></div>`);
  document.getElementById('iCalc').onclick=()=>{const r=installmentResult(toNumber(iCash.value)??-1,toNumber(iDown.value)??0,toNumber(iAmount.value)??-1,Math.trunc(toNumber(iCount.value)??0));if(!r)return;renderResult(iResult,{label:'کل پرداخت اقساطی',value:`${money(r.total)} ${state.currency}`,rows:[['اضافه نسبت به نقد',`${money(r.extra)} ${state.currency}`],['درصد اضافه',percent(r.extraPercent)],['نرخ ماهانه تقریبی',percent(r.monthly)],['نرخ موثر سالانه تقریبی',percent(r.annual)]]});};
}

function renderAnalytics() {
  const valid=parityState.priceBook.filter(x=>x.quantity>0); const cheapest=valid.slice().sort((a,b)=>a.price/a.quantity-b.price/b.quantity)[0];
  const grouped={};valid.forEach(x=>(grouped[x.name]??=[]).push(x));
  proShell('تحلیل قیمت','بهترین قیمت و تغییرات ثبت‌شده',`${cheapest?`<div class="result-card"><span class="result-label">بهترین قیمت واحد</span><strong class="result-value" style="direction:rtl">${escapeHtml(cheapest.name)}</strong><div class="result-row"><span>قیمت واحد</span><strong>${money(cheapest.price/cheapest.quantity)} ${state.currency}</strong></div></div>`:'<div class="empty-state">برای تحلیل، ابتدا در دفترچه قیمت داده ثبت کن.</div>'}<div class="data-list">${Object.entries(grouped).map(([name,items])=>{items.sort((a,b)=>a.createdAt-b.createdAt);const first=items[0],last=items.at(-1);const change=first&&last&&first.price>0?(last.price-first.price)/first.price*100:0;return`<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(name)}</strong><span>${items.length} ثبت · تغییر قیمت ${percent(change)}</span></div></div>`}).join('')}</div>`);
}

function renderWhatIf() {
  proShell('شبیه‌ساز What-if','تخفیف، سود و Margin زنده',`<div class="form-grid">${numberField('wSale','قیمت فروش پایه')}${numberField('wCost','هزینه تمام‌شده')}${numberField('wFee','کارمزد (%)','0')}${numberField('wDiscount','تخفیف (%)','10')}</div><div class="result-actions"><button id="wCalc" class="primary-button">شبیه‌سازی</button></div><div id="wResult"></div>`);
  document.getElementById('wCalc').onclick=()=>{const r=whatIfResult(toNumber(wSale.value)??-1,toNumber(wCost.value)??-1,toNumber(wFee.value)??0,toNumber(wDiscount.value)??-1);if(!r)return;renderResult(wResult,{label:r.isLoss?'زیان پس از تخفیف':'سود پس از تخفیف',value:`${money(Math.abs(r.profit))} ${state.currency}`,negative:r.isLoss,rows:[['قیمت مشتری',`${money(r.customerPrice)} ${state.currency}`],['Margin',percent(r.margin)]]});};
}

function renderInvoice() {
  const draft=[];
  proShell('فاکتور آفلاین','ثبت اقلام، تخفیف و مالیات',`<div class="inline-form"><div class="field"><label>شرح</label><input id="invTitle" class="input" dir="rtl" style="text-align:right"></div>${numberField('invQty','تعداد','1')}${numberField('invPrice','قیمت واحد')}<button id="invAdd" class="primary-button">افزودن</button></div><div id="invLines" class="data-list"></div><div class="form-grid" style="margin-top:14px">${numberField('invDiscount','تخفیف (%)','0')}${numberField('invTax','مالیات (%)','0')}</div><div class="result-actions"><button id="invCalc" class="primary-button">محاسبه فاکتور</button></div><div id="invResult"></div>`);
  const draw=()=>document.getElementById('invLines').innerHTML=draft.map((l,i)=>`<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(l.title)}</strong><span>${clean(l.quantity)} × ${money(l.unitPrice)}</span></div><button class="small-button danger" data-inv-del="${i}">حذف</button></div>`).join('');
  document.getElementById('invAdd').onclick=()=>{const title=invTitle.value.trim(),quantity=toNumber(invQty.value),unitPrice=toNumber(invPrice.value);if(!title||quantity==null||quantity<=0||unitPrice==null||unitPrice<0)return;draft.push({title,quantity,unitPrice});draw();document.querySelectorAll('[data-inv-del]').forEach(b=>b.onclick=()=>{draft.splice(Number(b.dataset.invDel),1);draw();});};
  document.getElementById('invCalc').onclick=()=>{const totals=invoiceTotals(draft,toNumber(invDiscount.value)??0,toNumber(invTax.value)??0);if(!totals)return;renderResult(invResult,{label:'جمع نهایی فاکتور',value:`${money(totals.finalTotal)} ${state.currency}`,rows:[['جمع اقلام',money(totals.subtotal)],['تخفیف',money(totals.discountAmount)],['مالیات',money(totals.taxAmount)]],save:()=>{parityState.invoices.unshift({id:Date.now(),lines:[...draft],...totals});paritySave(PARITY_KEYS.invoices,parityState.invoices);parityToast('فاکتور ذخیره شد.');}});};
}

function renderBusiness() {
  proShell('پروفایل کاری','چند فروشگاه روی یک دستگاه',`<div class="form-grid"><div class="field"><label>نام فروشگاه / کسب‌وکار</label><input id="bizName" class="input" dir="rtl" style="text-align:right"></div><div class="field"><label>یادداشت</label><input id="bizNote" class="input" dir="rtl" style="text-align:right"></div></div><div class="result-actions"><button id="bizAdd" class="primary-button">ذخیره پروفایل</button></div><div id="bizList" class="data-list"></div>`);
  const draw=()=>{bizList.innerHTML=parityState.businesses.map(b=>`<div class="data-item"><div class="data-item-main"><strong>${escapeHtml(b.name)}</strong><span>${escapeHtml(b.note||'')}</span></div><button class="small-button danger" data-biz-del="${b.id}">حذف</button></div>`).join('');document.querySelectorAll('[data-biz-del]').forEach(btn=>btn.onclick=()=>{parityState.businesses=parityState.businesses.filter(b=>String(b.id)!==btn.dataset.bizDel);paritySave(PARITY_KEYS.businesses,parityState.businesses);draw();});};
  bizAdd.onclick=()=>{const name=bizName.value.trim();if(!name)return;parityState.businesses.unshift({id:Date.now(),name,note:bizNote.value.trim()});paritySave(PARITY_KEYS.businesses,parityState.businesses);draw();};draw();
}

function renderImport() {
  proShell('Import محصولات','CSV به دفترچه قیمت',`<div class="capability-note">فرمت پیشنهادی هر خط: نام,قیمت,مقدار,واحد</div><div class="field" style="margin-top:12px"><label>فایل CSV</label><input id="importCsv" class="input" type="file" accept=".csv,text/csv" style="padding:10px;direction:rtl;text-align:right"></div><div id="importResult"></div>`);
  importCsv.onchange=async e=>{const file=e.target.files?.[0];if(!file)return;const text=await file.text();let added=0;text.split(/\r?\n/).forEach((line,index)=>{if(!line.trim())return;const parts=line.split(',').map(x=>x.trim().replace(/^"|"$/g,''));if(index===0&&/price|قیمت/i.test(line))return;const [name,p,q,u]=parts;const price=toNumber(p),quantity=toNumber(q)||1;if(name&&price!=null&&price>=0&&quantity>0){parityState.priceBook.push({id:Date.now()+Math.random(),name,price,quantity,unit:u||'عدد',createdAt:Date.now()});added++;}});paritySave(PARITY_KEYS.priceBook,parityState.priceBook);importResult.innerHTML=`<div class="result-card"><span class="result-label">Import انجام شد</span><strong class="result-value">${added} ردیف</strong></div>`;};
}

function renderSmartCommand() {
  proShell('فرمان متنی','محاسبه با جمله فارسی',`<div class="field"><label>فرمان</label><input id="smartText" class="input" dir="rtl" style="text-align:right" placeholder="مثلاً 850000 خریدم 7 درصد کارمزد 30 درصد سود"></div><div class="result-actions"><button id="smartCalc" class="primary-button">تحلیل فرمان</button></div><div id="smartResult"></div>`);
  smartCalc.onclick=()=>{const cmd=parseSmartCommand(smartText.value),price=smartSuggestedPrice(cmd);smartResult.innerHTML=`<div class="result-card"><span class="result-label">تحلیل</span><div class="result-row"><span>هزینه</span><strong>${cmd.cost==null?'—':money(cmd.cost)}</strong></div><div class="result-row"><span>کارمزد</span><strong>${cmd.fee==null?'—':percent(cmd.fee)}</strong></div><div class="result-row"><span>Margin هدف</span><strong>${cmd.margin==null?'—':percent(cmd.margin)}</strong></div>${price==null?'':'<span class="result-label" style="margin-top:14px">قیمت پیشنهادی</span><strong class="result-value">'+money(price)+' '+state.currency+'</strong>'}</div>`;};
}

function renderConverter() {
  proShell('تبدیل واحد و ارز','واحدهای روزمره و نرخ دستی',`<div class="form-grid">${numberField('cvValue','مقدار')}<div class="field"><label>از</label><select id="cvFrom" class="select"><option>mg</option><option>g</option><option>kg</option><option>mm</option><option>cm</option><option>m</option><option>km</option><option>ml</option><option>l</option></select></div><div class="field"><label>به</label><select id="cvTo" class="select"><option>g</option><option>kg</option><option>mg</option><option>cm</option><option>m</option><option>km</option><option>mm</option><option>l</option><option>ml</option></select></div>${numberField('cvRate','نرخ تبدیل ارز دستی','1')}</div><div class="result-actions"><button id="cvUnit" class="primary-button">تبدیل واحد</button><button id="cvCurrency" class="secondary-button">تبدیل با نرخ دستی</button></div><div id="cvResult"></div>`);
  cvUnit.onclick=()=>{const r=convertUnit(toNumber(cvValue.value)??-1,cvFrom.value,cvTo.value);cvResult.innerHTML=r==null?'<div class="validation-message">دو واحد باید از یک گروه باشند.</div>':`<div class="result-card"><span class="result-label">نتیجه</span><strong class="result-value">${clean(r)} ${cvTo.value}</strong></div>`;};
  cvCurrency.onclick=()=>{const amount=toNumber(cvValue.value),rate=toNumber(cvRate.value);const r=amount!=null&&amount>=0&&rate!=null&&rate>0?amount*rate:null;cvResult.innerHTML=r==null?'':`<div class="result-card"><span class="result-label">نتیجه نرخ دستی</span><strong class="result-value">${money(r)}</strong></div>`;};
}

async function encryptBackup(text, password) {
  if (!crypto?.subtle) throw new Error('unsupported');
  const enc = new TextEncoder();
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const material = await crypto.subtle.importKey('raw', enc.encode(password), 'PBKDF2', false, ['deriveKey']);
  const key = await crypto.subtle.deriveKey({name:'PBKDF2',salt,iterations:120000,hash:'SHA-256'}, material, {name:'AES-GCM',length:256}, false, ['encrypt']);
  const cipher = new Uint8Array(await crypto.subtle.encrypt({name:'AES-GCM',iv}, key, enc.encode(text)));
  const b64 = bytes => btoa(String.fromCharCode(...bytes));
  return JSON.stringify({format:'HesabYar-Web-Encrypted',v:1,salt:b64(salt),iv:b64(iv),data:b64(cipher)});
}

function renderSecureBackup() {
  proShell('Backup رمزدار','AES-256-GCM با رمز کاربر در خود مرورگر',`<div class="field"><label>رمز Backup</label><input id="securePass" class="input" type="password" autocomplete="new-password"></div><div class="result-actions"><button id="secureExport" class="primary-button">ساخت Backup رمزدار</button></div><div class="capability-note">رمز در هیچ سروری ارسال یا ذخیره نمی‌شود. اگر رمز را فراموش کنی، فایل قابل بازیابی نیست.</div>`);
  secureExport.onclick=async()=>{const pass=securePass.value;if(pass.length<6)return parityToast('رمز حداقل ۶ کاراکتر باشد.');try{const encrypted=await encryptBackup(JSON.stringify(buildBackupObject()),pass);downloadText('HesabYar-secure-backup.json',encrypted,'application/json');parityToast('Backup رمزدار ساخته شد.');}catch{parityToast('Web Crypto در این مرورگر در دسترس نیست.');}};
}

function renderCashCheck() {
  proShell('Cash Check','سربه‌سر ماهانه و سود هدف',`<div class="form-grid">${numberField('ccFixed','هزینه ثابت ماهانه')}${numberField('ccContribution','سود/Contribution هر واحد')}${numberField('ccSale','قیمت فروش هر واحد')}${numberField('ccTarget','سود هدف','0')}</div><div class="result-actions"><button id="ccCalc" class="primary-button">محاسبه</button></div><div id="ccResult"></div>`);
  ccCalc.onclick=()=>{const fixed=toNumber(ccFixed.value),con=toNumber(ccContribution.value),sale=toNumber(ccSale.value),target=toNumber(ccTarget.value)??0;if(fixed==null||fixed<0||con==null||con<=0||sale==null||sale<0||target<0)return;const be=Math.ceil(fixed/con),tu=Math.ceil((fixed+target)/con);renderResult(ccResult,{label:'فروش لازم برای سربه‌سر',value:`${be} واحد`,rows:[['درآمد سربه‌سر',`${money(be*sale)} ${state.currency}`],['فروش لازم برای سود هدف',`${tu} واحد`],['درآمد برای سود هدف',`${money(tu*sale)} ${state.currency}`]]});};
}

/* ----------------------- About / Contact / Settings --------------------- */

function renderAbout() {
  root.innerHTML=`<section class="panel"><div class="panel-head"><div><h1>درباره نرم‌افزار</h1><p>حسابیار</p></div></div><div class="panel-body"><p style="line-height:2;color:var(--muted)">حسابیار مجموعه‌ای از ابزارهای مالی روزمره، دستیار خرید و فروش، دفترچه قیمت و ابزارهای حرفه‌ای برای تصمیم‌گیری سریع‌تر است. نسخه وب به‌صورت PWA روی مرورگر اجرا می‌شود و داده‌های محلی را روی همان دستگاه نگه می‌دارد.</p><div class="drawer-divider"></div><strong>نسخه 3.0.0 · Web PWA</strong></div></section>`;
}

function renderContact() {
  root.innerHTML=`<section class="panel"><div class="panel-head"><div><h1>ارتباط با ما</h1><p>AS Team Group</p></div></div><div class="panel-body"><div class="setting-row"><div><strong>راه‌های ارتباطی با ما:</strong><span>پشتیبانی و گزارش مشکل</span></div><a class="secondary-button" href="mailto:AS.Developers.Support@Gmail.Com" style="display:grid;place-items:center;text-decoration:none">ایمیل</a></div><p style="text-align:center;margin-top:34px"><strong>Develop by AS Team Group</strong></p></div></section>`;
}

/* تنظیمات پایه app.js را نگه می‌داریم و فقط ناوبری گزارش را اضافه می‌کنیم. */

/* ---------------------------- Navigation -------------------------------- */

const baseNavigate = navigate;
navigate = function parityNavigate(page) {
  state.page = page;
  state.tool = null;
  updateNav();
  const pages = {
    home: renderHome,
    history: renderHistory,
    settings: renderSettings,
    reports: renderReportsParity,
    buyer: renderBuyer,
    seller: renderSeller,
    priceBook: renderPriceBook,
    scanner: renderScanner,
    pro: renderProCenter,
    dataTools: renderDataTools,
    about: renderAbout,
    contact: renderContact
  };
  const renderer = pages[page];
  if (renderer) renderer(); else baseNavigate(page);
  window.scrollTo({ top: 0, behavior: 'smooth' });
};

/* دکمه همبرگری بالای صفحه را به Drawer وصل می‌کند. */
document.getElementById('menuButton')?.addEventListener('click', openParityDrawer);

/* Bottom Navigation جدید را به router وصل می‌کند. */
document.querySelectorAll('[data-nav]').forEach(button => {
  button.onclick = () => navigate(button.dataset.nav);
});

/* Home جدید بعد از بارگذاری لایه parity نمایش داده می‌شود. */
ensureParityDrawer();
navigate('home');
